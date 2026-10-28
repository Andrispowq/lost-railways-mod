package io.github.andrispowq.lostrailways.surface;

import io.github.andrispowq.lostrailways.LostRailwaysMod;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class SurfaceGeneration {
    // Minecraft bounds outstanding chunk work. Use dedicated workers so even flat terrain never plans on the main thread.
    private static final Executor WORKERS = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "lost-railways-terrain");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicLong GENERATED_BLOCKS = new AtomicLong();

    private SurfaceGeneration() { }

    public static Executor executor() { return WORKERS; }
    public static long generatedBlocks() { return GENERATED_BLOCKS.get(); }

    public static boolean freshTerrain(ChunkAccess chunk) {
        return chunk instanceof ProtoChunk && !(chunk instanceof ImposterProtoChunk) && !chunk.isUpgrading()
                && chunk.getPersistedStatus().isBefore(ChunkStatus.TERRAIN);
    }

    public static void generate(WorldGenContext context, ChunkStep step, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        if (!LostRailwaysMod.config().generationEnabled() || !context.level().dimension().equals(Level.OVERWORLD) || !freshTerrain(chunk)) return;
        if (!(context.generator() instanceof NoiseBasedChunkGenerator) && !(context.generator() instanceof FlatLevelSource)) return;
        if (context.level().getServer().isSameThread()) throw new IllegalStateException("Railway planning must run off the main thread");
        RailwayWorlds.Context world = RailwayWorlds.get(context.level());
        if (world == null || !world.settings().generationEnabled()) return;
        int highestSection = chunk.getHighestFilledSectionIndex();
        if (highestSection < 0 || chunk.getSectionYFromSectionIndex(highestSection) * 16 + 15 < world.surface().config().minimumRailY()) return;
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        int regionSize = world.settings().config().regionSizeBlocks();
        int limit = 29_999_000 / regionSize - 2;
        if (Math.abs((long) Math.floorDiv(cx * 16, regionSize)) > limit || Math.abs((long) Math.floorDiv(cz * 16, regionSize)) > limit) return;

        WorldGenRegion region = new WorldGenRegion(context.level(), cache, step, chunk);
        // Native TERRAIN dependencies already supply BIOMES for these neighbors. No extra chunk requests.
        List<ChunkAccess> neighbors = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkAccess neighbor = region.getChunk(cx + dx, cz + dz, ChunkStatus.STRUCTURE_REFERENCES, false);
                if (neighbor == null || (!world.surface().branchesEnabled() && structureConflict(neighbor))) return;
                neighbors.add(neighbor);
            }
        }
        BranchStructures structures = BranchStructures.inspect(neighbors, position -> {
            int distance = chunk.getPos().getChessboardDistance(position.x(), position.z());
            if (distance >= step.directDependencies().size()
                    || !ChunkStatus.STRUCTURE_STARTS.isOrBefore(step.directDependencies().get(distance))) return null;
            return region.getChunk(position.x(), position.z(), ChunkStatus.STRUCTURE_STARTS, false);
        });
        if (structures == null) return;
        var graphs = world.graphsForChunk(context.level(), cx, cz);
        var generator = context.generator();
        var random = context.level().getChunkSource().randomState();
        var heights = LevelHeightAccessor.create(chunk.getMinY(), chunk.getHeight());
        var biomes = generator.getBiomeSource().createCachingResolver(random);
        SurfaceSampler sampler = (x, z) -> {
            int y = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random);
            String biome = biomes.getNoiseBiome(Math.floorDiv(x, 4), Math.floorDiv(y, 4), Math.floorDiv(z, 4))
                    .unwrapKey().orElseThrow().identifier().toString();
            var rule = world.settings().resolvedBiomes().get(biome);
            SurfaceStyle style = y < generator.getSeaLevel() || rule == null || rule.cost() == 0 ? SurfaceStyle.EXCLUDED
                    : world.surface().styles().getOrDefault(biome, SurfaceStyle.EXCLUDED);
            return new SurfaceSampler.Column(y, style);
        };
        var pieces = new SurfaceLayout().plan(graphs, cx, cz, context.level().getSeed(), world.surface().config(), sampler, world.surface().crossingsEnabled());
        var target = new ChunkSurfaceTarget(chunk, world.surface().config().minimumRailY(), pos -> context.level().getWorldBorder().isWithinBounds(pos) && structures.allows(pos));
        if (world.surface().branchesEnabled()) {
            pieces = LocalBranches.add(pieces, target, cell -> {
                String biome = biomes.getNoiseBiome(Math.floorDiv(cell.x(), 4), Math.floorDiv(cell.y(), 4), Math.floorDiv(cell.z(), 4))
                        .unwrapKey().orElseThrow().identifier().toString();
                var rule = world.settings().resolvedBiomes().get(biome);
                return rule != null && rule.cost() > 0 && !world.surface().protectedUndergroundBiomes().contains(biome);
            }, structures::near, cx, cz, context.level().getSeed());
        }
        var result = new SurfacePlacement().place(pieces, target);
        if (result.blocks() > 0) {
            SurfaceProtection.remember(chunk, target.writtenPositions());
            // The enclosing terrain task primed final heightmaps before this continuation, while status is still BIOMES.
            Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);
            GENERATED_BLOCKS.addAndGet(result.blocks());
        }
    }

    public static boolean structureConflict(ChunkAccess chunk) {
        return chunk.getAllReferences().values().stream().anyMatch(references -> !references.isEmpty())
                || chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid());
    }
}

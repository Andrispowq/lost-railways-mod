package io.github.andrispowq.lostrailways;

import com.google.gson.JsonParser;
import io.github.andrispowq.lostrailways.config.SurfaceConfig;
import io.github.andrispowq.lostrailways.planner.NetworkEdge;
import io.github.andrispowq.lostrailways.planner.NetworkNode;
import io.github.andrispowq.lostrailways.planner.RegionGraph;
import io.github.andrispowq.lostrailways.planner.RegionPos;
import io.github.andrispowq.lostrailways.planner.RoutePoint;
import io.github.andrispowq.lostrailways.surface.ChunkSurfaceTarget;
import io.github.andrispowq.lostrailways.surface.SurfaceGeneration;
import io.github.andrispowq.lostrailways.surface.SurfaceLayout;
import io.github.andrispowq.lostrailways.surface.SurfacePiece;
import io.github.andrispowq.lostrailways.surface.SurfacePlacement;
import io.github.andrispowq.lostrailways.surface.SurfaceSampler;
import io.github.andrispowq.lostrailways.surface.SurfaceSettings;
import io.github.andrispowq.lostrailways.surface.SurfaceStyle;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class SurfaceGameTests {
    private static final SurfaceConfig INTACT = new SurfaceConfig(12, 0, 0);
    private static final SurfaceSampler FLAT = (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.PLAINS);

    @GameTest
    public void generatedRailsUseVanillaShapesAndNeverGeneratePower(GameTestHelper helper) {
        var chunk = terrain(helper);
        var pieces = new SurfaceLayout().plan(List.of(graph(false, List.of(p(0, 8), p(8, 8), p(8, 15)))), 0, 0, 1, INTACT, FLAT);
        var result = new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(chunk, 12, pos -> true));
        helper.assertTrue(result.blocks() > 25, "Surface track must materialize");
        helper.assertTrue(chunk.getBlockState(new BlockPos(8, 64, 8)).getValue(RailBlock.SHAPE) == RailShape.SOUTH_WEST,
                "Generated corner must connect west and south");
        for (var piece : pieces) for (var change : piece.blocks()) {
            var state = chunk.getBlockState(pos(change.position()));
            if (state.is(Blocks.POWERED_RAIL)) helper.assertTrue(!state.getValue(PoweredRailBlock.POWERED), "Generated rail must require player power");
            if (change.material() == SurfacePiece.Material.RAIL || change.material() == SurfacePiece.Material.POWERED_RAIL) {
                helper.assertTrue(BaseRailBlock.isRail(state), "Only vanilla rails may be generated");
            }
        }
        helper.succeed();
    }

    @GameTest
    public void protectedBlocksAndNaturalObstaclesRemainUntouched(GameTestHelper helper) {
        var graph = graph(false, List.of(p(0, 8), p(15, 8)));
        var pieces = new SurfaceLayout().plan(List.of(graph), 0, 0, 1, INTACT, FLAT);
        for (var block : List.of(Blocks.CHEST, Blocks.SPAWNER, Blocks.NETHER_PORTAL, Blocks.OAK_LOG,
                Blocks.OAK_LEAVES, Blocks.WATER, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE, Blocks.STONE_BRICKS)) {
            var chunk = terrain(helper);
            BlockPos obstacle = new BlockPos(5, 64, 8);
            chunk.setBlockState(obstacle, block.defaultBlockState());
            var result = new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(chunk, 12, pos -> true));
            helper.assertTrue(result.rejectedPieces() > 0, "Unsafe column must be rejected");
            helper.assertTrue(chunk.getBlockState(obstacle).is(block), "Protected block/tree/fluid must remain unchanged");
            helper.assertTrue(chunk.getBlockState(obstacle.below()).is(Blocks.GRASS_BLOCK), "Rejected column must not partially change its bed");
        }
        helper.succeed();
    }

    @GameTest
    public void stationConflictRejectsTheWholeTemplate(GameTestHelper helper) {
        var graph = graph(true, List.of(p(0, 0), p(15, 0)));
        List<SurfacePiece> pieces = List.of();
        for (int seed = 0; seed < 100; seed++) {
            pieces = new SurfaceLayout().plan(List.of(graph), 0, 0, seed, INTACT, FLAT);
            if (pieces.stream().anyMatch(piece -> piece.kind() == SurfacePiece.Kind.STATION)) break;
        }
        var station = pieces.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.STATION).findFirst().orElseThrow();
        var clean = terrain(helper);
        new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(clean, 12, pos -> true));
        helper.assertTrue(station.blocks().stream().allMatch(change -> clean.getBlockState(pos(change.position())).equals(ChunkSurfaceTarget.state(change))),
                "A safe station must place its entire vanilla template");
        var protectedChunk = terrain(helper);
        BlockPos chest = pos(station.blocks().stream().filter(change -> change.position().y() == 64).findFirst().orElseThrow().position());
        protectedChunk.setBlockState(chest, Blocks.CHEST.defaultBlockState());
        new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(protectedChunk, 12, pos -> true));
        helper.assertTrue(protectedChunk.getBlockState(chest).is(Blocks.CHEST), "Chest cannot be replaced");
        helper.assertTrue(station.blocks().stream().filter(change -> change.position().y() == 63)
                .allMatch(change -> protectedChunk.getBlockState(pos(change.position())).is(Blocks.GRASS_BLOCK)), "No partial platform foundation");
        helper.succeed();
    }

    @GameTest
    public void skipsExistingTerrainAndAllStructureReferences(GameTestHelper helper) {
        var chunk = terrain(helper);
        helper.assertTrue(SurfaceGeneration.freshTerrain(chunk), "New proto terrain is eligible");
        chunk.setPersistedStatus(ChunkStatus.TERRAIN);
        helper.assertTrue(!SurfaceGeneration.freshTerrain(chunk), "Existing terrain must not be retrofitted");
        helper.assertTrue(!SurfaceGeneration.freshTerrain(helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO))), "Loaded player chunks must never be eligible");
        var structure = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE).iterator().next();
        chunk.setAllReferences(Map.of(structure, new LongOpenHashSet(new long[]{1L})));
        helper.assertTrue(SurfaceGeneration.structureConflict(chunk), "Any vanilla/mod structure reference protects the chunk");
        helper.succeed();
    }

    @GameTest
    public void surfaceSnapshotSurvivesRestartAndRejectsFutureVersions(GameTestHelper helper) throws Exception {
        var directory = Files.createTempDirectory("lost-railways-surface-test-");
        var path = directory.resolve("settings.json");
        try {
            var first = SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults());
            var reloaded = SurfaceSettings.loadOrCreate(path, helper.getLevel(), new SurfaceConfig(30, 0.3, 0.1));
            helper.assertTrue(first.equals(reloaded), "Surface tuning and styles must remain frozen after restart");
            helper.assertTrue(first.crossingsEnabled(), "New snapshots enable bounded crossings");
            var legacy = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            legacy.remove("crossingsEnabled");
            Files.writeString(path, legacy.toString());
            helper.assertTrue(!SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults()).crossingsEnabled()
                    && Files.readString(path).equals(legacy.toString()), "Old snapshots retain their layout without rewriting");
            var future = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            future.addProperty("version", 2);
            Files.writeString(path, future.toString());
            boolean rejected = false;
            try { SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults()); }
            catch (IllegalArgumentException exception) { rejected = true; }
            helper.assertTrue(rejected && Files.readString(path).equals(future.toString()), "Unsupported snapshot must be preserved and rejected");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void generationProtectionSurvivesChunkSerialization(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = terrain(helper);
        var cell = new BlockPos(3, 64, 8);
        io.github.andrispowq.lostrailways.surface.SurfaceProtection.remember(chunk, List.of(cell));
        var tag = net.minecraft.world.level.chunk.storage.SerializableChunkData.copyOf(level, chunk).write();
        var data = net.minecraft.world.level.chunk.storage.SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
        var restored = data.read(level, level.getPoiManager(),
                new net.minecraft.world.level.chunk.storage.RegionStorageInfo("test", level.dimension(), "chunk"), chunk.getPos());
        helper.assertTrue(io.github.andrispowq.lostrailways.surface.SurfaceProtection.contains(restored, cell),
                "Partial terrain must retain generation protection after saving and loading");
        helper.assertTrue(!io.github.andrispowq.lostrailways.surface.SurfaceProtection.contains(restored, cell.above()),
                "Protection must only cover the exact generated cells");
        helper.succeed();
    }

    @GameTest
    public void taigaPolicyStylesAndLegacySnapshots(GameTestHelper helper) throws Exception {
        var directory = Files.createTempDirectory("lost-railways-taiga-test-");
        var path = directory.resolve("surface.json");
        try {
            var settings = SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults());
            var rules = io.github.andrispowq.lostrailways.planner.MinecraftTerrain.resolveBiomes(helper.getLevel(),
                    io.github.andrispowq.lostrailways.config.PlannerConfig.defaults());
            for (String biome : List.of("taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga")) {
                helper.assertTrue(settings.styles().get("minecraft:" + biome) == SurfaceStyle.TAIGA, "Taiga tag must resolve spruce styling");
                var rule = rules.get("minecraft:" + biome);
                helper.assertTrue(rule.cost() == 3 && rule.stations(), "Taiga must allow forest-cost routes and occasional stations");
            }
            helper.assertTrue(rules.get("minecraft:deep_dark").cost() == 0, "Protected biomes remain excluded");
            var legacy = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            legacy.getAsJsonObject("styles").addProperty("minecraft:taiga", "EXCLUDED");
            Files.writeString(path, legacy.toString());
            var reloaded = SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults());
            helper.assertTrue(reloaded.styles().get("minecraft:taiga") == SurfaceStyle.EXCLUDED
                    && Files.readString(path).equals(legacy.toString()), "Existing worlds must not silently gain a new biome style");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void taigaStationPlacesVanillaSpruceBlocks(GameTestHelper helper) {
        for (int seed = 0; seed < 100; seed++) {
            var pieces = new SurfaceLayout().plan(List.of(graph(true, List.of(p(0, 0), p(15, 0)))), 0, 0, seed, INTACT,
                    (x, z) -> new SurfaceSampler.Column(64, SurfaceStyle.TAIGA));
            var station = pieces.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.STATION).findFirst();
            if (station.isEmpty()) continue;
            var chunk = terrain(helper);
            new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(chunk, 12, position -> true));
            helper.assertTrue(station.get().blocks().stream().allMatch(change ->
                    chunk.getBlockState(pos(change.position())).equals(ChunkSurfaceTarget.state(change))), "Entire spruce station must materialize");
            for (var material : List.of(SurfacePiece.Material.SPRUCE_PLANKS, SurfacePiece.Material.SPRUCE_LOG,
                    SurfacePiece.Material.SPRUCE_SLAB, SurfacePiece.Material.SPRUCE_FENCE)) {
                var state = ChunkSurfaceTarget.state(new SurfacePiece.Change(new SurfacePiece.Cell(0, 64, 0), material));
                helper.assertTrue(state.is(switch (material) {
                    case SPRUCE_PLANKS -> Blocks.SPRUCE_PLANKS;
                    case SPRUCE_LOG -> Blocks.SPRUCE_LOG;
                    case SPRUCE_SLAB -> Blocks.SPRUCE_SLAB;
                    default -> Blocks.SPRUCE_FENCE;
                }), "Spruce palette must use vanilla blocks");
            }
            helper.succeed();
            return;
        }
        throw new AssertionError("Expected an occasional taiga station");
    }

    @GameTest
    public void remainingBiomePoliciesAndRaisedSwampSafety(GameTestHelper helper) throws Exception {
        var rules = io.github.andrispowq.lostrailways.planner.MinecraftTerrain.resolveBiomes(helper.getLevel(),
                io.github.andrispowq.lostrailways.config.PlannerConfig.defaults());
        for (String name : List.of("desert", "jungle", "sparse_jungle", "bamboo_jungle")) {
            var rule = rules.get("minecraft:" + name);
            helper.assertTrue(rule.cost() >= 6 && !rule.stations(), "Desert/jungle remain expensive transit-only biomes");
        }
        for (String name : List.of("swamp", "mangrove_swamp")) {
            helper.assertTrue(rules.get("minecraft:" + name).cost() == 10, "Wetlands must be penalized");
        }
        for (String name : List.of("ocean", "deep_dark", "jagged_peaks")) {
            helper.assertTrue(rules.get("minecraft:" + name).cost() == 0, "Protected biomes stay excluded");
        }
        var graph = graph(false, List.of(p(0, 8), p(15, 8)));
        var pieces = new SurfaceLayout().plan(List.of(graph), 0, 0, 1, INTACT,
                (x, z) -> new SurfaceSampler.Column(64, z >= 16 ? SurfaceStyle.PLAINS : SurfaceStyle.SWAMP));
        var chunk = terrain(helper);
        chunk.setBlockState(new BlockPos(4, 63, 8), Blocks.MUD.defaultBlockState());
        chunk.setBlockState(new BlockPos(5, 64, 8), Blocks.WATER.defaultBlockState());
        new SurfacePlacement().place(pieces, new ChunkSurfaceTarget(chunk, 12, position -> true));
        helper.assertTrue(chunk.getBlockState(new BlockPos(4, 64, 8)).is(Blocks.OAK_PLANKS)
                && BaseRailBlock.isRail(chunk.getBlockState(new BlockPos(4, 65, 8))), "Dry swamp edge gets a raised wooden bed");
        helper.assertTrue(chunk.getBlockState(new BlockPos(5, 64, 8)).is(Blocks.WATER)
                && chunk.getBlockState(new BlockPos(5, 65, 8)).isAir(), "Water must reject the entire raised column");
        var directory = Files.createTempDirectory("lost-railways-biomes-test-");
        var path = directory.resolve("surface.json");
        try {
            var settings = SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults());
            helper.assertTrue(settings.styles().get("minecraft:desert") == SurfaceStyle.DESERT
                    && settings.styles().get("minecraft:bamboo_jungle") == SurfaceStyle.JUNGLE
                    && settings.styles().get("minecraft:mangrove_swamp") == SurfaceStyle.SWAMP, "New styles resolve from data tags");
            helper.assertTrue(settings.equals(SurfaceSettings.loadOrCreate(path, helper.getLevel(), SurfaceConfig.defaults())),
                    "New styles survive restart");
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(directory);
        }
        helper.succeed();
    }

    @GameTest
    public void bridgesAndTunnelsPlaceAtomicallyAndProtectObstacles(GameTestHelper helper) {
        for (int height : List.of(60, 68)) {
            var pieces = new SurfaceLayout().plan(List.of(graph(false, List.of(p(0, 8), p(15, 8)))), 0, 0, 1, INTACT,
                    (x, z) -> new SurfaceSampler.Column(x >= 4 && x <= 7 ? height : 64, SurfaceStyle.PLAINS));
            var crossing = pieces.stream().filter(piece -> piece.kind() == SurfacePiece.Kind.BRIDGE
                    || piece.kind() == SurfacePiece.Kind.TUNNEL).findFirst().orElseThrow();
            var clean = crossingTerrain(helper, height);
            if (height < 64) clean.setBlockState(new BlockPos(5, 61, 8), Blocks.WATER.defaultBlockState());
            var result = new SurfacePlacement().place(List.of(crossing), new ChunkSurfaceTarget(clean, 12, position -> true));
            helper.assertTrue(result.acceptedPieces() == 1, "Safe crossing must place as a complete piece");
            helper.assertTrue(BaseRailBlock.isRail(clean.getBlockState(new BlockPos(5, 64, 8))), "Crossing rails stay level");
            helper.assertTrue(clean.getBlockState(new BlockPos(5, 65, 8)).isAir(), "Tunnel/bridge has head clearance");
            if (height > 64) helper.assertTrue(clean.getBlockState(new BlockPos(5, 66, 8)).isAir()
                    && clean.getBlockState(new BlockPos(5, 67, 8)).is(Blocks.CRACKED_STONE_BRICKS), "Tunnel has three-block clearance and a stable roof");
            if (height < 64) helper.assertTrue(clean.getBlockState(new BlockPos(5, 61, 8)).is(Blocks.WATER), "Bridge must not drain water below its deck");
            for (var obstacle : List.of(Blocks.CHEST, Blocks.SPAWNER, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE,
                    Blocks.WATER, Blocks.OAK_LOG, Blocks.SAND, Blocks.GRAVEL)) {
                var chunk = crossingTerrain(helper, height);
                var conflict = new BlockPos(5, 64, 8);
                chunk.setBlockState(conflict, obstacle.defaultBlockState());
                var rejected = new SurfacePlacement().place(List.of(crossing), new ChunkSurfaceTarget(chunk, 12, position -> true));
                helper.assertTrue(rejected.blocks() == 0 && rejected.rejectedPieces() == 1, "One conflict must reject the entire crossing");
                helper.assertTrue(chunk.getBlockState(conflict).is(obstacle), "Conflicting block must remain untouched");
                helper.assertTrue(chunk.getBlockState(new BlockPos(3, 63, 8)).is(Blocks.GRASS_BLOCK), "No partial abutment placement");
            }
            if (height > 64) {
                var cave = crossingTerrain(helper, height);
                cave.setBlockState(new BlockPos(5, 65, 9), Blocks.AIR.defaultBlockState());
                helper.assertTrue(new SurfacePlacement().place(List.of(crossing), new ChunkSurfaceTarget(cave, 12, position -> true)).blocks() == 0,
                        "Tunnel must not open into existing caves");
            }
        }
        helper.succeed();
    }

    private static ProtoChunk crossingTerrain(GameTestHelper helper, int interiorHeight) {
        var chunk = terrain(helper);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int height = x >= 4 && x <= 7 ? interiorHeight : 64;
            for (int y = 58; y <= 70; y++) chunk.setBlockState(new BlockPos(x, y, z),
                    (y >= height ? Blocks.AIR : y == height - 1 ? Blocks.GRASS_BLOCK : Blocks.STONE).defaultBlockState());
        }
        return chunk;
    }

    private static ProtoChunk terrain(GameTestHelper helper) {
        var chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, helper.getLevel(), helper.getLevel().palettedContainerFactory(), null);
        chunk.setPersistedStatus(ChunkStatus.BIOMES);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            chunk.setBlockState(new BlockPos(x, 62, z), Blocks.DIRT.defaultBlockState());
            chunk.setBlockState(new BlockPos(x, 63, z), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        return chunk;
    }

    private static RegionGraph graph(boolean station, List<RoutePoint> path) {
        return new RegionGraph(new RegionPos(0, 0), List.of(new NetworkNode("a", station ? NetworkNode.Kind.STATION : NetworkNode.Kind.BOUNDARY, path.getFirst()),
                new NetworkNode("b", NetworkNode.Kind.BOUNDARY, path.getLast())), List.of(new NetworkEdge("ab", "a", "b", path, 1)), 0, 0);
    }
    private static RoutePoint p(int x, int z) { return new RoutePoint(x, 64, z); }
    private static BlockPos pos(SurfacePiece.Cell p) { return new BlockPos(p.x(), p.y(), p.z()); }
}

package io.github.andrispowq.lostrailways;

import com.google.gson.GsonBuilder;
import io.github.andrispowq.lostrailways.planner.MinecraftTerrain;
import io.github.andrispowq.lostrailways.planner.NetworkPlanner;
import io.github.andrispowq.lostrailways.planner.RegionPos;
import io.github.andrispowq.lostrailways.surface.RailwayWorlds;
import io.github.andrispowq.lostrailways.surface.SurfaceGeneration;
import io.github.andrispowq.lostrailways.surface.SurfaceProtection;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BaseRailBlock;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public final class SurfaceClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try {
            Map<String, String> first = null;
            List<Long> timings = new ArrayList<>();
            Properties properties = new Properties();
            properties.setProperty("server-ip", "127.0.0.1");
            properties.setProperty("online-mode", "false");
            properties.setProperty("view-distance", "2");
            properties.setProperty("simulation-distance", "2");
            int rails = 0;
            for (int order = 0; order < 2; order++) {
                try (var server = context.worldBuilder().setUseConsistentSettings(false)
                        .adjustSettings(settings -> settings.setSeed("42")).createServer(properties)) {
                    var graphFuture = server.computeOnServer(instance -> {
                        var level = instance.overworld();
                        var world = RailwayWorlds.get(level);
                        var sampler = MinecraftTerrain.capture(level, world.settings());
                        return CompletableFuture.supplyAsync(() -> new NetworkPlanner().plan(42, "minecraft:overworld",
                                new RegionPos(80, 80), world.settings().config(), sampler));
                    });
                    var graph = graphFuture.get(120, TimeUnit.SECONDS);
                    if (graph.edges().isEmpty()) throw new AssertionError("Reference route must exist");
                    List<ChunkPos> chunks = new ArrayList<>(graph.edges().stream().flatMap(edge -> edge.path().stream())
                            .map(point -> new ChunkPos(Math.floorDiv(point.x(), 16), Math.floorDiv(point.z(), 16))).distinct().toList());
                    if (order == 1) Collections.reverse(chunks);
                    for (ChunkPos chunk : chunks) {
                        long start = System.nanoTime();
                        server.runOnServer(instance -> instance.overworld().getChunk(chunk.x(), chunk.z()));
                        timings.add((System.nanoTime() - start) / 1_000_000);
                    }
                    context.waitTicks(5);
                    var snapshot = snapshot(server, chunks);
                    if (first == null) first = snapshot;
                    else if (!first.equals(snapshot)) throw new AssertionError("Surface blocks differ between forward/reverse generation order");
                    rails = server.computeOnServer(instance -> {
                        int count = 0;
                        for (ChunkPos pos : chunks) {
                            var chunk = instance.overworld().getChunk(pos.x(), pos.z());
                            var cells = ((AttachmentTarget) chunk).getAttached(SurfaceProtection.CELLS);
                            if (cells != null) for (long cell : cells) {
                                if (BaseRailBlock.isRail(chunk.getBlockState(BlockPos.of(cell)))) count++;
                            }
                        }
                        return count;
                    });
                    if (LostRailwaysMod.config().generationEnabled() && rails < 20) throw new AssertionError("New terrain must contain real abandoned rails; found " + rails);
                    if (!LostRailwaysMod.config().generationEnabled() && (!snapshot.isEmpty() || SurfaceGeneration.generatedBlocks() != 0)) {
                        throw new AssertionError("Disabled generation must not place or mark any railway blocks");
                    }
                    // Ordinary player edits and repeat access to a generated chunk remain unrestricted.
                    if (!snapshot.isEmpty()) {
                        var cell = snapshot.keySet().iterator().next().split(",");
                        BlockPos edit = new BlockPos(Integer.parseInt(cell[0]), Integer.parseInt(cell[1]), Integer.parseInt(cell[2]));
                        server.runOnServer(instance -> {
                            var level = instance.overworld();
                            level.setBlock(edit, net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
                            level.getChunkAt(edit);
                            if (!level.getBlockState(edit).is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK)) throw new AssertionError("Player edit was overwritten");
                        });
                    }
                }
            }
            var output = FabricLoader.getInstance().getGameDir().resolve("surface-proof.json");
            var sorted = timings.stream().sorted().toList();
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("generationEnabled", LostRailwaysMod.config().generationEnabled());
            proof.put("orderIndependent", true);
            proof.put("railsInSample", rails);
            proof.put("protectedBlocks", first.size());
            proof.put("chunkMilliseconds", timings);
            proof.put("meanMilliseconds", timings.stream().mapToLong(Long::longValue).average().orElseThrow());
            proof.put("p95Milliseconds", sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1));
            proof.put("blocks", first);
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
            context.setScreen(net.minecraft.client.gui.screens.TitleScreen::new);
        } catch (Exception exception) {
            throw new RuntimeException("Surface generation end-to-end test failed", exception);
        }
    }

    private static Map<String, String> snapshot(TestServerContext server, List<ChunkPos> chunks) {
        return server.computeOnServer(instance -> {
            Map<String, String> result = new java.util.TreeMap<>();
            for (ChunkPos pos : chunks) {
                var chunk = instance.overworld().getChunk(pos.x(), pos.z());
                var cells = ((AttachmentTarget) chunk).getAttached(SurfaceProtection.CELLS);
                if (cells != null) for (long cell : cells) {
                    BlockPos cellPos = BlockPos.of(cell);
                    result.put(cellPos.getX() + "," + cellPos.getY() + "," + cellPos.getZ(), chunk.getBlockState(cellPos).toString());
                }
            }
            return Map.copyOf(result);
        });
    }
}

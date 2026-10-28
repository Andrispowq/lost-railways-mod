package io.github.andrispowq.lostrailways;

import io.github.andrispowq.lostrailways.planner.MinecraftTerrain;
import io.github.andrispowq.lostrailways.planner.PlannerService;
import io.github.andrispowq.lostrailways.planner.RegionPos;
import io.github.andrispowq.lostrailways.planner.WorldPlanSettings;
import io.github.andrispowq.lostrailways.planner.WorldPlanStore;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;

public final class PlannerGameTests {
    @GameTest(maxTicks = 1200)
    public void inspectsSeedTerrainWithoutLoadingRemoteChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var config = LostRailwaysMod.config().planner();
        var settings = new WorldPlanSettings(1, true, config, MinecraftTerrain.resolveBiomes(level, config));
        helper.assertTrue(settings.resolvedBiomes().values().stream().anyMatch(rule -> rule.stations()),
                "Datapack biome tags must resolve suitable terrain");
        RegionPos region = new RegionPos(80, 80);
        int chunk = region.x() * config.regionSizeChunks();
        helper.assertTrue(!level.getChunkSource().hasChunk(chunk, chunk), "Remote region starts unloaded");
        var service = new PlannerService(settings, level.getServer().getWorldPath(LevelResource.DATA).resolve("lost_railways_test_graph"));
        var request = service.inspect(level.getSeed(), level.dimension().identifier().toString(), region, MinecraftTerrain.capture(level, settings));
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0] || !request.isDone()) {
                return;
            }
            finished[0] = true;
            try {
                var inspection = request.join();
                helper.assertTrue(inspection.graph().terrainSamples() == 1089, "Terrain work must remain bounded");
                helper.assertTrue(!level.getChunkSource().hasChunk(chunk, chunk), "Inspecting must not load remote chunks");
                helper.assertTrue(!level.getChunkSource().hasChunk(chunk + 64, chunk + 64), "Interior must also remain unloaded");
                helper.assertTrue(Files.isRegularFile(inspection.file()), "Graph must be exported for inspection");
                helper.succeed();
            } finally {
                service.close();
            }
        });
    }

    @GameTest(maxTicks = 1200)
    public void registersOperatorCommandAndPreservesWorldSnapshot(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("lostrailways") != null,
                "Operator inspection command must be registered");
        var snapshot = server.getWorldPath(LevelResource.DATA).resolve("lost_railways_planner.json");
        helper.assertTrue(Files.isRegularFile(snapshot), "Server startup must persist world settings");
        String before = Files.readString(snapshot);
        var loaded = WorldPlanStore.loadOrCreate(snapshot, () -> { throw new AssertionError("Existing world settings must not be replaced"); });
        helper.assertTrue(loaded.plannerVersion() == 1 && before.equals(Files.readString(snapshot)),
                "Reloading preserves the world snapshot");
        var output = server.getWorldPath(LevelResource.DATA).resolve("lost_railways_debug/region_81_81.json");
        Files.deleteIfExists(output);
        int result = server.getCommands().getDispatcher().execute("lostrailways graph 81 81", server.createCommandSourceStack());
        helper.assertTrue(result == 1, "Operator command must queue an inspection");
        helper.succeedWhen(() -> helper.assertTrue(Files.isRegularFile(output), "Command must export the inspected graph"));
    }
}

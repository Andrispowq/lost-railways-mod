package io.github.andrispowq.lostrailways;

import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

@SuppressWarnings("UnstableApiUsage")
public final class PlannerClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        // The command test needs a connected client, not a large rendered/simulated terrain area.
        int renderDistance = context.computeOnClient(client -> client.options.renderDistance().get());
        int simulationDistance = context.computeOnClient(client -> client.options.simulationDistance().get());
        context.runOnClient(client -> {
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(2);
        });
        // Exercise the real noise generator, not only the flat server GameTest fixture.
        try (var world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(settings -> {
            settings.setSeed("42");
            settings.setAllowCommands(true);
        }).create()) {
            var server = world.getServer();
            var output = server.computeOnServer(instance -> instance.getWorldPath(LevelResource.DATA)
                    .resolve("lost_railways_debug/region_80_80.json"));
            context.runOnClient(client -> client.player.connection.sendCommand("lostrailways graph 80 80"));
            server.waitFor(instance -> Files.isRegularFile(output), 2400);
            server.runOnServer(instance -> {
                if (instance.overworld().getChunkSource().hasChunk(80 * 128, 80 * 128)) {
                    throw new AssertionError("Normal-world graph inspection must not load its remote region");
                }
            });
            try {
                var graph = JsonParser.parseString(Files.readString(output)).getAsJsonObject();
                if (graph.getAsJsonArray("edges").size() == 0) {
                    throw new AssertionError("The seed-42 reference region must contain a connected route");
                }
                if (graph.get("terrainSamples").getAsInt() != 1089) {
                    throw new AssertionError("Noise-based planning must use the bounded grid");
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            context.takeScreenshot("railway-planner-normal-world");
        } finally {
            context.runOnClient(client -> {
                client.options.renderDistance().set(renderDistance);
                client.options.simulationDistance().set(simulationDistance);
            });
        }
    }
}

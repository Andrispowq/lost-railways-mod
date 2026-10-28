package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldPlanStoreTest {
    @TempDir Path directory;

    @Test
    void restartRetainsOriginalConfigurationAndResolvedBiomePolicy() throws Exception {
        Path file = directory.resolve("data/settings.json");
        var original = new WorldPlanSettings(1, false, PlannerConfig.defaults(), Map.of("minecraft:plains", new BiomeRule(1, true)));
        assertEquals(original, WorldPlanStore.loadOrCreate(file, () -> original));
        String persisted = Files.readString(file);
        assertEquals(original, WorldPlanStore.loadOrCreate(file, () -> { throw new AssertionError("Must not reevaluate config or datapack tags"); }));
        assertEquals(persisted, Files.readString(file));
        try (var paths = Files.list(file.getParent())) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void malformedAndFutureSnapshotsFailClosedWithoutRewriting() throws Exception {
        Path file = directory.resolve("settings.json");
        for (String content : new String[]{"{", "{}", "null", "{\"plannerVersion\":2,\"generationEnabled\":true,\"config\":{},\"resolvedBiomes\":{}}"}) {
            Files.writeString(file, content);
            assertThrows(Exception.class, () -> WorldPlanStore.loadOrCreate(file, () -> { throw new AssertionError("Cannot reset an existing world"); }));
            assertEquals(content, Files.readString(file));
        }
    }
    @Test
    void incompleteOrCoercedSnapshotFieldsCannotReplanAWorld() throws Exception {
        Path file = directory.resolve("settings.json");
        WorldPlanSettings settings = new WorldPlanSettings(1, true, PlannerConfig.defaults(),
                Map.of("minecraft:plains", new BiomeRule(1, true)));
        WorldPlanStore.loadOrCreate(file, () -> settings);
        String valid = Files.readString(file);
        for (int change = 0; change < 4; change++) {
            var json = com.google.gson.JsonParser.parseString(valid).getAsJsonObject();
            switch (change) {
                case 0 -> json.addProperty("plannerVersion", 1.5);
                case 1 -> json.addProperty("generationEnabled", "true");
                case 2 -> json.getAsJsonObject("config").remove("boundaryConnectionChance");
                case 3 -> json.getAsJsonObject("resolvedBiomes").getAsJsonObject("minecraft:plains").addProperty("cost", 1.5);
                default -> throw new AssertionError();
            }
            String damaged = json.toString();
            Files.writeString(file, damaged);
            assertThrows(Exception.class, () -> WorldPlanStore.loadOrCreate(file, () -> settings));
            assertEquals(damaged, Files.readString(file));
        }
    }

}

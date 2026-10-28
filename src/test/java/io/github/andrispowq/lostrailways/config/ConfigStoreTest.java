package io.github.andrispowq.lostrailways.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
    @TempDir
    Path directory;

    @Test
    void createsDefaultConfigAndReadsItBack() throws IOException {
        Path path = directory.resolve("config/lost_railways.json");
        assertEquals(ModConfig.defaults(), ConfigStore.load(path));
        assertEquals(ModConfig.defaults(), ConfigStore.load(path));
        assertTrue(Files.isRegularFile(path));
    }

    @Test
    void preservesIndependentSwitchesAndDefaultsForMissingFields() throws IOException {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{\"generationEnabled\":false,\"elytraBalanceEnabled\":true}");
        ModConfig config = ConfigStore.load(path);
        assertFalse(config.generationEnabled());
        assertTrue(config.minecartOverhaulEnabled());
        assertTrue(config.elytraBalanceEnabled());
    }

    @Test
    void rejectsUnsupportedSchemaWithoutOverwritingFile() throws IOException {
        Path path = directory.resolve("config.json");
        String content = "{\"schemaVersion\":2}";
        Files.writeString(path, content);
        assertThrows(IllegalArgumentException.class, () -> ConfigStore.load(path));
        assertEquals(content, Files.readString(path));
    }

    @Test
    void rejectsMalformedConfigAndWrongBooleanTypes() throws IOException {
        Path path = directory.resolve("config.json");
        for (String content : new String[]{"{", "null", "{\"generationEnabled\":\"false\"}",
                "{\"schemaVersion\":1.5}", "{\"schemaVersion\":\"1\"}"}) {
            Files.writeString(path, content);
            assertThrows(RuntimeException.class, () -> ConfigStore.load(path));
            assertEquals(content, Files.readString(path));
        }
    }
    @Test
    void oldConfigFilesGainMinecartDefaultsWithoutRewriting() throws IOException {
        Path path = directory.resolve("config.json");
        String oldConfig = "{\"schemaVersion\":1,\"minecartOverhaulEnabled\":false}";
        Files.writeString(path, oldConfig);
        ModConfig config = ConfigStore.load(path);
        assertFalse(config.minecartOverhaulEnabled());
        assertEquals(MinecartConfig.defaults(), config.minecarts());
        assertEquals(PlannerConfig.defaults(), config.planner());
        assertEquals(oldConfig, Files.readString(path));
    }

    @Test
    void supportsPartialMinecartSettings() throws IOException {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{\"minecarts\":{\"poweredAcceleration\":12,\"sustainedPoweredTicks\":100}}");
        MinecartConfig config = ConfigStore.load(path).minecarts();
        assertEquals(12, config.poweredAcceleration());
        assertEquals(100, config.sustainedPoweredTicks());
        assertEquals(35, config.sustainedSpeed());
    }

    @Test
    void rejectsUnsafeOrMalformedMinecartSettingsWithoutOverwriting() throws IOException {
        Path path = directory.resolve("config.json");
        for (String fields : new String[]{"\"ordinarySpeed\":0", "\"poweredSpeed\":10", "\"sustainedSpeed\":61",
                "\"curveSpeed\":17", "\"poweredAcceleration\":-1", "\"sustainedPoweredTicks\":1.5",
                "\"ordinarySpeed\":\"NaN\"", "\"ordinarySpeed\":1e999", "\"slopeSpeed\":null"}) {
            String content = "{\"minecarts\":{" + fields + "}}";
            Files.writeString(path, content);
            assertThrows(RuntimeException.class, () -> ConfigStore.load(path));
            assertEquals(content, Files.readString(path));
        }
    }
    @Test
    void plannerDefaultsMigrateAndPartialSettingsAreValidated() throws IOException {
        Path path = directory.resolve("config.json");
        Files.writeString(path, "{\"planner\":{\"regionSizeChunks\":64}}");
        assertEquals(64, ConfigStore.load(path).planner().regionSizeChunks());
        assertEquals(PlannerConfig.defaults().biomeRules(), ConfigStore.load(path).planner().biomeRules());
        for (String fields : new String[]{"\"regionSizeChunks\":65", "\"regionSizeChunks\":128.5",
                "\"boundaryConnectionChance\":2", "\"maximumSlope\":0", "\"biomeRules\":{}", "\"biomeRules\":null"}) {
            String content = "{\"planner\":{" + fields + "}}";
            Files.writeString(path, content);
            assertThrows(RuntimeException.class, () -> ConfigStore.load(path));
            assertEquals(content, Files.readString(path));
        }
    }

}

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
}

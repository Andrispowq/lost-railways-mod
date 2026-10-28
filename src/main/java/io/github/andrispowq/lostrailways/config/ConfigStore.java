package io.github.andrispowq.lostrailways.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads configuration without overwriting malformed or newer-version files. */
public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigStore() {
    }

    public static ModConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.toAbsolutePath().getParent());
            ModConfig defaults = ModConfig.defaults();
            Files.writeString(path, GSON.toJson(defaults) + System.lineSeparator(), StandardCharsets.UTF_8);
            return defaults;
        }
        JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        ModConfig defaults = ModConfig.defaults();
        int schema = defaults.schemaVersion();
        if (json.has("schemaVersion")) {
            if (!json.get("schemaVersion").isJsonPrimitive()
                    || !json.getAsJsonPrimitive("schemaVersion").isNumber()) {
                throw new IllegalArgumentException("schemaVersion must be an integer");
            }
            schema = json.get("schemaVersion").getAsBigDecimal().intValueExact();
        }
        return new ModConfig(schema,
                readBoolean(json, "generationEnabled", defaults.generationEnabled()),
                readBoolean(json, "minecartOverhaulEnabled", defaults.minecartOverhaulEnabled()),
                readBoolean(json, "elytraBalanceEnabled", defaults.elytraBalanceEnabled()));
    }

    private static boolean readBoolean(JsonObject json, String name, boolean fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        if (!json.get(name).isJsonPrimitive() || !json.getAsJsonPrimitive(name).isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return json.get(name).getAsBoolean();
    }
}

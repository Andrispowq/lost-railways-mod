package io.github.andrispowq.lostrailways.planner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.andrispowq.lostrailways.config.PlannerConfigJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

public final class WorldPlanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private WorldPlanStore() { }

    public static WorldPlanSettings loadOrCreate(Path path, Supplier<WorldPlanSettings> initial) throws IOException {
        if (Files.exists(path)) {
            if (Files.size(path) > 1_048_576) {
                throw new IOException("World planner settings exceed 1 MiB");
            }
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String field : new String[]{"plannerVersion", "generationEnabled", "config", "resolvedBiomes"}) {
                if (!json.has(field) || json.get(field).isJsonNull()) {
                    throw new IOException("Missing world planner field: " + field);
                }
            }
            return new WorldPlanSettings(PlannerConfigJson.integer(json, "plannerVersion"),
                    PlannerConfigJson.bool(json, "generationEnabled"),
                    PlannerConfigJson.read(json.getAsJsonObject("config"), false),
                    PlannerConfigJson.rules(json.getAsJsonObject("resolvedBiomes")));
        }
        WorldPlanSettings settings = initial.get();
        writeAtomic(path, settings);
        return settings;
    }

    public static void writeAtomic(Path path, Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), ".lost-railways-", ".json.tmp");
        try {
            Files.writeString(temporary, GSON.toJson(value) + System.lineSeparator());
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

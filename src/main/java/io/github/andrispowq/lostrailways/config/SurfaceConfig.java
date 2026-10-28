package io.github.andrispowq.lostrailways.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/** Initial surface-only tuning; snapshotted independently of planner-v1 topology. */
public record SurfaceConfig(int minimumRailY, double missingRailChance, double missingBedChance) {
    public SurfaceConfig {
        if (minimumRailY < 12 || minimumRailY > 256) {
            throw new IllegalArgumentException("minimumRailY must be between 12 and 256");
        }
        probability("missingRailChance", missingRailChance, 0.6);
        probability("missingBedChance", missingBedChance, 0.15);
    }

    public static SurfaceConfig defaults() {
        return new SurfaceConfig(12, 0.18, 0.035);
    }

    public static SurfaceConfig read(JsonObject overrides, boolean defaults) {
        JsonObject fields = defaults ? new Gson().toJsonTree(defaults()).getAsJsonObject() : new JsonObject();
        overrides.entrySet().forEach(entry -> fields.add(entry.getKey(), entry.getValue()));
        for (String key : new String[]{"minimumRailY", "missingRailChance", "missingBedChance"}) {
            if (!fields.has(key) || !fields.get(key).isJsonPrimitive() || !fields.getAsJsonPrimitive(key).isNumber()) {
                throw new IllegalArgumentException("surface." + key + " must be numeric");
            }
        }
        return new SurfaceConfig(fields.get("minimumRailY").getAsBigDecimal().intValueExact(),
                fields.get("missingRailChance").getAsDouble(), fields.get("missingBedChance").getAsDouble());
    }

    private static void probability(String name, double value, double maximum) {
        if (!Double.isFinite(value) || value < 0 || value > maximum) {
            throw new IllegalArgumentException(name + " must be between 0 and " + maximum);
        }
    }
}

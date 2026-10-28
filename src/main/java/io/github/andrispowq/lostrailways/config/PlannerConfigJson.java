package io.github.andrispowq.lostrailways.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.andrispowq.lostrailways.planner.BiomeRule;

import java.util.HashMap;
import java.util.Map;

/** Strict numeric/boolean parsing; Gson's permissive coercions must not change persisted topology. */
public final class PlannerConfigJson {
    private PlannerConfigJson() { }

    public static PlannerConfig read(JsonObject json, boolean allowMissingDefaults) {
        JsonObject fields = allowMissingDefaults ? new Gson().toJsonTree(PlannerConfig.defaults()).getAsJsonObject() : new JsonObject();
        json.entrySet().forEach(entry -> fields.add(entry.getKey(), entry.getValue()));
        return new PlannerConfig(integer(fields, "regionSizeChunks"), number(fields, "boundaryConnectionChance"),
                number(fields, "maximumSlope"), rules(fields.getAsJsonObject("biomeRules")));
    }

    public static Map<String, BiomeRule> rules(JsonObject json) {
        Map<String, BiomeRule> result = new HashMap<>();
        json.entrySet().forEach(entry -> {
            if (!entry.getKey().matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
                throw new IllegalArgumentException("Biome policy keys must be namespaced identifiers");
            }
            JsonObject rule = entry.getValue().getAsJsonObject();
            result.put(entry.getKey(), new BiomeRule(integer(rule, "cost"), bool(rule, "stations")));
        });
        return Map.copyOf(result);
    }

    public static int integer(JsonObject json, String key) {
        number(json, key);
        return json.get(key).getAsBigDecimal().intValueExact();
    }

    public static boolean bool(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return json.get(key).getAsBoolean();
    }

    private static double number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()
                || !Double.isFinite(json.get(key).getAsDouble())) {
            throw new IllegalArgumentException(key + " must be a finite number");
        }
        return json.get(key).getAsDouble();
    }
}

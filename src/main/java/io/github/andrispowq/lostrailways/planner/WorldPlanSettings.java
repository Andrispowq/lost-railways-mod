package io.github.andrispowq.lostrailways.planner;

import io.github.andrispowq.lostrailways.config.PlannerConfig;
import java.util.Map;
import java.util.Objects;

/** Frozen on first world initialization; new config/datapacks cannot silently move existing routes. */
public record WorldPlanSettings(int plannerVersion, boolean generationEnabled, PlannerConfig config,
                                Map<String, BiomeRule> resolvedBiomes) {
    public WorldPlanSettings {
        if (plannerVersion != 1) {
            throw new IllegalArgumentException("Unsupported world planner version: " + plannerVersion);
        }
        Objects.requireNonNull(config, "config");
        resolvedBiomes = Map.copyOf(resolvedBiomes);
        if (resolvedBiomes.isEmpty()) {
            throw new IllegalArgumentException("World biome policy is missing");
        }
    }
}

package io.github.andrispowq.lostrailways.config;

import io.github.andrispowq.lostrailways.planner.BiomeRule;
import java.util.Map;

public record PlannerConfig(int regionSizeChunks, double boundaryConnectionChance, double maximumSlope,
                            Map<String, BiomeRule> biomeRules) {
    public PlannerConfig {
        if (regionSizeChunks < 64 || regionSizeChunks > 256 || regionSizeChunks % 32 != 0) {
            throw new IllegalArgumentException("regionSizeChunks must be a multiple of 32 between 64 and 256");
        }
        if (!Double.isFinite(boundaryConnectionChance) || boundaryConnectionChance < 0 || boundaryConnectionChance > 1
                || !Double.isFinite(maximumSlope) || maximumSlope <= 0 || maximumSlope > 0.5) {
            throw new IllegalArgumentException("Invalid boundary chance or maximum slope");
        }
        biomeRules = Map.copyOf(biomeRules);
        if (biomeRules.isEmpty() || biomeRules.size() > 32 || biomeRules.keySet().stream()
                .anyMatch(tag -> !tag.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))) {
            throw new IllegalArgumentException("Provide 1–32 biome tag rules with namespaced identifiers");
        }
    }

    public static PlannerConfig defaults() {
        return new PlannerConfig(128, 0.45, 0.25, Map.of(
                "lost_railways:preferred", new BiomeRule(1, true),
                "lost_railways:forest", new BiomeRule(3, true),
                "lost_railways:taiga", new BiomeRule(3, true),
                "lost_railways:excluded", BiomeRule.EXCLUDED));
    }

    public int regionSizeBlocks() {
        return regionSizeChunks * 16;
    }
}

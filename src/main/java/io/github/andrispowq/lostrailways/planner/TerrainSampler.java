package io.github.andrispowq.lostrailways.planner;

/** Implementations sample seed-derived terrain, never loaded chunks or mutable player builds. */
@FunctionalInterface
public interface TerrainSampler {
    Sample sample(int blockX, int blockZ);

    record Sample(int height, BiomeRule rule) {
        public Sample {
            java.util.Objects.requireNonNull(rule, "rule");
        }
    }
}

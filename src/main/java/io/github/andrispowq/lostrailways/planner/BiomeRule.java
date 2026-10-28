package io.github.andrispowq.lostrailways.planner;

/** A zero cost excludes a biome; stations require an explicitly suitable biome. */
public record BiomeRule(int cost, boolean stations) {
    public static final BiomeRule EXCLUDED = new BiomeRule(0, false);

    public BiomeRule {
        if (cost < 0 || cost > 100 || cost == 0 && stations) {
            throw new IllegalArgumentException("Biome cost must be 0–100; excluded biomes cannot host stations");
        }
    }
}

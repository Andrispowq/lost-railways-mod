package io.github.andrispowq.lostrailways.config;

/** Bootstrap switches; feature-specific settings will be introduced with their implementations. */
public record ModConfig(int schemaVersion, boolean generationEnabled, boolean minecartOverhaulEnabled,
                        boolean elytraBalanceEnabled) {
    public ModConfig {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported config schema: " + schemaVersion);
        }
    }

    public static ModConfig defaults() {
        return new ModConfig(1, true, true, false);
    }
}

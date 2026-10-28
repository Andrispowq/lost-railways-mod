package io.github.andrispowq.lostrailways.config;

import java.util.Objects;

/** Server-owned configuration; world generation and Elytra switches are reserved for later phases. */
public record ModConfig(int schemaVersion, boolean generationEnabled, boolean minecartOverhaulEnabled,
                        boolean elytraBalanceEnabled, MinecartConfig minecarts) {
    public ModConfig {
        Objects.requireNonNull(minecarts, "minecarts");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported config schema: " + schemaVersion);
        }
    }

    public static ModConfig defaults() {
        return new ModConfig(1, true, true, false, MinecartConfig.defaults());
    }
}

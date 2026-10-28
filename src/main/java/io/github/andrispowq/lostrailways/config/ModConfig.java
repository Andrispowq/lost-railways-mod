package io.github.andrispowq.lostrailways.config;

import java.util.Objects;

/** Server-owned configuration; generation settings are snapshotted per world; Elytra remains reserved. */
public record ModConfig(int schemaVersion, boolean generationEnabled, boolean minecartOverhaulEnabled,
                        boolean elytraBalanceEnabled, MinecartConfig minecarts, PlannerConfig planner) {
    public ModConfig {
        Objects.requireNonNull(minecarts, "minecarts");
        Objects.requireNonNull(planner, "planner");
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Unsupported config schema: " + schemaVersion);
        }
    }

    public static ModConfig defaults() {
        return new ModConfig(1, true, true, false, MinecartConfig.defaults(), PlannerConfig.defaults());
    }
}

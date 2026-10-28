package io.github.andrispowq.lostrailways.minecart;

import io.github.andrispowq.lostrailways.LostRailwaysMod;
import net.minecraft.world.level.Level;

/** Client mode arrives in configuration phase, before any cart is constructed. Numeric physics stays server-side. */
public final class MinecartMode {
    private static volatile boolean clientEnabled;

    private MinecartMode() {
    }

    public static boolean enabled(Level level) {
        return level.isClientSide() ? clientEnabled : LostRailwaysMod.config().minecartOverhaulEnabled();
    }

    public static void setClientEnabled(boolean enabled) {
        clientEnabled = enabled;
    }
}

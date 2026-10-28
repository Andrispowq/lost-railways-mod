package io.github.andrispowq.lostrailways.minecart;

import io.github.andrispowq.lostrailways.config.MinecartConfig;

/** Pure speed policy. Minecraft remains responsible for rail following, braking, drag and collisions. */
public final class CartSpeedRules {
    private int poweredTicks;

    public void beginTick(boolean movingOnPoweredRail, MinecartConfig config) {
        poweredTicks = movingOnPoweredRail ? Math.min(poweredTicks + 1, config.sustainedPoweredTicks()) : 0;
    }

    public void leavePoweredRail() {
        poweredTicks = 0;
    }

    public double cap(MinecartConfig config, boolean powered, boolean curve, boolean slope, boolean water) {
        double speed = powered
                ? config.poweredSpeed() + (config.sustainedSpeed() - config.poweredSpeed())
                * poweredTicks / config.sustainedPoweredTicks()
                : config.ordinarySpeed();
        if (curve) {
            speed = Math.min(speed, config.curveSpeed());
        }
        if (slope) {
            speed = Math.min(speed, config.slopeSpeed());
        }
        return speed / 20.0 * (water ? 0.5 : 1.0);
    }

    public static double accelerate(double speed, double cap, MinecartConfig config, boolean water) {
        return Math.min(cap, speed + config.poweredAcceleration() / 400.0 * (water ? 0.2 : 1.0));
    }
}

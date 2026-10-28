package io.github.andrispowq.lostrailways.config;

/** Speeds are blocks/second; acceleration is blocks/second squared. Restart to apply changes. */
public record MinecartConfig(double ordinarySpeed, double poweredSpeed, double sustainedSpeed,
                             double curveSpeed, double slopeSpeed, double poweredAcceleration,
                             int sustainedPoweredTicks) {
    public MinecartConfig {
        checkRange("ordinarySpeed", ordinarySpeed, 1, 60);
        checkRange("poweredSpeed", poweredSpeed, ordinarySpeed, 60);
        checkRange("sustainedSpeed", sustainedSpeed, poweredSpeed, 60);
        checkRange("curveSpeed", curveSpeed, 1, ordinarySpeed);
        checkRange("slopeSpeed", slopeSpeed, 1, ordinarySpeed);
        checkRange("poweredAcceleration", poweredAcceleration, 1, 80);
        if (sustainedPoweredTicks < 1 || sustainedPoweredTicks > 1200) {
            throw new IllegalArgumentException("sustainedPoweredTicks must be between 1 and 1200");
        }
    }

    public static MinecartConfig defaults() {
        return new MinecartConfig(16, 28, 35, 8, 12, 24, 60);
    }

    private static void checkRange(String name, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}

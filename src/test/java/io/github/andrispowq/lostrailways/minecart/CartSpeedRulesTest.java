package io.github.andrispowq.lostrailways.minecart;

import io.github.andrispowq.lostrailways.config.MinecartConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartSpeedRulesTest {
    private final MinecartConfig config = MinecartConfig.defaults();
    private final CartSpeedRules rules = new CartSpeedRules();

    @Test
    void sustainedCapRequiresContinuousPoweredTime() {
        assertEquals(1.4, rules.cap(config, true, false, false, false));
        for (int tick = 0; tick < 60; tick++) {
            rules.beginTick(true, config);
        }
        assertEquals(1.75, rules.cap(config, true, false, false, false));
        rules.leavePoweredRail(); // Includes a gap traversed between tick boundaries.
        assertEquals(1.4, rules.cap(config, true, false, false, false));
        assertEquals(0.8, rules.cap(config, false, false, false, false));
    }

    @Test
    void curveSlopeAndWaterLimitsTakePrecedence() {
        assertEquals(0.4, rules.cap(config, true, true, false, false));
        assertEquals(0.6, rules.cap(config, true, false, true, false));
        assertEquals(0.3, rules.cap(config, true, false, true, true));
        assertEquals(0.4, rules.cap(config, false, false, false, true));
    }

    @Test
    void accelerationIsPerTickAndDoesNotJumpToCap() {
        assertEquals(0.06, CartSpeedRules.accelerate(0, 1.75, config, false), 1.0e-9);
        assertEquals(1.75, CartSpeedRules.accelerate(1.74, 1.75, config, false));
        assertEquals(0.012, CartSpeedRules.accelerate(0, 1.75, config, true), 1.0e-9);
        double speed = 0.1;
        for (int tick = 0; tick < 100; tick++) {
            rules.beginTick(true, config);
            speed = CartSpeedRules.accelerate(speed * 0.975, rules.cap(config, true, false, false, false), config, false);
        }
        assertTrue(speed >= 1.7, "Default acceleration must overcome vanilla empty-cart drag");
    }

    @Test
    void customSettingsControlCapsAccelerationAndRampDuration() {
        MinecartConfig custom = new MinecartConfig(10, 20, 30, 5, 6, 40, 100);
        for (int tick = 0; tick < 50; tick++) {
            rules.beginTick(true, custom);
        }
        assertEquals(1.25, rules.cap(custom, true, false, false, false));
        assertEquals(0.5, rules.cap(custom, false, false, false, false));
        assertEquals(0.25, rules.cap(custom, true, true, false, false));
        assertEquals(0.3, rules.cap(custom, true, false, true, false));
        assertEquals(0.2, CartSpeedRules.accelerate(0.1, 1.25, custom, false), 1.0e-9);
    }

    @Test
    void poweredTimeIsBoundedAndResetsAtRestOrOffPower() {
        for (int tick = 0; tick < 10000; tick++) {
            rules.beginTick(true, config);
        }
        assertEquals(1.75, rules.cap(config, true, false, false, false));
        rules.beginTick(false, config);
        assertEquals(1.4, rules.cap(config, true, false, false, false));
    }
}

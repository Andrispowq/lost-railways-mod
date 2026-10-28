package io.github.andrispowq.lostrailways.mixin;

import io.github.andrispowq.lostrailways.minecart.MinecartMode;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartMixin {
    // Select vanilla's stepped movement and matching interpolation without changing the world's feature flags.
    @Inject(method = "useExperimentalMovement", at = @At("HEAD"), cancellable = true)
    private static void lostRailways$selectMovement(Level level, CallbackInfoReturnable<Boolean> callback) {
        if (MinecartMode.enabled(level)) {
            callback.setReturnValue(true);
        }
    }
}

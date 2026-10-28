package io.github.andrispowq.lostrailways.mixin;

import io.github.andrispowq.lostrailways.surface.SurfaceProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {
    // Later lakes/vegetation must not remove rail supports or partially overwrite a station.
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void lostRailways$preserveSurface(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> callback) {
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        if (Math.abs(Math.floorDiv(pos.getX(), 16) - region.getCenter().x()) <= 1
                && Math.abs(Math.floorDiv(pos.getZ(), 16) - region.getCenter().z()) <= 1
                && SurfaceProtection.contains(region.getChunk(pos), pos)) {
            callback.setReturnValue(false);
        }
    }
}

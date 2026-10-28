package io.github.andrispowq.lostrailways.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.andrispowq.lostrailways.LostRailwaysMod;
import io.github.andrispowq.lostrailways.config.MinecartConfig;
import io.github.andrispowq.lostrailways.minecart.CartSpeedRules;
import io.github.andrispowq.lostrailways.minecart.ChunkSafety;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
public abstract class NewMinecartBehaviorMixin extends MinecartBehavior {
    @Unique
    private final CartSpeedRules lostRailways$rules = new CartSpeedRules();

    protected NewMinecartBehaviorMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    // Count powered time once per server tick, never once per rail substep.
    @Inject(method = "moveAlongTrack", at = @At("HEAD"), cancellable = true)
    private void lostRailways$beginMovement(ServerLevel level, CallbackInfo callback) {
        if (!LostRailwaysMod.config().minecartOverhaulEnabled()) {
            return;
        }
        MinecartConfig config = LostRailwaysMod.config().minecarts();
        BlockPos pos = minecart.blockPosition();
        if (!ChunkSafety.hasMovementArea(pos.getX(), pos.getZ(), config.sustainedSpeed(), level.getChunkSource()::hasChunk)) {
            lostRailways$rules.leavePoweredRail();
            setDeltaMovement(Vec3.ZERO);
            ((NewMinecartBehavior) (Object) this).lerpSteps.add(new NewMinecartBehavior.MinecartStep(
                    position(), Vec3.ZERO, getYRot(), getXRot(), 1));
            callback.cancel();
            return;
        }
        BlockState state = level.getBlockState(minecart.getCurrentBlockPosOrRailBelow());
        lostRailways$rules.beginTick(lostRailways$isPowered(state) && getDeltaMovement().horizontalDistance() > 0.01, config);
    }

    // Bypass the experimental-only gamerule lookup: ordinary worlds do not register that rule.
    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void lostRailways$speedLimit(ServerLevel level, CallbackInfoReturnable<Double> callback) {
        if (LostRailwaysMod.config().minecartOverhaulEnabled()) {
            callback.setReturnValue(lostRailways$cap());
        }
    }

    // Preserve vanilla's direction/start-from-rest decision, but replace its fixed boost with configurable acceleration.
    @ModifyReturnValue(method = "calculateBoostTrackSpeed", at = @At("RETURN"))
    private Vec3 lostRailways$accelerate(Vec3 vanilla, Vec3 movement, BlockPos pos, BlockState state) {
        if (!LostRailwaysMod.config().minecartOverhaulEnabled() || !lostRailways$isPowered(state)
                || vanilla.horizontalDistanceSqr() <= movement.horizontalDistanceSqr()) {
            return vanilla;
        }
        double speed = CartSpeedRules.accelerate(movement.horizontalDistance(), lostRailways$cap(),
                LostRailwaysMod.config().minecarts(), minecart.isInWater());
        return vanilla.normalize().scale(speed);
    }

    // Vanilla clamps only the first iteration: enforce curve/slope caps and reset power on every traversed rail.
    @ModifyReturnValue(method = "calculateTrackSpeed", at = @At("RETURN"))
    private Vec3 lostRailways$limitEachRail(Vec3 movement) {
        if (!LostRailwaysMod.config().minecartOverhaulEnabled()) {
            return movement;
        }
        if (!lostRailways$isPowered(level().getBlockState(minecart.getCurrentBlockPosOrRailBelow()))) {
            lostRailways$rules.leavePoweredRail();
        }
        double cap = lostRailways$cap();
        double speed = movement.horizontalDistance();
        return speed > cap ? movement.scale(cap / speed) : movement;
    }

    @Unique
    private double lostRailways$cap() {
        BlockState state = level().getBlockState(minecart.getCurrentBlockPosOrRailBelow());
        if (!BaseRailBlock.isRail(state)) {
            return minecart.isInWater() ? 0.2 : 0.4;
        }
        RailShape shape = state.getValue(((BaseRailBlock) state.getBlock()).getShapeProperty());
        boolean curve = shape == RailShape.NORTH_EAST || shape == RailShape.NORTH_WEST
                || shape == RailShape.SOUTH_EAST || shape == RailShape.SOUTH_WEST;
        return lostRailways$rules.cap(LostRailwaysMod.config().minecarts(), lostRailways$isPowered(state),
                curve, shape.isSlope(), minecart.isInWater());
    }

    @Unique
    private static boolean lostRailways$isPowered(BlockState state) {
        return state.is(Blocks.POWERED_RAIL) && state.getValue(PoweredRailBlock.POWERED);
    }
}

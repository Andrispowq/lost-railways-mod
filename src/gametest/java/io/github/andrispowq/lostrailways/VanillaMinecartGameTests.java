package io.github.andrispowq.lostrailways;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.OldMinecartBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/** Runs in a separate JVM with a real disabled config; never mutates shared settings during parallel tests. */
public final class VanillaMinecartGameTests {
    @GameTest(structure = "lost_railways_test:track", maxTicks = 50, skyAccess = true)
    public void disabledOverhaulKeepsVanillaMovementAndSpeed(GameTestHelper helper) {
        helper.assertTrue(!LostRailwaysMod.config().minecartOverhaulEnabled(), "This suite requires disabled configuration");
        for (int x = 4; x < 70; x++) {
            helper.setBlock(x, 1, 5, Blocks.REDSTONE_BLOCK);
            helper.setBlock(new BlockPos(x, 2, 5), Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).setValue(PoweredRailBlock.POWERED, true));
        }
        var cart = helper.spawn(EntityTypes.MINECART, new Vec3(4.5, 2.1, 5.5));
        helper.assertTrue(cart.getBehavior() instanceof OldMinecartBehavior, "Disabled overhaul must retain old vanilla movement in a normal world");
        cart.setDeltaMovement(0.1, 0, 0);
        double[] lastX = {cart.getX()};
        helper.onEachTick(() -> {
            helper.assertTrue(cart.getX() - lastX[0] <= 0.401, "Vanilla displacement must remain capped at 8 blocks/s");
            lastX[0] = cart.getX();
        });
        helper.runAtTickTime(35, () -> {
            helper.assertTrue(cart.getX() > helper.absoluteVec(new Vec3(10, 0, 0)).x, "Disabled cart must still move normally");
            helper.succeed();
        });
    }
}

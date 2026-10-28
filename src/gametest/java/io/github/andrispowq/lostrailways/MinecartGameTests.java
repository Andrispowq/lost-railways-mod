package io.github.andrispowq.lostrailways;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

public final class MinecartGameTests {
    private static final String TRACK = "lost_railways_test:track";

    @GameTest(structure = TRACK, maxTicks = 100, skyAccess = true)
    public void acceleratesGraduallyAcrossLoadedChunkBoundaries(GameTestHelper helper) {
        straight(helper, 4, 155, 2, 5, true);
        AbstractMinecart cart = spawn(helper, EntityTypes.MINECART, 4.5, 2.1, 5.5, 0.1);
        helper.assertTrue(cart.getBehavior() instanceof NewMinecartBehavior, "Stepped movement must be selected");
        double origin = cart.getX();
        helper.onEachTick(() -> helper.assertTrue(cart.getDeltaMovement().horizontalDistance() <= 1.751,
                "Must never exceed the configured 35 blocks/s cap"));
        helper.runAtTickTime(10, () -> helper.assertTrue(cart.getDeltaMovement().horizontalDistance() < 1,
                "Cart must accelerate rather than jump to its maximum speed"));
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(cart.getDeltaMovement().horizontalDistance() >= 1.70, "Sustained route must reach 35 blocks/s");
            helper.assertTrue(cart.getX() - origin > 60, "Cart must cross several real chunk boundaries");
            helper.assertTrue(cart.isOnRails(), "Cart must stay on the track at chunk boundaries");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 100, skyAccess = true)
    public void highSpeedCartDoesNotTunnelThroughSolidWall(GameTestHelper helper) {
        straight(helper, 4, 95, 2, 5, true);
        helper.setBlock(96, 1, 5, Blocks.STONE);
        helper.setBlock(96, 2, 5, Blocks.STONE);
        helper.setBlock(96, 3, 5, Blocks.STONE);
        AbstractMinecart cart = spawn(helper, EntityTypes.MINECART, 4.5, 2.1, 5.5, 0.1);
        double wall = helper.absolutePos(new BlockPos(96, 2, 5)).getX();
        double[] maximum = {cart.getX(), 0};
        helper.onEachTick(() -> {
            maximum[0] = Math.max(maximum[0], cart.getX());
            maximum[1] = Math.max(maximum[1], cart.getDeltaMovement().horizontalDistance());
            helper.assertTrue(cart.getX() < wall, "Cart must not cross the wall");
        });
        helper.runAtTickTime(90, () -> {
            helper.assertTrue(maximum[0] > wall - 2, "Cart must actually reach the obstacle: " + maximum[0] + " vs " + wall);
            helper.assertTrue(maximum[1] >= 1.7, "Cart must reach high speed before the collision");
            helper.assertTrue(cart.isAlive(), "Collision must not delete the cart");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 50, skyAccess = true)
    public void turnsCornerWithoutLeavingRails(GameTestHelper helper) {
        straight(helper, 4, 18, 2, 5, true);
        for (int z = 6; z <= 21; z++) {
            helper.setBlock(19, 1, z, Blocks.STONE);
            helper.setBlock(new BlockPos(19, 2, z), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH));
        }
        helper.setBlock(19, 1, 5, Blocks.STONE);
        helper.setBlock(new BlockPos(19, 2, 5), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.SOUTH_WEST));
        AbstractMinecart cart = spawn(helper, EntityTypes.MINECART, 17.5, 2.1, 5.5, 1.75);
        Vec3 corner = helper.absoluteVec(new Vec3(19.5, 2.1, 5.5));
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(cart.getZ() > corner.z + 3, "Cart must turn south");
            helper.assertTrue(Math.abs(cart.getX() - corner.x) < 0.1, "Cart must stay centered on the outgoing rail");
            helper.assertTrue(cart.getDeltaMovement().horizontalDistance() <= 0.401, "Curve must apply its 8 blocks/s cap");
            helper.assertTrue(cart.isOnRails(), "Cart must remain on the corner route");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 40, skyAccess = true)
    public void unpoweredRailsBrakeAndOrdinaryRailsLimitSpeed(GameTestHelper helper) {
        straight(helper, 4, 80, 2, 5, false);
        AbstractMinecart braking = spawn(helper, EntityTypes.MINECART, 10.5, 2.1, 5.5, 1.75);
        for (int x = 4; x <= 80; x++) {
            helper.setBlock(x, 1, 10, Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 10), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
        AbstractMinecart coasting = spawn(helper, EntityTypes.MINECART, 10.5, 2.1, 10.5, 1.75);
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(braking.getDeltaMovement().horizontalDistance() < 0.01, "Unpowered rails must stop the cart");
            helper.assertTrue(coasting.getDeltaMovement().horizontalDistance() <= 0.801, "Ordinary rails must cap speed at 16 blocks/s");
            helper.assertTrue(coasting.getDeltaMovement().horizontalDistance() > 0.3, "Ordinary rails must preserve coasting momentum");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 50, skyAccess = true)
    public void climbsSlopeSafely(GameTestHelper helper) {
        straight(helper, 4, 19, 2, 5, true);
        straight(helper, 21, 80, 3, 5, true);
        helper.setBlock(20, 1, 5, Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(20, 2, 5), Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.ASCENDING_EAST).setValue(PoweredRailBlock.POWERED, true));
        AbstractMinecart cart = spawn(helper, EntityTypes.MINECART, 17.5, 2.1, 5.5, 1.75);
        helper.runAtTickTime(25, () -> {
            helper.assertTrue(cart.getX() > helper.absolutePos(new BlockPos(23, 0, 0)).getX(), "Cart must traverse the slope");
            helper.assertTrue(Math.abs(cart.getY() - helper.absoluteVec(new Vec3(0, 3.1, 0)).y) < 0.2, "Cart must reach the upper track");
            helper.assertTrue(cart.isOnRails(), "Cart must remain on rails after climbing");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 100, skyAccess = true)
    public void chestCartKeepsCargoThroughHighSpeedTravelAndCollision(GameTestHelper helper) {
        straight(helper, 4, 95, 2, 5, true);
        helper.setBlock(96, 1, 5, Blocks.STONE);
        helper.setBlock(96, 2, 5, Blocks.STONE);
        helper.setBlock(96, 3, 5, Blocks.STONE);
        MinecartChest cart = spawn(helper, EntityTypes.CHEST_MINECART, 4.5, 2.1, 5.5, 0.1);
        cart.setItem(0, new ItemStack(Items.DIAMOND, 37));
        cart.setItem(26, new ItemStack(Items.IRON_INGOT, 64));
        double[] maximum = {0, cart.getX()};
        for (int tick = 1; tick < 90; tick++) {
            helper.runAtTickTime(tick, () -> {
                maximum[0] = Math.max(maximum[0], cart.getDeltaMovement().horizontalDistance());
                maximum[1] = Math.max(maximum[1], cart.getX());
                helper.assertTrue(cart.getX() < helper.absolutePos(new BlockPos(96, 2, 5)).getX(), "Chest cart must never cross the wall");
                helper.assertTrue(cart.isAlive() && cart.getItem(0).is(Items.DIAMOND) && cart.getItem(0).getCount() == 37,
                        "Diamonds must not be lost or duplicated");
                helper.assertTrue(cart.getItem(26).is(Items.IRON_INGOT) && cart.getItem(26).getCount() == 64,
                        "Cargo in the last slot must remain intact");
            });
        }
        helper.runAtTickTime(90, () -> {
            double wall = helper.absolutePos(new BlockPos(96, 2, 5)).getX();
            helper.assertTrue(cart.isAlive(), "Cargo cart must survive the collision");
            helper.assertTrue(cart.getItem(0).getCount() == 37 && cart.getItem(26).getCount() == 64,
                    "Cargo must remain intact at the end of the trip");
            helper.assertTrue(maximum[0] >= 1.7, "Cargo cart must reach sustained high speed");
            helper.assertTrue(maximum[1] > wall - 2, "Cargo cart must hit the wall before any vanilla powered-rail rebound");
            helper.succeed();
        });
    }

    @GameTest(structure = TRACK, maxTicks = 110, skyAccess = true)
    public void highSpeedCartPushesAnotherCartWithoutPassingThroughIt(GameTestHelper helper) {
        straight(helper, 4, 155, 2, 5, true);
        AbstractMinecart moving = spawn(helper, EntityTypes.MINECART, 4.5, 2.1, 5.5, 0.1);
        AbstractMinecart parked = spawn(helper, EntityTypes.CHEST_MINECART, 90.5, 2.1, 5.5, 0);
        double parkedStart = parked.getX();
        helper.onEachTick(() -> helper.assertTrue(moving.getX() < parked.getX(), "Carts must not pass through each other"));
        helper.runAtTickTime(95, () -> {
            helper.assertTrue(parked.getX() > parkedStart + 2, "Impact must push the parked cart");
            helper.assertTrue(moving.isAlive() && parked.isAlive(), "Both carts must survive contact");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void stopsBeforeAnUnloadedChunkWithoutLoadingIt(GameTestHelper helper) {
        var level = helper.getLevel();
        // Deliberately load only one distant chunk in this disposable test world.
        BlockPos remote = helper.absolutePos(new BlockPos(4096, 2, 4096));
        int chunkX = remote.getX() >> 4;
        int chunkZ = remote.getZ() >> 4;
        level.getChunk(chunkX, chunkZ);
        helper.assertTrue(!level.getChunkSource().hasChunk(chunkX + 1, chunkZ), "The test's destination chunk must be unloaded");
        BlockPos rail = new BlockPos(chunkX * 16 + 14, remote.getY(), chunkZ * 16 + 8);
        level.setBlock(rail.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 2);
        level.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).setValue(PoweredRailBlock.POWERED, true), 2);
        var cart = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(cart != null, "Test cart must be created");
        cart.setPos(rail.getX() + 0.5, rail.getY() + 0.1, rail.getZ() + 0.5);
        cart.setDeltaMovement(1.75, 0, 0);
        double initialX = cart.getX();
        cart.getBehavior().moveAlongTrack(level);
        helper.assertTrue(cart.getX() == initialX && cart.getDeltaMovement().lengthSqr() == 0,
                "The production movement hook must stop before the unloaded chunk");
        helper.assertTrue(!level.getChunkSource().hasChunk(chunkX + 1, chunkZ), "Movement must not load its destination chunk");
        cart.discard();
        helper.succeed();
    }

    private static void straight(GameTestHelper helper, int from, int to, int y, int z, boolean powered) {
        for (int x = from; x <= to; x++) {
            helper.setBlock(x, y - 1, z, powered ? Blocks.REDSTONE_BLOCK : Blocks.STONE);
            helper.setBlock(new BlockPos(x, y, z), Blocks.POWERED_RAIL.defaultBlockState()
                    .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).setValue(PoweredRailBlock.POWERED, powered));
        }
    }

    private static <T extends AbstractMinecart> T spawn(GameTestHelper helper, EntityType<T> type,
                                                       double x, double y, double z, double speed) {
        T cart = helper.spawn(type, new Vec3(x, y, z));
        cart.setDeltaMovement(speed, 0, 0);
        return cart;
    }
}

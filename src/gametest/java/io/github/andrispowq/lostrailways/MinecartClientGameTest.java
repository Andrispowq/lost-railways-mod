package io.github.andrispowq.lostrailways;

import io.github.andrispowq.lostrailways.minecart.MinecartMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.Properties;

@SuppressWarnings("UnstableApiUsage")
public final class MinecartClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            ride(context, world.getServer(), world.getConnection(), "minecart-singleplayer");
        }
        Properties properties = new Properties();
        properties.setProperty("server-ip", "127.0.0.1");
        properties.setProperty("online-mode", "false");
        try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties)) {
            try (TestDedicatedServerConnection connection = server.connect()) {
                ride(context, server, connection, "minecart-dedicated");
            }
            // A second connection must receive the movement mode again, not rely on stale client state.
            try (TestDedicatedServerConnection connection = server.connect()) {
                connection.waitForChunksRender();
                context.runOnClient(client -> {
                    if (!MinecartMode.enabled(client.level)) {
                        throw new AssertionError("Server minecart mode was not synchronized on reconnect");
                    }
                });
            }
        }
    }

    private static void ride(ClientGameTestContext context, TestServerContext server,
                             TestServerConnection connection, String screenshot) {
        connection.waitForChunksRender();
        int id = server.computeOnServer(instance -> {
            var level = connection.getServerLevel();
            var player = connection.getServerPlayer();
            BlockPos start = player.blockPosition().offset(0, 0, 3);
            for (int x = 0; x < 160; x++) {
                BlockPos rail = start.offset(x, 0, 0);
                level.setBlock(rail.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                level.setBlock(rail, Blocks.POWERED_RAIL.defaultBlockState()
                        .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST)
                        .setValue(PoweredRailBlock.POWERED, true), 3);
            }
            AbstractMinecart cart = EntityTypes.MINECART.create(level, EntitySpawnReason.COMMAND);
            if (cart == null) {
                throw new AssertionError("Could not create test cart");
            }
            cart.setPos(start.getX() + 0.5, start.getY() + 0.1, start.getZ() + 0.5);
            level.addFreshEntity(cart);
            player.startRiding(cart, true, true);
            cart.setDeltaMovement(0.1, 0, 0);
            return cart.getId();
        });
        context.waitFor(client -> client.level != null && client.level.getEntity(id) instanceof AbstractMinecart);
        context.runOnClient(client -> {
            AbstractMinecart cart = (AbstractMinecart) client.level.getEntity(id);
            if (!MinecartMode.enabled(client.level) || !(cart.getBehavior() instanceof NewMinecartBehavior)) {
                throw new AssertionError("Client must use the server-selected stepped movement");
            }
        });
        server.waitFor(instance -> {
            var cart = connection.getServerLevel().getEntity(id);
            return cart != null && cart.getDeltaMovement().horizontalDistance() >= 1.7;
        }, 160);
        connection.waitForClientboundEntityUpdates(EntityTypes.MINECART);
        context.waitTicks(5);
        double serverX = server.computeOnServer(instance -> connection.getServerLevel().getEntity(id).getX());
        double clientX = context.computeOnClient(client -> client.level.getEntity(id).getX());
        // Vanilla batches updates and then interpolates over three ticks. Allow both delays plus sampling.
        if (Math.abs(serverX - clientX) > 12) {
            throw new AssertionError("Client/server cart positions diverged: " + serverX + " vs " + clientX);
        }
        context.takeScreenshot(screenshot);
        server.runOnServer(instance -> {
            AbstractMinecart cart = (AbstractMinecart) connection.getServerLevel().getEntity(id);
            BlockPos rail = cart.getCurrentBlockPosOrRailBelow();
            connection.getServerLevel().setBlock(rail, Blocks.RAIL.defaultBlockState()
                    .setValue(RailBlock.SHAPE, RailShape.EAST_WEST), 3);
            cart.setDeltaMovement(Vec3.ZERO);
        });
        context.waitTicks(20);
        double stoppedServerX = server.computeOnServer(instance -> connection.getServerLevel().getEntity(id).getX());
        double stoppedClientX = context.computeOnClient(client -> client.level.getEntity(id).getX());
        if (Math.abs(stoppedServerX - stoppedClientX) > 0.25) {
            throw new AssertionError("Client must converge to the stopped server cart position");
        }
    }
}

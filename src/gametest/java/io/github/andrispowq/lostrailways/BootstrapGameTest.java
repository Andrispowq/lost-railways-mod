package io.github.andrispowq.lostrailways;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

public final class BootstrapGameTest {
    @GameTest
    public void loadsModAndPreservesVanillaRails(GameTestHelper context) {
        if (!FabricLoader.getInstance().isModLoaded(LostRailwaysMod.MOD_ID)) {
            throw new IllegalStateException("Lost Railways was not loaded");
        }
        if (LostRailwaysMod.config().schemaVersion() != 1) {
            throw new IllegalStateException("Configuration was not initialized");
        }
        context.setBlock(0, 0, 0, Blocks.STONE);
        context.setBlock(0, 1, 0, Blocks.RAIL);
        context.assertBlockPresent(Blocks.RAIL, 0, 1, 0);
        context.succeed();
    }
}

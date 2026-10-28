package io.github.andrispowq.lostrailways;

import io.github.andrispowq.lostrailways.config.ConfigStore;
import io.github.andrispowq.lostrailways.config.ModConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class LostRailwaysMod implements ModInitializer {
    public static final String MOD_ID = "lost_railways";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ModConfig config = ModConfig.defaults();

    @Override
    public void onInitialize() {
        try {
            config = ConfigStore.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
        } catch (IOException | RuntimeException exception) {
            config = ModConfig.defaults();
            LOGGER.error("Cannot load Lost Railways configuration; using defaults without replacing the file", exception);
        }
        LOGGER.info("Lost Railways initialized (Phase A bootstrap)");
    }

    public static ModConfig config() {
        return config;
    }
}

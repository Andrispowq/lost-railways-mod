package io.github.andrispowq.lostrailways.network;

import io.github.andrispowq.lostrailways.LostRailwaysMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.chat.Component;

public final class MinecartNetworking {
    private MinecartNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundConfiguration().register(MinecartModePayload.TYPE, MinecartModePayload.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            boolean enabled = LostRailwaysMod.config().minecartOverhaulEnabled();
            if (ServerConfigurationNetworking.canSend(listener, MinecartModePayload.TYPE)) {
                ServerConfigurationNetworking.send(listener, new MinecartModePayload(enabled));
            } else if (enabled) {
                listener.disconnect(Component.literal("This server requires Lost Railways with minecart mode protocol v1."));
            }
        });
    }
}

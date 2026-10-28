package io.github.andrispowq.lostrailways;

import io.github.andrispowq.lostrailways.minecart.MinecartMode;
import io.github.andrispowq.lostrailways.network.MinecartModePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class LostRailwaysClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientConfigurationConnectionEvents.INIT.register((listener, client) -> MinecartMode.setClientEnabled(false));
        ClientConfigurationNetworking.registerGlobalReceiver(MinecartModePayload.TYPE,
                (payload, context) -> MinecartMode.setClientEnabled(payload.enabled()));
        ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> MinecartMode.setClientEnabled(false));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> MinecartMode.setClientEnabled(false));
    }
}

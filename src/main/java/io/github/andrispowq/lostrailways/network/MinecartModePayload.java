package io.github.andrispowq.lostrailways.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record MinecartModePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<MinecartModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("lost_railways", "minecart_mode_v1"));
    public static final StreamCodec<FriendlyByteBuf, MinecartModePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MinecartModePayload::enabled, MinecartModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

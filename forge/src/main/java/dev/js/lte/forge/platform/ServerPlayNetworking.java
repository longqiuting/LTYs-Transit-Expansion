package dev.js.lte.forge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Fabric {@code ServerPlayNetworking} / {@code ClientPlayNetworking} 的等价物（骨架在 {@link Net}）。 */
public final class ServerPlayNetworking {

    /** 与 Fabric 同名的形参顺序：{@code (server, player, handler, buf, responseSender)}。 */
    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(MinecraftServer server, ServerPlayer player, ServerGamePacketListenerImpl handler,
                     FriendlyByteBuf buf, PacketSender responseSender);
    }

    private ServerPlayNetworking() {
    }

    public static void registerGlobalReceiver(final ResourceLocation channelName,
                                              final PlayChannelHandler handler) {
        Net.registerServer(channelName, handler);
    }

    public static boolean canSend(final ServerPlayer player, final ResourceLocation channelName) {
        return Net.canSend(player, channelName);
    }

    public static void send(final ServerPlayer player, final ResourceLocation channelName,
                            final FriendlyByteBuf buf) {
        Net.send(player, channelName, buf);
    }
}

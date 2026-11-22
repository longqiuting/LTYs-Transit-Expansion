package dev.js.lte.forge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Fabric {@code ClientPlayNetworking} 的等价物（骨架在 {@link Net}）。 */
public final class ClientPlayNetworking {

    /** 与 Fabric 同名的形参顺序：{@code (client, handler, buf, responseSender)}。 */
    @FunctionalInterface
    public interface PlayChannelHandler {
        void receive(Minecraft client, ClientPacketListener handler, FriendlyByteBuf buf,
                     PacketSender responseSender);
    }

    private ClientPlayNetworking() {
    }

    public static void registerGlobalReceiver(final ResourceLocation channelName,
                                              final PlayChannelHandler handler) {
        Net.registerClient(channelName, handler);
    }

    public static boolean canSend(final ResourceLocation channelName) {
        return Net.canSend(channelName);
    }

    public static void send(final ResourceLocation channelName, final FriendlyByteBuf buf) {
        Net.sendToServer(channelName, buf);
    }
}

package dev.js.lte.forge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Fabric {@code ClientPlayConnectionEvents} 的等价物（由 {@code ClientPlayerNetworkEvent} 驱动）。 */
public final class ClientPlayConnectionEvents {

    public interface Join {
        void onPlayReady(ClientPacketListener handler, PacketSender sender, Minecraft client);
    }

    public interface Disconnect {
        void onPlayDisconnect(ClientPacketListener handler, Minecraft client);
    }

    public static final FEvent<Join> JOIN = new FEvent<>();
    public static final FEvent<Disconnect> DISCONNECT = new FEvent<>();

    private ClientPlayConnectionEvents() {
    }

    static void fireJoin(final ClientPacketListener handler, final Minecraft client) {
        JOIN.fire(l -> l.onPlayReady(handler, NoPacketSender.INSTANCE, client));
    }

    static void fireDisconnect(final ClientPacketListener handler, final Minecraft client) {
        DISCONNECT.fire(l -> l.onPlayDisconnect(handler, client));
    }
}

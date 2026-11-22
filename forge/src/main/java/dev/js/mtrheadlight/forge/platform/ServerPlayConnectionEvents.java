package dev.js.mtrheadlight.forge.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Fabric {@code ServerPlayConnectionEvents} 的等价物（由 {@code PlayerLoggedInEvent} 驱动）。 */
public final class ServerPlayConnectionEvents {

    public interface Join {
        void onPlayReady(ServerGamePacketListenerImpl handler, PacketSender sender, MinecraftServer server);
    }

    public interface Disconnect {
        void onPlayDisconnect(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }

    public static final FEvent<Join> JOIN = new FEvent<>();
    public static final FEvent<Disconnect> DISCONNECT = new FEvent<>();

    private ServerPlayConnectionEvents() {
    }

    static void fireJoin(final ServerGamePacketListenerImpl handler, final MinecraftServer server) {
        JOIN.fire(l -> l.onPlayReady(handler, NoPacketSender.INSTANCE, server));
    }

    static void fireDisconnect(final ServerGamePacketListenerImpl handler, final MinecraftServer server) {
        DISCONNECT.fire(l -> l.onPlayDisconnect(handler, server));
    }
}

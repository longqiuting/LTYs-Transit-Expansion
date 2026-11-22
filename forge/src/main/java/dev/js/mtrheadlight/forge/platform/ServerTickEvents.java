package dev.js.mtrheadlight.forge.platform;

import net.minecraft.server.MinecraftServer;

/** Fabric {@code ServerTickEvents} 的等价物（由 {@code TickEvent.ServerTickEvent} 驱动）。 */
public final class ServerTickEvents {

    public interface StartTick {
        void onStartTick(MinecraftServer server);
    }

    public interface EndTick {
        void onEndTick(MinecraftServer server);
    }

    public static final FEvent<StartTick> START_SERVER_TICK = new FEvent<>();
    public static final FEvent<EndTick> END_SERVER_TICK = new FEvent<>();

    private ServerTickEvents() {
    }

    public static void fireStart(final MinecraftServer server) {
        START_SERVER_TICK.fire(l -> l.onStartTick(server));
    }

    public static void fireEnd(final MinecraftServer server) {
        END_SERVER_TICK.fire(l -> l.onEndTick(server));
    }
}

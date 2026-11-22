package dev.js.mtrheadlight.forge.platform;

import net.minecraft.client.Minecraft;

/** Fabric {@code ClientTickEvents} 的等价物（由 {@code TickEvent.ClientTickEvent} END 阶段驱动）。 */
public final class ClientTickEvents {

    public interface StartTick {
        void onStartTick(Minecraft client);
    }

    public interface EndTick {
        void onEndTick(Minecraft client);
    }

    public static final FEvent<StartTick> START_CLIENT_TICK = new FEvent<>();
    public static final FEvent<EndTick> END_CLIENT_TICK = new FEvent<>();

    private ClientTickEvents() {
    }

    public static void fireStart(final Minecraft client) {
        START_CLIENT_TICK.fire(l -> l.onStartTick(client));
    }

    public static void fireEnd(final Minecraft client) {
        END_CLIENT_TICK.fire(l -> l.onEndTick(client));
    }
}

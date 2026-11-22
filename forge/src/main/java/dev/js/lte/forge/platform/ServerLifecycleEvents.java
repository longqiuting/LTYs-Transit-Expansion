package dev.js.lte.forge.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;

/** Fabric {@code ServerLifecycleEvents} 的等价物。 */
public final class ServerLifecycleEvents {

    public interface ServerStarting {
        void onServerStarting(MinecraftServer server);
    }

    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }

    public interface ServerStopped {
        void onServerStopped(MinecraftServer server);
    }

    public interface EndDataPackReload {
        void onEndDataPackReload(MinecraftServer server, ResourceManager manager, boolean success);
    }

    public static final FEvent<ServerStarting> SERVER_STARTING = new FEvent<>();
    public static final FEvent<ServerStarted> SERVER_STARTED = new FEvent<>();
    public static final FEvent<ServerStopping> SERVER_STOPPING = new FEvent<>();
    public static final FEvent<ServerStopped> SERVER_STOPPED = new FEvent<>();
    public static final FEvent<EndDataPackReload> END_DATA_PACK_RELOAD = new FEvent<>();

    private ServerLifecycleEvents() {
    }

    public static void fireStarting(final MinecraftServer server) {
        SERVER_STARTING.fire(l -> l.onServerStarting(server));
    }

    public static void fireStarted(final MinecraftServer server) {
        SERVER_STARTED.fire(l -> l.onServerStarted(server));
    }

    public static void fireStopping(final MinecraftServer server) {
        SERVER_STOPPING.fire(l -> l.onServerStopping(server));
    }

    public static void fireStopped(final MinecraftServer server) {
        SERVER_STOPPED.fire(l -> l.onServerStopped(server));
    }

    public static void fireDataPackReload(final MinecraftServer server, final ResourceManager manager,
                                          final boolean success) {
        END_DATA_PACK_RELOAD.fire(l -> l.onEndDataPackReload(server, manager, success));
    }
}

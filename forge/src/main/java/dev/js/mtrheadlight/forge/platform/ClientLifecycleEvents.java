package dev.js.mtrheadlight.forge.platform;

import net.minecraft.client.Minecraft;

/**
 * Fabric {@code ClientLifecycleEvents} 的等价物。
 *
 * <p>Forge 1.20.1 没有「客户端启动完成」事件：这里由第一个 {@code ClientTickEvent} 触发一次
 * {@code CLIENT_STARTED}（此时资源包已加载完，标题界面已经能画），由 JVM/游戏关闭钩子触发一次
 * {@code CLIENT_STOPPING}。
 */
public final class ClientLifecycleEvents {

    public interface ClientStarted {
        void onClientStarted(Minecraft client);
    }

    public interface ClientStopping {
        void onClientStopping(Minecraft client);
    }

    public static final FEvent<ClientStarted> CLIENT_STARTED = new FEvent<>();
    public static final FEvent<ClientStopping> CLIENT_STOPPING = new FEvent<>();

    private ClientLifecycleEvents() {
    }

    public static void fireStarted(final Minecraft client) {
        CLIENT_STARTED.fire(l -> l.onClientStarted(client));
    }

    public static void fireStopping(final Minecraft client) {
        CLIENT_STOPPING.fire(l -> l.onClientStopping(client));
    }
}

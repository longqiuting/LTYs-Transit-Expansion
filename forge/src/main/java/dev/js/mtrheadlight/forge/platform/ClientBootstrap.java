package dev.js.mtrheadlight.forge.platform;

import dev.js.mtrheadlight.LteClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端入口接线。
 *
 * <p>职责与 Fabric 的 {@code client} entrypoint 一一对应：先跑 {@link LteClient#onInitializeClient()}
 * （灯光 / 光束 / 散射 / 碰撞 / 内置车型 / 车辆灯 JSON / 信号连接器的初始化与事件注册），再把
 * 「排队式」注册（粒子工厂、按键、资源重载监听器、客户端指令、渲染阶段）在各自的 Forge 事件里回放。
 *
 * <p>这个类只在 {@code Dist.CLIENT} 分支被加载（{@code LteForge} 里的 if），专用服务端不会碰它。
 */
public final class ClientBootstrap {

    private static boolean initialised;
    private static boolean startedGameFired;
    private static boolean stoppingHookInstalled;

    private ClientBootstrap() {
    }

    public static void init(final IEventBus modBus) {
        if (initialised) {
            return;
        }
        initialised = true;

        modBus.addListener(ClientBootstrap::onClientSetup);
        modBus.addListener(ParticleFactoryRegistry::onRegisterProviders);
        modBus.addListener(KeyBindingHelper::onRegisterKeyMappings);
        modBus.addListener(ResourceManagerHelper::onRegisterClientReloadListeners);

        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onRegisterClientCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::onClientTick);

        Net.installClientHooks();
        installStoppingHook();
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        // enqueueWork：Fabric 的 onInitializeClient 也是主线程跑的，保持一致。
        event.enqueueWork(() -> {
            try {
                new LteClient().onInitializeClient();
            } catch (Throwable t) {
                dev.js.mtrheadlight.LteLog.LOGGER.error("[lte] client init failed", t);
            }
        });
    }

    private static void onRegisterClientCommands(final RegisterClientCommandsEvent event) {
        ClientCommandRegistrationCallback.fire(event);
    }

    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (!WorldRenderEvents.START.isEmpty() || !WorldRenderEvents.AFTER_ENTITIES.isEmpty()
                || !WorldRenderEvents.AFTER_TRANSLUCENT.isEmpty() || !WorldRenderEvents.LAST.isEmpty()) {
            WorldRenderEvents.fire(event);
        }
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        final Minecraft client = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            ClientTickEvents.fireStart(client);
            return;
        }
        ClientTickEvents.fireEnd(client);
        if (!startedGameFired && client.getWindow() != null) {
            // Forge 1.20.1 没有「客户端启动完成」事件：首个 END tick 即视为启动完成
            // （此时资源包已加载、标题界面已能画）。
            startedGameFired = true;
            ClientLifecycleEvents.fireStarted(client);
            dev.js.mtrheadlight.LteLog.LOGGER.info("[lte] client started (forge)");
        }
    }

    private static void installStoppingHook() {
        if (stoppingHookInstalled) {
            return;
        }
        stoppingHookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ClientLifecycleEvents.fireStopping(Minecraft.getInstance());
            } catch (Throwable ignored) {
                // 关游戏时不许再抛。
            }
        }, "lte-client-stopping"));
    }

    /** 供诊断：当前 dist。 */
    public static Dist dist() {
        return Dist.CLIENT;
    }
}

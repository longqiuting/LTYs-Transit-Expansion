package dev.js.lte.forge.platform;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * 双端共用的 Forge 事件接线：把 Forge 事件翻译成 java-gen 认的 Fabric 形状。
 *
 * <p>只碰双端都存在的类（{@code TickEvent} / {@code ServerXxxEvent} / {@code PlayerInteractEvent} /
 * {@code BlockEvent} / {@code RegisterCommandsEvent}），所以专用服务端加载这个类不会碰到客户端类。
 */
public final class ForgeHooks {

    private static boolean installed;

    private ForgeHooks() {
    }

    public static void installCommon() {
        if (installed) {
            return;
        }
        installed = true;
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeHooks::onBlockBreak);
    }

    private static void onServerStarting(final ServerStartingEvent event) {
        ServerLifecycleEvents.fireStarting(event.getServer());
    }

    private static void onServerStarted(final ServerStartedEvent event) {
        ServerLifecycleEvents.fireStarted(event.getServer());
        ServerLifecycleEvents.fireDataPackReload(event.getServer(),
                event.getServer().getResourceManager(), true);
    }

    private static void onServerStopping(final ServerStoppingEvent event) {
        ServerLifecycleEvents.fireStopping(event.getServer());
    }

    private static void onServerStopped(final ServerStoppedEvent event) {
        ServerLifecycleEvents.fireStopped(event.getServer());
    }

    private static void onServerTick(final TickEvent.ServerTickEvent event) {
        final MinecraftServer server = event.getServer();
        if (event.phase == TickEvent.Phase.START) {
            ServerTickEvents.fireStart(server);
        } else {
            ServerTickEvents.fireEnd(server);
        }
    }

    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        CommandRegistrationCallback.fire(event);
    }

    /** {@code /reload}：先通知「数据包要重载了」，等资源管理器换完之后由 DatapackSync 再通知一次。 */
    private static void onAddReloadListener(final AddReloadListenerEvent event) {
        // 没有 server 引用：真正的 reload 完成通知走 OnDatapackSyncEvent。
    }

    private static void onDatapackSync(final OnDatapackSyncEvent event) {
        final MinecraftServer server = event.getPlayerList().getServer();
        if (server != null) {
            ServerLifecycleEvents.fireDataPackReload(server, server.getResourceManager(), true);
        }
    }

    /**
     * Fabric {@code AttackBlockCallback}：两端都触发，返回 {@code SUCCESS} 时取消这次左键。
     * Forge 对应 {@code PlayerInteractEvent.LeftClickBlock}（客户端与服务端都会 fire）。
     */
    private static void onLeftClickBlock(final PlayerInteractEvent.LeftClickBlock event) {
        if (AttackBlockCallback.EVENT.isEmpty() || event.getEntity() == null) {
            return;
        }
        final InteractionResult result = invokeAttack(event);
        if (result == InteractionResult.SUCCESS) {
            // Fabric 的语义：客户端本地不打、服务端也不破坏；Forge 用 cancel 达到同样效果。
            event.setCanceled(true);
            event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    private static InteractionResult invokeAttack(final PlayerInteractEvent.LeftClickBlock event) {
        final InteractionResult[] result = {InteractionResult.PASS};
        AttackBlockCallback.EVENT.fire(l -> {
            final InteractionResult r = l.interact(event.getEntity(), event.getLevel(),
                    net.minecraft.world.InteractionHand.MAIN_HAND, event.getPos(), event.getFace());
            if (r == InteractionResult.SUCCESS) {
                result[0] = InteractionResult.SUCCESS;
            }
        });
        return result[0];
    }

    /** Fabric {@code PlayerBlockBreakEvents.AFTER}：方块已经被拆掉之后清绑定。 */
    private static void onBlockBreak(final BlockEvent.BreakEvent event) {
        if (PlayerBlockBreakEvents.AFTER.isEmpty()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        final BlockEntity entity = level.getBlockEntity(event.getPos());
        PlayerBlockBreakEvents.AFTER.fire(l -> l.afterBlockBreak(level, event.getPlayer(),
                event.getPos(), event.getState(), entity));
    }
}

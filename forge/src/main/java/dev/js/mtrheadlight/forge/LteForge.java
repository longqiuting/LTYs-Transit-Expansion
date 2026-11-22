package dev.js.mtrheadlight.forge;

import dev.js.mtrheadlight.VehicleLightsJsonServer;
import dev.js.mtrheadlight.forge.platform.ClientBootstrap;
import dev.js.mtrheadlight.forge.platform.ForgeHooks;
import dev.js.mtrheadlight.forge.platform.MixinProbe;
import dev.js.mtrheadlight.forge.platform.Net;
import dev.js.mtrheadlight.forge.platform.Reg;
import dev.js.mtrheadlight.server.LteServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;

/**
 * Forge 入口（{@code lte}）。
 *
 * <p>顺序与 Fabric 的 {@code main} entrypoint 一致，但**不能**在构造器里跑注册：
 * <ol>
 *   <li>{@link LteForge#onRegister} 先挂 mod 总线（必须在 {@link Reg#register} 之前，见下）；</li>
 *   <li>{@link Reg#register} —— 三个 {@code DeferredRegister} 绑 mod 总线；</li>
 *   <li>{@link ForgeHooks#installCommon} / {@link Net#installCommonHooks} —— 事件与网络钩子；</li>
 *   <li>第一个 {@link RegisterEvent} 回调里跑 {@link LteServer#onInitialize()} 与
 *       {@link VehicleLightsJsonServer#onInitialize()} —— 与 Fabric main entrypoint 同一份代码；</li>
 *   <li>{@code Dist.CLIENT} 时 {@link ClientBootstrap#init} —— 客户端初始化与渲染/粒子/按键/指令接线。</li>
 * </ol>
 *
 * <p>为什么第 4 步必须延后：Forge 1.20.1 给 {@code Item} / {@code Block} 的构造器打了补丁，
 * 构造即建 intrusive holder；注册表在 {@code RegisterEvent} 之外是 frozen 的，在 {@code @Mod}
 * 构造器里 {@code new SignalLinkItem(...)} 会抛 {@code IllegalStateException: Registry is already frozen}。
 * Fabric 那套「new 完立刻 register」只有在这个回调里才成立。
 */
@Mod("lte")
public final class LteForge {

    /** 只跑第一次 RegisterEvent 回调——首个回调实测是 {@code SOUND_EVENT} 而不是 {@code BLOCK}，所以判据是「第一次」而非某个注册表。 */
    private static boolean registered;

    public LteForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1) 注册期回调：必须比 DeferredRegister 自己的监听器先挂上，
        //    这样「造实例」先于「DeferredRegister 用正式 id 落表」。
        modBus.addListener(LteForge::onRegister);

        // 2) 注册排队（DeferredRegister 绑 mod 总线）
        Reg.register(modBus);
        modBus.addListener(Reg::onBuildTabContents);

        // 3) 双端事件 / 网络钩子
        ForgeHooks.installCommon();
        Net.installCommonHooks();

        // 3b) 注册阶段结束后做一次 mixin 自检：空世界里 mtr.data.TrainServer 不会自然加载，
        //     「注入没生效」和「世界还没有列车」在日志里长得一样，这里把宿主提前加载并读出结果。
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(MixinProbe::check));

        // 4) 客户端
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(modBus);
        }

        ModLoadingContext.get().getActiveContainer().getModInfo(); // 保证 ModList 已就绪（无副作用）
        dev.js.mtrheadlight.LteLog.LOGGER.info("[lte] forge entrypoint initialised ({})", FMLEnvironment.dist);
    }

    /**
     * 注册阶段：在第一个 {@link RegisterEvent} 里跑 java-gen 的整套初始化（配置、握手、物品与页签、
     * {@code /ltebind}、解绑钩子、服务端 tick、注册表自检、车辆灯 JSON）。
     *
     * <p>整个 RegisterEvent 阶段内所有注册表都是 unfrozen 的，此时 {@code new Item(...)} 合法；
     * 而 6 个 DeferredRegister 各自的监听器还没收到自己的事件（本监听器先挂），所以业务代码
     * 往 {@code Reg.ITEMS} / {@code Reg.TABS} 里塞的条目仍会被正常刷出。
     */
    private static void onRegister(final RegisterEvent event) {
        if (registered) {
            return;
        }
        registered = true;
        new LteServer().onInitialize();
        new VehicleLightsJsonServer().onInitialize();
    }
}

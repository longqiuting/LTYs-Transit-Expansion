package dev.js.mtrheadlight.forge;

import dev.js.mtrheadlight.VehicleLightsJsonServer;
import dev.js.mtrheadlight.forge.platform.ClientBootstrap;
import dev.js.mtrheadlight.forge.platform.ForgeHooks;
import dev.js.mtrheadlight.forge.platform.Net;
import dev.js.mtrheadlight.forge.platform.Reg;
import dev.js.mtrheadlight.server.LteServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge 入口（{@code lte}）。
 *
 * <p>顺序与 Fabric 的 {@code main} entrypoint 一致：
 * <ol>
 *   <li>{@link Reg#register} —— 三个 {@code DeferredRegister} 先绑 mod 总线；</li>
 *   <li>{@link ForgeHooks#installCommon} / {@link Net#installCommonHooks} —— 事件与网络钩子；</li>
 *   <li>{@link LteServer#onInitialize()} —— 配置、握手、物品与页签、{@code /ltebind}、解绑钩子、
 *       服务端 tick、注册表自检；</li>
 *   <li>{@link VehicleLightsJsonServer#onInitialize()} —— 服务端车辆灯 JSON 的读盘与重载；</li>
 *   <li>{@code Dist.CLIENT} 时 {@link ClientBootstrap#init} —— 客户端初始化与渲染/粒子/按键/指令接线。</li>
 * </ol>
 */
@Mod("lte")
public final class LteForge {

    public LteForge() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 1) 注册排队（必须在任何对象被注册之前）
        Reg.register(modBus);
        modBus.addListener(Reg::onBuildTabContents);

        // 2) 双端事件 / 网络钩子
        ForgeHooks.installCommon();
        Net.installCommonHooks();

        // 3) 与 Fabric main entrypoint 同一份代码
        new LteServer().onInitialize();
        new VehicleLightsJsonServer().onInitialize();

        // 4) 客户端
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init(modBus);
        }

        ModLoadingContext.get().getActiveContainer().getModInfo(); // 保证 ModList 已就绪（无副作用）
        dev.js.mtrheadlight.LteLog.LOGGER.info("[lte] forge entrypoint initialised ({})", FMLEnvironment.dist);
    }
}

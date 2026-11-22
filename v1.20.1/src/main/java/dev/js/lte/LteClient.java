package dev.js.lte;

import dev.js.lte.collision.CollisionBootstrap;
import dev.js.lte.mtr.BuiltInTrainsInit;
import dev.js.lte.signal.SignalLinkClient;
import net.fabricmc.api.ClientModInitializer;

/**
 * {@code client} 入口点：灯光 / 光束 / 散射、碰撞、内置车型、车辆灯 JSON、信号连接器。
 *
 * <h2>入口形状</h2>
 * 拆分前 {@code fabric.mod.json} 的 {@code client} 列了 18 个 {@code ClientModInitializer}，混着优化与装饰；
 * 拆分后本模组只有这一个 client 入口，内部按<b>原来的注册顺序</b>调用 A 侧那批初始化器 ——
 * 顺序保持不变，所以注册 / 页签 / 渲染器的结果与拆分前一致。装饰侧的初始化器已随 LTED 仓库迁出。
 */
public final class LteClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 核心客户端：读配置、每 tick 的灯光控制、/mtr_lte、渲染钩子。
        new MtrHeadlightClient().onInitializeClient();
        // 列车碰撞（推出 / 车顶 / 道床）。
        new CollisionBootstrap().onInitializeClient();
        // 内置车型屏蔽（disableBuiltInTrains）。
        new BuiltInTrainsInit().onInitializeClient();
        // 车辆灯 JSON（资源包覆盖）。
        new VehicleLightsJsonClient().onInitializeClient();
        // 信号连接器：绑定表的客户端镜像与信号灯状态同步。
        new SignalLinkClient().onInitializeClient();
    }
}

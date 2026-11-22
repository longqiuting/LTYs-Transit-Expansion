package dev.js.lte.forge.platform;

import dev.js.lte.LteLog;

import java.lang.reflect.Method;

/**
 * Mixin 自检：把宿主类**提前加载**并检查我们注入的合成处理函数是否真的在。
 *
 * <p>为什么需要：Forge 侧按宿主拆了 4 份 mixin 配置，其中 {@code mtr.data.TrainServer} 这类宿主
 * 只有在存档里真有列车时才会被加载 —— 空世界里 {@code Mixing ... into mtr.data.TrainServer}
 * 永远不会出现，于是「注入没生效」和「世界还没列车」在日志里长得一模一样。这里在注册阶段结束后
 * 主动 {@code Class.forName(..., false, ...)}（只加载、不初始化），既让 Mixin 有机会应用，
 * 也能直接读出 {@code mtrheadlight$} 前缀的处理函数是否存在。
 *
 * <p>只做诊断，不改任何游戏状态；宿主不存在（没装 MTR / 没装方速）时静默跳过。
 */
public final class MixinProbe {

    /** 宿主类名 → 说明。只列「靠宿主自身加载时机不可控」的那几个。 */
    private static final String[][] HOSTS = {
            {"mtr.data.TrainServer", "MTR 服务端列车（列车碰撞捕获）"},
            {"mtr.data.RailwayData", "MTR 线路数据（碰撞捕获/信号灯）"},
    };

    private MixinProbe() {
    }

    /** 在 FMLCommonSetupEvent 里调用：此时所有 mixin 配置已加载，宿主类可以安全探测。 */
    public static void check() {
        for (final String[] host : HOSTS) {
            try {
                final Class<?> type = Class.forName(host[0], false, MixinProbe.class.getClassLoader());
                boolean applied = false;
                for (final Method m : type.getDeclaredMethods()) {
                    if (m.getName().contains("mtrheadlight$")) {
                        applied = true;
                        break;
                    }
                }
                LteLog.LOGGER.info("[lte] mixin probe: {} ({}) -> {}", host[0], host[1],
                        applied ? "applied" : "NOT applied");
            } catch (ClassNotFoundException notInstalled) {
                // 宿主没装，正常跳过
            } catch (Throwable t) {
                LteLog.LOGGER.warn("[lte] mixin probe failed for {}", host[0], t);
            }
        }
    }
}

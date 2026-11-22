package dev.js.mtrheadlight.server;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteLog;
import net.fabricmc.api.ModInitializer;

/**
 * {@code main} 入口点 —— 灯光 / 光束 / 碰撞 / 道床 / 连接器 / 服务端权威。
 *
 * <h2>拆分后的形态</h2>
 * 本类原先是一个「兼容壳」，同时调 A（优化）与 B（装饰）两半；拆出独立仓库后只剩 A 侧，
 * 于是壳本身也简化成直接调 {@link LteServerLights}。装饰侧（厦门系列 / PIDS / 时钟 / 设备）
 * 已随 LTED 仓库迁出，这里不再有任何引用。
 *
 * <p>本类同时保留两个注册包装 {@link #module} / {@link #blockModule}：失败时的日志格式
 * （ERROR + 全栈、点名漏网方块）与拆分前一字不差。
 */
public final class LteServer implements ModInitializer {

    @Override
    public void onInitialize() {
        new LteServerLights().onInitialize();
        // 方块注册表自检：必须排在所有方块注册完之后（孤儿方块点名 / SERVER_STARTING 全表扫描）。
        LteServerLights.installRegistrySelfCheck();
    }

    /** 本侧配置（A 侧持有；保留这个入口，老的调用点不用改）。 */
    public static HeadlightConfig config() {
        return LteServerLights.config();
    }

    /**
     * 跑一个模块的注册；失败时 <b>ERROR + 全栈</b>（不再是 warn），但绝不让服务端起不来。
     *
     * <p>{@code name} 会出现在日志里 —— 服主一眼能看到是哪一块坏了。
     */
    static void module(final String name, final Runnable body) {
        try {
            body.run();
        } catch (final Throwable t) {
            LteLog.LOGGER.error("[lte] {} registration aborted (the rest of the mod keeps working)", name, t);
        }
    }

    /**
     * 注册方块的模块：除了 {@link #module} 的 ERROR 日志，还额外点名「建了但没注册」的方块。
     *
     * <p>1.20.1 的 {@code Block} 构造器一执行就挂进注册表的「待绑定」表，只有
     * {@code Registry.register} 才会补上 id。所以模块在「建了一批、还没注册完」的时候抛异常，
     * 就会留下一批孤儿 Block —— 注册表冻结时抛
     * {@code Some intrusive holders were not registered: [Reference{null=Block{minecraft:air}}]}，
     * 而且崩溃信息里全是一样的 {@code minecraft:air}，完全看不出是谁。
     *
     * <p>这里量两件事并写进日志：<b>注册进去几块</b>（注册表里本模组条目数变化）、
     * <b>建了但没注册几块</b>（「待绑定」表条目数变化）。随后立刻清掉这些孤儿引用
     * （{@link RegistrySelfCheck#purgePending}），服务端就能正常冻结注册表、起到 {@code Done}。
     */
    static void blockModule(final String name, final Runnable body) {
        final int blocksBefore = RegistrySelfCheck.countBlocks();
        final int oursBefore = RegistrySelfCheck.countBlocks("lte");
        final int pendingBefore = RegistrySelfCheck.pendingCount();
        try {
            body.run();
        } catch (final Throwable t) {
            final int registered = RegistrySelfCheck.countBlocks("lte") - oursBefore;
            final int leaked = Math.max(0, RegistrySelfCheck.pendingCount() - pendingBefore);
            LteLog.LOGGER.error("[lte] {} registration aborted: {} block(s) registered, {} block(s) created"
                            + " but never registered (blocks total {} -> {}); the rest of the mod keeps working",
                    name, Integer.valueOf(registered), Integer.valueOf(leaked),
                    Integer.valueOf(blocksBefore), Integer.valueOf(RegistrySelfCheck.countBlocks()), t);
            try {
                RegistrySelfCheck.purgePending(name);
            } catch (final Throwable ignored) {
                // 自检自己不许再抛。
            }
        }
    }
}

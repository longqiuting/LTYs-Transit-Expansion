package dev.js.mtrheadlight.forge.platform;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 极简事件容器：Fabric 的 {@code Event<T>} 形状（{@code register(T)}）。
 *
 * <p>每个 shim 事件类持有一个或多个 {@code FEvent}，真正把它们接到 Forge 事件总线上的是
 * {@code ForgeHooks} / {@code ClientBootstrap}。监听器异常一律吞掉 —— Fabric 的 Event 也是这个行为，
 * 一个玩家的 tick 回调不能把整个 tick 打断。
 */
public final class FEvent<T> {

    private final List<T> listeners = new CopyOnWriteArrayList<>();

    public T register(final T listener) {
        if (listener != null) {
            listeners.add(listener);
        }
        return listener;
    }

    public void fire(final Consumer<T> invoker) {
        for (final T listener : listeners) {
            try {
                invoker.accept(listener);
            } catch (Throwable ignored) {
                // 与 Fabric 的 Event 一致：单个监听器失败不影响其它监听器，也不抛出。
            }
        }
    }

    public boolean isEmpty() {
        return listeners.isEmpty();
    }

    public int size() {
        return listeners.size();
    }
}

package dev.js.mtrheadlight.forge.platform;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric {@code ResourceManagerHelper} 的等价物：排队 → {@code RegisterClientReloadListenersEvent} 回放。
 *
 * <p>适配器把 Fabric 的两段式（{@code load} 后台 / {@code apply} 主线程）映射到 Forge 的
 * {@code PreparableReloadListener.reload}：load → barrier.wait → apply。
 */
public final class ResourceManagerHelper {

    private static final List<SimpleResourceReloadListener<?>> PENDING = new ArrayList<>();

    private final PackType type;

    private ResourceManagerHelper(final PackType type) {
        this.type = type;
    }

    public static ResourceManagerHelper get(final PackType type) {
        return new ResourceManagerHelper(type);
    }

    public void registerReloadListener(final SimpleResourceReloadListener<?> listener) {
        if (listener != null && type == PackType.CLIENT_RESOURCES) {
            PENDING.add(listener);
        }
    }

    public static void onRegisterClientReloadListeners(final RegisterClientReloadListenersEvent event) {
        for (final SimpleResourceReloadListener<?> listener : PENDING) {
            event.registerReloadListener(new Adapter<>(listener));
        }
    }

    private static final class Adapter<T> implements PreparableReloadListener {

        private final SimpleResourceReloadListener<T> delegate;

        private Adapter(final SimpleResourceReloadListener<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CompletableFuture<Void> reload(final PreparationBarrier barrier, final ResourceManager manager,
                                              final ProfilerFiller prepProfiler,
                                              final ProfilerFiller reloadProfiler,
                                              final Executor backgroundExecutor,
                                              final Executor gameExecutor) {
            return delegate.load(manager, prepProfiler, backgroundExecutor)
                    .thenCompose(data -> barrier.wait(null).thenCompose(ignored ->
                            delegate.apply(data, manager, reloadProfiler, gameExecutor)));
        }
    }
}

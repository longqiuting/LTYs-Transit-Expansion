package dev.js.lte.forge.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fabric {@code SimpleResourceReloadListener} 的等价物（形状保持：{@code getFabricId/load/apply}）。
 * 由 {@link ResourceManagerHelper} 适配成 Forge 的 {@code PreparableReloadListener}。
 */
public interface SimpleResourceReloadListener<T> {

    ResourceLocation getFabricId();

    CompletableFuture<T> load(ResourceManager manager, ProfilerFiller profiler, Executor executor);

    CompletableFuture<Void> apply(T data, ResourceManager manager, ProfilerFiller profiler,
                                  Executor executor);
}

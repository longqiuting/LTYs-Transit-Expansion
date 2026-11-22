package dev.js.lte.forge.platform;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 注册 shim：把 java-gen 里的 {@code Registry.register(BuiltInRegistries.X, id, value)}
 * 换成 {@link DeferredRegister}。
 *
 * <p>为什么不能保留 {@code Registry.register}：Forge 的 {@code Block} / {@code Item} 构造器会建
 * intrusive holder，在 {@code RegisterEvent} 之外调用就是 {@code Registry is already frozen}。
 * 对象仍在 mod 构造期 new 出来（保持 Fabric 的写法与注册顺序），只是真正写进注册表交给
 * {@code DeferredRegister} 在 {@code RegisterEvent} 里做。
 *
 * <p>页签条目：Fabric 的 {@code ItemGroupEvents.modifyEntriesEvent(...).register(...)} 由
 * {@link ItemGroupEvents} 收集，这里在 {@link BuildCreativeModeTabContentsEvent} 里按注册顺序回放 ——
 * 先让每个监听器往同一份 {@code List<ItemStack>} 里写，再统一 {@code accept}，这样
 * {@code entries.getDisplayStacks()} 的「摘出来重新插到下标 0」语义与 Fabric 完全一致。
 */
public final class Reg {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "lte");
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "lte");
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "lte");

    private Reg() {
    }

    public static void register(final IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        PARTICLE_TYPES.register(modBus);
    }

    /** {@code Registry.register(BuiltInRegistries.ITEM, id, value)} */
    public static <T extends Item> T item(final ResourceLocation id, final T value) {
        ITEMS.register(id.getPath(), () -> value);
        return value;
    }

    /** {@code Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id, value)} */
    public static CreativeModeTab tab(final ResourceLocation id, final CreativeModeTab value) {
        TABS.register(id.getPath(), () -> value);
        return value;
    }

    /** {@code Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, value)} */
    public static <T extends ParticleType<?>> T particle(final ResourceLocation id, final T value) {
        PARTICLE_TYPES.register(id.getPath(), () -> value);
        return value;
    }

    /** {@code BuildCreativeModeTabContentsEvent} 回放（由 {@code LteForge} 挂到 mod 总线）。 */
    public static void onBuildTabContents(final BuildCreativeModeTabContentsEvent event) {
        final List<Consumer<ItemGroupEvents.Entries>> listeners =
                ItemGroupEvents.listenersFor(event.getTabKey().location());
        if (listeners.isEmpty()) {
            return;
        }
        final List<ItemStack> stacks = new ArrayList<>();
        final ItemGroupEvents.Entries entries = new ItemGroupEvents.Entries(stacks);
        for (final Consumer<ItemGroupEvents.Entries> listener : listeners) {
            try {
                listener.accept(entries);
            } catch (Throwable ignored) {
                // 与 Fabric 一致：单个页签监听器失败不影响其余。
            }
        }
        for (final ItemStack stack : stacks) {
            event.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    /** 调试用：注册了哪些页签监听器。 */
    public static Map<ResourceLocation, Integer> tabListenerCounts() {
        final Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
        ItemGroupEvents.listenerKeys().forEach(k -> out.put(k, ItemGroupEvents.listenersFor(k).size()));
        return out;
    }
}

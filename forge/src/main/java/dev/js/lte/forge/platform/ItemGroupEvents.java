package dev.js.lte.forge.platform;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Fabric {@code ItemGroupEvents} 的等价物。
 *
 * <p>真正的写入时机是 {@link Reg#onBuildTabContents}（{@code BuildCreativeModeTabContentsEvent}）：
 * 每个监听器拿到同一份可变的 {@code List<ItemStack>}，跑完再统一 {@code accept}。
 * {@link Entries#getDisplayStacks()} 返回的就是那份列表（可增删改），与 Fabric 的
 * {@code FabricItemGroupEntries} 语义一致 —— 这是 LTE 把「工具」页签条目重新排序用的接口。
 */
public final class ItemGroupEvents {

    /** Fabric 的 {@code ModifyEntries}（反编译产物里会有这个强转）。 */
    public interface ModifyEntries {
        void modifyEntries(Entries entries);
    }

    /** 页签条目的可变视图。 */
    public static final class Entries {

        private final List<ItemStack> stacks;

        Entries(final List<ItemStack> stacks) {
            this.stacks = stacks;
        }

        public void add(final ItemLike item) {
            add(new ItemStack(item));
        }

        public void add(final Item item) {
            add(new ItemStack(item));
        }

        public void add(final ItemStack stack) {
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack);
            }
        }

        public void addAll(final java.util.Collection<ItemStack> items) {
            for (final ItemStack stack : items) {
                add(stack);
            }
        }

        public List<ItemStack> getDisplayStacks() {
            return stacks;
        }
    }

    private static final Map<ResourceLocation, List<Consumer<Entries>>> LISTENERS = new ConcurrentHashMap<>();

    private ItemGroupEvents() {
    }

    public static Collector modifyEntriesEvent(final ResourceKey<CreativeModeTab> key) {
        return new Collector(key.location());
    }

    /** Fabric 的 {@code ItemGroupEvents.ModifyEntriesEvent}：{@code register(ModifyEntries)}。 */
    public static final class Collector {

        private final ResourceLocation tab;

        Collector(final ResourceLocation tab) {
            this.tab = tab;
        }

        public void register(final ModifyEntries listener) {
            if (listener != null) {
                LISTENERS.computeIfAbsent(tab, k -> new CopyOnWriteArrayList<>()).add(listener::modifyEntries);
            }
        }

        public void register(final Consumer<Entries> listener) {
            if (listener != null) {
                LISTENERS.computeIfAbsent(tab, k -> new CopyOnWriteArrayList<>()).add(listener);
            }
        }
    }

    static List<Consumer<Entries>> listenersFor(final ResourceLocation tab) {
        final List<Consumer<Entries>> listeners = LISTENERS.get(tab);
        return listeners == null ? List.of() : new ArrayList<>(listeners);
    }

    static Set<ResourceLocation> listenerKeys() {
        return LISTENERS.keySet();
    }
}

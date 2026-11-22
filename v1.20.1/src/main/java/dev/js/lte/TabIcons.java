package dev.js.lte;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 「LTE 工具」创造页签的图标物品：{@code lte:tab_tools}。
 *
 * <h2>为什么要有「物品」</h2>
 * <p>1.20.1 里 {@code ItemGroup.Builder#icon} 收的是 {@code Supplier<ItemStack>}，而页签上那 16×16
 * 的小图是<b>由物品模型画出来的</b>（{@code ItemRenderer} 取 {@code Item} 的模型 id）。也就是说：
 * 想让页签显示一张我们自己画的贴图，就必须有一个「模型指向这张贴图的物品」—— 1.20.1 没有 1.20.5
 * 之后的 {@code item_model} 组件，也没有「无物品的页签贴图」这条路。
 * 直接 {@code new ItemStack(Items.PAPER)} 只能画成纸，改不到（除非覆盖原版 paper 的模型，那会影响
 * 全世界的纸）。
 *
 * <p>所以这里注册一个<b>纯图标物品</b>：无功能、不进任何创造页签（{@code ItemGroupEvents} 从不
 * add 它，创造模式里翻不到），存在的唯一意义就是让页签有一个可以指向的模型：
 * <ul>
 *   <li>{@code item/tab_tools.json} → {@code lte:item/tab_tools}</li>
 * </ul>
 * 贴图是 16×16 PNG、透明背景的 LTE 标识。
 *
 * <p>{@link #register()} 幂等，页签的注册类（{@code signal.LteCoreItems}）先调一次，所以入口里的
 * 调用顺序无所谓。
 */
public final class TabIcons {

    /** 图标物品 id：{@code lte:tab_tools}（「LTE 工具」页签）。 */
    public static final Identifier TOOLS_ID = new Identifier("lte", "tab_tools");

    private static Item tools;
    private static boolean registered;

    private TabIcons() {
    }

    /** 注册图标物品（{@code main} 入口，两端都要有）。幂等。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        tools = Registry.register(Registries.ITEM, TOOLS_ID, new Item(new Item.Settings()));
    }

    /** 「LTE 工具」页签的图标物品。 */
    public static Item tools() {
        register();
        return tools;
    }
}

package dev.js.lte.forge.platform;

import net.minecraft.world.item.CreativeModeTab;

/** Fabric {@code FabricItemGroup} 的等价物：只提供 {@code builder()}（= Forge 的 {@code CreativeModeTab.builder()}）。 */
public final class FabricItemGroup {

    private FabricItemGroup() {
    }

    public static CreativeModeTab.Builder builder() {
        return CreativeModeTab.builder();
    }
}

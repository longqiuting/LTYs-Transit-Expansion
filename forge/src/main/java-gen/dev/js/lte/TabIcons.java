package dev.js.lte;

import dev.js.lte.forge.platform.Reg;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class TabIcons {
   public static final ResourceLocation TOOLS_ID = new ResourceLocation("lte", "tab_tools");
   private static Item tools;
   private static boolean registered;

   private TabIcons() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         tools = (Item)Reg.item( TOOLS_ID, new Item(new Item.Properties()));
      }
   }

   public static Item tools() {
      register();
      return tools;
   }
}

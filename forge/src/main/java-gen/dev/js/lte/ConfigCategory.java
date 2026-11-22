package dev.js.lte;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

enum ConfigCategory {
   LIGHT("cat.light", true),
   COLLISION("cat.collision", true),
   PARTICLE("cat.particle", true),
   DAMAGE("cat.damage", true),
   OTHER("cat.other", false);

   final String titleKey;
   final boolean hasSidingRows;

   private ConfigCategory(final String titleKey, final boolean hasSidingRows) {
      this.titleKey = titleKey;
      this.hasSidingRows = hasSidingRows;
   }

   MutableComponent title() {
      return Component.translatable(this.titleKey);
   }
}

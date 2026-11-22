package dev.js.mtrheadlight;

import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.js.mtrheadlight.forge.platform.ClientTickEvents;
import dev.js.mtrheadlight.forge.platform.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyHooks {
   private static final String OPEN_CONFIG_KEY = "key.lte.open_config";
   private static final String CATEGORY_KEY = "key.categories.lte";
   private static KeyMapping openConfig;

   private KeyHooks() {
   }

   public static void init() {
      openConfig = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.lte.open_config", Type.KEYSYM, -1, "key.categories.lte"));
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         while(openConfig.consumeClick()) {
            if (client.screen == null) {
               MtrHeadlightClient.openConfigScreen();
            }
         }

      });
   }
}

package dev.js.lte;

import dev.js.lte.forge.platform.ClientModInitializer;
import dev.js.lte.forge.platform.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MtrHeadlightClient implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("lte");
   private static HeadlightConfig config = new HeadlightConfig();

   public static HeadlightConfig config() {
      return config;
   }

   public static String snapshotConfig() {
      return config.toJson();
   }

   public static void restoreConfig(String json) {
      if (json != null) {
         config = HeadlightConfig.fromJson(json);
      }

   }

   public static void openConfigScreen() {
      try {
         Minecraft client = Minecraft.getInstance();
         client.tell(() -> client.setScreen(new ConfigScreen(client.screen)));
      } catch (Throwable var1) {
         LOGGER.warn("[lte] could not open the config screen", var1);
      }

   }

   public void onInitializeClient() {
      config = HeadlightConfig.load();
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         try {
            DepartureState.tick();
            LightController.tick();
         } catch (Throwable var2) {
            LOGGER.error("[lte] tick failed", var2);
         }

      });

      try {
         Commands.register();
      } catch (Throwable var3) {
         LOGGER.warn("[lte] could not register /mtr_lte", var3);
      }

      try {
         RenderHooks.init();
      } catch (Throwable var2) {
         LOGGER.warn("[lte] could not register the rail render hooks", var2);
      }

      LOGGER.info("[lte] client initialised (head={} tail={} len={}/{})", new Object[]{config.headLuminance, config.tailLuminance, config.headLength, config.tailLength});
   }
}

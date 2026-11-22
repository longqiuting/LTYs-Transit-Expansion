package dev.js.lte.mtr;

import dev.js.lte.MtrHeadlightClient;
import dev.js.lte.forge.platform.ClientModInitializer;
import dev.js.lte.forge.platform.ClientTickEvents;

public final class BuiltInTrainsInit implements ClientModInitializer {
   public void onInitializeClient() {
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         try {
            BuiltInTrains.tick(MtrHeadlightClient.config(), client);
         } catch (Throwable var2) {
            MtrHeadlightClient.LOGGER.warn("[lte] built-in train tick failed", var2);
         }

      });
   }
}

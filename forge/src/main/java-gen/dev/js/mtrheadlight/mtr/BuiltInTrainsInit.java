package dev.js.mtrheadlight.mtr;

import dev.js.mtrheadlight.MtrHeadlightClient;
import dev.js.mtrheadlight.forge.platform.ClientModInitializer;
import dev.js.mtrheadlight.forge.platform.ClientTickEvents;

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

package dev.js.lte;

import dev.js.lte.forge.platform.EnvType;
import dev.js.lte.forge.platform.ModInitializer;
import dev.js.lte.forge.platform.ServerLifecycleEvents;
import dev.js.lte.forge.platform.FabricLoader;
import net.minecraft.server.MinecraftServer;

public final class VehicleLightsJsonServer implements ModInitializer {
   public void onInitialize() {
      try {
         ServerLifecycleEvents.SERVER_STARTED.register((ServerLifecycleEvents.ServerStarted)(server) -> reload(server));
         ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((ServerLifecycleEvents.EndDataPackReload)(server, manager, success) -> {
            if (success) {
               reload(server);
            }

         });
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] lights json: server hooks unavailable", var2);
      }

   }

   private static void reload(MinecraftServer server) {
      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
         try {
            VehicleLightPacks.reloadServer(server);
         } catch (Throwable var2) {
            LteLog.LOGGER.warn("[lte] lights json: server scan failed", var2);
         }

      }
   }
}

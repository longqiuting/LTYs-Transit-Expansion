package dev.js.mtrheadlight;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import dev.js.mtrheadlight.forge.platform.ClientModInitializer;
import dev.js.mtrheadlight.forge.platform.ClientLifecycleEvents;
import dev.js.mtrheadlight.forge.platform.ResourceManagerHelper;
import dev.js.mtrheadlight.forge.platform.SimpleResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

public final class VehicleLightsJsonClient implements ClientModInitializer {
   private static final ResourceLocation ID = new ResourceLocation("lte", "vehicle_lights_json");

   public void onInitializeClient() {
      try {
         VehicleLightOverrides.setPackBeamReader(BeamPackWriter::read);
      } catch (Throwable var4) {
         LteLog.LOGGER.warn("[lte] beam pack: reader not installed", var4);
      }

      try {
         ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new VehicleLightsJsonClient.Listener());
      } catch (Throwable var3) {
         LteLog.LOGGER.warn("[lte] lights json: reload listener unavailable", var3);
      }

      try {
         ClientLifecycleEvents.CLIENT_STARTED.register((ClientLifecycleEvents.ClientStarted)(client) -> {
            try {
               if (!VehicleLightOverrides.loaded()) {
                  VehicleLightPacks.apply(VehicleLightPacks.configSources());
               }
            } catch (Throwable var2) {
               LteLog.LOGGER.warn("[lte] lights json: startup scan failed", var2);
            }

         });
      } catch (Throwable var2) {
         LteLog.LOGGER.debug("[lte] lights json: no client lifecycle hook", var2);
      }

   }

   private static final class Listener implements SimpleResourceReloadListener<Void> {
      public ResourceLocation getFabricId() {
         return VehicleLightsJsonClient.ID;
      }

      public CompletableFuture<Void> load(ResourceManager manager, ProfilerFiller profiler, Executor executor) {
         return CompletableFuture.completedFuture((Void)null);
      }

      public CompletableFuture<Void> apply(Void data, ResourceManager manager, ProfilerFiller profiler, Executor executor) {
         try {
            BeamPackWriter.invalidatePacks();
            VehicleLightPacks.reloadClient(manager);
         } catch (Throwable var6) {
            LteLog.LOGGER.warn("[lte] lights json: client scan failed", var6);
         }

         return CompletableFuture.completedFuture((Void)null);
      }
   }
}

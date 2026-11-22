package dev.js.mtrheadlight;

import dev.js.mtrheadlight.signal.ConnectorNodeHighlight;
import dev.js.mtrheadlight.sss.ScatterPass;
import dev.js.mtrheadlight.forge.platform.ClientLifecycleEvents;
import dev.js.mtrheadlight.forge.platform.ClientTickEvents;
import dev.js.mtrheadlight.forge.platform.WorldRenderEvents;

public final class RenderHooks {
   private static long lightsAt = System.currentTimeMillis();

   private RenderHooks() {
   }

   private static void traceLightBranches(HeadlightConfig cfg) {
      long now = System.currentTimeMillis();
      if (now - lightsAt >= 5000L) {
         lightsAt = now;
         if (cfg == null) {
            MtrHeadlightClient.LOGGER.info("[lte] lights: no-config (nothing is drawn)");
         } else {
            MtrHeadlightClient.LOGGER.info("[lte] lights: band={} flare={} scatter={} block={} test={}", new Object[]{cfg.drawBand(), cfg.drawFlare(), cfg.drawScatter(), cfg.drawBlockLight(), LightTest.enabled()});
         }
      }
   }

   public static void init() {
      FangsuCompat.announce();

      try {
         SprayType.init();
      } catch (Throwable var8) {
         MtrHeadlightClient.LOGGER.warn("[lte] spray particle registration failed", var8);
      }

      try {
         ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> RainSpray.tick());
      } catch (Throwable var7) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the rain spray tick", var7);
      }

      try {
         ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> DebugProbe.tick());
      } catch (Throwable var6) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the debug probe tick", var6);
      }

      WorldRenderEvents.AFTER_ENTITIES.register((WorldRenderEvents.AfterEntities)(context) -> {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         traceLightBranches(cfg);

         try {
            BeamGlow.render(context, false);
         } catch (Throwable var4) {
            MtrHeadlightClient.LOGGER.error("[lte] beam glow failed", var4);
         }

         try {
            LightFlare.render(context, false);
         } catch (Throwable var3) {
            MtrHeadlightClient.LOGGER.error("[lte] light flare failed", var3);
         }

      });
      WorldRenderEvents.AFTER_TRANSLUCENT.register((WorldRenderEvents.AfterTranslucent)(context) -> {
         try {
            BeamGlow.render(context, true);
         } catch (Throwable var3) {
            MtrHeadlightClient.LOGGER.error("[lte] beam glow (late pass) failed", var3);
         }

         try {
            LightFlare.render(context, true);
         } catch (Throwable var2) {
            MtrHeadlightClient.LOGGER.error("[lte] light flare (late pass) failed", var2);
         }

      });

      try {
         ClientTickEvents.END_CLIENT_TICK.register(ScatterPass::tick);
      } catch (Throwable var5) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the scatter shader probe", var5);
      }

      WorldRenderEvents.LAST.register((WorldRenderEvents.Last)(context) -> {
         try {
            ScatterPass.render(context);
         } catch (Throwable var2) {
            MtrHeadlightClient.LOGGER.error("[lte] scatter: pass threw", var2);
         }

      });

      try {
         ClientLifecycleEvents.CLIENT_STOPPING.register((ClientLifecycleEvents.ClientStopping)(client) -> ScatterPass.shutdown());
      } catch (Throwable var4) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the scatter shutdown hook", var4);
      }

      try {
         WorldRenderEvents.AFTER_TRANSLUCENT.register(ConnectorNodeHighlight::render);
      } catch (Throwable var3) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the node highlight", var3);
      }

      try {
         KeyHooks.init();
      } catch (Throwable var2) {
         MtrHeadlightClient.LOGGER.warn("[lte] could not register the key binding", var2);
      }

      try {
         BeamRelight.install();
         ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
            try {
               BeamRelight.tick(MtrHeadlightClient.config());
            } catch (Throwable var2) {
               MtrHeadlightClient.LOGGER.error("[lte] beam block light tick failed", var2);
            }

         });
      } catch (Throwable var1) {
         MtrHeadlightClient.LOGGER.warn("[lte] beam block light not installed", var1);
      }

   }
}

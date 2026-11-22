package dev.js.lte;

import dev.js.lte.forge.platform.FabricLoader;

public final class FangsuCompat {
   private static final String MOD_ID = "fangsu";

   private FangsuCompat() {
   }

   public static boolean present() {
      try {
         return FabricLoader.getInstance().isModLoaded("fangsu");
      } catch (Throwable var1) {
         return false;
      }
   }

   public static void announce() {
      try {
         if (present()) {
            MtrHeadlightClient.LOGGER.info("[lte] \u65b9\u901f (fangsu) detected: its extra rail model layers are built from ANTE's own rail chunk classes, so the light redirect and registerLightUpdate cover them; the hand-built turnout OBJ is lit by the optional fangsu mixins (FangsuTurnoutRenderMixin + FangsuBaseRenderLightMixin, require=0)");
            MtrHeadlightClient.LOGGER.info("[lte] \u65b9\u901f\u4ea4\u901a\u706f fangsu:traffic_light \u53ef\u53c2\u4e0e\u4fe1\u53f7\u8fde\u63a5\u5668\u7ed1\u5b9a\uff1a\u53f3\u952e\u4f1a\u5148\u628a\u5b83\u7684 dispType \u5207\u5230 mtr\uff08\u5b83\u81ea\u5df1\u7684 EnumConfig setter\uff09\uff0c\u518d\u7531 FangsuTrafficLightRenderMixin -> FangsuSignalSync \u6bcf\u5e27\u6309\u7ed1\u5b9a\u8282\u70b9\u7684 aspect \u5199 lightState\uff08mtr \u6863\u5728 1.3.2 \u91cc\u662f\u7a7a\u5b9e\u73b0\uff09\u3002\u8bc1\u636e\u89c1 docs/report-2026-11-10-fangsu-traffic-light-mtr-mode.md");
         }
      } catch (Throwable var1) {
      }

   }
}

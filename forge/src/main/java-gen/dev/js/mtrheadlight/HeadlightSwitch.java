package dev.js.mtrheadlight;

import java.util.Locale;
import mtr.data.TrainClient;
import net.minecraft.client.multiplayer.ClientLevel;

public final class HeadlightSwitch {
   private HeadlightSwitch() {
   }

   public static HeadlightSwitch.Verdict judge(ClientLevel world, TrainPose pose, HeadlightConfig cfg, TrainClient train) {
      if (train != null && cfg != null) {
         try {
            if (!DepartureState.departed(train)) {
               return HeadlightSwitch.Verdict.OFF_ROUTE;
            } else {
               if (cfg.autoHeadlights()) {
                  if (!isLit(world, pose, cfg, train.id)) {
                     return HeadlightSwitch.Verdict.DARK;
                  }

                  if (DepartureState.stopped(train, cfg)) {
                     return HeadlightSwitch.Verdict.PARKED;
                  }
               }

               return HeadlightSwitch.Verdict.LIT;
            }
         } catch (Throwable var5) {
            return HeadlightSwitch.Verdict.LIT;
         }
      } else {
         return HeadlightSwitch.Verdict.LIT;
      }
   }

   public static boolean isLit(ClientLevel world, TrainPose pose, HeadlightConfig cfg, long trainId) {
      try {
         if (cfg == null) {
            return true;
         } else {
            return !cfg.autoHeadlights() ? true : RailWeather.lightsNeeded(world, pose, trainId);
         }
      } catch (Throwable var6) {
         return true;
      }
   }

   public static String diagnose(ClientLevel world, TrainPose pose, HeadlightConfig cfg, long trainId) {
      String mode = cfg == null ? "AUTO" : cfg.resolvedMode();
      boolean lit = isLit(world, pose, cfg, trainId);
      boolean openSky = RailWeather.openSky(world, pose, trainId);
      boolean night = RailWeather.isNight(world);
      int sky = RailWeather.skyLevelNose(world, pose);
      int skyMid = RailWeather.skyLevelMid(world, pose);
      float bright = RailWeather.skyBrightness(world);
      return String.format(Locale.ROOT, "auto light: mode=%s openSky=%s night=%s sky=%d skyMid=%d bright=%.2f lit=%s", mode, openSky, night, sky, skyMid, bright, lit ? "on" : "off");
   }

   public static String diagnose(ClientLevel world, TrainPose pose, HeadlightConfig cfg, TrainClient train) {
      return diagnose(world, pose, cfg, train == null ? Long.MIN_VALUE : train.id) + " why=" + judge(world, pose, cfg, train).token;
   }

   public static enum Verdict {
      LIT("lit"),
      OFF_ROUTE("offRoute"),
      DARK("dark"),
      PARKED("parked");

      public final String token;

      private Verdict(final String token) {
         this.token = token;
      }

      public boolean lit() {
         return this == LIT;
      }
   }
}

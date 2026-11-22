package dev.js.mtrheadlight;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class RainHush {
   private static final double END_INSET = 0.3D;
   private static final double SHELL_INSET = 0.05D;
   private static final double FLOOR_INSET = 0.1D;
   private static final double ROOF_INSET = 0.1D;
   private static final double SEARCH_RANGE = 48.0D;
   private static final long DIAG_PERIOD_MS = 5000L;
   private static volatile boolean forceInside;
   private static int cacheTick = Integer.MIN_VALUE;
   private static LocalPlayer cachePlayer;
   private static boolean cacheInside;
   private static ClientLevel lastWorld;
   private static long frames;
   private static long suppressed;
   private static long splashSuppressed;
   private static long lastLogMs;
   private static boolean lastInside;

   private RainHush() {
   }

   public static void setForceInside(boolean value) {
      forceInside = value;
      cacheTick = Integer.MIN_VALUE;
      MtrHeadlightClient.LOGGER.info("[lte] rain: forceInside={}", value);
   }

   public static boolean forceInside() {
      return forceInside;
   }

   public static boolean shouldSuppress() {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != lastWorld) {
         lastWorld = client.level;
         reset();
      }

      ++frames;
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if (!cfg.rainHush && !cfg.rainHushAnySiding()) {
         diag(cfg, false);
         return false;
      } else if (client != null && client.player != null && client.level != null) {
         boolean inside = forceInside || playerInsideCabin(client);
         diag(cfg, inside);
         if (inside) {
            ++suppressed;
         }

         return inside;
      } else {
         return false;
      }
   }

   public static boolean shouldSuppressSplash() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if ((cfg.rainHush || cfg.rainHushAnySiding()) && cfg.rainHushSplash) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.level != null) {
            if (!forceInside && !playerInsideCabin(client)) {
               return false;
            } else {
               ++splashSuppressed;
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean playerInsideCabin(Minecraft client) {
      LocalPlayer player = client.player;
      ClientLevel world = client.level;
      if (player != null && world != null) {
         int tick = player.tickCount;
         if (cachePlayer == player && cacheTick == tick) {
            return cacheInside;
         } else {
            cachePlayer = player;
            cacheTick = tick;
            cacheInside = computeInside(player, world);
            return cacheInside;
         }
      } else {
         return false;
      }
   }

   private static boolean computeInside(LocalPlayer player, ClientLevel world) {
      try {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         double height = cfg.trainCollisionHeightClamped();
         AABB box = player.getBoundingBox();
         double hx = (box.maxX - box.minX) * 0.5D;
         double hz = (box.maxZ - box.minZ) * 0.5D;
         double pcx = (box.minX + box.maxX) * 0.5D;
         double pcz = (box.minZ + box.maxZ) * 0.5D;

         List<TrainClient> trains;
         try {
            trains = new ArrayList(ClientData.TRAINS);
         } catch (Throwable var54) {
            return false;
         }

         Iterator t = trains.iterator();

         while(true) {
            TrainClient train;
            TrainPose pose;
            while(true) {
               if (!t.hasNext()) {
                  return false;
               }

               train = (TrainClient)t.next();
               if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0 && train.transportMode == TransportMode.TRAIN && cfg.effectiveFor(train.sidingId).rainHush) {
                  try {
                     boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                     pose = TrainPose.ofShared(train, reversed);
                     break;
                  } catch (Throwable var55) {
                  }
               }
            }

            int cars = pose.cars();
            if (cars > 0) {
               double halfW = halfWidth(train);

               for(int i = 0; i < cars; ++i) {
                  Vec3 a = pose.carStart(i);
                  Vec3 b = pose.carEnd(i);
                  double ax = b.x - a.x;
                  double az = b.z - a.z;
                  double len = Math.sqrt(ax * ax + az * az);
                  if (!(len < 0.5D)) {
                     double cx = (a.x + b.x) * 0.5D;
                     double cz = (a.z + b.z) * 0.5D;
                     double dx = cx - player.getX();
                     double dz = cz - player.getZ();
                     if (!(dx * dx + dz * dz > 2304.0D)) {
                        double ux = ax / len;
                        double uz = az / len;
                        double along = (pcx - a.x) * ux + (pcz - a.z) * uz;
                        double lat = (pcx - a.x) * -uz + (pcz - a.z) * ux;
                        double halfAlong = hx * Math.abs(ux) + hz * Math.abs(uz);
                        double halfLat = hx * Math.abs(uz) + hz * Math.abs(ux);
                        if (!(along - halfAlong < 0.3D) && !(along + halfAlong > len - 0.3D) && !(Math.abs(lat) + halfLat > halfW - 0.05D)) {
                           double tLte = along / len;
                           double floor = a.y + (b.y - a.y) * tLte;
                           if (!(box.minY < floor + 0.1D) && !(box.maxY > floor + height - 0.1D)) {
                              trace(player, train, i, along, len, lat, floor, height);
                              return true;
                           }
                        }
                     }
                  }
               }
            }
         }
      } catch (Throwable var56) {
         return false;
      }
   }

   private static void trace(LocalPlayer player, TrainClient train, int car, double along, double len, double lat, double floor, double height) {
      boolean now = true;
      if (!lastInside) {
         lastInside = true;
         MtrHeadlightClient.LOGGER.info("[lte] rain: inside=true train={} car={} along={}/{} lat={} feet={} floor={} roof={}", new Object[]{train.trainId, car, fmt(along), fmt(len), fmt(lat), fmt(player.getY()), fmt(floor), fmt(floor + height)});
      }
   }

   private static void diag(HeadlightConfig cfg, boolean inside) {
      if (inside != lastInside && !inside) {
         lastInside = false;
         if (cfg.rainHushDiagnostics) {
            MtrHeadlightClient.LOGGER.info("[lte] rain: inside=false");
         }
      }

      if (cfg.rainHushDiagnostics) {
         long now = System.currentTimeMillis();
         if (lastLogMs == 0L || now - lastLogMs >= 5000L) {
            lastLogMs = now;
            MtrHeadlightClient.LOGGER.info("[lte] rain: frames={} suppressed={} splash={} inside={} force={} switch={}", new Object[]{frames, suppressed, splashSuppressed, inside, forceInside, cfg.rainHush});
         }
      }
   }

   public static void reset() {
      cachePlayer = null;
      cacheTick = Integer.MIN_VALUE;
      cacheInside = false;
      frames = 0L;
      suppressed = 0L;
      splashSuppressed = 0L;
      lastLogMs = 0L;
      lastInside = false;
   }

   public static String status() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      return "rainHush=" + cfg.rainHush + " splash=" + cfg.rainHushSplash + " force=" + forceInside + " inside=" + cacheInside + " frames=" + frames + " suppressed=" + suppressed;
   }

   private static double halfWidth(TrainClient train) {
      double half;
      try {
         half = (double)train.width * 0.5D;
      } catch (Throwable var4) {
         return 1.25D;
      }

      return !(half > 0.0D) ? 1.25D : Math.max(1.3D, Math.min(4.0D, half));
   }

   private static String fmt(double v) {
      return String.format(Locale.ROOT, "%.2f", v);
   }
}

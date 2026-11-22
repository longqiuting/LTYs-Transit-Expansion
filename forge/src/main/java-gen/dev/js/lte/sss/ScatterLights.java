package dev.js.lte.sss;

import dev.js.lte.DepartureState;
import dev.js.lte.HeadlightConfig;
import dev.js.lte.HeadlightSwitch;
import dev.js.lte.TrainLamps;
import dev.js.lte.TrainPose;
import java.util.ArrayList;
import java.util.List;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

final class ScatterLights {
   static final int MAX = 16;
   static final double MAX_DISTANCE = 96.0D;
   private static final double FADE_FROM = 0.7D;
   private static final double TAIL_RADIUS_SCALE = 0.65D;
   private static final double TAIL_POWER_SCALE = 0.6D;
   private static final double MIN_POWER = 0.001D;
   private static final List<ScatterLights.Cand> POOL = new ArrayList();
   private static int used;
   private static int candidates;

   private ScatterLights() {
   }

   static int collect(Minecraft client, HeadlightConfig cfg, float[] pos, float[] dir, float[] color) {
      used = 0;
      candidates = 0;
      if (client != null && client.level != null && client.gameRenderer != null) {
         Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
         Vec3 eye = client.player == null ? camera : client.player.position();
         List<TrainClient> trains = snapshotTrains();

         for(int t = 0; t < trains.size(); ++t) {
            TrainClient train = (TrainClient)trains.get(t);
            if (drawable(cfg, train)) {
               HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
               if (eff.enabled && cfg.beamScatter) {
                  boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                  TrainPose pose = TrainPose.ofShared(train, reversed);
                  if (pose.cars() > 0 && HeadlightSwitch.judge(client.level, pose, cfg, train).lit()) {
                     List<TrainLamps.Lamp> lamps = TrainLamps.of(train, pose, cfg, eff);

                     for(int i = 0; i < lamps.size(); ++i) {
                        TrainLamps.Lamp lamp = (TrainLamps.Lamp)lamps.get(i);
                        if (lamp != null) {
                           double d = lamp.pos.distanceTo(eye);
                           if (!(d > 96.0D) && !(d > eff.beamDistance) && !(d < 0.001D)) {
                              double radius = cfg.scatterRadiusClamped() * (lamp.head ? 1.0D : 0.65D);
                              if (!(radius < 0.05D)) {
                                 double power = lamp.alpha * (lamp.head ? 1.0D : 0.6D) * distanceFade(d, eff.beamDistance);
                                 if (!(power < 0.001D)) {
                                    ++candidates;
                                    ScatterLights.Cand c = next();
                                    c.dist = lamp.pos.distanceToSqr(camera);
                                    c.px = lamp.pos.x - camera.x;
                                    c.py = lamp.pos.y - camera.y;
                                    c.pz = lamp.pos.z - camera.z;
                                    Vec3 beam = lamp.beam();
                                    c.dx = beam.x;
                                    c.dy = beam.y;
                                    c.dz = beam.z;
                                    c.power = power;
                                    c.radius = radius;
                                    c.length = lamp.length;
                                    c.rgb = lamp.rgb;
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         if (used == 0) {
            return 0;
         } else {
            sortByDistance();
            int n = Math.min(used, 16);

            for(int i = 0; i < n; ++i) {
               ScatterLights.Cand c = (ScatterLights.Cand)POOL.get(i);
               int o = i * 4;
               pos[o] = (float)c.px;
               pos[o + 1] = (float)c.py;
               pos[o + 2] = (float)c.pz;
               pos[o + 3] = (float)c.radius;
               dir[o] = (float)c.dx;
               dir[o + 1] = (float)c.dy;
               dir[o + 2] = (float)c.dz;
               dir[o + 3] = (float)c.length;
               color[o] = (float)(c.rgb >> 16 & 255) / 255.0F;
               color[o + 1] = (float)(c.rgb >> 8 & 255) / 255.0F;
               color[o + 2] = (float)(c.rgb & 255) / 255.0F;
               color[o + 3] = (float)c.power;
            }

            return n;
         }
      } else {
         return 0;
      }
   }

   private static void sortByDistance() {
      for(int i = 1; i < used; ++i) {
         ScatterLights.Cand key = (ScatterLights.Cand)POOL.get(i);

         int j;
         for(j = i - 1; j >= 0 && ((ScatterLights.Cand)POOL.get(j)).dist > key.dist; --j) {
            POOL.set(j + 1, (ScatterLights.Cand)POOL.get(j));
         }

         POOL.set(j + 1, key);
      }

   }

   private static ScatterLights.Cand next() {
      while(POOL.size() <= used) {
         POOL.add(new ScatterLights.Cand());
      }

      return (ScatterLights.Cand)POOL.get(used++);
   }

   private static boolean drawable(HeadlightConfig cfg, TrainClient train) {
      if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0) {
         return !cfg.onlyTrains || train.transportMode == TransportMode.TRAIN;
      } else {
         return false;
      }
   }

   private static List<TrainClient> snapshotTrains() {
      try {
         return new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var1) {
         return List.of();
      }
   }

   private static double distanceFade(double d, double limit) {
      if (limit <= 0.0D) {
         return 1.0D;
      } else {
         double from = limit * 0.7D;
         if (d <= from) {
            return 1.0D;
         } else if (d >= limit) {
            return 0.0D;
         } else {
            double t = (d - from) / (limit - from);
            double inv = 1.0D - t;
            return inv * inv;
         }
      }
   }

   static int candidates() {
      return candidates;
   }

   private static final class Cand {
      double dist;
      double px;
      double py;
      double pz;
      double dx;
      double dy;
      double dz;
      double power;
      double radius;
      double length;
      int rgb;
   }
}

package dev.js.lte;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

public final class LightController {
   private static final int MASS_CLEAR_MIN = 2;
   private static final int POSE_LOST_TICKS = 3;
   private static final long MISSING_LIT_MAX_TICKS = 40L;
   private static final Map<String, LightController.Tracked> ACTIVE = new HashMap();
   private static ClientLevel lastWorld;
   private static long tickCounter;
   private static final int WEATHER_DIAG_TICKS = 100;
   private static final int WEATHER_DIAG_MAX = 8;

   private LightController() {
   }

   public static void clear() {
      LightBackends.backend().clearAll();
      ACTIVE.clear();
      BeamBlockLight.clear();
   }

   public static void reload() {
      clear();
   }

   public static void tick() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      Minecraft client = Minecraft.getInstance();
      ClientLevel world = client.level;
      if (world != lastWorld) {
         ACTIVE.clear();
         LightBackends.backend().clearAll();
         TrainPose.clearCache();
         VehicleLights.clearCache();
         RailWeather.reset();
         lastWorld = world;
         tickCounter = 0L;
      }

      if (world != null && client.player != null) {
         if (cfg.enabled && cfg.dynamicLight) {
            ++tickCounter;
            LightBackend backend = LightBackends.backend();
            backend.tick();
            Vec3 eye = client.player.position();
            Set<String> seen = new HashSet();
            List<TrainClient> trains = snapshotTrains();
            double[] beams = cfg.beamBlockLight ? new double[128] : null;
            int beamCount = 0;
            Set<String> known = new HashSet(Math.max(16, trains.size() * 2));

            for(TrainClient train : trains) {
               if (train != null) {
                  known.add(DepartureState.keyOf(train));
               }
            }

            List<String> skyLines = cfg.weatherDiagnostics && LteDebug.lights() && tickCounter % 100L == 0L ? new ArrayList() : null;

            for(TrainClient train : trains) {
               if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0 && (!cfg.onlyTrains || train.transportMode == TransportMode.TRAIN)) {
                  String key = DepartureState.keyOf(train);
                  HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
                  if (eff.enabled && eff.dynamicLight) {
                     if (!DepartureState.departed(train)) {
                        LightController.Tracked inShed = (LightController.Tracked)ACTIVE.remove(key);
                        if (inShed != null) {
                           inShed.remove(backend, key);
                        }
                     } else {
                        double viewLimit = eff.maxDistance;
                        LightController.Tracked tracked = (LightController.Tracked)ACTIVE.get(key);
                        if (tracked == null) {
                           tracked = new LightController.Tracked();
                           ACTIVE.put(key, tracked);
                        }

                        boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                        TrainPose pose = TrainPose.ofShared(train, reversed);
                        if (pose.front() == Vec3.ZERO && pose.rear() == Vec3.ZERO) {
                           if (++tracked.unplacedTicks >= 3) {
                              tracked.hide(backend, key);
                           }
                        } else {
                           tracked.unplacedTicks = 0;
                           boolean weatherLit = HeadlightSwitch.judge(world, pose, cfg, train).lit();
                           double frontDistance = pose.front().distanceTo(eye);
                           double rearDistance = pose.rear().distanceTo(eye);
                           if (frontDistance > viewLimit && rearDistance > viewLimit) {
                              if (tracked != null) {
                                 seen.add(key);
                                 tracked.lastSeen = tickCounter;
                                 if (frontDistance > viewLimit * 1.25D && rearDistance > viewLimit * 1.25D) {
                                    tracked.remove(backend, key);
                                    ACTIVE.remove(key);
                                    seen.remove(key);
                                 } else {
                                    tracked.hide(backend, key);
                                 }
                              }
                           } else {
                              VehicleLights.announce(train);
                              List<TrainLamps.Lamp> lamps = TrainLamps.of(train, pose, cfg, eff);
                              EndLight head = endSegment(lamps, true);
                              EndLight tail = endSegment(lamps, false);
                              tracked.update(backend, key, head, tail, weatherLit && frontDistance <= viewLimit, weatherLit && rearDistance <= viewLimit, eff);
                              tracked.lastSeen = tickCounter;
                              seen.add(key);
                              if (beams != null && beamCount + 2 <= 16) {
                                 if (weatherLit && frontDistance <= viewLimit) {
                                    beamCount = putBeam(beams, beamCount, head, cfg.beamBlockLightRadius, cfg.headLuminance);
                                 }

                                 if (weatherLit && rearDistance <= viewLimit) {
                                    beamCount = putBeam(beams, beamCount, tail, Math.min(cfg.beamBlockLightRadius, 6), cfg.tailLuminance);
                                 }
                              }

                              weatherLine(world, pose, cfg, train, skyLines);
                           }
                        }
                     }
                  } else {
                     LightController.Tracked stale = (LightController.Tracked)ACTIVE.remove(key);
                     if (stale != null) {
                        stale.remove(backend, key);
                     }
                  }
               }
            }

            if (skyLines != null) {
               for(int i = 0; i < skyLines.size(); ++i) {
                  MtrHeadlightClient.LOGGER.info("[lte] {}", skyLines.get(i));
               }

               MtrHeadlightClient.LOGGER.info("[lte] auto light: sampled={} trains={} ldl={}", new Object[]{skyLines.size(), known.size(), backend.liveSources()});
            }

            int trackedCount = ACTIVE.size();
            List<String> gone = new ArrayList();

            for(String key : ACTIVE.keySet()) {
               if (!known.contains(key)) {
                  gone.add(key);
               }
            }

            int missingNow = gone.size();
            int massClearThreshold = Math.max(2, (int)Math.ceil((double)trackedCount * cfg.massClearRatio));
            if (missingNow > 0 && (known.isEmpty() || missingNow >= massClearThreshold)) {
               for(String key : gone) {
                  LightController.Tracked dropped = (LightController.Tracked)ACTIVE.remove(key);
                  if (dropped != null) {
                     dropped.remove(backend, key);
                  }
               }

               ClearDiag.cleared(gone.size());
            }

            Iterator<Map.Entry<String, LightController.Tracked>> it = ACTIVE.entrySet().iterator();

            while(it.hasNext()) {
               Map.Entry<String, LightController.Tracked> entry = (Map.Entry)it.next();
               if (!seen.contains(entry.getKey())) {
                  LightController.Tracked tracked = (LightController.Tracked)entry.getValue();
                  long age = tickCounter - tracked.lastSeen;
                  if (age > (long)cfg.missingKeepTicks && (!tracked.near(eye, cfg.maxDistance) || age > 40L)) {
                     if (age <= (long)cfg.missingDimTicks) {
                        tracked.hide(backend, (String)entry.getKey());
                     } else if (age > (long)cfg.missingDetachTicks) {
                        tracked.remove(backend, (String)entry.getKey());
                        it.remove();
                     }
                  }
               }
            }

            BeamBlockLight.publish(beams == null ? null : trimBeams(beams, beamCount));
            ClearDiag.tick(cfg.massClearDiagnostics && LteDebug.clear(), tickCounter, trackedCount, missingNow);
         } else {
            clear();
         }
      }
   }

   private static int putBeam(double[] buffer, int index, EndLight light, int radius, int peak) {
      if (light != null && radius > 0 && peak > 0) {
         int o = index * 8;
         buffer[o] = light.start.x;
         buffer[o + 1] = light.start.y;
         buffer[o + 2] = light.start.z;
         buffer[o + 3] = light.end.x;
         buffer[o + 4] = light.end.y;
         buffer[o + 5] = light.end.z;
         buffer[o + 6] = (double)radius;
         buffer[o + 7] = (double)Math.min(15, peak);
         return index + 1;
      } else {
         return index;
      }
   }

   private static double[] trimBeams(double[] buffer, int count) {
      int used = count * 8;
      double[] out = new double[used];
      System.arraycopy(buffer, 0, out, 0, used);
      return out;
   }

   private static EndLight endSegment(List<TrainLamps.Lamp> lamps, boolean head) {
      Vec3 centre = TrainLamps.centre(lamps, head);
      Vec3 dir = TrainLamps.heading(lamps, head);
      if (centre != Vec3.ZERO && dir != Vec3.ZERO) {
         double length = Math.max(1.0D, TrainLamps.wedgeLength(lamps, head));
         return EndLight.ofSegment(centre, centre.add(dir.scale(length)));
      } else {
         return null;
      }
   }

   private static void weatherLine(ClientLevel world, TrainPose pose, HeadlightConfig cfg, TrainClient train, List<String> out) {
      if (out != null && out.size() < 8) {
         out.add(HeadlightSwitch.diagnose(world, pose, cfg, train));
      }
   }

   private static List<TrainClient> snapshotTrains() {
      try {
         return new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var1) {
         return List.of();
      }
   }

   private static final class Tracked {
      long lastSeen;
      int unplacedTicks;
      Vec3 lastHeadStart = Vec3.ZERO;
      Vec3 lastHeadEnd = Vec3.ZERO;
      boolean headLit;
      boolean tailLit;

      void update(LightBackend backend, String key, EndLight head, EndLight tail, boolean showHead, boolean showTail, HeadlightConfig.Effective eff) {
         if (head != null) {
            this.lastHeadStart = head.start;
            this.lastHeadEnd = head.end;
         }

         this.headLit = applyEnd(backend, key + "#head", head, showHead, this.headLit, eff.headLuminance);
         this.tailLit = applyEnd(backend, key + "#tail", tail, showTail, this.tailLit, eff.tailLuminance);
      }

      private static boolean applyEnd(LightBackend backend, String key, EndLight end, boolean show, boolean wasLit, int luminance) {
         if (show) {
            if (end == null) {
               return wasLit;
            } else {
               backend.updateLine(key, end.start, end.end, luminance);
               return true;
            }
         } else {
            if (wasLit) {
               backend.removeLine(key);
            }

            return false;
         }
      }

      void hide(LightBackend backend, String key) {
         backend.hideLine(key + "#head");
         backend.hideLine(key + "#tail");
         this.headLit = false;
         this.tailLit = false;
      }

      void remove(LightBackend backend, String key) {
         backend.removeLine(key + "#head");
         backend.removeLine(key + "#tail");
         this.headLit = false;
         this.tailLit = false;
      }

      boolean near(Vec3 eye, double limit) {
         return this.lastHeadStart.distanceTo(eye) <= limit || this.lastHeadEnd.distanceTo(eye) <= limit;
      }
   }
}

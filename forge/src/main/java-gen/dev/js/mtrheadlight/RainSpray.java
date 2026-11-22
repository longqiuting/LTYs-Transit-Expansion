package dev.js.mtrheadlight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class RainSpray {
   public static final double MAX_PLAYER_DISTANCE = 64.0D;
   public static final int MAX_PER_TRAIN_PER_TICK = 24;
   private static final int MAX_PER_TICK = 80;
   private static final int MAX_CARS_SCANNED = 16;
   private static final double TOKENS_PER_TICK = 60.0D;
   private static final double TOKEN_CAP = 240.0D;
   private static final double KMH_PER_BLOCK_PER_TICK = 72.0D;
   private static final double FULL_INTENSITY_KMH = 120.0D;
   private static final double LOW_SPEED_KMH = 20.0D;
   private static final double LOW_SPEED_DENSITY_FLOOR = 0.35D;
   private static final double CAR_HALF_WIDTH = 1.3D;
   private static final double LATERAL_PAD_MIN = 0.2D;
   private static final double LATERAL_PAD_MAX = 0.7D;
   private static final double SPLASH_PAD_MIN = 0.02D;
   private static final double SPLASH_PAD_MAX = 0.4D;
   private static final double MIST_Y_MIN = 0.3D;
   private static final double MIST_Y_MAX = 1.0D;
   private static final double WAKE_BACK_MIN = 0.1D;
   private static final double WAKE_BACK_MAX = 0.3D;
   private static final double MIST_DOWN_MIN = 0.05D;
   private static final double MIST_DOWN_MAX = 0.15D;
   private static final double SPEED_FACTOR_REF_KMH = 80.0D;
   private static final double SPEED_FACTOR_MIN = 0.5D;
   private static final double SPEED_FACTOR_MAX = 1.5D;
   private static final double MIST_SPREAD = 0.05D;
   private static final double SPLASH_Y_MIN = 0.12D;
   private static final double SPLASH_Y_MAX = 0.45D;
   private static final double SPLASH_MIN_KMH = 60.0D;
   private static final double SPLASH_CHANCE_BASE = 0.25D;
   private static final double SPLASH_CHANCE_RANGE = 0.5D;
   private static final int DIAG_PERIOD_TICKS = 100;
   private static ParticleStatus mode = ParticleStatus.ALL;
   private static final double MINIMAL_CHANCE_SCALE = 0.55D;
   private static final int MINIMAL_PUFFS = 4;
   private static final int MIST_PER_EVENT = 2;
   private static final double MINIMAL_JITTER = 0.3D;
   private static long tick;
   private static String probeAt;
   private static long probeTick = Long.MIN_VALUE;
   private static double tokens = 240.0D;
   private static final Set<String> windowTrains = new HashSet();
   private static int windowSpawned;
   private static int windowSkipped;
   private static int windowRoofSkipped;
   private static final Map<String, RainSpray.Diag> WINDOW = new LinkedHashMap();
   private static final Map<String, double[]> LAST_PROGRESS = new HashMap();

   private RainSpray() {
   }

   public static void tick() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      Minecraft client = Minecraft.getInstance();
      ClientLevel world = client.level;
      ++tick;
      tokens = Math.min(240.0D, tokens + 60.0D);
      mode = particleMode(client);
      if (world != null && client.player != null && cfg != null && cfg.enabled && (cfg.rainSpray || cfg.rainSprayAnySiding())) {
         boolean raining;
         try {
            raining = raining(world);
         } catch (Throwable var7) {
            resetWindow();
            return;
         }

         try {
            probeAtPlayer(client, world);
         } catch (Throwable var6) {
            MtrHeadlightClient.LOGGER.error("[lte] spray probe failed", var6);
         }

         try {
            emit(cfg, client, world, raining);
         } catch (Throwable var5) {
            MtrHeadlightClient.LOGGER.error("[lte] rain spray failed", var5);
         }

         report(cfg, world, raining);
      } else {
         resetWindow();
      }
   }

   private static void probeAtPlayer(Minecraft client, ClientLevel world) {
      if (DebugProbe.sprayEnabled()) {
         Player player = client.player;
         if (player != null) {
            Vec3 look = player.getViewVector(1.0F);
            double lx = look.x;
            double lz = look.z;
            double llen = Math.sqrt(lx * lx + lz * lz);
            if (llen < 1.0E-4D) {
               lx = 0.0D;
               lz = 1.0D;
            } else {
               lx /= llen;
               lz /= llen;
            }

            double cx = player.getX() + lx * 2.5D;
            double cy = player.getEyeY();
            double cz = player.getZ() + lz * 2.5D;
            probeAt = fmtCoord(cx) + "," + fmtCoord(cy) + "," + fmtCoord(cz);
            probeTick = tick;
            ParticleOptions effect = SprayType.effect();

            for(int i = 0; i < 12; ++i) {
               double a = (Math.PI * 2D) * (double)i / 12.0D + world.random.nextDouble() * 0.5D;
               double r = 0.25D + 0.75D * world.random.nextDouble();
               world.addParticle(effect, true, cx + Math.cos(a) * r, cy + (world.random.nextDouble() - 0.5D) * 0.8D, cz + Math.sin(a) * r, 0.0D, 0.01D, 0.0D);
            }

         }
      }
   }

   private static boolean raining(ClientLevel world) {
      return RailWeather.raining(world);
   }

   private static void emit(HeadlightConfig cfg, Minecraft client, ClientLevel world, boolean raining) {
      List<TrainClient> trains;
      try {
         trains = new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var50) {
         return;
      }

      if (!trains.isEmpty()) {
         Vec3 eye = client.player.position();
         double limitSq = 4096.0D;
         int tickBudget = 80;
         boolean forceSky = DebugProbe.sprayEnabled();

         for(TrainClient train : trains) {
            if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0 && train.transportMode == TransportMode.TRAIN) {
               HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
               String key = DepartureState.keyOf(train) + "#" + System.identityHashCode(train);
               double kmh = kmh(train);
               RainSpray.Diag diag = diag(key, train);
               diag.kmh = kmh;
               diag.railKmh = railKmh(train, key);
               diag.raining = raining;
               ++diag.ticks;
               if (!eff.rainSpray) {
                  diag.reason = "off";
               } else {
                  double minKmh = eff.rainSprayMinSpeedKmh;
                  if (!raining) {
                     diag.reason = "dry";
                  } else if (tickBudget <= 0) {
                     diag.reason = "budget";
                  } else {
                     boolean lowBoost = cfg.rainSprayLowSpeedBoost && minKmh > 20.0D;
                     double gateKmh = lowBoost ? 20.0D : minKmh;
                     if (kmh <= gateKmh) {
                        diag.reason = "slow(" + Math.round(kmh) + "<=" + Math.round(gateKmh) + ")";
                     } else {
                        double intensity = intensity(kmh, minKmh);
                        double lowFactor = lowFactor(kmh, minKmh, lowBoost);
                        diag.lowFactor = lowFactor;
                        boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                        TrainPose pose = TrainPose.ofShared(train, reversed);
                        int cars = pose.cars();
                        if (cars <= 0) {
                           diag.reason = "nopose";
                        } else {
                           int start = (int)(tick % (long)cars);
                           int scan = Math.min(cars, 16);
                           int spawnedForTrain = 0;
                           boolean sawFar = false;
                           boolean sawRoofed = false;
                           boolean sawThrottle = false;
                           boolean sawChance = false;
                           boolean sawCandidate = false;
                           boolean skyAny = false;
                           boolean skySampled = false;
                           double nearest = Double.MAX_VALUE;

                           for(int k = 0; k < scan && spawnedForTrain < 24 && tickBudget > 0; ++k) {
                              int car = (start + k) % cars;
                              Vec3 a = pose.carStart(car);
                              Vec3 b = pose.carEnd(car);
                              Vec3 center = a.add(b).scale(0.5D);
                              double distSq = center.distanceToSqr(eye);
                              nearest = Math.min(nearest, Math.sqrt(distSq));
                              if (distSq > 4096.0D) {
                                 sawFar = true;
                              } else {
                                 Boolean sampled = skyState(world, center);
                                 if (sampled != null) {
                                    skySampled = true;
                                    skyAny |= sampled;
                                 }

                                 if (sampled == null) {
                                    if (!forceSky) {
                                       continue;
                                    }
                                 } else if (!sampled && !forceSky) {
                                    sawRoofed = true;
                                    ++windowRoofSkipped;
                                    continue;
                                 }

                                 sawCandidate = true;
                                 if (tokens < 1.0D) {
                                    ++windowSkipped;
                                    sawThrottle = true;
                                 } else if (world.random.nextDouble() > spawnChance(intensity) * chanceScale() * lowFactor) {
                                    ++windowSkipped;
                                    sawChance = true;
                                 } else {
                                    int made = spawnOne(world, train, pose, car, a, b, intensity, kmh, diag);
                                    if (made > 0) {
                                       tokens = Math.max(0.0D, tokens - (double)Math.min(made, 4));
                                       tickBudget -= made;
                                       spawnedForTrain += made;
                                       windowSpawned += made;
                                       diag.spawned += made;
                                    }
                                 }
                              }
                           }

                           diag.sky = skySampled ? skyAny : null;
                           diag.dist = nearest == Double.MAX_VALUE ? -1.0D : nearest;
                           if (spawnedForTrain > 0) {
                              diag.reason = "ok";
                              windowTrains.add(key);
                           } else if (sawThrottle) {
                              diag.reason = "throttled";
                           } else if (sawChance) {
                              diag.reason = "chance";
                           } else if (sawCandidate) {
                              diag.reason = "ok";
                              windowTrains.add(key);
                           } else if (sawRoofed) {
                              diag.reason = "roofed";
                           } else if (sawFar) {
                              diag.reason = "far(" + Math.round(nearest) + ">64)";
                           } else {
                              diag.reason = "none";
                           }
                        }
                     }
                  }
               }
            }
         }

      }
   }

   private static int spawnOne(ClientLevel world, TrainClient train, TrainPose pose, int car, Vec3 a, Vec3 b, double intensity, double kmh, RainSpray.Diag diag) {
      Vec3 dir = pose.carForward(car);
      double fx = dir.x;
      double fz = dir.z;
      double len = Math.sqrt(fx * fx + fz * fz);
      if (len < 1.0E-4D) {
         fx = 0.0D;
         fz = 1.0D;
         len = 1.0D;
      }

      fx /= len;
      fz /= len;
      double rx = -fz;
      double rz = fx;
      double halfW = Math.max(1.3D, (double)train.width * 0.5D);
      double side = world.random.nextBoolean() ? 1.0D : -1.0D;
      double along = 0.12D + world.random.nextDouble() * 0.76D;
      Vec3 base = a.add(b.subtract(a).scale(along));
      double railY = Math.min(a.y, b.y);
      double speedFactor = speedFactor(kmh);
      double back = (0.1D + 0.19999999999999998D * world.random.nextDouble()) * speedFactor;
      double out = back * (0.15D + 0.2D * world.random.nextDouble());
      double mistVx = -fx * back + rx * side * out;
      double mistVz = -fz * back + fx * side * out;
      int made = 0;
      boolean minimal = mode == ParticleStatus.MINIMAL;
      int puffs = minimal ? 4 : 2;

      for(int i = 0; i < puffs; ++i) {
         double jx = minimal ? (world.random.nextDouble() - 0.5D) * 2.0D * 0.3D : 0.0D;
         double jy = minimal ? (world.random.nextDouble() - 0.5D) * 2.0D * 0.25D : 0.0D;
         double jz = minimal ? (world.random.nextDouble() - 0.5D) * 2.0D * 0.3D : 0.0D;
         double alongPuff = 0.12D + world.random.nextDouble() * 0.76D;
         Vec3 puffBase = a.add(b.subtract(a).scale(alongPuff));
         double puffSide = (i & 1) == 0 ? side : -side;
         double mistPad = 0.2D + world.random.nextDouble() * 0.49999999999999994D;
         double mistLateral = puffSide * (halfW + mistPad);
         double mistX = puffBase.x + rx * mistLateral + jx;
         double mistZ = puffBase.z + rz * mistLateral + jz;
         double mistY = airY(world, mistX, railY + 0.3D + world.random.nextDouble() * 0.7D + jy, mistZ);
         if (Double.isNaN(mistY)) {
            ++diag.blocked;
         } else {
            double mistVy = -(0.05D + world.random.nextDouble() * 0.09999999999999999D);
            double mistSx = mistVx + (world.random.nextDouble() - 0.5D) * 2.0D * 0.05D;
            double mistSz = mistVz + (world.random.nextDouble() - 0.5D) * 2.0D * 0.05D;
            double hlen = Math.sqrt(mistSx * mistSx + mistSz * mistSz);
            if (hlen > 1.0E-6D) {
               diag.vdirSum += (mistSx * fx + mistSz * fz) / hlen;
               ++diag.vdirN;
            }

            add(world, SprayType.effect(), minimal, mistX, mistY, mistZ, mistSx, mistVy, mistSz);
            ++made;
            diag.halfW = halfW;
            diag.lat = mistLateral;
            diag.dy = mistY - railY;
            diag.at = fmtCoord(mistX) + "," + fmtCoord(mistY) + "," + fmtCoord(mistZ);
         }
      }

      if (kmh >= 60.0D && world.random.nextDouble() < 0.25D + 0.5D * intensity) {
         double splashPad = 0.02D + world.random.nextDouble() * 0.38D;
         double splashLateral = side * (halfW + splashPad);
         double splashX = base.x + rx * splashLateral;
         double splashZ = base.z + rz * splashLateral;
         double splashY = railY + 0.12D + world.random.nextDouble() * 0.33D;
         add(world, ParticleTypes.SPLASH, minimal, splashX, splashY, splashZ, mistVx * 1.6D, 0.16D + world.random.nextDouble() * 0.14D, mistVz * 1.6D);
         ++made;
      }

      return made;
   }

   private static void add(ClientLevel world, ParticleOptions particle, boolean force, double x, double y, double z, double vx, double vy, double vz) {
      world.addParticle(particle, force, x, y, z, vx, vy, vz);
   }

   private static ParticleStatus particleMode(Minecraft client) {
      try {
         ParticleStatus value = client == null ? null : (ParticleStatus)client.options.particles().get();
         return value == null ? ParticleStatus.ALL : value;
      } catch (Throwable var2) {
         return ParticleStatus.ALL;
      }
   }

   private static double chanceScale() {
      return mode == ParticleStatus.MINIMAL ? 0.55D : 1.0D;
   }

   private static double airY(ClientLevel world, double x, double y, double z) {
      try {
         for(int i = 0; i <= 4; ++i) {
            double probe = y + (double)i;
            if (world.isEmptyBlock(BlockPos.containing(x, probe, z))) {
               return probe;
            }
         }

         return Double.NaN;
      } catch (Throwable var10) {
         return y;
      }
   }

   private static Boolean skyState(ClientLevel world, Vec3 pos) {
      int bx = (int)Math.floor(pos.x);
      int bz = (int)Math.floor(pos.z);
      return !world.hasChunk(bx >> 4, bz >> 4) ? null : RailWeather.openSky(world, pos);
   }

   private static double kmh(TrainClient train) {
      try {
         return (double)Math.abs(train.getSpeed()) * 72.0D;
      } catch (RuntimeException var2) {
         return 0.0D;
      }
   }

   private static double railKmh(TrainClient train, String key) {
      try {
         double progress = train.getRailProgress();
         double[] last = (double[])LAST_PROGRESS.get(key);
         LAST_PROGRESS.put(key, new double[]{progress, (double)tick});
         if (LAST_PROGRESS.size() > 128) {
            LAST_PROGRESS.clear();
         }

         if (last == null) {
            return -1.0D;
         } else {
            double dtTicks = Math.max(1.0D, (double)tick - last[1]);
            return Math.abs(progress - last[0]) / dtTicks * 20.0D * 3.6D;
         }
      } catch (RuntimeException var7) {
         return -1.0D;
      }
   }

   private static double intensity(double kmh, double minKmh) {
      if (kmh <= minKmh) {
         return 0.0D;
      } else {
         double span = 120.0D - minKmh;
         return span <= 0.0D ? 1.0D : Math.min(1.0D, (kmh - minKmh) / span);
      }
   }

   private static double spawnChance(double intensity) {
      return 0.25D + 0.75D * intensity;
   }

   private static double lowFactor(double kmh, double minKmh, boolean lowBoost) {
      if (lowBoost && kmh < minKmh) {
         double span = minKmh - 20.0D;
         if (span <= 0.0D) {
            return 1.0D;
         } else {
            double t = (kmh - 20.0D) / span;
            if (t < 0.0D) {
               t = 0.0D;
            } else if (t > 1.0D) {
               t = 1.0D;
            }

            return 0.35D + 0.65D * t;
         }
      } else {
         return 1.0D;
      }
   }

   private static double speedFactor(double kmh) {
      return !(kmh > 0.0D) ? 0.5D : Math.max(0.5D, Math.min(1.5D, kmh / 80.0D));
   }

   static double mistDownMin() {
      return 0.05D;
   }

   static double mistDownMax() {
      return 0.15D;
   }

   private static RainSpray.Diag diag(String key, TrainClient train) {
      RainSpray.Diag d = (RainSpray.Diag)WINDOW.get(key);
      if (d == null) {
         d = new RainSpray.Diag();

         try {
            d.id = train.trainId == null ? key : train.trainId;
         } catch (RuntimeException var4) {
            d.id = key;
         }

         WINDOW.put(key, d);
      }

      return d;
   }

   private static void report(HeadlightConfig cfg, ClientLevel world, boolean raining) {
      if (!cfg.rainSprayDiagnostics) {
         resetWindow();
      } else if (tick % 100L == 0L) {
         int trains = windowTrains.size();
         int spawned = windowSpawned;
         int skipped = windowSkipped;
         int roofSkipped = windowRoofSkipped;
         List<RainSpray.Diag> lines = new ArrayList(WINDOW.values());
         double[] sizes = SprayParticle.drainScaleStats();
         String avgSize = sizes[0] < 0.0D ? "-" : String.format(Locale.ROOT, "%.3f", sizes[0]);
         int expected = 8000;
         String density = String.format(Locale.ROOT, "%.3f(%d/%d)", (double)spawned / 8000.0D, spawned, 8000);
         resetWindow();

         try {
            MtrHeadlightClient.LOGGER.info("[lte] spray: trains={} spawned={} density={} skipped={} roofSkipped={} cap={} lowBoost={} avgSize={} {} raining={} weather={} grad={} particles={} sprayType={} culled={} probeAt={}", new Object[]{trains, spawned, density, skipped, roofSkipped, 240, lowBoostLabel(cfg), avgSize, SprayParticle.paramsLabel(), raining, RailWeather.weatherRaining(world), fmt((double)RailWeather.rainGradient(world)), mode, SprayType.label(), ParticleCull.culled().isEmpty() ? "none" : ParticleCull.culled(), probeAt != null && tick - probeTick <= 100L ? probeAt : "-"});

            for(RainSpray.Diag d : lines) {
               MtrHeadlightClient.LOGGER.info("spray-diag: id={} speed={} rail={} low={} raining={} sky={} dist={} lat={} halfW={} dy={} vdir={} at={} spawned={} blocked={} reason={} probeAt={}", new Object[]{d.id, fmt(d.kmh), fmt(d.railKmh), String.format(Locale.ROOT, "%.2f", d.lowFactor), d.raining, d.sky == null ? "-" : skyName(d.sky), d.dist < 0.0D ? "-" : fmt(d.dist), d.lat == Double.MAX_VALUE ? "-" : fmt(d.lat), d.halfW < 0.0D ? "-" : fmt(d.halfW), d.dy == Double.MAX_VALUE ? "-" : fmt(d.dy), d.vdirN == 0 ? "-" : String.format(Locale.ROOT, "%.2f", d.vdirSum / (double)d.vdirN), d.at == null ? "-" : d.at, d.spawned, d.blocked, d.reason, probeAt != null && tick - probeTick <= 100L ? probeAt : "-"});
            }
         } catch (Throwable var14) {
         }

      }
   }

   private static String fmt(double value) {
      return value < 0.0D ? "-" : String.format(Locale.ROOT, "%.1f", value);
   }

   private static String skyName(Boolean sky) {
      if (sky == null) {
         return "-";
      } else {
         return sky ? "open" : "roof";
      }
   }

   private static String lowBoostLabel(HeadlightConfig cfg) {
      if (cfg != null && cfg.rainSprayLowSpeedBoost) {
         double minKmh = (double)cfg.rainSprayMinSpeedKmh;
         return !(minKmh > 20.0D) ? "off" : "on(" + Math.round(20.0D) + "-" + Math.round(minKmh) + " " + String.format(Locale.ROOT, "%.2f..1.00", 0.35D) + ")";
      } else {
         return "off";
      }
   }

   private static String fmtCoord(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static void resetWindow() {
      windowTrains.clear();
      windowSpawned = 0;
      windowSkipped = 0;
      windowRoofSkipped = 0;
      WINDOW.clear();
      SprayParticle.drainScaleStats();
   }

   private static final class Diag {
      String id = "?";
      double kmh;
      double railKmh = -1.0D;
      boolean raining;
      Boolean sky;
      double dist = -1.0D;
      int spawned;
      int blocked;
      int ticks;
      String reason = "-";
      double lowFactor = 1.0D;
      double lat = Double.MAX_VALUE;
      double halfW = -1.0D;
      double dy = Double.MAX_VALUE;
      String at;
      double vdirSum;
      int vdirN;
   }
}

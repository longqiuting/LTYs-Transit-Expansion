package dev.js.lte.server;

import dev.js.lte.HeadlightConfig;
import dev.js.lte.LteDebug;
import dev.js.lte.LteLog;
import dev.js.lte.collision.CarGeometry;
import dev.js.lte.collision.CollisionImpact;
import dev.js.lte.collision.DriverKeyBypass;
import dev.js.lte.collision.OnboardToolBypass;
import dev.js.lte.collision.TrainTickCapture;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import mtr.data.Train;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ServerCollision {
   private static final double SEARCH_RANGE = 48.0D;
   private static final double PUSH_PLAYER_MIN_PEN = 0.6D;
   private static final double DOOR_VALUE_EPS = 0.02D;
   private static final long NO_DATA_WARN_TICK = 400L;
   private static long ticks;
   private static boolean everCaptured;
   private static boolean warnedNoData;
   private static long probeTicks;
   private static long probeNanos;
   private static long probeMaxNanos;
   private static int diagTrains;
   private static int diagCars;
   private static int diagHits;
   private static int diagCulled;
   private static int diagReused;
   private static int diagTotalCars;
   private static double diagHp;
   private static final CarScan.Extras EXTRAS = new CarScan.Extras() {
      public double head(long sidingId) {
         return ServerConfig.get().effectiveFor(sidingId).collisionHeadExtra;
      }

      public double tail(long sidingId) {
         return ServerConfig.get().effectiveFor(sidingId).collisionTailExtra;
      }
   };
   private static final AtomicBoolean WARNED = new AtomicBoolean();
   private static final Map<UUID, Long> LAST_HIT = new HashMap();
   private static final double ROOF_FALL_MIN_DROP = 1.2D;
   private static final int ROOF_FALL_TICKS = 60;
   private static final Map<UUID, ServerCollision.Roof> ROOFS = new HashMap();
   private static volatile Method RIDING_CHECK;
   private static volatile boolean RIDING_PROBED;

   private ServerCollision() {
   }

   public static void tick(MinecraftServer server) {
      if (server != null) {
         ++ticks;
         HeadlightConfig cfg = ServerConfig.get();
         boolean trainsOn = cfg.trainCollision;
         if (trainsOn || cfg.trackbedCollision) {
            boolean dedicated = server.isDedicatedServer();
            long now = (long)server.getTickCount();
            boolean probe = LteDebug.probe();
            boolean collect = probe || LteDebug.collision();
            long t0 = probe ? System.nanoTime() : 0L;
            double height = cfg.trainCollisionHeightClamped();
            double endPad = cfg.trainCollisionEndPadClamped();

            for(ServerLevel world : server.getAllLevels()) {
               try {
                  List<ServerPlayer> players = world.players();
                  int playerCount = players.size();
                  double[] px = new double[playerCount];
                  double[] pz = new double[playerCount];

                  for(int i = 0; i < playerCount; ++i) {
                     ServerPlayer player = (ServerPlayer)players.get(i);
                     px[i] = player.getX();
                     pz[i] = player.getZ();
                  }

                  if (trainsOn && playerCount > 0) {
                     List<Train> trains = TrainTickCapture.trains(world, world.getGameTime());
                     if (!trains.isEmpty()) {
                        everCaptured = true;
                        List<CarGeometry.Car> cars = CarScan.scan(trains, height, endPad, EXTRAS, px, pz, playerCount, now);
                        if (collect) {
                           diagTrains = Math.max(diagTrains, CarScan.lastTrains());
                           diagCars = Math.max(diagCars, cars.size());
                           diagCulled = Math.max(diagCulled, CarScan.lastCulled());
                           diagReused = Math.max(diagReused, CarScan.lastReused());
                           diagTotalCars = Math.max(diagTotalCars, CarScan.lastTotalCars());
                        }

                        if (!cars.isEmpty()) {
                           for(Entity entity : world.getAllEntities()) {
                              if (entity instanceof LivingEntity) {
                                 LivingEntity living = (LivingEntity)entity;
                                 if (dedicated || living instanceof Player) {
                                    try {
                                       collide(world, living, trains, cars, cfg, now, dedicated);
                                    } catch (Throwable var27) {
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  ServerTrackbed.tick(world, cfg, now, px, pz, playerCount);
               } catch (Throwable var28) {
                  warnOnce(var28);
               }
            }

            try {
               CollisionImpact.sweep(now);
            } catch (Throwable var26) {
            }

            pruneHits(now);
            if (probe) {
               long elapsed = System.nanoTime() - t0;
               ++probeTicks;
               probeNanos += elapsed;
               if (elapsed > probeMaxNanos) {
                  probeMaxNanos = elapsed;
               }
            }

            diag(probe);
            noDataHint();
         }
      }
   }

   public static void reset() {
      TrainTickCapture.clear();
      CollisionImpact.clear();
      CarScan.reset();
      ServerTrackbed.reset();
      synchronized(LAST_HIT) {
         LAST_HIT.clear();
      }

      synchronized(ROOFS) {
         ROOFS.clear();
      }

      everCaptured = false;
      warnedNoData = false;
   }

   public static void warnOnce(Throwable t) {
      if (WARNED.compareAndSet(false, true)) {
         LteLog.LOGGER.warn("[lte] server collision: MTR data unavailable - server-side collision disabled until it recovers", t);
      }

   }

   public static boolean recentlyHit(Entity entity, long now, int grace) {
      if (entity == null) {
         return false;
      } else {
         Long tick;
         synchronized(LAST_HIT) {
            tick = (Long)LAST_HIT.get(entity.getUUID());
         }

         return tick != null && now - tick < (long)grace;
      }
   }

   private static void pruneHits(long now) {
      if (!LAST_HIT.isEmpty()) {
         synchronized(LAST_HIT) {
            LAST_HIT.entrySet().removeIf((e) -> now - e.getValue() > 200L);
         }
      }

      if (!ROOFS.isEmpty()) {
         synchronized(ROOFS) {
            ROOFS.entrySet().removeIf((e) -> {
               ServerCollision.Roof st = (ServerCollision.Roof)e.getValue();
               return st != null && st.seenTick >= 0L && now - st.seenTick > 100L;
            });
         }
      }

   }

   private static void collide(ServerLevel world, LivingEntity living, List<Train> trains, List<CarGeometry.Car> cars, HeadlightConfig cfg, long now, boolean dedicated) {
      if (living.isAlive() && !living.isDeadOrDying() && !(living.getHealth() <= 0.0F)) {
         if (!DriverKeyBypass.active(living)) {
            if (!OnboardToolBypass.active(living)) {
               float flightHp = CollisionImpact.flightDamage(living, cfg, now);
               if (flightHp > 0.0F) {
                  CollisionImpact.applyDamage(living, flightHp, CollisionImpact.wasFast(living.getUUID()), cfg);
                  diagHp += (double)flightHp;
               } else if (!living.isSpectator() && !living.isInvulnerable()) {
                  if (!living.isPassenger() && living.getVehicle() == null) {
                     if (living instanceof Player) {
                        Player rider = (Player)living;
                        if (ridingAny(trains, rider)) {
                           return;
                        }
                     }

                     if (living instanceof Player) {
                        Player player = (Player)living;
                        if (player.isSpectator() || !cfg.damageInCreative && player.isCreative()) {
                           return;
                        }
                     }

                     CarGeometry.Push hit = null;
                     CarGeometry.Car hitCar = null;
                     double hitSpeed = 0.0D;
                     ServerCollision.Roof roof = roofOf(living);
                     CarGeometry.Car standing = onRoof(living, cars, cfg.trainCollisionHeightClamped());
                     roofTick(living, roof, standing, cfg, now);
                     if (standing == null) {
                        for(CarGeometry.Car car : cars) {
                           if (!roofEscape(roof, living, car) && near(living, car)) {
                              CarGeometry.Push push = firstHit(world, living, car);
                              if (push != null) {
                                 hit = push;
                                 hitCar = car;
                                 hitSpeed = car.speedKmh;
                                 break;
                              }
                           }
                        }

                        if (hit == null) {
                           float hurtHp = CollisionImpact.hurtDamage(living, cfg, now);
                           if (hurtHp > 0.0F) {
                              CollisionImpact.applyDamage(living, hurtHp, CollisionImpact.wasFast(living.getUUID()), cfg);
                              diagHp += (double)hurtHp;
                           }

                        } else {
                           ++diagHits;
                           synchronized(LAST_HIT) {
                              LAST_HIT.put(living.getUUID(), now);
                           }

                           boolean player = living instanceof ServerPlayer;
                           boolean takeOver = !player || dedicated && hit.pen >= 0.6D;
                           if (takeOver) {
                              CarGeometry.applyPush(world, living, hit.dirX, hit.dirZ, hit.pen);
                           } else if (dedicated) {
                              velocityPush(living, hitCar);
                           }

                           HeadlightConfig.Effective eff = cfg.effectiveFor(hitCar.sidingId);
                           float hp = CollisionImpact.tick(living, cfg, eff, hitSpeed, hit.dirX, hit.dirZ, now);
                           if (hp > 0.0F) {
                              CollisionImpact.applyDamage(living, hp, CollisionImpact.wasFast(living.getUUID()), cfg);
                              diagHp += (double)hp;
                           }

                        }
                     }
                  }
               }
            }
         }
      } else {
         CollisionImpact.forget(living.getUUID());
      }
   }

   private static ServerCollision.Roof roofOf(LivingEntity living) {
      return (ServerCollision.Roof)ROOFS.computeIfAbsent(living.getUUID(), (id) -> new ServerCollision.Roof());
   }

   private static CarGeometry.Car onRoof(LivingEntity living, List<CarGeometry.Car> cars, double height) {
      Iterator var4 = cars.iterator();

      CarGeometry.Car car;
      while(true) {
         if (!var4.hasNext()) {
            return null;
         }

         car = (CarGeometry.Car)var4.next();
         if (near(living, car)) {
            double roof = car.roofAt(living.getX(), living.getZ(), height);
            if (!Double.isNaN(roof)) {
               double feet = living.getY();
               if (!(feet < roof - 0.45D) && !(feet > roof + 0.06D)) {
                  if (living instanceof Player) {
                     if (!living.onGround()) {
                        continue;
                     }
                  } else if (living.getDeltaMovement().y > 0.08D) {
                     continue;
                  }
                  break;
               }
            }
         }
      }

      return car;
   }

   private static void roofTick(LivingEntity living, ServerCollision.Roof st, CarGeometry.Car standing, HeadlightConfig cfg, long now) {
      st.seenTick = now;
      if (standing != null) {
         st.key = standing.key;
         st.index = standing.index;
         st.roofY = standing.roofAt(living.getX(), living.getZ(), cfg.trainCollisionHeightClamped());
         st.speedKmh = standing.speedKmh;
         st.standTick = now;
         clearFall(st);
      } else {
         if (st.standTick == now - 1L) {
            st.fallKey = st.key;
            st.fallIndex = st.index;
            st.fallRoofY = st.roofY;
            st.fallSpeedKmh = st.speedKmh;
            st.fallUntil = now + 60L;
            st.standTick = -1L;
         }

         if (st.fallKey != null) {
            if (now > st.fallUntil) {
               clearFall(st);
            } else if (living.onGround() && !Double.isNaN(st.fallRoofY)) {
               double drop = st.fallRoofY - living.getY();
               double speed = st.fallSpeedKmh;
               clearFall(st);
               if (drop >= 1.2D && cfg.trainCollisionDamage) {
                  float hp = CollisionImpact.roofFallHp(living, cfg, speed);
                  if (hp > 0.0F) {
                     try {
                        LteLog.LOGGER.info("[lte] roof-fall: v={}km/h drop={} hp={} by=server", new Object[]{String.format(Locale.ROOT, "%.1f", speed), String.format(Locale.ROOT, "%.2f", drop), String.format(Locale.ROOT, "%.2f", hp)});
                     } catch (Throwable var12) {
                     }

                     if (CollisionImpact.applyRoofFall(living, hp, cfg)) {
                        diagHp += (double)hp;
                     }

                  }
               }
            }
         }
      }
   }

   private static void clearFall(ServerCollision.Roof st) {
      st.fallKey = null;
      st.fallIndex = -1;
      st.fallRoofY = Double.NaN;
      st.fallSpeedKmh = 0.0D;
      st.fallUntil = -1L;
   }

   private static boolean roofEscape(ServerCollision.Roof st, LivingEntity living, CarGeometry.Car car) {
      if (st.fallKey != null && car.index == st.fallIndex && st.fallKey.equals(car.key)) {
         if (Double.isNaN(st.fallRoofY)) {
            return false;
         } else if (living.getY() >= st.fallRoofY - 0.06D) {
            return true;
         } else {
            return !living.onGround();
         }
      } else {
         return false;
      }
   }

   private static void velocityPush(LivingEntity living, CarGeometry.Car car) {
      double speed = car.speedKmh / 72.0D;
      if (speed > 0.02D) {
         if (living.isAlive() && !living.isDeadOrDying() && living.getHealth() > 0.0F) {
            if (!CollisionImpact.impulseActive(living)) {
               double carry = Math.min(speed, 1.35D);
               Vec3 cur = living.getDeltaMovement();
               double vx = -car.ux * carry;
               double vz = -car.uz * carry;
               if (!(Math.abs(cur.x - vx) <= 0.03D) || !(Math.abs(cur.z - vz) <= 0.03D)) {
                  living.setDeltaMovement(vx, cur.y, vz);
                  living.hurtMarked = true;
               }
            }
         }
      }
   }

   private static boolean ridingAny(List<Train> trains, Player player) {
      Method check = ridingCheck();
      if (check == null) {
         return false;
      } else {
         for(Train train : trains) {
            try {
               if (Boolean.TRUE.equals(check.invoke(train, player))) {
                  return true;
               }
            } catch (Throwable var6) {
            }
         }

         return false;
      }
   }

   private static Method ridingCheck() {
      Method cached = RIDING_CHECK;
      if (cached == null && !RIDING_PROBED) {
         RIDING_PROBED = true;

         try {
            cached = Train.class.getMethod("isPlayerRiding", Player.class);
         } catch (Throwable var2) {
            cached = null;
         }

         RIDING_CHECK = cached;
         return cached;
      } else {
         return cached;
      }
   }

   private static boolean near(LivingEntity living, CarGeometry.Car car) {
      double dx = living.getX() - car.cx;
      double dz = living.getZ() - car.cz;
      double reach = 48.0D + car.len;
      return dx * dx + dz * dz <= reach * reach;
   }

   private static CarGeometry.Push firstHit(ServerLevel world, LivingEntity living, CarGeometry.Car car) {
      if (!car.doorsOpen) {
         for(CarGeometry.Seg seg : car.body) {
            CarGeometry.Push push = CarGeometry.contact(living, seg);
            if (push.moved) {
               return push;
            }
         }
      }

      for(CarGeometry.Seg cap : car.caps) {
         CarGeometry.Push push = CarGeometry.contact(living, cap);
         if (push.moved) {
            return push;
         }
      }

      return null;
   }

   private static void diag(boolean probe) {
      boolean stats = LteDebug.collision();
      boolean wantPerf = probe && probeTicks > 0L;
      boolean haveStats = stats && (diagTrains > 0 || diagHits > 0 || diagHp > 0.0D || ServerTrackbed.diagDirty());
      if (!wantPerf && !haveStats) {
         if (stats) {
            resetDiag();
         }

      } else if (LteDebug.due()) {
         try {
            if (wantPerf) {
               double avgMs = (double)probeNanos / 1000000.0D / (double)probeTicks;
               double maxMs = (double)probeMaxNanos / 1000000.0D;
               LteLog.LOGGER.info("[lte] perf: collision avg={}ms max={}ms ticks={} trains={} cars={}/{} culled={} reused={}", new Object[]{String.format(Locale.ROOT, "%.3f", avgMs), String.format(Locale.ROOT, "%.3f", maxMs), probeTicks, diagTrains, diagCars, diagTotalCars, diagCulled, diagReused});
            }

            if (haveStats) {
               LteLog.LOGGER.info("[lte] debug: collision trains={} cars={} hits={} damage={} creativeDmg={}", new Object[]{diagTrains, diagCars, diagHits, String.format(Locale.ROOT, "%.1f", diagHp), ServerConfig.get().damageInCreative});
               String bedLine = ServerTrackbed.diagDirty() ? ServerTrackbed.diagLine() : null;
               if (bedLine != null) {
                  LteLog.LOGGER.info("[lte] {}", bedLine);
               }
            }
         } catch (Throwable var8) {
         }

         resetDiag();
      }
   }

   private static void resetDiag() {
      probeTicks = 0L;
      probeNanos = 0L;
      probeMaxNanos = 0L;
      diagTrains = 0;
      diagCars = 0;
      diagHits = 0;
      diagHp = 0.0D;
      diagCulled = 0;
      diagReused = 0;
      diagTotalCars = 0;
   }

   private static void noDataHint() {
      if (!warnedNoData && !everCaptured && ticks >= 400L) {
         warnedNoData = true;
         LteLog.LOGGER.warn("[lte] server collision: no MTR train was captured after {} ticks - the MTR server injection did not apply (version mismatch?). Client-side collision is unaffected.", ticks);
      }
   }

   private static final class Roof {
      String key;
      int index = -1;
      double roofY = Double.NaN;
      double speedKmh;
      long standTick = -1L;
      String fallKey;
      int fallIndex = -1;
      double fallRoofY = Double.NaN;
      double fallSpeedKmh;
      long fallUntil = -1L;
      long seenTick = -1L;
   }
}

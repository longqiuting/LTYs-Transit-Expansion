package dev.js.mtrheadlight.collision;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteDebug;
import dev.js.mtrheadlight.MtrHeadlightClient;
import dev.js.mtrheadlight.server.CarScan;
import java.util.List;
import java.util.Locale;
import mtr.data.Train;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class MobCollision {
   private static final double SEARCH_RANGE = 48.0D;
   private static final int DIAG_PERIOD = 100;
   private static long lastDiagTick = -4611686018427387904L;
   private static int diagHits;
   private static int diagTrains;
   private static double diagHp;
   private static final CarScan.Extras EXTRAS = new CarScan.Extras() {
      public double head(long sidingId) {
         return MtrHeadlightClient.config().effectiveFor(sidingId).collisionHeadExtra;
      }

      public double tail(long sidingId) {
         return MtrHeadlightClient.config().effectiveFor(sidingId).collisionTailExtra;
      }
   };

   private MobCollision() {
   }

   public static void serverTick(MinecraftServer server) {
      if (server != null) {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         if (cfg.trainCollision && cfg.trainCollisionMobs) {
            long now = (long)server.getTickCount();
            double height = cfg.trainCollisionHeightClamped();
            double endPad = cfg.trainCollisionEndPadClamped();

            try {
               for(ServerLevel world : server.getAllLevels()) {
                  List<ServerPlayer> players = world.players();
                  int playerCount = players.size();
                  if (playerCount > 0) {
                     List<Train> trains = TrainTickCapture.trains(world, world.getGameTime());
                     if (!trains.isEmpty()) {
                        double[] px = new double[playerCount];
                        double[] pz = new double[playerCount];

                        for(int i = 0; i < playerCount; ++i) {
                           px[i] = ((ServerPlayer)players.get(i)).getX();
                           pz[i] = ((ServerPlayer)players.get(i)).getZ();
                        }

                        List<CarGeometry.Car> cars = CarScan.scan(trains, height, endPad, EXTRAS, px, pz, playerCount, now);
                        if (LteDebug.collision()) {
                           diagTrains = Math.max(diagTrains, CarScan.lastTrains());
                        }

                        if (!cars.isEmpty()) {
                           for(Entity entity : world.getAllEntities()) {
                              if (entity instanceof LivingEntity) {
                                 LivingEntity living = (LivingEntity)entity;
                                 if (!(entity instanceof Player)) {
                                    try {
                                       collide(world, living, cars, cfg, now);
                                    } catch (Throwable var20) {
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               CollisionImpact.sweep(now);
               diag(now);
            } catch (Throwable var21) {
            }

         }
      }
   }

   public static void clear() {
      TrainTickCapture.clear();
      CollisionImpact.clear();
   }

   private static void collide(ServerLevel world, LivingEntity living, List<CarGeometry.Car> cars, HeadlightConfig cfg, long now) {
      if (living.isAlive() && !living.isDeadOrDying() && !(living.getHealth() <= 0.0F)) {
         float flightHp = CollisionImpact.flightDamage(living, cfg, now);
         if (flightHp > 0.0F) {
            CollisionImpact.applyDamage(living, flightHp, CollisionImpact.wasFast(living.getUUID()), cfg);
            diagHp += (double)flightHp;
         } else if (!living.isSpectator() && !living.isInvulnerable()) {
            if (!living.isPassenger() && living.getVehicle() == null) {
               CarGeometry.Car hitCar = null;
               CarGeometry.Push hit = null;

               for(CarGeometry.Car car : cars) {
                  if (car.speedKmh > 0.0D && near(living, car)) {
                     hit = firstHit(world, living, car);
                     if (hit != null && hit.moved) {
                        hitCar = car;
                        break;
                     }

                     hit = null;
                  }
               }

               if (hit != null && hitCar != null) {
                  CarGeometry.applyPush(world, living, hit.dirX, hit.dirZ, hit.pen);
                  HeadlightConfig.Effective eff = cfg.effectiveFor(hitCar.sidingId);
                  float hp = CollisionImpact.tick(living, cfg, eff, hitCar.speedKmh, hit.dirX, hit.dirZ, now);
                  if (hp > 0.0F) {
                     CollisionImpact.applyDamage(living, hp, CollisionImpact.wasFast(living.getUUID()), cfg);
                     ++diagHits;
                     diagHp += (double)hp;
                  }

               } else {
                  float hurtHp = CollisionImpact.hurtDamage(living, cfg, now);
                  if (hurtHp > 0.0F) {
                     CollisionImpact.applyDamage(living, hurtHp, CollisionImpact.wasFast(living.getUUID()), cfg);
                     diagHp += (double)hurtHp;
                  }

               }
            }
         }
      } else {
         CollisionImpact.forget(living.getUUID());
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

   private static void diag(long now) {
      if (!LteDebug.collision()) {
         diagHits = 0;
         diagTrains = 0;
         diagHp = 0.0D;
      } else if (now - lastDiagTick >= 100L) {
         lastDiagTick = now;
         int hits = diagHits;
         int trains = diagTrains;
         double hp = diagHp;
         diagHits = 0;
         diagTrains = 0;
         diagHp = 0.0D;

         try {
            MtrHeadlightClient.LOGGER.info("[lte] debug: collision(mobs) trains={} hits={} damage={}", new Object[]{trains, hits, String.format(Locale.ROOT, "%.1f", hp)});
         } catch (Throwable var7) {
         }

      }
   }
}

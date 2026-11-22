package dev.js.mtrheadlight.server;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteDebug;
import dev.js.mtrheadlight.collision.CollisionImpact;
import dev.js.mtrheadlight.collision.RailBed;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ServerTrackbed {
   private static final double RANGE = 128.0D;
   private static final int IMPACT_GRACE = 10;
   private static final Map<ServerLevel, Map<Long, RailBed>> BEDS = new WeakHashMap();
   private static final Map<UUID, ServerTrackbed.Held> FOLLOW = new HashMap();
   private static int diagItems;
   private static int diagMobs;
   private static int diagPlayers;
   private static int diagOk;
   private static int diagLost;
   private static int diagHop;

   private ServerTrackbed() {
   }

   public static void tick(ServerLevel world, HeadlightConfig cfg, long now, double[] px, double[] pz, int playerCount) {
      if (world != null && cfg.trackbedCollision) {
         if (cfg.trackbedCollisionItems || cfg.trackbedCollisionEntities) {
            if (playerCount > 0 && px != null && pz != null) {
               Object rails = MtrServerData.rails(world);
               if (rails != null) {
                  boolean stats = LteDebug.collision();
                  double thickness = cfg.trackbedThicknessClamped();
                  double stepUp = cfg.trackbedStepUpClamped();
                  int seen = 0;

                  for(Entity entity : world.getAllEntities()) {
                     try {
                        if (entity instanceof Player) {
                           if (stats && near(entity.getX(), entity.getZ(), px, pz, playerCount)) {
                              ++seen;
                           }
                        } else {
                           boolean item = entity instanceof ItemEntity || entity instanceof FallingBlockEntity;
                           if (item) {
                              if (!cfg.trackbedCollisionItems) {
                                 continue;
                              }
                           } else if (!cfg.trackbedCollisionEntities || !(entity instanceof LivingEntity) || !entity.isAlive() || CollisionImpact.launched(entity.getUUID())) {
                              continue;
                           }

                           if (near(entity.getX(), entity.getZ(), px, pz, playerCount) && chunkLoaded(world, entity) && !ServerCollision.recentlyHit(entity, now, 10) && snap(world, rails, entity, thickness, stepUp, now) && stats) {
                              if (item) {
                                 ++diagItems;
                              } else {
                                 ++diagMobs;
                              }
                           }
                        }
                     } catch (Throwable var19) {
                     }
                  }

                  if (stats) {
                     diagPlayers = Math.max(diagPlayers, seen);
                  }

                  if (now % 20L == 0L) {
                     synchronized(FOLLOW) {
                        FOLLOW.entrySet().removeIf((entry) -> ((ServerTrackbed.Held)entry.getValue()).tick < now - 40L);
                     }
                  }

               }
            }
         }
      }
   }

   private static boolean snap(ServerLevel world, Object rails, Entity entity, double thickness, double stepUp, long now) {
      double x = entity.getX();
      double feet = entity.getY();
      double z = entity.getZ();
      int cellX = entity.getBlockX() >> 4;
      int cellZ = entity.getBlockZ() >> 4;
      UUID id = entity.getUUID();
      ServerTrackbed.Held held;
      synchronized(FOLLOW) {
         held = (ServerTrackbed.Held)FOLLOW.get(id);
      }

      RailBed.Step step = bed(world, cellX, cellZ).follow(rails, now, held == null ? null : held.state, x, feet, z);
      synchronized(FOLLOW) {
         if (step.follow != null) {
            FOLLOW.put(id, new ServerTrackbed.Held(step.follow, now));
         } else {
            FOLLOW.remove(id);
         }
      }

      if (step.kind == RailBed.Kind.HOP) {
         ++diagHop;
      }

      if (step.follow != null) {
         ++diagOk;
      } else if (held != null) {
         ++diagLost;
      }

      RailBed.Sample sample = step.sample;
      if (sample == null) {
         return false;
      } else {
         double surface = sample.y + thickness;
         Vec3 v = entity.getDeltaMovement();
         double from = entity.yo;
         RailBed.Verdict verdict = RailBed.verdict(feet - surface, v.y, Double.isNaN(from) ? Double.NaN : from - surface, stepUp);
         if (verdict != RailBed.Verdict.SNAP && step.continued() && sample.distance <= 1.3D && v.y <= 0.02D && feet - surface >= -1.5D && feet - surface <= 0.6D) {
            verdict = RailBed.Verdict.SNAP;
         }

         if (verdict != RailBed.Verdict.SNAP) {
            return false;
         } else {
            AABB moved = entity.getBoundingBox().move(0.0D, surface - feet, 0.0D);
            if (!world.noCollision(entity, moved)) {
               return false;
            } else {
               entity.setPos(x, surface, z);
               entity.setDeltaMovement(v.x, 0.0D, v.z);
               entity.fallDistance = 0.0F;
               entity.setOnGround(true);
               entity.hurtMarked = true;
               return true;
            }
         }
      }
   }

   private static boolean near(double x, double z, double[] px, double[] pz, int n) {
      for(int i = 0; i < n; ++i) {
         double dx = px[i] - x;
         double dz = pz[i] - z;
         if (dx * dx + dz * dz <= 16384.0D) {
            return true;
         }
      }

      return false;
   }

   private static boolean chunkLoaded(ServerLevel world, Entity entity) {
      try {
         return world.hasChunk(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
      } catch (Throwable var3) {
         return false;
      }
   }

   private static RailBed bed(ServerLevel world, int cellX, int cellZ) {
      long key = (long)cellX << 32 ^ (long)cellZ & 4294967295L;
      synchronized(BEDS) {
         Map<Long, RailBed> cells = (Map)BEDS.get(world);
         if (cells == null) {
            cells = new HashMap();
            BEDS.put(world, cells);
         }

         RailBed bed = (RailBed)cells.get(key);
         if (bed == null) {
            if (cells.size() > 512) {
               cells.clear();
            }

            bed = new RailBed();
            cells.put(key, bed);
         }

         return bed;
      }
   }

   public static void reset() {
      synchronized(BEDS) {
         BEDS.clear();
      }

      synchronized(FOLLOW) {
         FOLLOW.clear();
      }

      MtrServerData.reset();
      diagItems = 0;
      diagMobs = 0;
      diagPlayers = 0;
      diagOk = 0;
      diagLost = 0;
      diagHop = 0;
   }

   public static String diagLine() {
      String line = "debug: trackbed players=" + diagPlayers + " items=" + diagItems + " mobs=" + diagMobs + " ok=" + diagOk + " lost=" + diagLost + " hop=" + diagHop;
      diagItems = 0;
      diagMobs = 0;
      diagPlayers = 0;
      diagOk = 0;
      diagLost = 0;
      diagHop = 0;
      return line;
   }

   public static boolean diagDirty() {
      return diagItems > 0 || diagMobs > 0 || diagPlayers > 0;
   }

   private static final class Held {
      final RailBed.Follow state;
      final long tick;

      Held(RailBed.Follow state, long tick) {
         this.state = state;
         this.tick = tick;
      }
   }
}

package dev.js.mtrheadlight.collision;

import dev.js.mtrheadlight.DepartureState;
import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteDebug;
import dev.js.mtrheadlight.MtrHeadlightClient;
import dev.js.mtrheadlight.TrainPose;
import dev.js.mtrheadlight.net.ServerAuthority;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CollisionSystem {
   /** 物理客户端判定：专用服务端上 net.minecraft.client.* 被 RuntimeDistCleaner 拦截。 */
   private static final boolean CLIENT =
         dev.js.mtrheadlight.forge.platform.FabricLoader.getInstance().getEnvironmentType()
               == dev.js.mtrheadlight.forge.platform.EnvType.CLIENT;
   private static final double SEARCH_RANGE = 48.0D;
   private static final double PUSH_EPS = 0.001D;
   private static final double SIDE_INSET = 0.05D;
   private static final double FRONTAL_PEN_RATIO = 1.5D;
   private static final double MAX_CARRY_VEL = 1.35D;
   private static final double SIDE_CARRY = 0.45D;
   private static final double OUT_VEL_SCALE = 0.55D;
   private static final double MIN_OUT_VEL = 0.12D;
   private static final double PUSH_VEL_EPS = 0.03D;
   private static final float DOOR_VALUE_EPS = 0.02F;
   private static final double SEG_OVERLAP = 0.3D;
   private static final double SEG_TARGET_LEN = 2.0D;
   private static final double SEG_MIN_LEN = 1.5D;
   private static final int SEG_MAX = 32;
   private static final int SEG_WINDOW = 1;
   private static final double ROOF_TOL = 0.45D;
   private static final double ROOF_ABOVE = 0.06D;
   private static final double CARRY_MAX = 4.0D;
   private static final double ROOF_AIR_MAX = 3.0D;
   private static final int ROOF_FALL_TICKS = 60;
   private static final double ROOF_FALL_MIN_DROP = 1.2D;
   private static final double DEFAULT_HALF_WIDTH = 1.3D;
   private static final double MIN_HALF_WIDTH = 1.3D;
   private static final double MIN_HALF_WIDTH_ABS = 0.6D;
   private static final double CURVE_EPS = 3.0D;
   private static final double STRAIGHT_ANGLE = 0.02D;
   private static final double KMH_PER_BLOCK_PER_TICK = 72.0D;
   private static final int DIAG_PERIOD = 100;
   private static long ticks;
   private static double startY = Double.NaN;
   private static double startX = Double.NaN;
   private static double startZ = Double.NaN;
   private static Entity cacheEntity;
   private static long cacheTick = -1L;
   private static double cacheHeight;
   private static double cacheEndPad;
   private static List<CollisionSystem.CarHit> cacheCars = List.of();
   private static String roofKey;
   private static String standKey;
   private static int standIndex = -1;
   private static long standTick = -1L;
   private static double standRoofY = Double.NaN;
   private static double standSpeedKmh;
   private static String fallKey;
   private static int fallIndex = -1;
   private static double fallRoofY = Double.NaN;
   private static double fallSpeedKmh;
   private static long fallUntil = -1L;
   private static double pushDirX;
   private static double pushDirZ;
   private static double lastRoofY = Double.NaN;
   private static final Map<String, CollisionSystem.CarTrack> TRACKS = new HashMap();
   private static int diagCars;
   private static int diagSegs;
   private static int diagHits;
   private static double diagDamage;
   private static int diagPushed;
   private static int diagVelPushes;
   private static double diagHurt;
   private static long diagImpulsesLast;
   private static int diagRiding;
   private static double diagTrackbed = Double.NaN;
   private static double diagHead = Double.NaN;
   private static double diagTail = Double.NaN;
   private static ClientLevel lastWorld;
   private static long rideTick = -1L;
   private static Entity rideEntity;
   private static boolean rideCached;
   private static Method RIDING_CHECK;
   private static boolean RIDING_PROBED;
   private static final AtomicBoolean WARNED = new AtomicBoolean();

   public static long tickCount() {
      return ticks;
   }

   private CollisionSystem() {
   }

   private static boolean isRiding(Entity entity) {
      if (entity instanceof Player player) {
         if (!player.isPassenger() && player.getVehicle() == null) {
            if (rideTick == ticks && rideEntity == entity) {
               return rideCached;
            } else {
               rideTick = ticks;
               rideEntity = entity;
               rideCached = ridingAny(player);
               return rideCached;
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean ridingAny(Player player) {
      Method check = ridingCheck();
      if (check == null) {
         return false;
      } else {
         List<TrainClient> trains;
         try {
            trains = new ArrayList(ClientData.TRAINS);
         } catch (Throwable var7) {
            return false;
         }

         for(TrainClient train : trains) {
            if (train != null) {
               try {
                  if (Boolean.TRUE.equals(check.invoke(train, player))) {
                     return true;
                  }
               } catch (Throwable var6) {
               }
            }
         }

         return false;
      }
   }

   private static Method ridingCheck() {
      if (RIDING_PROBED) {
         return RIDING_CHECK;
      } else {
         RIDING_PROBED = true;

         try {
            RIDING_CHECK = TrainClient.class.getMethod("isPlayerRiding", Player.class);
         } catch (Throwable var1) {
            RIDING_CHECK = null;
         }

         return RIDING_CHECK;
      }
   }

   public static void beforeMove(Entity entity) {
      if (isLocal(entity)) {
         startY = entity.getY();
         startX = entity.getX();
         startZ = entity.getZ();
      }
   }

   public static Vec3 clampMovement(Entity entity, Vec3 movement) {
      if (isLocal(entity) && !(movement.y >= 0.0D)) {
         if (entity instanceof Player) {
            Player spectator = (Player)entity;
            if (spectator.isSpectator()) {
               return movement;
            }
         }

         if (isRiding(entity)) {
            return movement;
         } else if (DriverKeyBypass.active(entity)) {
            return movement;
         } else if (OnboardToolBypass.active(entity)) {
            return movement;
         } else {
            try {
               HeadlightConfig cfg = MtrHeadlightClient.config();
               if (!cfg.trainCollision) {
                  return movement;
               } else {
                  double height = cfg.trainCollisionHeightClamped();
                  double feet = entity.getY();
                  double clamped = movement.y;

                  for(CollisionSystem.CarHit car : cars(entity, height)) {
                     double roof = car.roofAt(entity.getX(), entity.getZ(), height);
                     if (!Double.isNaN(roof) && !(feet < roof - 0.06D) && feet + clamped < roof) {
                        clamped = roof - feet;
                     }
                  }

                  return clamped == movement.y ? movement : new Vec3(movement.x, clamped, movement.z);
               }
            } catch (Throwable var13) {
               warnOnce(var13);
               return movement;
            }
         }
      } else {
         return movement;
      }
   }

   public static void afterMove(Entity entity) {
      if (isLocal(entity)) {
         roofKey = null;

         try {
            HeadlightConfig cfg = MtrHeadlightClient.config();
            if (!cfg.trainCollision && !cfg.trackbedCollision) {
               return;
            }

            Player player = (Player)entity;
            if (player.isDeadOrDying() || player.getHealth() <= 0.0F) {
               roofKey = null;
               return;
            }

            if (player.isSpectator() || player.isPassenger()) {
               return;
            }

            if (isRiding(player)) {
               roofKey = null;
               ++diagRiding;
               flight(player, cfg);
               return;
            }

            ClientLevel world = (ClientLevel)entity.level();
            if (DriverKeyBypass.active(player)) {
               roofKey = null;
               if (cfg.trackbedCollision) {
                  trackbed(player, cfg, world);
               }

               return;
            }

            if (OnboardToolBypass.active(player)) {
               roofKey = null;
               if (cfg.trackbedCollision) {
                  trackbed(player, cfg, world);
               }

               return;
            }

            double height = cfg.trainCollisionHeightClamped();
            List<CollisionSystem.CarHit> cars = cfg.trainCollision ? cars(entity, height) : List.of();
            lastRoofY = Double.NaN;
            boolean onRoof = false;
            CollisionSystem.CarHit roofCar = null;

            for(CollisionSystem.CarHit car : cars) {
               if (standOnRoof(player, car, height, world)) {
                  onRoof = true;
                  roofKey = car.key;
                  roofCar = car;
                  break;
               }
            }

            roofState(onRoof ? roofCar : roofStandby(player, cars, height));
            if (!onRoof) {
               airborneCarry(player, cars, height, world);
            }

            int contacted = 0;
            double hitSpeed = 0.0D;
            long hitSidingId = 0L;
            double hitDirX = 0.0D;
            double hitDirZ = 0.0D;
            pushDirX = 0.0D;
            pushDirZ = 0.0D;
            if (!onRoof) {
               for(CollisionSystem.CarHit car : cars) {
                  if (!roofEscape(player, car) && pushCar(player, car, world)) {
                     ++contacted;
                     if (car.speedKmh > hitSpeed) {
                        hitSpeed = car.speedKmh;
                        hitSidingId = car.sidingId;
                        hitDirX = pushDirX;
                        hitDirZ = pushDirZ;
                     }
                  }
               }
            }

            diagPushed += contacted;
            diagHits += contacted;
            if (hitSpeed > 0.0D) {
               impact(player, cfg, hitSpeed, hitSidingId, hitDirX, hitDirZ);
            }

            flight(player, cfg);
            hurt(player, cfg);
            if (!onRoof && cfg.trackbedCollision) {
               trackbed(player, cfg, world);
            }

            settleRoofFall(player, cfg);
            remember(cars);
            int segs = 0;

            for(CollisionSystem.CarHit car : cars) {
               segs += car.body.length + car.caps.length;
            }

            diagCars = Math.max(diagCars, cars.size());
            diagSegs = Math.max(diagSegs, segs);
         } catch (Throwable var21) {
            warnOnce(var21);
         }

      }
   }

   public static void tick() {
      ++ticks;
      Minecraft client = Minecraft.getInstance();
      ClientLevel world = client == null ? null : client.level;
      if (world != lastWorld) {
         reset();
         lastWorld = world;
      }

      Trackbed.endTick();
      if (LteDebug.collision() && ticks % 100L == 0L) {
         MtrHeadlightClient.LOGGER.info(Trackbed.followLine());
      }

      if (Trackbed.watch() && ticks % 100L == 0L) {
         MtrHeadlightClient.LOGGER.info(Trackbed.describe(Trackbed.snapped()));
         Trackbed.clearCounters();
      }

      stillLimbs(client == null ? null : client.player);
      CollisionImpact.suppressHurtTilt(client == null ? null : client.player);
      CollisionImpact.sweep(ticks);
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if (!cfg.collisionDiagnostics) {
         resetDiag();
      } else if (ticks % 100L == 0L) {
         int cars = diagCars;
         int segs = diagSegs;
         int hits = diagHits;
         double damage = diagDamage;
         int pushed = diagPushed;
         int velPushes = diagVelPushes;
         double hurt = diagHurt;
         long impulses = CollisionImpact.impulsesTotal() - diagImpulsesLast;
         diagImpulsesLast = CollisionImpact.impulsesTotal();
         double bed = diagTrackbed;
         double headExtra = diagHead;
         double tailExtra = diagTail;
         int riding = diagRiding;
         resetDiag();

         try {
            MtrHeadlightClient.LOGGER.info("[lte] collision: cars={} segs={} hits={} damage={} trackbed={} pushed={} impulses={} vel={} hurt={} head=+{} tail=+{} pad={} riding={} creativeDmg={}", new Object[]{cars, segs, hits, String.format(Locale.ROOT, "%.1f", damage), bed == bed ? String.format(Locale.ROOT, "%.2f", bed) : "-", pushed, impulses, velPushes, String.format(Locale.ROOT, "%.1f", hurt), endExtraText(headExtra), endExtraText(tailExtra), String.format(Locale.ROOT, "%.1f", cfg.trainCollisionEndPadClamped()), riding, cfg.damageInCreative});
         } catch (Throwable var22) {
         }

      }
   }

   private static void stillLimbs(LocalPlayer player) {
      if (roofKey != null && player != null && player.walkAnimation != null) {
         WalkAnimationState animator = player.walkAnimation;
         if (animator.speed(0.0F) != 0.0F || animator.speed() != 0.0F) {
            animator.update(0.0F, 1.0F);
            animator.update(0.0F, 1.0F);
         }
      }
   }

   public static void reset() {
      cacheEntity = null;
      cacheTick = -1L;
      cacheCars = List.of();
      roofKey = null;
      lastRoofY = Double.NaN;
      standKey = null;
      standIndex = -1;
      standTick = -1L;
      standRoofY = Double.NaN;
      standSpeedKmh = 0.0D;
      clearFall();
      rideTick = -1L;
      rideEntity = null;
      rideCached = false;
      TRACKS.clear();
      CollisionImpact.clear();
      Trackbed.reset();
      Trackbed.clearWarned();
      resetDiag();
   }

   private static List<CollisionSystem.CarHit> cars(Entity entity, double height) {
      double endPad = MtrHeadlightClient.config().trainCollisionEndPadClamped();
      if (cacheEntity == entity && cacheTick == ticks && cacheHeight == height && cacheEndPad == endPad) {
         return cacheCars;
      } else {
         cacheEntity = entity;
         cacheTick = ticks;
         cacheHeight = height;
         cacheEndPad = endPad;
         cacheCars = buildCars(entity, height, endPad);
         return cacheCars;
      }
   }

   private static List<CollisionSystem.CarHit> buildCars(Entity entity, double height, double endPad) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel world = client == null ? null : client.level;
      if (world == null) {
         return List.of();
      } else {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         Vec3 eye = entity.position();
         double rangeSq = 2304.0D;
         List<CollisionSystem.CarHit> out = new ArrayList();

         List<TrainClient> trains;
         try {
            trains = new ArrayList(ClientData.TRAINS);
         } catch (Throwable var65) {
            warnOnce(var65);
            return out;
         }

         Iterator t = trains.iterator();

         while(true) {
            TrainClient train;
            TrainPose pose;
            boolean reversed;
            while(true) {
               if (!t.hasNext()) {
                  for(CollisionSystem.CarHit car : out) {
                     car.syncMotion();
                  }

                  return out;
               }

               train = (TrainClient)t.next();
               if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0 && train.transportMode == TransportMode.TRAIN) {
                  try {
                     reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                     pose = TrainPose.ofShared(train, reversed);
                     break;
                  } catch (Throwable var66) {
                     warnOnce(var66);
                  }
               }
            }

            int carCount = pose.cars();
            if (carCount > 0) {
               String key = DepartureState.keyOf(train);
               double halfW = halfWidth(train);
               boolean doorsOpen = doorsOpen(train);
               double speed = speedKmh(train);
               HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
               double noseExtra = Math.max(endPad, eff.collisionHeadExtra);
               double rearExtra = Math.max(endPad, eff.collisionTailExtra);
               noteEndExtra(noseExtra, rearExtra);
               boolean curveNeeded = speed > (double)cfg.roofDropMinSpeedKmh;
               double total = 0.0D;
               double progress = 0.0D;
               int spacing = 0;

               try {
                  total = TrainPose.pathLength(train);
                  progress = train.getRailProgress();
                  spacing = Math.max(0, train.spacing);
               } catch (Throwable var64) {
                  curveNeeded = false;
               }

               for(int i = 0; i < carCount; ++i) {
                  Vec3 a = pose.carStart(i);
                  Vec3 b = pose.carEnd(i);
                  double cx = (a.x + b.x) * 0.5D;
                  double cz = (a.z + b.z) * 0.5D;
                  double cy = (a.y + b.y) * 0.5D;
                  double dx = cx - eye.x;
                  double dy = cy - eye.y;
                  double dz = cz - eye.z;
                  if (!(dx * dx + dy * dy + dz * dz > 2304.0D)) {
                     double ax = b.x - a.x;
                     double az = b.z - a.z;
                     double len = Math.sqrt(ax * ax + az * az);
                     if (len < 0.001D) {
                        Vec3 fwd = pose.carForward(i);
                        ax = fwd.x;
                        az = fwd.z;
                        len = Math.sqrt(ax * ax + az * az);
                        if (len < 1.0E-4D) {
                           continue;
                        }
                     }

                     double ux = ax / len;
                     double uz = az / len;
                     double midProgress = carProgress(progress, spacing, carCount, reversed, i);
                     double radius = curveNeeded && total > 0.0D ? radiusAt(train, midProgress) : Double.POSITIVE_INFINITY;
                     out.add(new CollisionSystem.CarHit(key, i, a, b, ux, uz, len, halfW, height, doorsOpen, speed, radius, i == 0 ? noseExtra : endPad, i == carCount - 1 ? rearExtra : endPad, train.sidingId));
                  }
               }
            }
         }
      }
   }

   private static double carProgress(double progress, int spacing, int cars, boolean reversed, int i) {
      double here = progress - (reversed ? (double)(cars - i) : (double)i) * (double)spacing;
      double next = progress - (reversed ? (double)(cars - i - 1) : (double)(i + 1)) * (double)spacing;
      return (here + next) * 0.5D;
   }

   private static double radiusAt(TrainClient train, double mid) {
      try {
         double total = TrainPose.pathLength(train);
         if (!(total > 0.0D)) {
            return Double.POSITIVE_INFINITY;
         } else {
            Vec3 p0 = TrainPose.samplePath(train, clamp(mid - 3.0D, total));
            Vec3 p1 = TrainPose.samplePath(train, clamp(mid, total));
            Vec3 p2 = TrainPose.samplePath(train, clamp(mid + 3.0D, total));
            double l1 = horizontal(p1.subtract(p0));
            double l2 = horizontal(p2.subtract(p1));
            if (!(l1 < 0.5D) && !(l2 < 0.5D)) {
               double v1x = p1.x - p0.x;
               double v1z = p1.z - p0.z;
               double v2x = p2.x - p1.x;
               double v2z = p2.z - p1.z;
               double cos = (v1x * v2x + v1z * v2z) / (l1 * l2);
               cos = Math.max(-1.0D, Math.min(1.0D, cos));
               double theta = Math.acos(cos);
               return !(theta > 0.02D) ? Double.POSITIVE_INFINITY : (l1 + l2) / theta;
            } else {
               return Double.POSITIVE_INFINITY;
            }
         }
      } catch (Throwable var24) {
         return Double.POSITIVE_INFINITY;
      }
   }

   private static double horizontal(Vec3 v) {
      return Math.sqrt(v.x * v.x + v.z * v.z);
   }

   private static double clamp(double v, double total) {
      if (!(v > 0.0D)) {
         return 0.0D;
      } else {
         return v > total ? total : v;
      }
   }

   private static double halfWidth(TrainClient train) {
      double half;
      try {
         half = (double)train.width * 0.5D;
      } catch (Throwable var4) {
         return 1.25D;
      }

      if (!(half > 0.0D)) {
         return 1.25D;
      } else {
         half = Math.max(1.3D, Math.min(4.0D, half));
         return Math.max(0.6D, half - 0.05D);
      }
   }

   private static boolean doorsOpen(TrainClient train) {
      try {
         if (train.isDoorOpening()) {
            return true;
         }
      } catch (Throwable var3) {
      }

      try {
         return train.getDoorValue() > 0.02F;
      } catch (Throwable var2) {
         return false;
      }
   }

   private static double speedKmh(TrainClient train) {
      try {
         return (double)Math.abs(train.getSpeed()) * 72.0D;
      } catch (Throwable var2) {
         return 0.0D;
      }
   }

   private static boolean pushCar(Player player, CollisionSystem.CarHit car, ClientLevel world) {
      boolean hit = false;
      if (!car.doorsOpen) {
         hit = pushBody(player, car, world);
      }

      if (pushCaps(player, car, world)) {
         hit = true;
      }

      return hit;
   }

   private static boolean pushBody(Player player, CollisionSystem.CarHit car, ClientLevel world) {
      CollisionSystem.Seg[] body = car.body;
      if (body.length == 0) {
         return false;
      } else {
         double s = car.alongDistance(player.getX(), player.getZ());
         double segLen = car.len / (double)body.length;
         int centre = segLen < 1.0E-4D ? 0 : (int)Math.floor(s / segLen);
         if (centre < 0) {
            centre = 0;
         } else if (centre >= body.length) {
            centre = body.length - 1;
         }

         int from = Math.max(0, centre - 1);
         int to = Math.min(body.length - 1, centre + 1);

         for(int i = from; i <= to; ++i) {
            if (pushSegment(player, car, body[i], world)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean pushCaps(Player player, CollisionSystem.CarHit car, ClientLevel world) {
      for(CollisionSystem.Seg cap : car.caps) {
         if (pushSegment(player, car, cap, world)) {
            return true;
         }
      }

      return false;
   }

   private static boolean pushSegment(Player player, CollisionSystem.CarHit car, CollisionSystem.Seg seg, ClientLevel world) {
      AABB pb = player.getBoundingBox();
      if (!(pb.maxX <= seg.minX) && !(pb.minX >= seg.maxX) && !(pb.maxZ <= seg.minZ) && !(pb.minZ >= seg.maxZ)) {
         if (!(pb.maxY <= seg.yLo + 0.02D) && !(pb.minY >= seg.yHi - 0.02D)) {
            double px = (pb.minX + pb.maxX) * 0.5D;
            double pz = (pb.minZ + pb.maxZ) * 0.5D;
            double dx = px - seg.cx;
            double dz = pz - seg.cz;
            double along = dx * seg.ux + dz * seg.uz;
            double lat = dx * seg.nx + dz * seg.nz;
            double hx = (pb.maxX - pb.minX) * 0.5D;
            double hz = (pb.maxZ - pb.minZ) * 0.5D;
            double projU = hx * Math.abs(seg.ux) + hz * Math.abs(seg.uz);
            double projN = hx * Math.abs(seg.nx) + hz * Math.abs(seg.nz);
            double penN = seg.halfW + projN - Math.abs(lat);
            double penU = seg.halfLen + projU - Math.abs(along);
            if (!(penN <= 0.0D) && !(penU <= 0.0D)) {
               double sideN = lat >= 0.0D ? 1.0D : -1.0D;
               boolean frontal = isFrontal(car, seg, px, pz, penU, penN);
               if (frontal) {
                  double fx = car.moving ? car.moveX : seg.ux * (double)seg.side;
                  double fz = car.moving ? car.moveZ : seg.uz * (double)seg.side;
                  if (applyPush(player, car, fx, fz, penU, true)) {
                     return true;
                  }
               }

               return seg.cap && penU < penN && applyPush(player, car, seg.ux * (double)seg.side, seg.uz * (double)seg.side, penU, true) ? true : applyPush(player, car, seg.nx * sideN, seg.nz * sideN, penN, frontal);
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static boolean isFrontal(CollisionSystem.CarHit car, CollisionSystem.Seg seg, double px, double pz, double penU, double penN) {
      if (car.moving && car.moveSpeed > 0.02D) {
         double ahead = (px - car.cx) * car.moveX + (pz - car.cz) * car.moveZ;
         if (ahead <= 0.0D) {
            return false;
         } else {
            return penU < penN * 1.5D;
         }
      } else {
         return seg.cap && penU < penN;
      }
   }

   private static boolean applyPush(Player player, CollisionSystem.CarHit car, double dirX, double dirZ, double pen, boolean frontal) {
      if (pen > 0.0D && !(pen > 8.0D)) {
         if (!player.isDeadOrDying() && !(player.getHealth() <= 0.0F)) {
            pushDirX = dirX;
            pushDirZ = dirZ;
            velocityPush(player, car, dirX, dirZ, frontal);
            double amount = pen + 0.001D;
            if (!fits(player, world(player), dirX, dirZ, amount)) {
               amount = maxFree(player, world(player), dirX, dirZ, pen);
            }

            if (amount > 0.0D) {
               player.setPos(player.getX() + dirX * amount, player.getY(), player.getZ() + dirZ * amount);
               Vec3 v = player.getDeltaMovement();
               double inward = v.x * dirX + v.z * dirZ;
               if (inward < 0.0D) {
                  player.setDeltaMovement(v.x - inward * dirX, v.y, v.z - inward * dirZ);
                  player.hurtMarked = true;
               }
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static ClientLevel world(Player player) {
      Level var2 = player.level();
      ClientLevel var10000;
      if (var2 instanceof ClientLevel w) {
         var10000 = w;
      } else {
         var10000 = null;
      }

      return var10000;
   }

   private static boolean fits(Player player, ClientLevel world, double dirX, double dirZ, double amount) {
      if (world == null) {
         return true;
      } else {
         AABB moved = player.getBoundingBox().move(dirX * amount, 0.0D, dirZ * amount);
         return world.noCollision(player, moved);
      }
   }

   private static double maxFree(Player player, ClientLevel world, double dirX, double dirZ, double pen) {
      if (!fits(player, world, dirX, dirZ, 0.0D)) {
         return 0.0D;
      } else {
         double lo = 0.0D;
         double hi = pen;

         for(int i = 0; i < 4; ++i) {
            double mid = (lo + hi) * 0.5D;
            if (fits(player, world, dirX, dirZ, mid)) {
               lo = mid;
            } else {
               hi = mid;
            }
         }

         return lo > 0.05D ? lo : 0.0D;
      }
   }

   private static void velocityPush(Player player, CollisionSystem.CarHit car, double dirX, double dirZ, boolean frontal) {
      if (car.moving && car.moveSpeed > 0.02D) {
         if (!player.isDeadOrDying() && !(player.getHealth() <= 0.0F)) {
            if (!CollisionImpact.impulseActive(player)) {
               double carry = Math.min(car.moveSpeed, 1.35D);
               double out = Math.min(Math.max(car.moveSpeed * 0.55D, 0.12D), 1.35D);
               double vx;
               double vz;
               if (frontal) {
                  vx = car.moveX * carry;
                  vz = car.moveZ * carry;
               } else {
                  vx = car.moveX * carry * 0.45D + dirX * out;
                  vz = car.moveZ * carry * 0.45D + dirZ * out;
               }

               Vec3 cur = player.getDeltaMovement();
               if (!(Math.abs(cur.x - vx) <= 0.03D) || !(Math.abs(cur.z - vz) <= 0.03D)) {
                  player.setDeltaMovement(vx, cur.y, vz);
                  player.hurtMarked = true;
                  ++diagVelPushes;
               }
            }
         }
      }
   }

   private static boolean serverOwnsIt() {
      if (ServerAuthority.remoteAuthoritative()) {
         return true;
      } else {
         Minecraft client = Minecraft.getInstance();
         return client != null && client.getSingleplayerServer() != null;
      }
   }

   private static void impact(Player player, HeadlightConfig cfg, double speedKmh, long sidingId, double dirX, double dirZ) {
      if (!serverOwnsIt()) {
         HeadlightConfig.Effective eff = cfg.effectiveFor(sidingId);
         float hp = CollisionImpact.tick(player, cfg, eff, speedKmh, dirX, dirZ, ticks);
         if (hp > 0.0F) {
            boolean high = speedKmh >= (double)cfg.trainCollisionHighDamageMaxKmhClamped();
            hurtOnServer(player, hp, high, cfg);
            diagDamage += (double)hp / 2.0D;
         }

      }
   }

   private static void hurt(Player player, HeadlightConfig cfg) {
      if (!serverOwnsIt()) {
         float hp = CollisionImpact.hurtDamage(player, cfg, ticks);
         if (hp > 0.0F) {
            hurtOnServer(player, hp, CollisionImpact.wasFast(player.getUUID()), cfg);
            diagHurt += (double)hp;
            diagDamage += (double)hp / 2.0D;
         }

      }
   }

   private static void flight(Player player, HeadlightConfig cfg) {
      if (!serverOwnsIt()) {
         if (CollisionImpact.launched(player.getUUID())) {
            float hp = CollisionImpact.flightDamage(player, cfg, ticks);
            if (hp > 0.0F) {
               hurtOnServer(player, hp, CollisionImpact.wasFast(player.getUUID()), cfg);
               diagDamage += (double)hp / 2.0D;
            }

         }
      }
   }

   private static void hurtOnServer(Player player, float hp, boolean high, HeadlightConfig cfg) {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      ServerPlayer authoritative = null;
      if (server != null) {
         try {
            authoritative = server.getPlayerList().getPlayer(player.getUUID());
         } catch (Throwable var7) {
            authoritative = null;
         }
      }

      if (authoritative != null) {
         ServerPlayer target = authoritative;
         server.execute(() -> CollisionImpact.applyDamage(target, hp, high, cfg));
      } else {
         CollisionImpact.applyDamage(player, hp, high, cfg);
      }
   }

   private static boolean standOnRoof(Player player, CollisionSystem.CarHit car, double height, ClientLevel world) {
      if (car.slidesOff(MtrHeadlightClient.config())) {
         return false;
      } else {
         double x = player.getX();
         double z = player.getZ();
         double roof = car.roofAt(x, z, height);
         if (Double.isNaN(roof)) {
            return false;
         } else {
            double feet = player.getY();
            if (feet < roof - 0.45D) {
               return false;
            } else if (feet > roof + 0.06D) {
               return false;
            } else if (player.getDeltaMovement().y > 0.08D) {
               return false;
            } else {
               player.setPos(x, roof, z);
               player.setOnGround(true);
               player.fallDistance = 0.0F;
               lastRoofY = roof;
               Vec3 carry = carryFor(car);
               if (carry.lengthSqr() > 1.0E-9D) {
                  AABB moved = player.getBoundingBox().move(carry.x, 0.0D, carry.z);
                  if (world.noCollision(player, moved)) {
                     player.setPos(player.getX() + carry.x, roof, player.getZ() + carry.z);
                  }
               }

               return true;
            }
         }
      }
   }

   private static void roofState(CollisionSystem.CarHit car) {
      if (car != null) {
         standKey = car.key;
         standIndex = car.index;
         standTick = ticks;
         standSpeedKmh = car.speedKmh;
         standRoofY = lastRoofY;
         clearFall();
      } else {
         if (standTick == ticks - 1L) {
            fallKey = standKey;
            fallIndex = standIndex;
            fallRoofY = standRoofY;
            fallSpeedKmh = standSpeedKmh;
            fallUntil = ticks + 60L;
            standTick = -1L;
         }

      }
   }

   private static CollisionSystem.CarHit roofStandby(Player player, List<CollisionSystem.CarHit> cars, double height) {
      if (player.getDeltaMovement().y > 0.08D) {
         return null;
      } else {
         double feet = player.getY();

         for(CollisionSystem.CarHit car : cars) {
            double roof = car.roofAt(player.getX(), player.getZ(), height);
            if (!Double.isNaN(roof) && !(feet < roof - 0.45D) && !(feet > roof + 0.06D)) {
               lastRoofY = roof;
               return car;
            }
         }

         return null;
      }
   }

   private static void clearFall() {
      fallKey = null;
      fallIndex = -1;
      fallRoofY = Double.NaN;
      fallSpeedKmh = 0.0D;
      fallUntil = -1L;
   }

   private static boolean isFallCar(CollisionSystem.CarHit car) {
      return fallKey != null && car.index == fallIndex && fallKey.equals(car.key);
   }

   private static void airborneCarry(Player player, List<CollisionSystem.CarHit> cars, double height, ClientLevel world) {
      if (fallKey != null) {
         if (ticks > fallUntil) {
            clearFall();
         } else {
            double feet = player.getY();

            for(CollisionSystem.CarHit car : cars) {
               if (isFallCar(car)) {
                  if (car.slidesOff(MtrHeadlightClient.config())) {
                     return;
                  }

                  double roof = car.roofAt(player.getX(), player.getZ(), height);
                  if (Double.isNaN(roof)) {
                     return;
                  }

                  if (!(feet < roof - 0.06D) && !(feet > roof + 3.0D)) {
                     Vec3 carry = carryFor(car);
                     if (carry.lengthSqr() < 1.0E-9D) {
                        return;
                     }

                     AABB moved = player.getBoundingBox().move(carry.x, 0.0D, carry.z);
                     if (world.noCollision(player, moved)) {
                        player.setPos(player.getX() + carry.x, feet, player.getZ() + carry.z);
                     }

                     return;
                  }

                  return;
               }
            }

         }
      }
   }

   private static boolean roofEscape(Player player, CollisionSystem.CarHit car) {
      if (fallKey == null) {
         return false;
      } else if (ticks > fallUntil) {
         clearFall();
         return false;
      } else if (isFallCar(car) && !Double.isNaN(fallRoofY)) {
         if (player.getY() >= fallRoofY - 0.06D) {
            return true;
         } else {
            return !player.onGround();
         }
      } else {
         return false;
      }
   }

   private static void settleRoofFall(Player player, HeadlightConfig cfg) {
      if (fallKey != null) {
         if (ticks > fallUntil) {
            clearFall();
         } else if (!Double.isNaN(fallRoofY) && player.onGround()) {
            double drop = fallRoofY - player.getY();
            double speed = fallSpeedKmh;
            clearFall();
            if (drop >= 1.2D && cfg.trainCollisionDamage) {
               float hp = CollisionImpact.roofFallHp(player, cfg, speed);
               if (hp > 0.0F) {
                  try {
                     MtrHeadlightClient.LOGGER.info("[lte] roof-fall: v={}km/h drop={} hp={} by={}", new Object[]{String.format(Locale.ROOT, "%.1f", speed), String.format(Locale.ROOT, "%.2f", drop), String.format(Locale.ROOT, "%.2f", hp), serverOwnsIt() ? "server" : "client"});
                  } catch (Throwable var8) {
                  }

                  hurtForFall(player, hp, cfg);
               }
            }
         }
      }
   }

   private static void hurtForFall(Player player, float hp, HeadlightConfig cfg) {
      if (!serverOwnsIt()) {
         MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
         ServerPlayer authoritative = null;
         if (server != null) {
            try {
               authoritative = server.getPlayerList().getPlayer(player.getUUID());
            } catch (Throwable var6) {
               authoritative = null;
            }
         }

         if (authoritative != null) {
            ServerPlayer target = authoritative;
            server.execute(() -> CollisionImpact.applyRoofFall(target, hp, cfg));
         } else {
            CollisionImpact.applyRoofFall(player, hp, cfg);
         }
      }
   }

   private static Vec3 carryFor(CollisionSystem.CarHit car) {
      String key = car.key + "#" + car.index;
      CollisionSystem.CarTrack track = (CollisionSystem.CarTrack)TRACKS.get(key);
      if (track != null && track.tick == ticks - 1L) {
         double dx = car.cx - track.x;
         double dz = car.cz - track.z;
         return dx * dx + dz * dz > 16.0D ? Vec3.ZERO : new Vec3(dx, 0.0D, dz);
      } else {
         return Vec3.ZERO;
      }
   }

   private static void remember(List<CollisionSystem.CarHit> cars) {
      if (cars.isEmpty()) {
         if (TRACKS.size() > 64) {
            TRACKS.clear();
         }

      } else {
         for(CollisionSystem.CarHit car : cars) {
            TRACKS.put(car.key + "#" + car.index, new CollisionSystem.CarTrack(car.cx, car.cz, ticks));
         }

         if (TRACKS.size() > 512) {
            TRACKS.entrySet().removeIf((e) -> ((CollisionSystem.CarTrack)e.getValue()).tick < ticks - 2L);
         }

      }
   }

   private static boolean trackbed(Player player, HeadlightConfig cfg, ClientLevel world) {
      double feet = player.getY();
      double x = player.getX();
      double z = player.getZ();
      boolean onGround = player.onGround();
      double moved = movedSinceStart(feet, x, z);
      double vy = player.getDeltaMovement().y;
      Trackbed.Sample sample = Trackbed.follow(world, ticks, x, feet, z);
      double railY = sample == null ? Double.NaN : sample.y;
      double surface = sample == null ? Double.NaN : railY + cfg.trackbedThicknessClamped();
      double dy = sample == null ? Double.NaN : feet - surface;
      double startDy = Double.isNaN(startY) ? Double.NaN : startY - surface;
      RailBed.Verdict verdict;
      if (sample == null) {
         verdict = RailBed.Verdict.NO_HIT;
      } else if (sample.distance > 1.3D) {
         verdict = RailBed.Verdict.LATERAL;
      } else {
         verdict = RailBed.verdict(dy, vy, startDy, cfg.trackbedStepUpClamped());
      }

      if (!verdict.snaps() && sample != null && Trackbed.continued() && sample.distance <= 1.3D && vy <= 0.02D && dy >= -1.5D && dy <= 0.6D) {
         verdict = RailBed.Verdict.SNAP;
      }

      if (!verdict.snaps() && sample != null && Trackbed.rescue(sample.distance, startDy, dy, vy)) {
         verdict = RailBed.Verdict.RESCUE;
      }

      if (verdict.snaps()) {
         if (realBlockWins(world, x, feet, z, surface)) {
            verdict = RailBed.Verdict.BLOCK;
         } else if (!world.noCollision(player, player.getBoundingBox().move(0.0D, surface - feet, 0.0D))) {
            verdict = RailBed.Verdict.SPACE;
         }
      }

      boolean snapped = verdict.snaps();
      if (snapped) {
         player.setPos(x, surface, z);
         player.setOnGround(true);
         player.fallDistance = 0.0F;
         Vec3 v = player.getDeltaMovement();
         player.setDeltaMovement(v.x, 0.0D, v.z);
         diagTrackbed = surface;
      }

      Trackbed.noteStep(sample, x, feet, z, startDy, dy, vy, onGround, moved, verdict);
      return snapped;
   }

   private static double movedSinceStart(double feet, double x, double z) {
      if (!Double.isNaN(startX) && !Double.isNaN(startY) && !Double.isNaN(startZ)) {
         double dx = x - startX;
         double dy = feet - startY;
         double dz = z - startZ;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      } else {
         return 0.0D;
      }
   }

   private static boolean realBlockWins(ClientLevel world, double x, double feet, double z, double surface) {
      BlockPos below = BlockPos.containing(x, feet - 0.02D, z);

      try {
         BlockState state = world.getBlockState(below);
         VoxelShape shape = state.getCollisionShape(world, below);
         return !shape.isEmpty() && (double)below.getY() + shape.max(Axis.Y) >= surface - 0.02D;
      } catch (Throwable var12) {
         return true;
      }
   }

   private static boolean isLocal(Entity entity) {
      // 专用服务端上 net.minecraft.client.* 被 RuntimeDistCleaner 拦住（"invalid dist DEDICATED_SERVER"），
      // 一旦执行到 Minecraft/LocalPlayer 的引用就整服崩。判据放在最前：服务端直接 false。
      if (!CLIENT || entity == null || !entity.level().isClientSide()) {
         return false;
      }

      Minecraft client = Minecraft.getInstance();
      return entity instanceof LocalPlayer && entity == client.player;
   }

   private static void resetDiag() {
      diagCars = 0;
      diagSegs = 0;
      diagHits = 0;
      diagDamage = 0.0D;
      diagPushed = 0;
      diagVelPushes = 0;
      diagHurt = 0.0D;
      diagRiding = 0;
      diagTrackbed = Double.NaN;
      diagHead = Double.NaN;
      diagTail = Double.NaN;
   }

   private static void noteEndExtra(double head, double tail) {
      diagHead = Double.isNaN(diagHead) ? head : Math.max(diagHead, head);
      diagTail = Double.isNaN(diagTail) ? tail : Math.max(diagTail, tail);
   }

   private static String endExtraText(double value) {
      return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.1f", value);
   }

   private static void warnOnce(Throwable t) {
      if (WARNED.compareAndSet(false, true)) {
         MtrHeadlightClient.LOGGER.warn("[lte] collision: MTR data unavailable, train collision disabled until it recovers", t);
      }

   }

   private static final class CarHit {
      final String key;
      final int index;
      final Vec3 a;
      final Vec3 b;
      final double ux;
      final double uz;
      final double len;
      final double halfW;
      final double cx;
      final double cz;
      final double roofA;
      final double roofB;
      final double floorA;
      final double floorB;
      final boolean doorsOpen;
      final double speedKmh;
      final long sidingId;
      final double radiusM;
      final CollisionSystem.Seg[] body;
      final CollisionSystem.Seg[] caps;
      double deltaX;
      double deltaZ;
      boolean moving;
      double moveX;
      double moveZ;
      double moveSpeed;

      CarHit(String key, int index, Vec3 a, Vec3 b, double ux, double uz, double len, double halfW, double height, boolean doorsOpen, double speedKmh, double radiusM, double capA, double capB, long sidingId) {
         this.key = key;
         this.index = index;
         this.a = a;
         this.b = b;
         this.ux = ux;
         this.uz = uz;
         this.len = len;
         this.halfW = halfW;
         this.roofA = a.y + height;
         this.roofB = b.y + height;
         this.floorA = a.y;
         this.floorB = b.y;
         this.doorsOpen = doorsOpen;
         this.speedKmh = speedKmh;
         this.radiusM = radiusM;
         this.sidingId = sidingId;
         this.cx = (a.x + b.x) * 0.5D;
         this.cz = (a.z + b.z) * 0.5D;
         this.body = this.buildBody(height);
         this.caps = this.buildCaps(height, capA, capB);
      }

      private CollisionSystem.Seg[] buildBody(double height) {
         int count = (int)Math.ceil(this.len / 2.0D);
         int upper = Math.max(1, (int)Math.floor(this.len / 1.5D));
         count = Math.max(1, Math.min(Math.min(count, upper), 32));
         double segLen = this.len / (double)count;
         CollisionSystem.Seg[] out = new CollisionSystem.Seg[count];
         double half = 0.15D;

         for(int i = 0; i < count; ++i) {
            double s0 = (double)i * segLen - 0.15D;
            double s1 = (double)(i + 1) * segLen + 0.15D;
            if (i == 0) {
               s0 = 0.0D;
            }

            if (i == count - 1) {
               s1 = this.len;
            }

            out[i] = this.seg(s0, s1, height, false, 0);
         }

         return out;
      }

      private CollisionSystem.Seg[] buildCaps(double height, double capA, double capB) {
         boolean head = capA > 0.0D;
         boolean tail = capB > 0.0D;
         if (head && tail) {
            return new CollisionSystem.Seg[]{this.seg(-capA, 0.0D, height, true, -1), this.seg(this.len, this.len + capB, height, true, 1)};
         } else if (head) {
            return new CollisionSystem.Seg[]{this.seg(-capA, 0.0D, height, true, -1)};
         } else {
            return tail ? new CollisionSystem.Seg[]{this.seg(this.len, this.len + capB, height, true, 1)} : new CollisionSystem.Seg[0];
         }
      }

      private CollisionSystem.Seg seg(double s0, double s1, double height, boolean cap, int side) {
         double mid = (s0 + s1) * 0.5D;
         double halfLen = Math.max(1.0E-4D, (s1 - s0) * 0.5D);
         double t0 = this.len < 1.0E-4D ? 0.0D : s0 / this.len;
         double t1 = this.len < 1.0E-4D ? 1.0D : s1 / this.len;
         double y0 = this.floorA + (this.floorB - this.floorA) * t0;
         double y1 = this.floorA + (this.floorB - this.floorA) * t1;
         return new CollisionSystem.Seg(this.a.x + this.ux * mid, this.a.z + this.uz * mid, this.ux, this.uz, halfLen, this.halfW, Math.min(y0, y1), Math.max(y0, y1) + height, cap, side);
      }

      double along(double x, double z) {
         return this.len < 1.0E-4D ? 0.5D : ((x - this.a.x) * this.ux + (z - this.a.z) * this.uz) / this.len;
      }

      double alongDistance(double x, double z) {
         return (x - this.a.x) * this.ux + (z - this.a.z) * this.uz;
      }

      void syncMotion() {
         CollisionSystem.CarTrack track = (CollisionSystem.CarTrack)CollisionSystem.TRACKS.get(this.key + "#" + this.index);
         if (track != null && track.tick == CollisionSystem.ticks - 1L) {
            double dx = this.cx - track.x;
            double dz = this.cz - track.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0E-4D && len <= 4.0D) {
               this.moving = true;
               this.moveX = dx / len;
               this.moveZ = dz / len;
               this.moveSpeed = len;
               this.deltaX = dx;
               this.deltaZ = dz;
               return;
            }
         }

         double speed = this.speedKmh / 72.0D;
         if (speed > 0.02D) {
            this.moving = true;
            this.moveX = -this.ux;
            this.moveZ = -this.uz;
            this.moveSpeed = speed;
         } else {
            this.moving = false;
            this.moveSpeed = 0.0D;
         }
      }

      double lateral(double x, double z) {
         return (x - this.a.x) * -this.uz + (z - this.a.z) * this.ux;
      }

      double roofAt(double x, double z, double height) {
         double t = this.along(x, z);
         if (!(t < -0.02D) && !(t > 1.02D)) {
            if (Math.abs(this.lateral(x, z)) > this.halfW + 0.05D + 0.08D) {
               return Double.NaN;
            } else {
               double tc = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
               return this.floorA + (this.floorB - this.floorA) * tc + height;
            }
         } else {
            return Double.NaN;
         }
      }

      double floorAt(double x, double z) {
         double t = this.along(x, z);
         if (!(t < -0.02D) && !(t > 1.02D)) {
            if (Math.abs(this.lateral(x, z)) > this.halfW + 0.05D + 0.08D) {
               return Double.NaN;
            } else {
               double tc = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
               return this.floorA + (this.floorB - this.floorA) * tc;
            }
         } else {
            return Double.NaN;
         }
      }

      boolean slidesOff(HeadlightConfig cfg) {
         return this.speedKmh > (double)cfg.roofDropMinSpeedKmh && this.radiusM < cfg.roofDropMaxRadiusMClamped();
      }
   }

   private static final class CarTrack {
      final double x;
      final double z;
      final long tick;

      CarTrack(double x, double z, long tick) {
         this.x = x;
         this.z = z;
         this.tick = tick;
      }
   }

   private static final class Seg {
      final double cx;
      final double cz;
      final double ux;
      final double uz;
      final double nx;
      final double nz;
      final double halfLen;
      final double halfW;
      final double yLo;
      final double yHi;
      final double minX;
      final double maxX;
      final double minZ;
      final double maxZ;
      final boolean cap;
      final int side;

      Seg(double cx, double cz, double ux, double uz, double halfLen, double halfW, double yLo, double yHi, boolean cap, int side) {
         this.cx = cx;
         this.cz = cz;
         this.ux = ux;
         this.uz = uz;
         this.nx = -uz;
         this.nz = ux;
         this.halfLen = halfLen;
         this.halfW = halfW;
         this.yLo = yLo;
         this.yHi = yHi;
         double ex = Math.abs(ux) * halfLen + Math.abs(this.nx) * halfW;
         double ez = Math.abs(uz) * halfLen + Math.abs(this.nz) * halfW;
         this.minX = cx - ex;
         this.maxX = cx + ex;
         this.minZ = cz - ez;
         this.maxZ = cz + ez;
         this.cap = cap;
         this.side = side;
      }
   }
}

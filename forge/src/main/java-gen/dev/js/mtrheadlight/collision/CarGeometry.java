package dev.js.mtrheadlight.collision;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CarGeometry {
   public static final double SIDE_INSET = 0.05D;
   public static final double PUSH_EPS = 0.001D;
   public static final double SEG_OVERLAP = 0.3D;
   public static final double SEG_TARGET_LEN = 2.0D;
   public static final double SEG_MIN_LEN = 1.5D;
   public static final int SEG_MAX = 32;
   public static final double ROOF_TOL = 0.45D;
   public static final double ROOF_ABOVE = 0.06D;
   public static final double DEFAULT_HALF_WIDTH = 1.3D;
   public static final double MIN_HALF_WIDTH = 1.3D;
   public static final double MIN_HALF_WIDTH_ABS = 0.6D;
   public static final double KMH_PER_BLOCK_PER_TICK = 72.0D;

   private CarGeometry() {
   }

   public static CarGeometry.Push pushSegment(Level world, Entity entity, CarGeometry.Seg seg) {
      CarGeometry.Push push = resolve(world, entity, seg);
      if (push.moved) {
         applyPush(world, entity, push.dirX, push.dirZ, push.pen);
      }

      return push;
   }

   public static CarGeometry.Push contact(Entity entity, CarGeometry.Seg seg) {
      return resolve((Level)null, entity, seg, true);
   }

   public static CarGeometry.Push resolve(Level world, Entity entity, CarGeometry.Seg seg) {
      return resolve(world, entity, seg, false);
   }

   private static CarGeometry.Push resolve(Level world, Entity entity, CarGeometry.Seg seg, boolean ignoreSpace) {
      AABB pb = entity.getBoundingBox();
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
               if (seg.cap && penU < penN) {
                  CarGeometry.Push axial = probe(world, entity, seg.ux * (double)seg.side, seg.uz * (double)seg.side, penU, ignoreSpace);
                  if (axial.moved) {
                     return axial;
                  }
               }

               return probe(world, entity, seg.nx * sideN, seg.nz * sideN, penN, ignoreSpace);
            } else {
               return CarGeometry.Push.NONE;
            }
         } else {
            return CarGeometry.Push.NONE;
         }
      } else {
         return CarGeometry.Push.NONE;
      }
   }

   private static CarGeometry.Push probe(Level world, Entity entity, double dirX, double dirZ, double pen, boolean ignoreSpace) {
      if (pen > 0.0D && !(pen > 8.0D)) {
         if (ignoreSpace) {
            return new CarGeometry.Push(dirX, dirZ, pen, true);
         } else {
            double amount = pen + 0.001D;
            AABB moved = entity.getBoundingBox().move(dirX * amount, 0.0D, dirZ * amount);
            return !world.noCollision(entity, moved) ? CarGeometry.Push.NONE : new CarGeometry.Push(dirX, dirZ, pen, true);
         }
      } else {
         return CarGeometry.Push.NONE;
      }
   }

   public static CarGeometry.Push applyPush(Level world, Entity entity, double dirX, double dirZ, double pen) {
      if (pen > 0.0D && !(pen > 8.0D)) {
         double amount = pen + 0.001D;
         if (!fits(world, entity, dirX, dirZ, amount)) {
            amount = maxFree(world, entity, dirX, dirZ, pen);
         }

         if (amount > 0.0D) {
            double nx = entity.getX() + dirX * amount;
            double nz = entity.getZ() + dirZ * amount;
            if (entity instanceof ServerPlayer) {
               ServerPlayer serverPlayer = (ServerPlayer)entity;
               serverPlayer.teleportTo(nx, entity.getY(), nz);
            } else {
               entity.setPos(nx, entity.getY(), nz);
            }
         }

         Vec3 v = entity.getDeltaMovement();
         double inward = v.x * dirX + v.z * dirZ;
         if (inward < 0.0D) {
            entity.setDeltaMovement(v.x - inward * dirX, v.y, v.z - inward * dirZ);
            entity.hurtMarked = true;
         }

         return new CarGeometry.Push(dirX, dirZ, pen, amount > 0.0D);
      } else {
         return CarGeometry.Push.NONE;
      }
   }

   private static boolean fits(Level world, Entity entity, double dirX, double dirZ, double amount) {
      if (world == null) {
         return true;
      } else {
         AABB moved = entity.getBoundingBox().move(dirX * amount, 0.0D, dirZ * amount);
         return world.noCollision(entity, moved);
      }
   }

   private static double maxFree(Level world, Entity entity, double dirX, double dirZ, double pen) {
      if (!fits(world, entity, dirX, dirZ, 0.0D)) {
         return 0.0D;
      } else {
         double lo = 0.0D;
         double hi = pen;

         for(int i = 0; i < 4; ++i) {
            double mid = (lo + hi) * 0.5D;
            if (fits(world, entity, dirX, dirZ, mid)) {
               lo = mid;
            } else {
               hi = mid;
            }
         }

         return lo > 0.05D ? lo : 0.0D;
      }
   }

   public static double halfWidth(int width) {
      double half = (double)width * 0.5D;
      if (!(half > 0.0D)) {
         return 1.25D;
      } else {
         half = Math.max(1.3D, Math.min(4.0D, half));
         return Math.max(0.6D, half - 0.05D);
      }
   }

   public static double speedKmh(float speed) {
      return (double)Math.abs(speed) * 72.0D;
   }

   public static class Car {
      public final String key;
      public final int index;
      public final Vec3 a;
      public final Vec3 b;
      public final double ux;
      public final double uz;
      public final double len;
      public final double halfW;
      public final double cx;
      public final double cz;
      public final double roofA;
      public final double roofB;
      public final double floorA;
      public final double floorB;
      public final boolean doorsOpen;
      public final double speedKmh;
      public final long sidingId;
      public final double radiusM;
      public final CarGeometry.Seg[] body;
      public final CarGeometry.Seg[] caps;
      public double deltaX;
      public double deltaZ;

      public Car(String key, int index, Vec3 a, Vec3 b, double ux, double uz, double len, double halfW, double height, boolean doorsOpen, double speedKmh, double radiusM, double capA, double capB, long sidingId) {
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

      private CarGeometry.Seg[] buildBody(double height) {
         int count = (int)Math.ceil(this.len / 2.0D);
         int upper = Math.max(1, (int)Math.floor(this.len / 1.5D));
         count = Math.max(1, Math.min(Math.min(count, upper), 32));
         double segLen = this.len / (double)count;
         CarGeometry.Seg[] out = new CarGeometry.Seg[count];
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

      private CarGeometry.Seg[] buildCaps(double height, double capA, double capB) {
         boolean head = capA > 0.0D;
         boolean tail = capB > 0.0D;
         if (head && tail) {
            return new CarGeometry.Seg[]{this.seg(-capA, 0.0D, height, true, -1), this.seg(this.len, this.len + capB, height, true, 1)};
         } else if (head) {
            return new CarGeometry.Seg[]{this.seg(-capA, 0.0D, height, true, -1)};
         } else {
            return tail ? new CarGeometry.Seg[]{this.seg(this.len, this.len + capB, height, true, 1)} : new CarGeometry.Seg[0];
         }
      }

      private CarGeometry.Seg seg(double s0, double s1, double height, boolean cap, int side) {
         double mid = (s0 + s1) * 0.5D;
         double halfLen = Math.max(1.0E-4D, (s1 - s0) * 0.5D);
         double t0 = this.len < 1.0E-4D ? 0.0D : s0 / this.len;
         double t1 = this.len < 1.0E-4D ? 1.0D : s1 / this.len;
         double y0 = this.floorA + (this.floorB - this.floorA) * t0;
         double y1 = this.floorA + (this.floorB - this.floorA) * t1;
         return new CarGeometry.Seg(this.a.x + this.ux * mid, this.a.z + this.uz * mid, this.ux, this.uz, halfLen, this.halfW, Math.min(y0, y1), Math.max(y0, y1) + height, cap, side);
      }

      public double along(double x, double z) {
         return this.len < 1.0E-4D ? 0.5D : ((x - this.a.x) * this.ux + (z - this.a.z) * this.uz) / this.len;
      }

      public double alongDistance(double x, double z) {
         return (x - this.a.x) * this.ux + (z - this.a.z) * this.uz;
      }

      public double lateral(double x, double z) {
         return (x - this.a.x) * -this.uz + (z - this.a.z) * this.ux;
      }

      public double roofAt(double x, double z, double height) {
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

      public double floorAt(double x, double z) {
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

      public boolean slidesOff(double minSpeedKmh, double maxRadiusM) {
         return this.speedKmh > minSpeedKmh && this.radiusM < maxRadiusM;
      }
   }

   public static final class Push {
      public static final CarGeometry.Push NONE = new CarGeometry.Push(0.0D, 0.0D, 0.0D, false);
      public final double dirX;
      public final double dirZ;
      public final double pen;
      public final boolean moved;

      public Push(double dirX, double dirZ, double pen, boolean moved) {
         this.dirX = dirX;
         this.dirZ = dirZ;
         this.pen = pen;
         this.moved = moved;
      }
   }

   public static final class Seg {
      public final double cx;
      public final double cz;
      public final double ux;
      public final double uz;
      public final double nx;
      public final double nz;
      public final double halfLen;
      public final double halfW;
      public final double yLo;
      public final double yHi;
      public final double minX;
      public final double maxX;
      public final double minZ;
      public final double maxZ;
      public final boolean cap;
      public final int side;

      public Seg(double cx, double cz, double ux, double uz, double halfLen, double halfW, double yLo, double yHi, boolean cap, int side) {
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

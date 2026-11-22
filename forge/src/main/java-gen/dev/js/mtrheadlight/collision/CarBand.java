package dev.js.mtrheadlight.collision;

import dev.js.mtrheadlight.TrainPose;
import mtr.data.Train;
import net.minecraft.world.phys.Vec3;

public final class CarBand {
   public static final double SAG_TOL = 0.05D;
   public static final double DS_MAX = 3.0D;
   public static final double DS_MIN = 0.4D;
   public static final double OVERLAP = 0.15D;
   public static final double OVERLAP_EPS = 0.02D;
   public static final int PIECE_MAX = 48;
   public static final double STRAIGHT_ANGLE = 0.02D;
   private static final double MIN_PROBE = 0.5D;
   private static final double EPS = 1.0E-4D;

   private CarBand() {
   }

   public static CarBand.Band build(Train train, int carIndex, TrainPose pose, boolean reversed, double halfW, double height, double capA, double capB) {
      Vec3 a = pose.carStart(carIndex);
      Vec3 b = pose.carEnd(carIndex);
      double ax = b.x - a.x;
      double az = b.z - a.z;
      double len = Math.sqrt(ax * ax + az * az);
      if (len < 0.001D) {
         Vec3 fwd = pose.carForward(carIndex);
         ax = fwd.x;
         az = fwd.z;
         len = Math.sqrt(ax * ax + az * az);
         if (len < 1.0E-4D) {
            return null;
         }
      }

      double ux = ax / len;
      double uz = az / len;
      CarGeometry.Seg[] caps = caps(a, ux, uz, len, a.y, b.y, halfW, height, capA, capB);
      double total = TrainPose.pathLength(train);
      if (!(total > 1.0E-4D)) {
         return straight(a, b, ux, uz, len, halfW, height, caps);
      } else {
         int cars = Math.max(0, train.trainCars);
         double spacing = Math.max(0.0D, (double)train.spacing);
         double progress = train.getRailProgress();
         double fa = clamp(progress - (reversed ? (double)(cars - carIndex) : (double)carIndex) * spacing, total);
         double fb = clamp(progress - (reversed ? (double)(cars - carIndex - 1) : (double)(carIndex + 1)) * spacing, total);
         double s0 = Math.min(fa, fb);
         double s1 = Math.max(fa, fb);
         double arc = s1 - s0;
         if (!(arc > 0.001D)) {
            return straight(a, b, ux, uz, len, halfW, height, caps);
         } else {
            double radius = radius(train, s0, s1, total);
            double step = stepFor(radius, arc);
            int n = (int)Math.max(1L, Math.min(48L, (long)Math.ceil(arc / step)));
            double ds = arc / (double)n;
            double overlap = overlapFor(halfW, ds, radius);
            double bulge = Double.isFinite(radius) ? ds * ds / (8.0D * radius) : 0.0D;
            double bandHalfW = halfW + bulge;
            CarGeometry.Seg[] body = new CarGeometry.Seg[n];
            Vec3 prev = at(train, s0, total);

            for(int k = 0; k < n; ++k) {
               Vec3 next = at(train, s0 + (double)(k + 1) * ds, total);
               double dx = next.x - prev.x;
               double dz = next.z - prev.z;
               double chord = Math.sqrt(dx * dx + dz * dz);
               double px;
               double pz;
               if (chord > 1.0E-4D) {
                  px = dx / chord;
                  pz = dz / chord;
               } else {
                  px = ux;
                  pz = uz;
               }

               body[k] = new CarGeometry.Seg((prev.x + next.x) * 0.5D, (prev.z + next.z) * 0.5D, px, pz, chord * 0.5D + overlap, bandHalfW, Math.min(prev.y, next.y), Math.max(prev.y, next.y) + height, false, 0);
               prev = next;
            }

            double sag = Double.isFinite(radius) ? arc * arc / (8.0D * radius) : 0.0D;
            return new CarBand.Band(body, caps, n, ds, radius, overlap, sag, arc, true, a, b, ux, uz, len);
         }
      }
   }

   public static double stepFor(double radius, double arc) {
      if (Double.isFinite(radius) && radius > 0.0D) {
         double step = Math.sqrt(8.0D * radius * 0.05D);
         if (!(step > 0.4D)) {
            step = 0.4D;
         } else if (step > 3.0D) {
            step = 3.0D;
         }

         return arc > 0.0D && step > arc ? Math.max(0.4D, arc) : step;
      } else {
         return 3.0D;
      }
   }

   public static double overlapFor(double halfW, double step, double radius) {
      if (Double.isFinite(radius) && radius > 0.0D) {
         double theta = Math.min((Math.PI / 2D), step / radius);
         double need = halfW * Math.sin(theta * 0.5D) + 0.02D;
         return need > 0.15D ? need : 0.15D;
      } else {
         return 0.15D;
      }
   }

   public static double radius(Train train, double s0, double s1, double total) {
      try {
         Vec3 p0 = at(train, s0, total);
         Vec3 pm = at(train, (s0 + s1) * 0.5D, total);
         Vec3 p1 = at(train, s1, total);
         double l1 = horizontal(pm.x - p0.x, pm.z - p0.z);
         double l2 = horizontal(p1.x - pm.x, p1.z - pm.z);
         if (!(l1 < 0.5D) && !(l2 < 0.5D)) {
            double cos = ((pm.x - p0.x) * (p1.x - pm.x) + (pm.z - p0.z) * (p1.z - pm.z)) / (l1 * l2);
            if (cos > 1.0D) {
               cos = 1.0D;
            } else if (cos < -1.0D) {
               cos = -1.0D;
            }

            double theta = Math.acos(cos);
            return !(theta > 0.02D) ? Double.POSITIVE_INFINITY : (l1 + l2) / theta;
         } else {
            return Double.POSITIVE_INFINITY;
         }
      } catch (Throwable var18) {
         return Double.POSITIVE_INFINITY;
      }
   }

   public static boolean inside(CarGeometry.Seg[] body, double x, double z, double pad) {
      if (body == null) {
         return false;
      } else {
         for(CarGeometry.Seg seg : body) {
            double dx = x - seg.cx;
            double dz = z - seg.cz;
            double reach = seg.halfLen + seg.halfW + pad + 0.01D;
            if (!(Math.abs(dx) > reach) || !(Math.abs(dz) > reach)) {
               double along = dx * seg.ux + dz * seg.uz;
               double lat = dx * seg.nx + dz * seg.nz;
               if (Math.abs(along) <= seg.halfLen + pad && Math.abs(lat) <= seg.halfW + pad) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   private static CarBand.Band straight(Vec3 a, Vec3 b, double ux, double uz, double len, double halfW, double height, CarGeometry.Seg[] caps) {
      CarGeometry.Seg[] body = new CarGeometry.Seg[]{new CarGeometry.Seg((a.x + b.x) * 0.5D, (a.z + b.z) * 0.5D, ux, uz, len * 0.5D, halfW, Math.min(a.y, b.y), Math.max(a.y, b.y) + height, false, 0)};
      return new CarBand.Band(body, caps, 1, len, Double.POSITIVE_INFINITY, 0.0D, 0.0D, len, false, a, b, ux, uz, len);
   }

   private static CarGeometry.Seg[] caps(Vec3 a, double ux, double uz, double len, double floorA, double floorB, double halfW, double height, double capA, double capB) {
      boolean head = capA > 0.0D;
      boolean tail = capB > 0.0D;
      if (head && tail) {
         return new CarGeometry.Seg[]{cap(a, ux, uz, -capA * 0.5D, capA * 0.5D, halfW, floorA, height, -1), cap(a, ux, uz, len + capB * 0.5D, capB * 0.5D, halfW, floorB, height, 1)};
      } else if (head) {
         return new CarGeometry.Seg[]{cap(a, ux, uz, -capA * 0.5D, capA * 0.5D, halfW, floorA, height, -1)};
      } else {
         return tail ? new CarGeometry.Seg[]{cap(a, ux, uz, len + capB * 0.5D, capB * 0.5D, halfW, floorB, height, 1)} : new CarGeometry.Seg[0];
      }
   }

   private static CarGeometry.Seg cap(Vec3 a, double ux, double uz, double mid, double halfLen, double halfW, double floorY, double height, int side) {
      return new CarGeometry.Seg(a.x + ux * mid, a.z + uz * mid, ux, uz, halfLen, halfW, floorY, floorY + height, true, side);
   }

   private static Vec3 at(Train train, double progress, double total) {
      return TrainPose.samplePath(train, clamp(progress, total));
   }

   private static double clamp(double v, double hi) {
      if (!(v > 0.0D)) {
         return 0.0D;
      } else {
         return v > hi ? hi : v;
      }
   }

   private static double horizontal(double dx, double dz) {
      return Math.sqrt(dx * dx + dz * dz);
   }

   public static final class Band {
      public final CarGeometry.Seg[] body;
      public final CarGeometry.Seg[] caps;
      public final int samples;
      public final double step;
      public final double radius;
      public final double overlap;
      public final double sag;
      public final double arc;
      public final boolean sampled;
      public final Vec3 a;
      public final Vec3 b;
      public final double ux;
      public final double uz;
      public final double len;

      Band(CarGeometry.Seg[] body, CarGeometry.Seg[] caps, int samples, double step, double radius, double overlap, double sag, double arc, boolean sampled, Vec3 a, Vec3 b, double ux, double uz, double len) {
         this.body = body;
         this.caps = caps;
         this.samples = samples;
         this.step = step;
         this.radius = radius;
         this.overlap = overlap;
         this.sag = sag;
         this.arc = arc;
         this.sampled = sampled;
         this.a = a;
         this.b = b;
         this.ux = ux;
         this.uz = uz;
         this.len = len;
      }
   }
}

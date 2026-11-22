package dev.js.mtrheadlight;

import java.util.concurrent.atomic.AtomicLong;

public final class BeamBlockLight {
   public static final int STRIDE = 8;
   public static final int MAX_BEAMS = 16;
   private static final double[] NONE = new double[0];
   private static volatile double[] beams = NONE;
   private static volatile double[] override;
   private static volatile long version;
   private static final AtomicLong HITS = new AtomicLong();
   private static final AtomicLong NANOS = new AtomicLong();
   private static volatile long calls;
   private static volatile boolean regionSeen;
   private static volatile boolean alive;
   private static final double VERTICAL = 4.0D;

   public static void setTestBeam(double[] beam) {
      override = beam;
      publish((double[])null);
   }

   private BeamBlockLight() {
   }

   public static void publish(double[] snapshot) {
      double[] next = override != null ? override : (snapshot == null ? NONE : snapshot);
      if (!same(beams, next)) {
         beams = next;
         ++version;
      }
   }

   private static boolean same(double[] a, double[] b) {
      if (a.length != b.length) {
         return false;
      } else {
         for(int i = 0; i < a.length; ++i) {
            if (a[i] != b[i]) {
               return false;
            }
         }

         return true;
      }
   }

   public static void noteCall() {
      alive = true;
      ++calls;
   }

   public static void noteAlive() {
      alive = true;
      regionSeen = true;
   }

   public static boolean alive() {
      return alive;
   }

   public static boolean regionSeen() {
      return regionSeen;
   }

   public static long calls() {
      return calls;
   }

   public static void clear() {
      publish((double[])null);
   }

   public static boolean active() {
      return beams.length != 0;
   }

   public static int count() {
      return beams.length / 8;
   }

   public static double[] snapshot() {
      return beams;
   }

   public static long version() {
      return version;
   }

   public static int level(int bx, int by, int bz) {
      double[] all = beams;
      if (all.length == 0) {
         return 0;
      } else {
         double px = (double)bx + 0.5D;
         double py = (double)by + 0.5D;
         double pz = (double)bz + 0.5D;
         double best = 0.0D;

         for(int i = 0; i + 8 <= all.length; i += 8) {
            double sx = all[i];
            double sy = all[i + 1];
            double sz = all[i + 2];
            double ex = all[i + 3];
            double ey = all[i + 4];
            double ez = all[i + 5];
            double radius = all[i + 6];
            double peak = all[i + 7];
            if (!(px < min(sx, ex) - radius) && !(px > max(sx, ex) + radius) && !(pz < min(sz, ez) - radius) && !(pz > max(sz, ez) + radius) && !(py < min(sy, ey) - 4.0D) && !(py > max(sy, ey) + 4.0D)) {
               double hx = ex - sx;
               double hz = ez - sz;
               double length = Math.sqrt(hx * hx + hz * hz);
               if (!(length < 1.0E-4D)) {
                  double ux = hx / length;
                  double uz = hz / length;
                  double rx = px - sx;
                  double rz = pz - sz;
                  double t = (rx * ux + rz * uz) / length;
                  t = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
                  double perp = Math.abs(rx * uz - rz * ux);
                  double lateral = 1.5D + (radius - 1.5D) * t;
                  if (!(perp >= lateral)) {
                     double radial = 1.0D - perp / lateral;
                     double dy = py - (sy + (ey - sy) * t);
                     double vert = 1.0D - Math.abs(dy) / 4.0D;
                     if (!(vert <= 0.0D)) {
                        double value = peak * radial * radial * vert * (1.0D - 0.45D * t);
                        if (value > best) {
                           best = value;
                        }
                     }
                  }
               }
            }
         }

         if (best <= 0.0D) {
            return 0;
         } else {
            int rounded = (int)Math.round(best);
            return rounded < 0 ? 0 : (rounded > 15 ? 15 : rounded);
         }
      }
   }

   public static int signature(int sectionX, int sectionY, int sectionZ) {
      int sum = 0;
      int ox = sectionX << 4;
      int oy = sectionY << 4;
      int oz = sectionZ << 4;

      for(int x = 2; x < 16; x += 4) {
         for(int y = 2; y < 16; y += 4) {
            for(int z = 2; z < 16; z += 4) {
               sum += level(ox + x, oy + y, oz + z);
            }
         }
      }

      return sum;
   }

   public static void countHit(long nanos) {
      HITS.incrementAndGet();
      if (nanos > 0L) {
         NANOS.addAndGet(nanos);
      }

   }

   public static long[] drainStats() {
      return new long[]{HITS.getAndSet(0L), NANOS.getAndSet(0L)};
   }

   private static double min(double a, double b) {
      return a < b ? a : b;
   }

   private static double max(double a, double b) {
      return a > b ? a : b;
   }
}

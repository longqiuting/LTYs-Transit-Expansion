package dev.js.lte;

import java.util.Locale;

final class BeamGizmo {
   static final int AXIS_X = 0;
   static final int AXIS_Y = 1;
   static final int AXIS_Z = 2;
   static final double MOVE_STEP = 0.0625D;
   static final double MOVE_STEP_FINE = 0.015625D;
   static final double ROT_STEP = 2.5D;
   static final double ROT_STEP_FINE = 0.5D;
   static final double RADIUS_PX = 44.0D;
   static final double SHAFT_PX = 48.0D;
   static final double MIN_SIZE = 0.35D;
   static final double MAX_SIZE = 32.0D;
   static final double RING_TOL_PX = 7.0D;
   static final double AXIS_TOL_PX = 6.0D;
   static final double PLANE_MIN_COS = 0.15D;
   static final int RING_SEGMENTS = 48;

   private BeamGizmo() {
   }

   static double moveStep(boolean fine) {
      return fine ? 0.015625D : 0.0625D;
   }

   static double rotStep(boolean fine) {
      return fine ? 0.5D : 2.5D;
   }

   static double quantize(double anchor, double value, double step) {
      return step > 0.0D && Double.isFinite(value) ? anchor + (double)Math.round((value - anchor) / step) * step : value;
   }

   static int steps(double anchor, double value, double step) {
      return !(step > 0.0D) ? 0 : (int)Math.round((value - anchor) / step);
   }

   static String stepLabel(double step) {
      if (Math.abs(step - 0.015625D) < 1.0E-9D) {
         return "1/64";
      } else if (Math.abs(step - 0.0625D) < 1.0E-9D) {
         return "1/16";
      } else if (Math.abs(step - 0.5D) < 1.0E-9D) {
         return "0.5\u00b0";
      } else {
         return Math.abs(step - 2.5D) < 1.0E-9D ? "2.5\u00b0" : String.valueOf(step);
      }
   }

   static double ringSize(float scale) {
      return clamp(44.0D / Math.max(0.001D, (double)scale), 0.35D, 32.0D);
   }

   static double shaftSize(float scale) {
      return clamp(48.0D / Math.max(0.001D, (double)scale), 0.35D, 32.0D);
   }

   static String nameOf(int axis) {
      return axis == 0 ? "X" : (axis == 1 ? "Y" : "Z");
   }

   static double[] unit(int axis) {
      switch (axis) {
         case 0:
            return new double[]{1.0D, 0.0D, 0.0D};
         case 1:
            return new double[]{0.0D, 1.0D, 0.0D};
         default:
            return new double[]{0.0D, 0.0D, 1.0D};
      }
   }

   static double[] headDirection(double[] rot) {
      double yaw = Math.toRadians(rot == null ? 0.0D : rot[0]);
      double pitch = Math.toRadians(rot == null ? 0.0D : rot[1]);
      double cosPitch = Math.cos(pitch);
      return new double[]{-Math.sin(yaw) * cosPitch, -Math.sin(pitch), -Math.cos(yaw) * cosPitch};
   }

   static double[] rotFromHead(double[] dir, double roll) {
      double len = Math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2]);
      if (!(len > 1.0E-9D)) {
         return new double[]{0.0D, 0.0D, wrap(roll)};
      } else {
         double x = dir[0] / len;
         double y = dir[1] / len;
         double z = dir[2] / len;
         double pitch = Math.toDegrees(Math.asin(clamp(-y, -1.0D, 1.0D)));
         double cosPitch = Math.cos(Math.toRadians(pitch));
         double yaw = cosPitch < 1.0E-6D ? 0.0D : Math.toDegrees(Math.atan2(-x, -z));
         return new double[]{wrap(yaw), pitch, wrap(roll)};
      }
   }

   static double[] ringNormal(int axis, double[] rot) {
      switch (axis) {
         case 0:
            double yaw = Math.toRadians(rot == null ? 0.0D : rot[0]);
            return new double[]{Math.cos(yaw), 0.0D, -Math.sin(yaw)};
         case 1:
            return new double[]{0.0D, 1.0D, 0.0D};
         default:
            return normalise(headDirection(rot));
      }
   }

   static int rotIndex(int axis) {
      return axis == 0 ? 1 : (axis == 1 ? 0 : 2);
   }

   static int ringSign(int axis) {
      return axis == 0 ? -1 : 1;
   }

   static double[][] planeBasis(double[] normal) {
      double[] n = normalise(normal);
      double[] ref = Math.abs(n[1]) > 0.9D ? new double[]{1.0D, 0.0D, 0.0D} : new double[]{0.0D, 1.0D, 0.0D};
      double[] u = normalise(cross(ref, n));
      double[] v = normalise(cross(n, u));
      return new double[][]{u, v};
   }

   static double[] rotate(double[] v, double[] axis, double degrees) {
      double[] k = normalise(axis);
      double a = Math.toRadians(degrees);
      double cos = Math.cos(a);
      double sin = Math.sin(a);
      double[] kxv = cross(k, v);
      double kdv = k[0] * v[0] + k[1] * v[1] + k[2] * v[2];
      return new double[]{v[0] * cos + kxv[0] * sin + k[0] * kdv * (1.0D - cos), v[1] * cos + kxv[1] * sin + k[1] * kdv * (1.0D - cos), v[2] * cos + kxv[2] * sin + k[2] * kdv * (1.0D - cos)};
   }

   static double[] mirror(double[] v, int axis) {
      double[] e = unit(axis);
      double d = 2.0D * (v[0] * e[0] + v[1] * e[1] + v[2] * e[2]);
      return new double[]{v[0] - d * e[0], v[1] - d * e[1], v[2] - d * e[2]};
   }

   static double[] mirrorPoint(double[] p, double[] pivot, int axis) {
      double[] e = unit(axis);
      double d = (p[0] - pivot[0]) * e[0] + (p[1] - pivot[1]) * e[1] + (p[2] - pivot[2]) * e[2];
      return new double[]{p[0] - 2.0D * d * e[0], p[1] - 2.0D * d * e[1], p[2] - 2.0D * d * e[2]};
   }

   static String selfCheck() {
      StringBuilder bad = new StringBuilder();
      double anchorPos = 0.3125D;
      double[] stepsToTry = new double[]{0.0625D, 0.015625D, 2.5D, 0.5D};

      for(double step : stepsToTry) {
         for(int k = -8; k <= 8; ++k) {
            double target = 0.3125D + (double)k * step + step * 0.31D;
            double q = quantize(0.3125D, target, step);
            double grid = 0.3125D + (double)Math.round((q - 0.3125D) / step) * step;
            if (Math.abs(q - grid) > 1.0E-12D) {
               bad.append("quantize off-grid: step=").append(step).append(" k=").append(k).append("; ");
            }

            if (Math.abs(q - target) > step * 0.5D + 1.0E-12D) {
               bad.append("quantize too far: step=").append(step).append(" k=").append(k).append("; ");
            }
         }
      }

      double[] base = new double[]{20.0D, 10.0D, 5.0D};
      double delta = 7.5D;
      if (rotIndex(0) != 1 || rotIndex(1) != 0 || rotIndex(2) != 2) {
         bad.append("rotIndex table does not match the doc (X=pitch, Y=yaw, Z=roll); ");
      }

      for(int axis = 0; axis < 3; ++axis) {
         int component = rotIndex(axis);
         double[] next = (double[])base.clone();
         next[component] = base[component] + (double)ringSign(axis) * 7.5D;

         for(int other = 0; other < 3; ++other) {
            if (other != component && next[other] != base[other]) {
               bad.append("ring ").append(nameOf(axis)).append(" leaked into rot[").append(other).append("]; ");
            }
         }

         double[] before = headDirection(base);
         double[] after = headDirection(next);
         double[] expect = rotate(before, ringNormal(axis, base), 7.5D);
         if (dist(after, expect) > 1.0E-6D) {
            bad.append("ring ").append(nameOf(axis)).append(" direction mismatch ").append(dist(after, expect)).append("; ");
         }
      }

      for(int axis = 0; axis < 3; ++axis) {
         double[] dir = headDirection(base);
         double[] flipped = mirror(dir, axis);

         for(int other = 0; other < 3; ++other) {
            if (other != axis && Math.abs(flipped[other] - dir[other]) > 1.0E-12D) {
               bad.append("mirror ").append(nameOf(axis)).append(" leaked into ").append(other).append("; ");
            }
         }

         if (Math.abs(flipped[axis] + dir[axis]) > 1.0E-12D) {
            bad.append("mirror ").append(nameOf(axis)).append(" did not negate; ");
         }

         double[] pivot = new double[]{1.5D, 2.0D, -3.5D};
         double[] moved = mirrorPoint(pivot, pivot, axis);
         if (dist(moved, pivot) > 1.0E-12D) {
            bad.append("mirror ").append(nameOf(axis)).append(" moved the anchor; ");
         }

         double[] rot = rotFromHead(flipped, 0.0D);
         if (dist(headDirection(rot), flipped) > 1.0E-6D) {
            bad.append("rotFromHead round-trip failed on ").append(nameOf(axis)).append("; ");
         }
      }

      double[] front = headDirection(new double[]{0.0D, 0.0D, 0.0D});
      double[] up = rotate(front, unit(0), 90.0D);
      if (dist(up, new double[]{0.0D, 1.0D, 0.0D}) > 1.0E-9D) {
         bad.append("rotate X 90 of -Z is ").append(fmt(up)).append(" (want +Y); ");
      }

      double[] right = rotate(front, unit(1), -90.0D);
      if (dist(right, new double[]{1.0D, 0.0D, 0.0D}) > 1.0E-9D) {
         bad.append("rotate Y -90 of -Z is ").append(fmt(right)).append(" (want +X); ");
      }

      double[] rotUp = rotFromHead(up, 0.0D);
      if (Math.abs(rotUp[1] + 90.0D) > 1.0E-6D) {
         bad.append("pitch of straight-up is ").append(rotUp[1]).append(" (want -90); ");
      }

      float[] scales = new float[]{6.0F, 22.0F, 90.0F};

      for(float scale : scales) {
         double px = ringSize(scale) * (double)scale;
         if (Math.abs(px - 44.0D) > 0.001D && ringSize(scale) > 0.35D && ringSize(scale) < 32.0D) {
            bad.append("ring px at scale ").append(scale).append(" is ").append(px).append("; ");
         }
      }

      return bad.toString();
   }

   static double[] cross(double[] a, double[] b) {
      return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
   }

   static double[] normalise(double[] v) {
      double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
      return len < 1.0E-9D ? new double[]{0.0D, 0.0D, 1.0D} : new double[]{v[0] / len, v[1] / len, v[2] / len};
   }

   static double dist(double[] a, double[] b) {
      double dx = a[0] - b[0];
      double dy = a[1] - b[1];
      double dz = a[2] - b[2];
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   private static String fmt(double[] v) {
      return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", v[0], v[1], v[2]);
   }

   static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   static double wrap(double degrees) {
      double d = degrees % 360.0D;
      if (d > 180.0D) {
         d -= 360.0D;
      }

      if (d <= -180.0D) {
         d += 360.0D;
      }

      return d;
   }
}

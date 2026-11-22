package dev.js.lte;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mtr.data.Rail;
import mtr.data.Train;
import mtr.path.PathData;
import net.minecraft.world.phys.Vec3;

public final class TrainPose {
   public static final double TANGENT_EPS = 0.5D;
   private static final TrainPose EMPTY = new TrainPose(new Vec3[]{Vec3.ZERO}, new Vec3[0], new Vec3[0]);
   public final Vec3[] boundaries;
   private final Vec3[] carDir;
   private final Vec3[] bodyDir;
   private static final int CACHE_SIZE = 64;
   private static final Map<Integer, TrainPose.CachedPose> CACHE = new LinkedHashMap<Integer, TrainPose.CachedPose>(32, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<Integer, TrainPose.CachedPose> eldest) {
         return this.size() > 64;
      }
   };

   private TrainPose(Vec3[] boundaries, Vec3[] carDir, Vec3[] bodyDir) {
      this.boundaries = boundaries;
      this.carDir = carDir;
      this.bodyDir = bodyDir;
   }

   public static TrainPose of(Train train) {
      return of(train, train.isReversed());
   }

   public static TrainPose of(Train train, boolean reversed) {
      int cars = Math.max(0, train.trainCars);
      if (cars != 0 && !train.path.isEmpty()) {
         double spacing = Math.max(0.0D, (double)train.spacing);
         double progress = train.getRailProgress();
         double total = pathLength(train);
         Vec3[] boundaries = new Vec3[cars + 1];
         double[] at = new double[cars + 1];

         for(int i = 0; i <= cars; ++i) {
            double p = clamp(progress - (reversed ? (double)(cars - i) : (double)i) * spacing, total);
            at[i] = p;
            boundaries[i] = positionAt(train, p);
         }

         Vec3[] dir = new Vec3[cars];

         for(int i = 0; i < cars; ++i) {
            double mid = (at[i] + at[i + 1]) * 0.5D;
            Vec3 d = tangent(train, mid, total, reversed);
            if (d == Vec3.ZERO) {
               d = chord(boundaries[i], boundaries[i + 1]);
            }

            if (d == Vec3.ZERO && i > 0) {
               d = dir[i - 1];
            }

            dir[i] = d;
         }

         Vec3[] body = new Vec3[cars];

         for(int i = 0; i < cars; ++i) {
            body[i] = chord(boundaries[i], boundaries[i + 1]);
         }

         return new TrainPose(boundaries, dir, body);
      } else {
         return EMPTY;
      }
   }

   public static TrainPose ofShared(Train train, boolean reversed) {
      Integer key = System.identityHashCode(train);
      TrainPose.CachedPose cached = (TrainPose.CachedPose)CACHE.get(key);
      if (cached != null && cached.matches(train, reversed)) {
         return cached.pose;
      } else {
         TrainPose pose = of(train, reversed);
         CACHE.put(key, new TrainPose.CachedPose(train, reversed, pose));
         return pose;
      }
   }

   public static void clearCache() {
      CACHE.clear();
   }

   public int cars() {
      return this.carDir.length;
   }

   public Vec3 front() {
      return this.boundaries[0];
   }

   public Vec3 rear() {
      return this.boundaries[this.boundaries.length - 1];
   }

   public Vec3 forward() {
      return this.carDir.length == 0 ? Vec3.ZERO : this.carDir[0];
   }

   public Vec3 backward() {
      return this.carDir.length == 0 ? Vec3.ZERO : this.carDir[this.carDir.length - 1].scale(-1.0D);
   }

   public Vec3 carStart(int car) {
      return this.boundaries[clampIndex(car, this.boundaries.length - 1)];
   }

   public Vec3 carEnd(int car) {
      return this.boundaries[clampIndex(car + 1, this.boundaries.length - 1)];
   }

   public Vec3 carForward(int car) {
      return this.carDir.length == 0 ? Vec3.ZERO : this.carDir[clampIndex(car, this.carDir.length - 1)];
   }

   public Vec3 carBodyForward(int car) {
      if (this.bodyDir.length == 0) {
         return this.carForward(car);
      } else {
         Vec3 body = this.bodyDir[clampIndex(car, this.bodyDir.length - 1)];
         return body == Vec3.ZERO ? this.carForward(car) : body;
      }
   }

   public boolean carBodyFromChord(int car) {
      return this.bodyDir.length > 0 && this.bodyDir[clampIndex(car, this.bodyDir.length - 1)] != Vec3.ZERO;
   }

   private static int clampIndex(int value, int last) {
      return value < 0 ? 0 : Math.min(value, last);
   }

   private static Vec3 tangent(Train train, double progress, double total, boolean reversed) {
      double low = clamp(progress - 0.5D, total);
      double high = clamp(progress + 0.5D, total);
      if (high - low < 1.0E-6D) {
         return Vec3.ZERO;
      } else {
         Vec3 d = positionAt(train, high).subtract(positionAt(train, low));
         double len = d.length();
         if (len < 1.0E-6D) {
            return Vec3.ZERO;
         } else {
            Vec3 unit = d.scale(1.0D / len);
            return reversed ? unit.scale(-1.0D) : unit;
         }
      }
   }

   private static Vec3 chord(Vec3 lead, Vec3 trail) {
      Vec3 d = lead.subtract(trail);
      double len = d.length();
      return len < 1.0E-6D ? Vec3.ZERO : d.scale(1.0D / len);
   }

   private static double clamp(double progress, double total) {
      if (progress < 0.0D) {
         return 0.0D;
      } else {
         return progress > total ? total : progress;
      }
   }

   private static Vec3 positionAt(Train train, double rawProgress) {
      List<PathData> path = train.path;
      int size = path.size();
      if (size == 0) {
         return Vec3.ZERO;
      } else {
         double progress = Math.max(rawProgress, 0.0D);
         double before = 0.0D;
         double length = 0.0D;
         int index = size - 1;

         for(int i = 0; i < size; ++i) {
            length = ((PathData)path.get(i)).rail.getLength();
            if (progress < before + length || i == size - 1) {
               index = i;
               break;
            }

            before += length;
         }

         double t = progress - before;
         if (t < 0.0D) {
            t = 0.0D;
         } else if (t > length) {
            t = length;
         }

         int railOffset = train.transportMode == null ? 0 : train.transportMode.railOffset;
         return ((PathData)path.get(index)).rail.getPosition(t).add(0.0D, (double)railOffset, 0.0D);
      }
   }

   public static double pathLength(Train train) {
      double total = 0.0D;

      for(PathData data : train.path) {
         total += data.rail.getLength();
      }

      return total;
   }

   public static Vec3 samplePath(Train train, double progress) {
      return positionAt(train, progress);
   }

   public static Rail railAt(Train train, double rawProgress) {
      List<PathData> path = train.path;
      if (path.isEmpty()) {
         return null;
      } else {
         double progress = Math.max(rawProgress, 0.0D);
         double before = 0.0D;

         for(int i = 0; i < path.size(); ++i) {
            double length = ((PathData)path.get(i)).rail.getLength();
            if (progress < before + length) {
               return ((PathData)path.get(i)).rail;
            }

            before += length;
         }

         return ((PathData)path.get(path.size() - 1)).rail;
      }
   }

   private static final class CachedPose {
      private final Train train;
      private final double progress;
      private final boolean reversed;
      private final int cars;
      private final int spacing;
      private final List<PathData> path;
      private final TrainPose pose;

      CachedPose(Train train, boolean reversed, TrainPose pose) {
         this.train = train;
         this.progress = train.getRailProgress();
         this.reversed = reversed;
         this.cars = train.trainCars;
         this.spacing = train.spacing;
         this.path = train.path;
         this.pose = pose;
      }

      boolean matches(Train other, boolean direction) {
         return other == this.train && direction == this.reversed && other.trainCars == this.cars && other.spacing == this.spacing && other.path == this.path && other.getRailProgress() == this.progress;
      }
   }
}

package dev.js.lte.server;

import dev.js.lte.TrainPose;
import dev.js.lte.collision.CarGeometry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import mtr.data.Train;
import mtr.data.TransportMode;
import net.minecraft.world.phys.Vec3;

public final class CarScan {
   public static final double SEARCH_RANGE = 48.0D;
   private static final double DOOR_VALUE_EPS = 0.02D;
   private static final double CULL_RADIUS = cullRadius();
   private static final long TTL = 200L;
   private static final double[] NO_X = new double[0];
   private static final double[] NO_Z = new double[0];
   private static final Map<Train, CarScan.Entry> CACHE = new IdentityHashMap();
   private static final List<CarGeometry.Car> OUT = new ArrayList();
   private static int lastTrains;
   private static int lastCulled;
   private static int lastReused;
   private static int lastKeptCars;
   private static int lastTotalCars;

   private CarScan() {
   }

   private static double cullRadius() {
      try {
         String v = System.getProperty("lte.cull.radius");
         if (v != null) {
            double d = Double.parseDouble(v.trim());
            return d > 0.0D ? d : 0.0D;
         }
      } catch (Throwable var3) {
      }

      return 128.0D;
   }

   public static List<CarGeometry.Car> scan(List<Train> trains, double height, double endPad, CarScan.Extras extras, double[] px, double[] pz, int n, long now) {
      OUT.clear();
      lastTrains = 0;
      lastCulled = 0;
      lastReused = 0;
      lastKeptCars = 0;
      lastTotalCars = 0;
      if (trains != null && !trains.isEmpty()) {
         if (n <= 0) {
            prune(now);
            return OUT;
         } else {
            double[] xs = px == null ? NO_X : px;
            double[] zs = pz == null ? NO_Z : pz;

            for(int t = 0; t < trains.size(); ++t) {
               Train train = (Train)trains.get(t);

               try {
                  if (train != null && train.path != null && !train.path.isEmpty() && train.trainCars > 0 && train.transportMode == TransportMode.TRAIN) {
                     ++lastTrains;
                     double progress = train.getRailProgress();
                     boolean reversed = train.isReversed();
                     double speed = CarGeometry.speedKmh(train.getSpeed());
                     boolean doors = (double)train.getDoorValue() > 0.02D;
                     CarScan.Entry cached = (CarScan.Entry)CACHE.get(train);
                     if (cached != null && cached.sameBase(train, reversed, height, endPad)) {
                        double travel = Math.abs(progress - cached.progress) + 1.0D;
                        lastTotalCars += cached.cars.size();
                        if (!cached.mayReach(xs, zs, n, travel)) {
                           cached.seen = now;
                           ++lastCulled;
                        } else {
                           double head = extras == null ? endPad : extras.head(train.sidingId);
                           double tail = extras == null ? endPad : extras.tail(train.sidingId);
                           if (cached.sameTick(progress, speed, doors, head, tail)) {
                              cached.seen = now;
                              ++lastReused;
                              OUT.addAll(cached.cars);
                           } else {
                              List<CarGeometry.Car> rebuilt = build(train, height, endPad, head, tail, progress, reversed, speed, doors);
                              if (rebuilt.isEmpty()) {
                                 CACHE.remove(train);
                              } else {
                                 CACHE.put(train, new CarScan.Entry(train, rebuilt, progress, speed, doors, height, endPad, head, tail, now));
                                 OUT.addAll(rebuilt);
                              }
                           }
                        }
                     } else {
                        double head = extras == null ? endPad : extras.head(train.sidingId);
                        double tail = extras == null ? endPad : extras.tail(train.sidingId);
                        List<CarGeometry.Car> built = build(train, height, endPad, head, tail, progress, reversed, speed, doors);
                        if (built.isEmpty()) {
                           CACHE.remove(train);
                        } else {
                           CACHE.put(train, new CarScan.Entry(train, built, progress, speed, doors, height, endPad, head, tail, now));
                           lastTotalCars += built.size();
                           OUT.addAll(built);
                        }
                     }
                  }
               } catch (Throwable var29) {
               }
            }

            lastKeptCars = OUT.size();
            prune(now);
            return OUT;
         }
      } else {
         return OUT;
      }
   }

   private static List<CarGeometry.Car> build(Train train, double height, double endPad, double headExtra, double tailExtra, double progress, boolean reversed, double speed, boolean doorsOpen) {
      TrainPose pose = TrainPose.of(train, reversed);
      int carCount = pose.cars();
      List<CarGeometry.Car> out = new ArrayList(Math.max(1, carCount));
      if (carCount <= 0) {
         return out;
      } else {
         double halfW = CarGeometry.halfWidth(train.width);
         String key = Long.toString(train.id);
         double noseExtra = Math.max(endPad, headExtra);
         double rearExtra = Math.max(endPad, tailExtra);

         for(int i = 0; i < carCount; ++i) {
            Vec3 a = pose.carStart(i);
            Vec3 b = pose.carEnd(i);
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

            out.add(new CarGeometry.Car(key, i, a, b, ax / len, az / len, len, halfW, height, doorsOpen, speed, Double.POSITIVE_INFINITY, i == 0 ? noseExtra : endPad, i == carCount - 1 ? rearExtra : endPad, train.sidingId));
         }

         return out;
      }
   }

   private static void prune(long now) {
      if (now % 100L == 0L && !CACHE.isEmpty()) {
         Iterator<Map.Entry<Train, CarScan.Entry>> it = CACHE.entrySet().iterator();

         while(it.hasNext()) {
            if (now - ((CarScan.Entry)((Map.Entry)it.next()).getValue()).seen > 200L) {
               it.remove();
            }
         }

      }
   }

   public static int lastTrains() {
      return lastTrains;
   }

   public static int lastCulled() {
      return lastCulled;
   }

   public static int lastReused() {
      return lastReused;
   }

   public static int lastKeptCars() {
      return lastKeptCars;
   }

   public static int lastTotalCars() {
      return lastTotalCars;
   }

   public static void reset() {
      CACHE.clear();
      OUT.clear();
      lastTrains = 0;
      lastCulled = 0;
      lastReused = 0;
   }

   private static final class Entry {
      final Train train;
      final List<CarGeometry.Car> cars;
      final double progress;
      final Object path;
      final int carsCount;
      final int spacing;
      final int width;
      final boolean reversed;
      final double speed;
      final boolean doorsOpen;
      final double height;
      final double endPad;
      final double headExtra;
      final double tailExtra;
      long seen;

      Entry(Train train, List<CarGeometry.Car> cars, double progress, double speed, boolean doorsOpen, double height, double endPad, double headExtra, double tailExtra, long seen) {
         this.train = train;
         this.cars = cars;
         this.progress = progress;
         this.path = train.path;
         this.carsCount = train.trainCars;
         this.spacing = train.spacing;
         this.width = train.width;
         this.reversed = train.isReversed();
         this.speed = speed;
         this.doorsOpen = doorsOpen;
         this.height = height;
         this.endPad = endPad;
         this.headExtra = headExtra;
         this.tailExtra = tailExtra;
         this.seen = seen;
      }

      boolean sameBase(Train t, boolean rev, double h, double pad) {
         return t == this.train && t.path == this.path && t.trainCars == this.carsCount && t.spacing == this.spacing && t.width == this.width && rev == this.reversed && h == this.height && pad == this.endPad;
      }

      boolean sameTick(double prog, double sp, boolean doors, double head, double tail) {
         return prog == this.progress && sp == this.speed && doors == this.doorsOpen && head == this.headExtra && tail == this.tailExtra;
      }

      boolean mayReach(double[] px, double[] pz, int n, double travel) {
         for(int i = 0; i < this.cars.size(); ++i) {
            CarGeometry.Car car = (CarGeometry.Car)this.cars.get(i);
            double reach = CarScan.CULL_RADIUS + 48.0D + car.len;

            for(int j = 0; j < n; ++j) {
               double dx = px[j] - car.cx;
               double dz = pz[j] - car.cz;
               if (dx * dx + dz * dz <= (reach + travel) * (reach + travel)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public interface Extras {
      double head(long var1);

      double tail(long var1);
   }
}

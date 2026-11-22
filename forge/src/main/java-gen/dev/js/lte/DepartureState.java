package dev.js.lte;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class DepartureState {
   private static final double STEP_EPSILON = 0.02D;
   private static final double MAX_STEP = 2.0D;
   private static final double DEPART_DISTANCE = 2.0D;
   private static final int DEPART_STEPS = 10;
   private static final int STILL_TICKS_TO_CLEAR = 60;
   private static final double IDLE_SPEED_EPSILON = 0.01D;
   private static final boolean DIAG = !"false".equalsIgnoreCase(System.getProperty("mtrheadlight.departdiag", "true"));
   private static final int DIAG_PERIOD = 100;
   private static final Map<String, DepartureState.Entry> TRACKED = new HashMap();
   private static ClientLevel lastWorld;
   private static long tickCounter;
   private static String lastDiag = "";

   private DepartureState() {
   }

   public static void clear() {
      TRACKED.clear();
      lastDiag = "";
   }

   public static void tick() {
      Minecraft client = Minecraft.getInstance();
      ClientLevel world = client.level;
      if (world != lastWorld) {
         TRACKED.clear();
         lastDiag = "";
         lastWorld = world;
      }

      if (world != null && client.player != null) {
         ++tickCounter;
         HeadlightConfig cfg = MtrHeadlightClient.config();
         int trains = 0;
         int onRoute = 0;
         int moved = 0;
         int parked = 0;
         int lit = 0;
         Set<String> seen = new HashSet();

         for(TrainClient train : snapshotTrains()) {
            if (train != null && !train.isRemoved && !train.path.isEmpty()) {
               ++trains;
               String key = keyOf(train);
               seen.add(key);
               double progress = train.getRailProgress();
               boolean routed = isOnRoute(train);
               if (routed) {
                  ++onRoute;
               }

               DepartureState.Entry entry = (DepartureState.Entry)TRACKED.get(key);
               if (entry == null) {
                  entry = new DepartureState.Entry(train.isReversed(), progress);
                  TRACKED.put(key, entry);
               } else {
                  entry.observe(progress - entry.lastProgress);
                  entry.lastProgress = progress;
               }

               entry.observeSpeed(speedOf(train));
               if (!routed) {
                  entry.settle();
               } else {
                  entry.stillTicks = 0;
               }

               boolean isParked = !routed && entry.moved && entry.isParked(cfg);
               if (entry.moved) {
                  ++moved;
               }

               if (isParked) {
                  ++parked;
               }

               if (routed || entry.moved && !isParked) {
                  ++lit;
               }
            }
         }

         TRACKED.keySet().retainAll(seen);
         if (DIAG && tickCounter % 100L == 0L) {
            String line = "depart: trains=" + trains + " onRoute=" + onRoute + " moved=" + moved + " parked=" + parked + " lit=" + lit;
            if (!line.equals(lastDiag)) {
               MtrHeadlightClient.LOGGER.info("[lte] {}", line);
               lastDiag = line;
            }
         }

      } else {
         if (!TRACKED.isEmpty()) {
            TRACKED.clear();
         }

      }
   }

   public static boolean departed(TrainClient train) {
      if (train == null) {
         return false;
      } else if (isOnRoute(train)) {
         return true;
      } else {
         DepartureState.Entry entry = (DepartureState.Entry)TRACKED.get(keyOf(train));
         if (entry != null && entry.moved) {
            return !entry.isParked(MtrHeadlightClient.config());
         } else {
            return false;
         }
      }
   }

   public static long idleTicks(TrainClient train) {
      if (train == null) {
         return 0L;
      } else {
         DepartureState.Entry entry = (DepartureState.Entry)TRACKED.get(keyOf(train));
         return entry == null ? 0L : (long)entry.idleTicks;
      }
   }

   public static boolean stopped(TrainClient train, HeadlightConfig cfg) {
      if (train != null && cfg != null && cfg.parkedLightsOff) {
         DepartureState.Entry entry = (DepartureState.Entry)TRACKED.get(keyOf(train));
         return entry != null && entry.isParked(cfg);
      } else {
         return false;
      }
   }

   public static boolean reversed(TrainClient train, boolean fallback) {
      if (train == null) {
         return fallback;
      } else {
         DepartureState.Entry entry = (DepartureState.Entry)TRACKED.get(keyOf(train));
         return entry == null ? fallback : entry.reversed;
      }
   }

   public static String keyOf(TrainClient train) {
      return train.id != 0L ? "id:" + train.id : "tid:" + train.trainId;
   }

   private static boolean isOnRoute(TrainClient train) {
      try {
         return train.getIsOnRoute();
      } catch (Throwable var2) {
         return false;
      }
   }

   private static List<TrainClient> snapshotTrains() {
      try {
         return new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var1) {
         return List.of();
      }
   }

   private static double speedOf(TrainClient train) {
      try {
         return (double)train.getSpeed();
      } catch (Throwable var2) {
         return 0.0D;
      }
   }

   private static final class Entry {
      double lastProgress;
      double travelled;
      int movingTicks;
      int stillTicks;
      int idleTicks;
      boolean moved;
      boolean reversed;

      Entry(boolean reversedInitially, double progress) {
         this.reversed = reversedInitially;
         this.lastProgress = progress;
      }

      void observe(double delta) {
         double step = Math.abs(delta);
         if (step < 0.02D) {
            ++this.stillTicks;
         } else if (step > 2.0D) {
            this.reset();
            this.stillTicks = 0;
         } else {
            this.travelled += step;
            ++this.movingTicks;
            this.stillTicks = 0;
            this.reversed = delta < 0.0D;
            if (this.travelled >= 2.0D && this.movingTicks >= 10) {
               this.moved = true;
            }

         }
      }

      void settle() {
         if (this.stillTicks >= 60) {
            this.reset();
         }

      }

      void observeSpeed(double speed) {
         if (Math.abs(speed) < 0.01D) {
            if (this.idleTicks < Integer.MAX_VALUE) {
               ++this.idleTicks;
            }
         } else {
            this.idleTicks = 0;
         }

      }

      boolean isParked(HeadlightConfig cfg) {
         if (cfg != null && cfg.parkedLightsOff) {
            return this.idleTicks > cfg.parkIdleTicks();
         } else {
            return false;
         }
      }

      void reset() {
         this.travelled = 0.0D;
         this.movingTicks = 0;
         this.moved = false;
      }
   }
}

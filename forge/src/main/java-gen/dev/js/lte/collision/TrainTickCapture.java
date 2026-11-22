package dev.js.lte.collision;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import mtr.data.Train;
import net.minecraft.world.level.Level;

public final class TrainTickCapture {
   private static final long TTL = 20L;
   private static final Map<Level, Map<Train, Long>> BY_WORLD = new IdentityHashMap();

   private TrainTickCapture() {
   }

   public static void capture(Level world, Train train) {
      if (world != null && train != null) {
         long now = world.getGameTime();
         Map<Train, Long> trains = (Map)BY_WORLD.get(world);
         if (trains == null) {
            trains = new IdentityHashMap();
            BY_WORLD.put(world, trains);
         }

         trains.put(train, now);
      }
   }

   public static List<Train> trains(Level world, long now) {
      Map<Train, Long> trains = (Map)BY_WORLD.get(world);
      if (trains != null && !trains.isEmpty()) {
         List<Train> out = new ArrayList(trains.size());
         Iterator<Map.Entry<Train, Long>> it = trains.entrySet().iterator();

         while(it.hasNext()) {
            Map.Entry<Train, Long> entry = (Map.Entry)it.next();
            Train train = (Train)entry.getKey();
            if (train == null) {
               it.remove();
            } else if (now - entry.getValue() > 20L) {
               it.remove();
            } else {
               out.add(train);
            }
         }

         return out;
      } else {
         return List.of();
      }
   }

   public static void clear() {
      BY_WORLD.clear();
   }
}

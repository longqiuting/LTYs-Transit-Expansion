package dev.js.lte.server;

import dev.js.lte.LteLog;
import dev.js.lte.MtrCompat;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import mtr.data.RailwayData;
import net.minecraft.world.level.Level;

public final class MtrServerData {
   private static final Map<Level, RailwayData> BY_WORLD = new WeakHashMap();
   private static volatile Field worldField;
   private static volatile boolean probed;
   private static volatile boolean warned;

   private MtrServerData() {
   }

   public static void capture(RailwayData data) {
      if (data != null) {
         try {
            Field field = worldField();
            if (field == null) {
               return;
            }

            Object world = field.get(data);
            if (world instanceof Level) {
               Level w = (Level)world;
               synchronized(BY_WORLD) {
                  BY_WORLD.put(w, data);
               }
            }
         } catch (Throwable var7) {
            warnOnce(var7);
         }

      }
   }

   public static Object rails(Level world) {
      if (world == null) {
         return null;
      } else {
         RailwayData data;
         synchronized(BY_WORLD) {
            data = (RailwayData)BY_WORLD.get(world);
         }

         if (data == null) {
            return null;
         } else {
            try {
               return MtrCompat.railsMap(data);
            } catch (Throwable var4) {
               warnOnce(var4);
               return null;
            }
         }
      }
   }

   public static void reset() {
      synchronized(BY_WORLD) {
         BY_WORLD.clear();
      }
   }

   private static Field worldField() {
      if (probed) {
         return worldField;
      } else {
         probed = true;

         try {
            Field field = RailwayData.class.getDeclaredField("world");
            field.setAccessible(true);
            worldField = field;
         } catch (Throwable var1) {
            warnOnce(var1);
         }

         return worldField;
      }
   }

   private static void warnOnce(Throwable t) {
      if (!warned) {
         warned = true;
         LteLog.LOGGER.warn("[lte] trackbed: could not read MTR railway data - trackbed collision for non-player entities is off (train collision unaffected)", t);
      }
   }
}

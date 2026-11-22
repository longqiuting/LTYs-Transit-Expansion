package dev.js.mtrheadlight;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import mtr.data.Rail;
import mtr.data.RailwayData;
import net.minecraft.core.BlockPos;

public final class MtrCompat {
   private static Field railsField;
   private static boolean railsFieldFailed;

   private MtrCompat() {
   }

   public static Map<BlockPos, Map<BlockPos, Rail>> railsMap(RailwayData data) {
      if (data == null) {
         return Collections.emptyMap();
      } else {
         try {
            Method m = RailwayData.class.getMethod("getRailsMap");
            Object v = m.invoke(data);
            if (v instanceof Map) {
               return (Map)v;
            }
         } catch (Throwable var4) {
         }

         if (!railsFieldFailed) {
            try {
               if (railsField == null) {
                  railsField = RailwayData.class.getDeclaredField("rails");
                  railsField.setAccessible(true);
               }

               Object v = railsField.get(data);
               if (v instanceof Map) {
                  return (Map)v;
               }
            } catch (Throwable var3) {
               railsFieldFailed = true;
               LteLog.LOGGER.warn("[lte] RailwayData.rails unavailable: {}", String.valueOf(var3));
            }
         }

         return Collections.emptyMap();
      }
   }
}

package dev.js.lte;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ShaderPackProbe {
   private static final long REFRESH_MILLIS = 1000L;
   private static boolean resolved;
   private static Method irisInUse;
   private static Object irisApi;
   private static Method optifineInUse;
   private static Field optifineActiveProgram;
   private static volatile boolean cached;
   private static volatile long cachedAt;
   private static boolean announced;
   private static boolean lastAnnounced;

   private ShaderPackProbe() {
   }

   public static boolean active() {
      long now = System.currentTimeMillis();
      if (now - cachedAt < 1000L) {
         return cached;
      } else {
         cachedAt = now;
         cached = query();
         return cached;
      }
   }

   public static void announce(HeadlightConfig cfg) {
      boolean inUse = active();
      if (!announced || inUse != lastAnnounced) {
         announced = true;
         lastAnnounced = inUse;

         try {
            if (inUse) {
               MtrHeadlightClient.LOGGER.info("[lte] shader pack active: the beam sheet and the lamp flares draw in the late pass, the scatter pass is skipped (band={} flare={} scatter={})", new Object[]{cfg != null && cfg.beamBand, cfg != null && cfg.beamFlare, cfg != null && cfg.beamScatter});
            } else {
               MtrHeadlightClient.LOGGER.info("[lte] shader pack inactive: all three light effects use their own path");
            }
         } catch (Throwable var3) {
         }

      }
   }

   private static boolean query() {
      if (!resolved) {
         resolved = true;
         resolve();
      }

      if (irisInUse != null && irisApi != null) {
         try {
            return Boolean.TRUE.equals(irisInUse.invoke(irisApi));
         } catch (Throwable var3) {
         }
      }

      if (optifineInUse != null) {
         try {
            return Boolean.TRUE.equals(optifineInUse.invoke((Object)null));
         } catch (Throwable var1) {
            return false;
         }
      } else if (optifineActiveProgram != null) {
         try {
            return ((Number)optifineActiveProgram.get((Object)null)).intValue() != 0;
         } catch (Throwable var2) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static void resolve() {
      try {
         Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
         Object instance = api.getMethod("getInstance").invoke((Object)null);
         Method inUse = api.getMethod("isShaderPackInUse");
         if (instance != null && inUse.getReturnType() == Boolean.TYPE) {
            irisApi = instance;
            irisInUse = inUse;
            return;
         }
      } catch (Throwable var5) {
         irisApi = null;
         irisInUse = null;
      }

      try {
         Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
         Method inUse = shaders.getMethod("isShaderPackInUse");
         if (inUse.getReturnType() == Boolean.TYPE) {
            optifineInUse = inUse;
            return;
         }
      } catch (Throwable var4) {
         optifineInUse = null;
      }

      try {
         Class<?> shaders = Class.forName("net.optifine.shaders.Shaders");
         Field field = shaders.getDeclaredField("activeProgramID");
         field.setAccessible(true);
         optifineActiveProgram = field;
      } catch (Throwable var3) {
         optifineActiveProgram = null;
      }

   }
}

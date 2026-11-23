package dev.js.lte;

public final class LteDebug {
   public static final long PERIOD_MS = 30000L;
   private static final boolean COLLISION = flag("lte.debug.collision");
   private static final boolean PROBE = flag("lte.debug.probe") || COLLISION;
   private static final boolean LIGHTS = flag("lte.debug.lights");
   private static final boolean CLEAR = flag("lte.debug.clear");
   private static final boolean RAIN = flag("lte.debug.rain");
   private static final boolean SPRAY = flag("lte.debug.spray");
   private static long lastMs;

   private LteDebug() {
   }

   private static boolean flag(String key) {
      try {
         String v = System.getProperty(key);
         return v != null && !"false".equalsIgnoreCase(v.trim()) && !"0".equals(v.trim());
      } catch (Throwable var2) {
         return false;
      }
   }

   public static boolean collision() {
      return COLLISION;
   }

   public static boolean probe() {
      return PROBE;
   }

   public static boolean lights() {
      return LIGHTS;
   }

   public static boolean clear() {
      return CLEAR;
   }

   public static boolean rain() {
      return RAIN;
   }

   public static boolean spray() {
      return SPRAY;
   }

   public static boolean due() {
      long now = System.currentTimeMillis();
      if (lastMs != 0L && now - lastMs < 30000L) {
         return false;
      } else {
         lastMs = now;
         return true;
      }
   }
}

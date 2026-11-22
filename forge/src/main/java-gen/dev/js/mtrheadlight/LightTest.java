package dev.js.mtrheadlight;

public final class LightTest {
   private static final long LOG_INTERVAL_MS = 5000L;
   private static volatile boolean on;
   private static long windowStart = System.currentTimeMillis();
   private static int flareQuads;
   private static boolean programTint;
   private static boolean fallbackTint;
   private static int submits;
   private static String flareError;
   private static String tintError;

   private LightTest() {
   }

   public static boolean enabled() {
      return on;
   }

   public static void set(boolean value) {
      on = value;
      windowStart = System.currentTimeMillis();
      flareQuads = 0;
      submits = 0;
      programTint = false;
      fallbackTint = false;
      flareError = null;
      tintError = null;
   }

   public static String status() {
      return "lighttest = " + (on ? "on" : "off") + (on ? "\uff08\u6d0b\u7ea2\u56db\u8fb9\u5f62 = \u706f\u6655\u5c42\u51e0\u4f55\uff1b\u5de6\u534a\u5c4f\u7ea2 = \u6563\u5c04 pass \u5728\u5199\u989c\u8272\uff1b\u53f3\u534a\u5c4f\u84dd = \u65f6\u673a\u5bf9\u7167\uff09" : "");
   }

   static void flareQuad() {
      ++flareQuads;
   }

   static void flareFailure(String reason) {
      flareError = reason;
   }

   public static void programTintSubmitted() {
      ++submits;
      programTint = true;
   }

   public static void tintFailure(String reason) {
      tintError = reason;
   }

   public static boolean drawFallbackTint() {
      try {
         GlowCompat.drawScreenQuad(0.0F, -1.0F, 1.0F, 1.0F, 0.15F, 0.35F, 1.0F, 0.55F);
         fallbackTint = true;
         return true;
      } catch (Throwable var1) {
         tintError = var1.getClass().getSimpleName() + ":" + firstLine(var1);
         return false;
      }
   }

   public static void report() {
      if (on) {
         long now = System.currentTimeMillis();
         if (now - windowStart >= 5000L) {
            windowStart = now;
            StringBuilder extra = new StringBuilder();
            if (fallbackTint) {
               extra.append(" fallbackTint=true");
            }

            if (tintError != null) {
               extra.append(" tintError=").append(tintError);
            } else if (flareError != null) {
               extra.append(" flareError=").append(flareError);
            } else if (submits > 0) {
               extra.append(" tintSubmits=").append(submits);
            }

            MtrHeadlightClient.LOGGER.info("[lte] lighttest: flareQuads={} scatterTint={}{}", new Object[]{flareQuads, programTint, extra.toString()});
            flareQuads = 0;
            submits = 0;
            programTint = false;
            fallbackTint = false;
            flareError = null;
            tintError = null;
         }
      }
   }

   private static String firstLine(Throwable t) {
      String text = t == null ? "unknown" : String.valueOf(t.getMessage());
      int newline = text.indexOf(10);
      String head = newline < 0 ? text : text.substring(0, newline);
      return head.length() > 120 ? head.substring(0, 120) : head;
   }
}

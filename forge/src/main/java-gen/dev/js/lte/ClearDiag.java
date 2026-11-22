package dev.js.lte;

public final class ClearDiag {
   private static final int PERIOD_TICKS = 100;
   private static int clearedInWindow;

   private ClearDiag() {
   }

   public static void cleared(int trains) {
      clearedInWindow += trains;
   }

   public static void tick(boolean enabled, long tick, int tracked, int missing) {
      if (!enabled) {
         clearedInWindow = 0;
      } else if (tick % 100L == 0L) {
         int cleared = clearedInWindow;
         clearedInWindow = 0;

         try {
            MtrHeadlightClient.LOGGER.info("[lte] clear: tracked={} missing={} massClear={}", new Object[]{tracked, missing, cleared});
         } catch (Throwable var7) {
         }

      }
   }
}

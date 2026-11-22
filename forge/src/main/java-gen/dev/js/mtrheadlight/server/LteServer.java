package dev.js.mtrheadlight.server;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteLog;
import dev.js.mtrheadlight.forge.platform.ModInitializer;

public final class LteServer implements ModInitializer {
   public void onInitialize() {
      (new LteServerLights()).onInitialize();
      LteServerLights.installRegistrySelfCheck();
   }

   public static HeadlightConfig config() {
      return LteServerLights.config();
   }

   static void module(String name, Runnable body) {
      try {
         body.run();
      } catch (Throwable var3) {
         LteLog.LOGGER.error("[lte] {} registration aborted (the rest of the mod keeps working)", name, var3);
      }

   }

   static void blockModule(String name, Runnable body) {
      int blocksBefore = RegistrySelfCheck.countBlocks();
      int oursBefore = RegistrySelfCheck.countBlocks("lte");
      int pendingBefore = RegistrySelfCheck.pendingCount();

      try {
         body.run();
      } catch (Throwable var10) {
         int registered = RegistrySelfCheck.countBlocks("lte") - oursBefore;
         int leaked = Math.max(0, RegistrySelfCheck.pendingCount() - pendingBefore);
         LteLog.LOGGER.error("[lte] {} registration aborted: {} block(s) registered, {} block(s) created but never registered (blocks total {} -> {}); the rest of the mod keeps working", new Object[]{name, registered, leaked, blocksBefore, RegistrySelfCheck.countBlocks(), var10});

         try {
            RegistrySelfCheck.purgePending(name);
         } catch (Throwable var9) {
         }
      }

   }
}

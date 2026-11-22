package dev.js.mtrheadlight;

import net.minecraft.world.phys.Vec3;

public final class LightBackends {
   private static final LightBackend BLOCK = new LightBackends.BlockBackend();

   private LightBackends() {
   }

   public static LightBackend backend() {
      return BLOCK;
   }

   public static boolean available() {
      return true;
   }

   private static final class BlockBackend implements LightBackend {
      public void updateLine(String key, Vec3 start, Vec3 end, int luminance) {
      }

      public void hideLine(String key) {
      }

      public void removeLine(String key) {
      }

      public void clearAll() {
      }

      public int liveSources() {
         return 0;
      }

      public void tick() {
      }
   }
}

package dev.js.mtrheadlight;

import net.minecraft.world.phys.Vec3;

public interface LightBackend {
   void updateLine(String var1, Vec3 var2, Vec3 var3, int var4);

   void hideLine(String var1);

   void removeLine(String var1);

   void clearAll();

   default void tick() {
   }

   default int liveSources() {
      return -1;
   }
}

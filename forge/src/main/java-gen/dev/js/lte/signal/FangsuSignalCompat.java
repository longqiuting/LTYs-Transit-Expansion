package dev.js.lte.signal;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class FangsuSignalCompat {
   private FangsuSignalCompat() {
   }

   public static boolean isFangsuTrafficLight(BlockState state) {
      if (state == null) {
         return false;
      } else {
         try {
            return isFangsuTrafficLight(state.getBlock());
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   public static boolean isFangsuTrafficLight(Block block) {
      if (block == null) {
         return false;
      } else {
         try {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id != null && "fangsu".equals(id.getNamespace())) {
               String path = id.getPath();
               return path.contains("traffic_light");
            } else {
               return false;
            }
         } catch (Throwable var3) {
            return false;
         }
      }
   }
}

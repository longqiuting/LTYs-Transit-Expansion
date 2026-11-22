package dev.js.lte;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FangsuTurnoutLight {
   private FangsuTurnoutLight() {
   }

   public static int lift(Object blockEntity, int packed) {
      try {
         if (blockEntity instanceof BlockEntity be) {
            if (!enabled()) {
               return packed;
            } else {
               Level world = be.getLevel();
               if (world == null) {
                  return packed;
               } else {
                  BlockPos pos = be.getBlockPos();
                  if (pos == null) {
                     return packed;
                  } else {
                     int incomingBlock = packed & '\uffff';
                     int incomingSky = packed >>> 16 & '\uffff';
                     if (incomingBlock <= 15 && incomingSky <= 15) {
                        int block = Math.max(incomingBlock, sanitize(world.getBrightness(LightLayer.BLOCK, pos)));
                        int sky = Math.max(incomingSky, sanitize(world.getBrightness(LightLayer.SKY, pos)));
                        return block == incomingBlock && sky == incomingSky ? packed : LightTexture.pack(block, sky);
                     } else {
                        return packed;
                     }
                  }
               }
            }
         } else {
            return packed;
         }
      } catch (Throwable var9) {
         return packed;
      }
   }

   private static boolean enabled() {
      try {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         return cfg != null && cfg.enabled && cfg.dynamicLight;
      } catch (Throwable var1) {
         return false;
      }
   }

   private static int sanitize(int level) {
      return Mth.clamp(level, 0, 15);
   }
}

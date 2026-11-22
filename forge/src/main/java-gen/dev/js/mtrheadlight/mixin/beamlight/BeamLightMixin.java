package dev.js.mtrheadlight.mixin.beamlight;

import dev.js.mtrheadlight.BeamBlockLight;
import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.MtrHeadlightClient;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LevelRenderer.class})
public abstract class BeamLightMixin {
   private static boolean probed;

   @Inject(
      method = {"getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void mtrheadlight$beamBlockLight(BlockAndTintGetter view, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
      if (!probed) {
         probed = true;

         try {
            Files.writeString(Path.of(System.getProperty("java.io.tmpdir"), "lte-beamligh-probe.txt"), "first call view=" + view.getClass().getName() + " pos=" + String.valueOf(pos) + " thread=" + Thread.currentThread().getName() + " loader=" + String.valueOf(BeamBlockLight.class.getClassLoader()) + "\n");
         } catch (Throwable var11) {
         }
      }

      BeamBlockLight.noteCall();
      if (view instanceof RenderChunkRegion) {
         BeamBlockLight.noteAlive();
         if (BeamBlockLight.active()) {
            if (!(state.getBlock() instanceof RailBlock)) {
               HeadlightConfig cfg = MtrHeadlightClient.config();
               if (cfg != null && cfg.beamBlockLight) {
                  try {
                     int original = cir.getReturnValueI();
                     int block = original >> 4 & 15;
                     if (block >= 15) {
                        return;
                     }

                     long start = System.nanoTime();
                     int beam = BeamBlockLight.level(pos.getX(), pos.getY(), pos.getZ());
                     if (beam > block) {
                        cir.setReturnValue(original & -241 | beam << 4);
                        BeamBlockLight.countHit(System.nanoTime() - start);
                     }
                  } catch (Throwable var10) {
                  }

               }
            }
         }
      }
   }
}

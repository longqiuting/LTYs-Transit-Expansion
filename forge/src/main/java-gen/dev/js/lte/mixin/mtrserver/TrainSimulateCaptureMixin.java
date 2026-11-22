package dev.js.lte.mixin.mtrserver;

import dev.js.lte.collision.TrainTickCapture;
import java.util.List;
import java.util.Map;
import mtr.data.DataCache;
import mtr.data.Depot;
import mtr.data.TrainServer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {TrainServer.class},
   remap = false
)
public abstract class TrainSimulateCaptureMixin {
   @Inject(
      method = {"simulateTrain"},
      at = {@At("TAIL")},
      remap = false
   )
   private void mtrheadlight$capture(Level world, float ticks, Depot depot, DataCache dataCache, List<?> trainPositions, Map<?, ?> map1, Map<?, ?> map2, Map<?, ?> map3, CallbackInfoReturnable<Boolean> cir) {
      try {
         TrainTickCapture.capture(world, (TrainServer)(Object)this);
      } catch (Throwable var11) {
      }

   }
}

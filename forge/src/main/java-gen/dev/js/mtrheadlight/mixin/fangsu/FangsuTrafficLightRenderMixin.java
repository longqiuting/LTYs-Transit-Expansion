package dev.js.mtrheadlight.mixin.fangsu;

import dev.js.mtrheadlight.signal.FangsuSignalSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   targets = {"com.fangsu.blockEntities.BlockEntityTrafficLight"},
   remap = false
)
public abstract class FangsuTrafficLightRenderMixin {
   @Inject(
      method = {"whenRendering"},
      at = {@At("HEAD")},
      require = 0,
      remap = false
   )
   private void lte$syncFromMtrSignal(CallbackInfo ci) {
      try {
         FangsuSignalSync.onRender(this);
      } catch (Throwable var3) {
      }

   }
}

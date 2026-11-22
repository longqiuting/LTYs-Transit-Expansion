package dev.js.mtrheadlight.mixin.mtrserver;

import dev.js.mtrheadlight.server.MtrServerData;
import mtr.data.RailwayData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {RailwayData.class},
   remap = false
)
public abstract class RailwayDataCaptureMixin {
   @Inject(
      method = {"simulateTrains"},
      at = {@At("HEAD")},
      remap = false
   )
   private void mtrheadlight$captureRails(CallbackInfo ci) {
      try {
         MtrServerData.capture((RailwayData)(Object)this);
      } catch (Throwable var3) {
      }

   }
}

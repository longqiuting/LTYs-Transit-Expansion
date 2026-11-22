package dev.js.mtrheadlight.mixin.mtr;

import dev.js.mtrheadlight.mtr.BuiltInTrains;
import java.util.List;
import java.util.Map;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.data.TransportMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {TrainClientRegistry.class},
   remap = false
)
public abstract class TrainClientRegistryMixin {
   @Shadow
   @Final
   private static Map<String, TrainProperties> REGISTRY;
   @Shadow
   @Final
   private static Map<TransportMode, List<String>> KEY_ORDERS;

   @Inject(
      method = {"reset()V"},
      at = {@At("HEAD")},
      remap = false
   )
   private static void mtrheadlight$beforeReset(CallbackInfo ci) {
      try {
         BuiltInTrains.beforeReset();
      } catch (Throwable var2) {
         BuiltInTrains.fail(var2);
      }

   }

   @Inject(
      method = {"reset()V"},
      at = {@At("TAIL")},
      remap = false
   )
   private static void mtrheadlight$afterReset(CallbackInfo ci) {
      try {
         BuiltInTrains.afterReset(REGISTRY);
         BuiltInTrains.prune(REGISTRY, KEY_ORDERS);
      } catch (Throwable var2) {
         BuiltInTrains.fail(var2);
      }

   }

   @Inject(
      method = {"register(Ljava/lang/String;Lmtr/client/TrainProperties;)V"},
      at = {@At("TAIL")},
      remap = false
   )
   private static void mtrheadlight$afterRegister(String id, TrainProperties properties, CallbackInfo ci) {
      try {
         BuiltInTrains.onRegister(id, properties);
      } catch (Throwable var4) {
         BuiltInTrains.fail(var4);
      }

   }
}

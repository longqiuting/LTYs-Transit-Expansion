package dev.js.lte.mixin.rain;

import dev.js.lte.RainHush;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LevelRenderer.class})
public abstract class WorldRendererRainMixin {
   @Inject(
      method = {"renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void lte$skipRain(LightTexture lightmap, float delta, double camX, double camY, double camZ, CallbackInfo ci) {
      if (RainHush.shouldSuppress()) {
         ci.cancel();
      }

   }

   @Inject(
      method = {"tickRain(Lnet/minecraft/client/Camera;)V"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void lte$skipRainSplash(Camera camera, CallbackInfo ci) {
      if (RainHush.shouldSuppressSplash()) {
         ci.cancel();
      }

   }
}

package dev.js.lte.mixin.scatter;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.js.lte.MtrHeadlightClient;
import dev.js.lte.sss.ScatterPass;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {GameRenderer.class},
   priority = 1500
)
public abstract class GameRendererScatterMixin {
   @Inject(
      method = {"renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V"},
      at = {@At("RETURN")},
      require = 0
   )
   private void lte$scatterAfterWorld(float tickDelta, long limitTime, PoseStack matrices, CallbackInfo ci) {
      try {
         ScatterPass.renderDeferred();
      } catch (Throwable var7) {
         MtrHeadlightClient.LOGGER.warn("[lte] scatter: deferred hook failed", var7);
      }

   }
}

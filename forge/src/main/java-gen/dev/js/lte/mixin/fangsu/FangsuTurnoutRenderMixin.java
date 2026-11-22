package dev.js.lte.mixin.fangsu;

import dev.js.lte.FangsuTurnoutLight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(
   targets = {"com.fangsu.blocks.client.TurnoutBlockEntityRenderer"},
   remap = false
)
public abstract class FangsuTurnoutRenderMixin {
   @ModifyArgs(
      method = {"render"},
      at = @At(
   value = "INVOKE",
   target = "Lcom/fangsu/blockEntities/client/BaseBlockEntityRender;render(Lcom/fangsu/blockEntities/BaseObjBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
),
      require = 0
   )
   private void mtrheadlight$liftTurnoutLight(Args args) {
      try {
         int light = args.get(4);
         int lifted = FangsuTurnoutLight.lift(args.get(0), light);
         if (lifted != light) {
            args.set(4, lifted);
         }
      } catch (Throwable var4) {
      }

   }
}

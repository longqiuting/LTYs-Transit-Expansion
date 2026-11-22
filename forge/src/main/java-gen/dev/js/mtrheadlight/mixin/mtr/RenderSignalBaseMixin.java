package dev.js.mtrheadlight.mixin.mtr;

import dev.js.mtrheadlight.signal.SignalLinks;
import dev.js.mtrheadlight.signal.SignalSourceCapture;
import mtr.block.BlockNode;
import mtr.render.RenderSignalBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {RenderSignalBase.class},
   remap = false
)
public abstract class RenderSignalBaseMixin {
   private static final ThreadLocal<SignalSourceCapture> LTE_SOURCE = new ThreadLocal();

   @Inject(
      method = {"getNodePos"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private static void lte$useLinkedNode(BlockGetter view, BlockPos signalPos, Direction facing, CallbackInfoReturnable<BlockPos> cir) {
      try {
         BlockPos source = SignalLinks.resolveSource(view, signalPos);
         if (source != null) {
            Direction sourceFacing = lte$facingOf(view, source, facing);
            BlockPos node = SignalLinks.nodeFor(view, source);
            if (node == null) {
               node = lte$scanNode(view, source, sourceFacing);
            }

            LTE_SOURCE.set(new SignalSourceCapture(facing, sourceFacing, node));
            if (node != null) {
               cir.setReturnValue(node);
            }

            return;
         }

         LTE_SOURCE.remove();
         BlockPos linked = SignalLinks.nodeFor(view, signalPos);
         if (linked != null) {
            cir.setReturnValue(linked);
         }
      } catch (Throwable var7) {
         LTE_SOURCE.remove();
      }

   }

   @ModifyVariable(
      method = {"getOccupiedAspect"},
      at = @At("HEAD"),
      argsOnly = true,
      index = 2,
      remap = false
   )
   private float lte$syncAngle(float angle) {
      SignalSourceCapture capture = (SignalSourceCapture)LTE_SOURCE.get();
      if (capture != null && capture.node != null) {
         try {
            float base = capture.facing.toYRot() + 90.0F;
            float relative = lte$normalize(angle - base);
            float sourceAngle = capture.sourceFacing.toYRot() + 90.0F;
            return Math.abs(relative) > 90.0F ? sourceAngle + 180.0F : sourceAngle;
         } catch (Throwable var6) {
            return angle;
         }
      } else {
         return angle;
      }
   }

   private static Direction lte$facingOf(BlockGetter view, BlockPos pos, Direction fallback) {
      try {
         BlockState state = view.getBlockState(pos);
         if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
         }
      } catch (Throwable var4) {
      }

      return fallback;
   }

   private static BlockPos lte$scanNode(BlockGetter view, BlockPos pos, Direction facing) {
      int[] d = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4};

      for(int dx : d) {
         for(int dz : d) {
            for(int up = -5; up <= 0; ++up) {
               BlockPos p = pos.above(up).relative(facing.getOpposite(), dz).relative(facing, dx);
               if (BlockNode.class.isInstance(view.getBlockState(p).getBlock())) {
                  return p;
               }
            }
         }
      }

      return null;
   }

   private static float lte$normalize(float degrees) {
      float d = degrees % 360.0F;
      if (d > 180.0F) {
         d -= 360.0F;
      }

      if (d <= -180.0F) {
         d += 360.0F;
      }

      return d;
   }
}

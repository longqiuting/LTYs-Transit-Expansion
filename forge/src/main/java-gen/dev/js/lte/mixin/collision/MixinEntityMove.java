package dev.js.lte.mixin.collision;

import dev.js.lte.collision.CollisionSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Entity.class})
public abstract class MixinEntityMove {
   @Inject(
      method = {"move"},
      at = {@At("HEAD")}
   )
   private void mtrheadlight$beforeMove(MoverType type, Vec3 movement, CallbackInfo ci) {
      CollisionSystem.beforeMove((Entity)(Object)this);
   }

   @ModifyVariable(
      method = {"move"},
      at = @At("HEAD"),
      index = 2,
      argsOnly = true
   )
   private Vec3 mtrheadlight$clampMovement(Vec3 movement) {
      return CollisionSystem.clampMovement((Entity)(Object)this, movement);
   }

   @Inject(
      method = {"move"},
      at = {@At("TAIL")}
   )
   private void mtrheadlight$afterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
      CollisionSystem.afterMove((Entity)(Object)this);
   }
}

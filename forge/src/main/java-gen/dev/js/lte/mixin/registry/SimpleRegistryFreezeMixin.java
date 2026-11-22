package dev.js.lte.mixin.registry;

import dev.js.lte.server.RegistrySelfCheck;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MappedRegistry.class})
public abstract class SimpleRegistryFreezeMixin {
   @Inject(
      method = {"freeze"},
      at = {@At("HEAD")}
   )
   private void lte$checkLeakedBlocksBeforeFreeze(CallbackInfoReturnable<Registry<?>> cir) {
      RegistrySelfCheck.onFreeze((Registry)(Object)this);
   }
}

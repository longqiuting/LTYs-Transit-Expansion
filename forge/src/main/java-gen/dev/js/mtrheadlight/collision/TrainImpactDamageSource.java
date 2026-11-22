package dev.js.mtrheadlight.collision;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

public final class TrainImpactDamageSource extends DamageSource {
   public static final String KEY_SLOW = "death.attack.lte.trainLow";
   public static final String KEY_FAST = "death.attack.lte.trainHigh";
   private final boolean fast;

   public TrainImpactDamageSource(Holder<DamageType> type, boolean fast) {
      super(type);
      this.fast = fast;
   }

   public boolean high() {
      return this.fast;
   }

   public String key() {
      return this.fast ? "death.attack.lte.trainHigh" : "death.attack.lte.trainLow";
   }

   public Component getLocalizedDeathMessage(LivingEntity entity) {
      return Component.translatable(this.fast ? "death.attack.lte.trainHigh" : "death.attack.lte.trainLow", new Object[]{entity.getDisplayName()});
   }
}

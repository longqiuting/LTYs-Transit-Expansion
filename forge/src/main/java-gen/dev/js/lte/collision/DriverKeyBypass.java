package dev.js.lte.collision;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class DriverKeyBypass {
   public static final ResourceLocation DRIVER_KEY_ID = new ResourceLocation("mtr", "driver_key");

   private DriverKeyBypass() {
   }

   public static boolean active(Entity entity) {
      if (entity instanceof Player player) {
         return holds(player);
      } else {
         return false;
      }
   }

   public static boolean holds(Player player) {
      if (player == null) {
         return false;
      } else {
         try {
            return isDriverKey(player.getMainHandItem()) || isDriverKey(player.getOffhandItem());
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   private static boolean isDriverKey(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         try {
            return DRIVER_KEY_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
         } catch (Throwable var2) {
            return false;
         }
      } else {
         return false;
      }
   }
}

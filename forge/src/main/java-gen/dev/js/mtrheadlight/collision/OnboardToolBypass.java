package dev.js.mtrheadlight.collision;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class OnboardToolBypass {
   public static final ResourceLocation ONBOARD_TOOL_ID = new ResourceLocation("mtryum", "onboard_tool");

   private OnboardToolBypass() {
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
            return isOnboardTool(player.getMainHandItem()) || isOnboardTool(player.getOffhandItem());
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   private static boolean isOnboardTool(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         try {
            return ONBOARD_TOOL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
         } catch (Throwable var2) {
            return false;
         }
      } else {
         return false;
      }
   }
}

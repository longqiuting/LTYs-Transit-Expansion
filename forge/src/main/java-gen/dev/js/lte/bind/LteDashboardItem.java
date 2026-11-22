package dev.js.lte.bind;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class LteDashboardItem extends Item {
   public LteDashboardItem(Item.Properties settings) {
      super(settings);
   }

   public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
      ItemStack stack = user.getItemInHand(hand);
      if (!world.isClientSide) {
         return InteractionResultHolder.pass(stack);
      } else {
         open();
         return InteractionResultHolder.success(stack);
      }
   }

   public static boolean open() {
      try {
         Class.forName("dev.js.lte.MtrHeadlightClient").getMethod("openConfigScreen").invoke((Object)null);
         return true;
      } catch (Throwable var1) {
         return false;
      }
   }

   public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
      tooltip.add(Component.translatable("tooltip.lte.dashboard.hint").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("tooltip.lte.dashboard.client_only").withStyle(ChatFormatting.DARK_GRAY));
   }
}

package dev.js.lte.signal;

import java.util.List;
import mtr.block.BlockNode;
import mtr.block.BlockSignalLightBase;
import mtr.block.BlockSignalSemaphoreBase;
import mtr.block.IBlock;
import mtr.data.Rail;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

public final class SignalLinkItem extends Item {
   private static final String TAG_NODE = "lte_node";
   private static final String TAG_LAMP = "lte_lamp";
   private static final String TAG_DIM = "lte_dim";
   private static final int NODE_SEARCH = 3;

   public SignalLinkItem(Item.Properties settings) {
      super(settings);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level world = context.getLevel();
      if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         Player clickedState = context.getPlayer();
         ServerPlayer var10000;
         if (clickedState instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer)clickedState;
            var10000 = p;
         } else {
            var10000 = null;
         }

         ServerPlayer player = var10000;
         if (player == null) {
            return InteractionResult.PASS;
         } else {
            BlockPos clicked = context.getClickedPos();
            BlockState clickedStateLte = world.getBlockState(clicked);
            if (isLamp(clickedStateLte)) {
               if (!player.isShiftKeyDown() && FangsuSignalCompat.isFangsuTrafficLight(clickedStateLte)) {
                  switchFangsuMode(player, world, clicked);
               }

               return this.onSignalClicked(world, player, context.getItemInHand(), clicked);
            } else {
               BlockPos node = findNode(world, clicked, context);
               if (node != null) {
                  return this.onNodeClicked(world, player, context.getItemInHand(), node);
               } else {
                  say(player, "message.lte.signal_link.not_linkable", ChatFormatting.RED);
                  return InteractionResult.PASS;
               }
            }
         }
      }
   }

   private InteractionResult onNodeClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos node) {
      if (!SignalLinkNet.hasRail(world, node)) {
         say(player, "message.lte.signal_link.no_rail", ChatFormatting.RED);
         return InteractionResult.SUCCESS;
      } else {
         CompoundTag nbt = stack.getOrCreateTag();
         if (nbt.contains("lte_lamp")) {
            return this.finishLampToNode(world, player, stack, nbt, node);
         } else {
            nbt.putLong("lte_node", node.asLong());
            nbt.putString("lte_dim", SignalLinkNet.dimensionOf(world));
            say(player, "message.lte.signal_link.node_set", ChatFormatting.AQUA, posText(node));
            return InteractionResult.SUCCESS;
         }
      }
   }

   private InteractionResult finishLampToNode(Level world, ServerPlayer player, ItemStack stack, CompoundTag nbt, BlockPos node) {
      String dim = SignalLinkNet.dimensionOf(world);
      if (!dim.equals(nbt.getString("lte_dim"))) {
         say(player, "message.lte.signal_link.wrong_dim", ChatFormatting.RED);
         return InteractionResult.SUCCESS;
      } else {
         BlockPos lamp = BlockPos.of(nbt.getLong("lte_lamp"));
         BlockState lampState = world.getBlockState(lamp);
         if (!isLamp(lampState)) {
            say(player, "message.lte.signal_link.lamp_gone", ChatFormatting.RED);
            clearPending(nbt);
            return InteractionResult.SUCCESS;
         } else {
            SignalLinkStore store = SignalLinkStore.get((ServerLevel)world);
            store.put(lamp, node);
            SignalLinkNet.broadcastPut(world.getServer(), dim, lamp, node);
            SignalLinkNet.broadcastLampRemove(world.getServer(), dim, lamp);
            clearPending(nbt);
            warnFacing(player, world, lampState, node);
            say(player, "message.lte.signal_link.linked", ChatFormatting.GREEN, posText(node));
            return InteractionResult.SUCCESS;
         }
      }
   }

   private InteractionResult onSignalClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos signal) {
      CompoundTag nbt = stack.getOrCreateTag();
      SignalLinkStore store = SignalLinkStore.get((ServerLevel)world);
      String dim = SignalLinkNet.dimensionOf(world);
      if (player.isShiftKeyDown()) {
         if (store.remove(signal)) {
            SignalLinkNet.broadcastRemove(world.getServer(), dim, signal);
            SignalLinkNet.broadcastLampRemove(world.getServer(), dim, signal);
            say(player, "message.lte.signal_link.unlinked", ChatFormatting.YELLOW);
         } else {
            say(player, "message.lte.signal_link.not_linked", ChatFormatting.GRAY);
         }

         clearPending(nbt);
         return InteractionResult.SUCCESS;
      } else if (nbt.contains("lte_lamp")) {
         BlockPos source = BlockPos.of(nbt.getLong("lte_lamp"));
         if (source.equals(signal)) {
            say(player, "message.lte.signal_link.lamp_set", ChatFormatting.AQUA, posText(signal));
            return InteractionResult.SUCCESS;
         } else if (!dim.equals(nbt.getString("lte_dim"))) {
            say(player, "message.lte.signal_link.wrong_dim", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            BlockState sourceState = world.getBlockState(source);
            if (!isLamp(sourceState)) {
               say(player, "message.lte.signal_link.lamp_gone", ChatFormatting.RED);
               clearPending(nbt);
               return InteractionResult.SUCCESS;
            } else if (!store.putLamp(signal, source)) {
               say(player, "message.lte.signal_link.lamp_cycle", ChatFormatting.RED);
               return InteractionResult.SUCCESS;
            } else {
               SignalLinkNet.broadcastLampPut(world.getServer(), dim, signal, source);
               SignalLinkNet.broadcastRemove(world.getServer(), dim, signal);
               clearPending(nbt);
               say(player, "message.lte.signal_link.lamp_linked", ChatFormatting.GREEN, posText(source));
               return InteractionResult.SUCCESS;
            }
         }
      } else if (nbt.contains("lte_node")) {
         if (!dim.equals(nbt.getString("lte_dim"))) {
            say(player, "message.lte.signal_link.wrong_dim", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            BlockPos node = BlockPos.of(nbt.getLong("lte_node"));
            if (BlockNode.class.isInstance(world.getBlockState(node).getBlock()) && SignalLinkNet.hasRail(world, node)) {
               store.put(signal, node);
               SignalLinkNet.broadcastPut(world.getServer(), dim, signal, node);
               SignalLinkNet.broadcastLampRemove(world.getServer(), dim, signal);
               clearPending(nbt);
               warnFacing(player, world, world.getBlockState(signal), node);
               say(player, "message.lte.signal_link.linked", ChatFormatting.GREEN, posText(node));
               return InteractionResult.SUCCESS;
            } else {
               say(player, "message.lte.signal_link.node_gone", ChatFormatting.RED);
               nbt.remove("lte_node");
               nbt.remove("lte_dim");
               return InteractionResult.SUCCESS;
            }
         }
      } else {
         nbt.putLong("lte_lamp", signal.asLong());
         nbt.putString("lte_dim", dim);
         say(player, "message.lte.signal_link.lamp_set", ChatFormatting.AQUA, posText(signal));
         return InteractionResult.SUCCESS;
      }
   }

   private static BlockPos findNode(Level world, BlockPos clicked, UseOnContext context) {
      return findNode(world, clicked, context.getClickLocation());
   }

   private static BlockPos findNode(Level world, BlockPos clicked, Vec3 hitPos) {
      if (BlockNode.class.isInstance(world.getBlockState(clicked).getBlock())) {
         return clicked;
      } else {
         double hitX = hitPos.x;
         double hitY = hitPos.y;
         double hitZ = hitPos.z;
         BlockPos best = null;
         double bestDist = Double.MAX_VALUE;
         BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

         for(int dx = -3; dx <= 3; ++dx) {
            for(int dy = -3; dy <= 3; ++dy) {
               for(int dz = -3; dz <= 3; ++dz) {
                  cursor.set(clicked.getX() + dx, clicked.getY() + dy, clicked.getZ() + dz);
                  if (BlockNode.class.isInstance(world.getBlockState(cursor).getBlock()) && SignalLinkNet.hasRail(world, cursor)) {
                     double d = cursor.distToLowCornerSqr(hitX, hitY, hitZ);
                     if (d < bestDist) {
                        bestDist = d;
                        best = cursor.immutable();
                     }
                  }
               }
            }
         }

         return best;
      }
   }

   private static boolean facingMismatch(Level world, BlockState state, BlockPos node) {
      if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
         return false;
      } else {
         List<Rail> rails = SignalLinkNet.railsAt(world, node);
         if (rails.isEmpty()) {
            return false;
         } else {
            return !matches(rails, (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING));
         }
      }
   }

   private static void warnFacing(ServerPlayer player, Level world, BlockState lampState, BlockPos node) {
      if (facingMismatch(world, lampState, node)) {
         say(player, "message.lte.signal_link.facing_hint", ChatFormatting.YELLOW);
      }

   }

   private static boolean matches(List<Rail> rails, Direction dir) {
      float angle = dir.toYRot() + 90.0F;

      for(Rail rail : rails) {
         if (rail.facingStart.similarFacing(angle)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isUnbindTarget(Level world, BlockPos pos) {
      try {
         return targetOf(world, pos) != null;
      } catch (Throwable var3) {
         return false;
      }
   }

   public static boolean unlinkAt(Level world, ServerPlayer player, BlockPos clicked) {
      if (world instanceof ServerLevel server) {
         BlockPos target = targetOf(world, clicked);
         if (target == null) {
            return false;
         } else {
            if (unlink(server, target)) {
               say(player, "message.lte.link.unlinked", ChatFormatting.GREEN);
            } else {
               say(player, "message.lte.link.none", ChatFormatting.YELLOW);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static void unlinkBroken(ServerLevel world, BlockPos pos, BlockState state) {
      unlink(world, pos);
      BlockPos otherHalf = otherHalfOf(pos, state);
      if (otherHalf != null) {
         unlink(world, otherHalf);
      }

   }

   private static BlockPos otherHalfOf(BlockPos pos, BlockState state) {
      if (state == null) {
         return null;
      } else {
         try {
            if (!state.hasProperty(IBlock.HALF)) {
               return null;
            } else {
               return IBlock.getStatePropertySafe(state, IBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            }
         } catch (Throwable var3) {
            return null;
         }
      }
   }

   private static BlockPos targetOf(Level world, BlockPos clicked) {
      try {
         return isLamp(world.getBlockState(clicked)) ? clicked : findNode(world, clicked, Vec3.atCenterOf(clicked));
      } catch (Throwable var3) {
         return null;
      }
   }

   private static boolean unlink(ServerLevel world, BlockPos pos) {
      SignalLinkStore.Removed removed = SignalLinkStore.get(world).removeInvolving(pos);
      if (!removed.any()) {
         return false;
      } else {
         String dim = SignalLinkNet.dimensionOf(world);

         for(BlockPos signal : removed.signals) {
            SignalLinkNet.broadcastRemove(world.getServer(), dim, signal);
         }

         for(BlockPos lamp : removed.lamps) {
            SignalLinkNet.broadcastLampRemove(world.getServer(), dim, lamp);
         }

         return true;
      }
   }

   private static void clearPending(CompoundTag nbt) {
      nbt.remove("lte_node");
      nbt.remove("lte_lamp");
      nbt.remove("lte_dim");
   }

   private static boolean isSignal(BlockState state) {
      return BlockSignalLightBase.class.isInstance(state.getBlock()) || BlockSignalSemaphoreBase.class.isInstance(state.getBlock());
   }

   private static boolean isLamp(BlockState state) {
      return isSignal(state) || FangsuSignalCompat.isFangsuTrafficLight(state);
   }

   private static void switchFangsuMode(ServerPlayer player, Level world, BlockPos lamp) {
      FangsuTrafficLightMode.Result result;
      try {
         result = FangsuTrafficLightMode.toMtr(world, lamp);
      } catch (Throwable var5) {
         say(player, "message.lte.signal_link.fangsu_mode_manual", ChatFormatting.YELLOW);
         return;
      }

      switch (result) {
         case OK:
            say(player, "message.lte.signal_link.fangsu_mode_mtr", ChatFormatting.GREEN);
         case ALREADY:
            break;
         case FAIL:
            say(player, "message.lte.signal_link.fangsu_mode_manual", ChatFormatting.YELLOW);
            break;
         default:
            say(player, "message.lte.signal_link.fangsu_mode_manual", ChatFormatting.YELLOW);
      }

   }

   public static BlockPos selectedNode(ItemStack stack) {
      try {
         if (stack != null && !stack.isEmpty()) {
            CompoundTag nbt = stack.getTag();
            return nbt != null && nbt.contains("lte_node") ? BlockPos.of(nbt.getLong("lte_node")) : null;
         } else {
            return null;
         }
      } catch (Throwable var2) {
         return null;
      }
   }

   private static Component posText(BlockPos pos) {
      return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
   }

   private static void say(ServerPlayer player, String key, ChatFormatting color, Object... args) {
      player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
   }

   public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
      CompoundTag nbt = stack.getTag();
      if (nbt != null && nbt.contains("lte_lamp")) {
         tooltip.add(Component.translatable("tooltip.lte.signal_link.selected_lamp", new Object[]{posText(BlockPos.of(nbt.getLong("lte_lamp")))}).withStyle(ChatFormatting.AQUA));
      } else if (nbt != null && nbt.contains("lte_node")) {
         tooltip.add(Component.translatable("tooltip.lte.signal_link.selected", new Object[]{posText(BlockPos.of(nbt.getLong("lte_node")))}).withStyle(ChatFormatting.AQUA));
      } else {
         tooltip.add(Component.translatable("tooltip.lte.signal_link.hint").withStyle(ChatFormatting.GRAY));
      }

      tooltip.add(Component.translatable("tooltip.lte.link.right_click").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("tooltip.lte.link.left_click").withStyle(ChatFormatting.DARK_GRAY));
      tooltip.add(Component.translatable("tooltip.lte.signal_link.node_marks").withStyle(ChatFormatting.DARK_GRAY));
   }
}

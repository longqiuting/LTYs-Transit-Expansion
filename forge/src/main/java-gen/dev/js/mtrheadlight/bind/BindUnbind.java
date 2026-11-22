package dev.js.mtrheadlight.bind;

import dev.js.mtrheadlight.LteLog;
import dev.js.mtrheadlight.signal.SignalLinkItem;
import mtr.block.IBlock;
import dev.js.mtrheadlight.forge.platform.AttackBlockCallback;
import dev.js.mtrheadlight.forge.platform.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public final class BindUnbind {
   private BindUnbind() {
   }

   public static void register() {
      try {
         AttackBlockCallback.EVENT.register((AttackBlockCallback)(player, world, hand, pos, direction) -> {
            if (player != null && world != null && pos != null && hand == InteractionHand.MAIN_HAND) {
               if (player.isSpectator()) {
                  return InteractionResult.PASS;
               } else {
                  Item item = player.getItemInHand(hand).getItem();
                  if (!owns(item, world, pos)) {
                     return InteractionResult.PASS;
                  } else {
                     if (!world.isClientSide && player instanceof ServerPlayer) {
                        ServerPlayer serverPlayer = (ServerPlayer)player;
                        onClick(serverPlayer, pos);
                     }

                     return InteractionResult.SUCCESS;
                  }
               }
            } else {
               return InteractionResult.PASS;
            }
         });
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] left-click unbind unavailable", var2);
      }

      try {
         PlayerBlockBreakEvents.AFTER.register((PlayerBlockBreakEvents.After)(world, player, pos, state, entity) -> {
            if (world instanceof ServerLevel server) {
               if (pos != null && state != null) {
                  try {
                     onBroken(server, pos, state);
                  } catch (Throwable var7) {
                     LteLog.LOGGER.debug("[lte] auto-unbind on break failed at {}", pos, var7);
                  }

                  return;
               }
            }

         });
      } catch (Throwable var1) {
         LteLog.LOGGER.warn("[lte] auto-unbind on break unavailable", var1);
      }

   }

   private static boolean owns(Item item, Level world, BlockPos pos) {
      if (item instanceof SignalLinkItem) {
         return SignalLinkItem.isUnbindTarget(world, pos);
      } else if (item instanceof ProjectorConnectorItem) {
         return ProjectorConnectorItem.isUnbindTarget(world, pos);
      } else {
         return item instanceof PidsConnectorItem ? PidsConnectorItem.isUnbindTarget(world, pos) : false;
      }
   }

   private static void onClick(ServerPlayer player, BlockPos pos) {
      Level world = player.level();
      if (!world.isClientSide) {
         Item item = player.getMainHandItem().getItem();
         if (item instanceof SignalLinkItem) {
            SignalLinkItem.unlinkAt(world, player, pos);
         } else if (item instanceof ProjectorConnectorItem) {
            ProjectorConnectorItem.unlinkAt(world, player, pos);
         } else if (item instanceof PidsConnectorItem) {
            PidsConnectorItem.unlinkAt(world, player, pos);
         }

      }
   }

   private static void onBroken(ServerLevel world, BlockPos pos, BlockState state) {
      SignalLinkItem.unlinkBroken(world, pos, state);
      ProjectorConnectorItem.unlinkBroken(world, pos);
      PidsConnectorItem.unlinkBroken(world, pos, isLowerHalf(state));
   }

   private static boolean isLowerHalf(BlockState state) {
      try {
         return state.hasProperty(IBlock.HALF) && IBlock.getStatePropertySafe(state, IBlock.HALF) == DoubleBlockHalf.LOWER;
      } catch (Throwable var2) {
         return false;
      }
   }
}

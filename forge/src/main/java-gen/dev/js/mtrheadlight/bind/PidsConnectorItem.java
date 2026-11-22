package dev.js.mtrheadlight.bind;

import dev.js.mtrheadlight.LteLog;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mtr.data.Station;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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

public final class PidsConnectorItem extends Item {
   private static final String TAG_PIDS = "lte_pids";
   private static final String TAG_NODE = "lte_node";
   private static final String TAG_DIM = "lte_dim";

   public PidsConnectorItem(Item.Properties settings) {
      super(settings);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level world = context.getLevel();
      if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         Player pids = context.getPlayer();
         ServerPlayer var10000;
         if (pids instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer)pids;
            var10000 = p;
         } else {
            var10000 = null;
         }

         ServerPlayer player = var10000;
         if (player == null) {
            return InteractionResult.PASS;
         } else {
            BlockPos clicked = context.getClickedPos();
            List<MtrTiles.PidsTile> pidsLte = BindTargets.pidsAt(world, clicked);
            if (!pidsLte.isEmpty()) {
               return this.onPidsClicked(world, player, context.getItemInHand(), BindTargets.dataPos(world, clicked), pidsLte);
            } else {
               BlockPos node = BindTargets.findNode(world, clicked, context.getClickLocation());
               if (node != null) {
                  return this.onNodeClicked(world, player, context.getItemInHand(), node);
               } else if (LteMtrLookup.platformIdAtExact(world, clicked) != 0L) {
                  return this.onNodeClicked(world, player, context.getItemInHand(), clicked);
               } else {
                  say(player, "message.lte.pids.not_linkable", ChatFormatting.RED);
                  return InteractionResult.PASS;
               }
            }
         }
      }
   }

   private InteractionResult onPidsClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos pidsPos, List<MtrTiles.PidsTile> pids) {
      CompoundTag nbt = stack.getOrCreateTag();
      String dim = world.dimension().location().toString();
      if (!player.isShiftKeyDown()) {
         if (nbt.contains("lte_node")) {
            if (!dim.equals(nbt.getString("lte_dim"))) {
               say(player, "message.lte.pids.wrong_dim", ChatFormatting.RED);
               return InteractionResult.SUCCESS;
            } else {
               BlockPos node = BlockPos.of(nbt.getLong("lte_node"));
               clearPending(nbt);
               return this.bind(world, player, pidsPos, pids, node);
            }
         } else {
            nbt.putLong("lte_pids", pidsPos.asLong());
            nbt.putString("lte_dim", dim);
            say(player, "message.lte.pids.pids_set", ChatFormatting.AQUA, posText(pidsPos));
            return InteractionResult.SUCCESS;
         }
      } else {
         boolean removed = PidsLinkStore.get((ServerLevel)world).remove(pidsPos);
         boolean cleared = clearPids(pids, world);
         if (!removed && !cleared) {
            say(player, "message.lte.pids.not_linked", ChatFormatting.GRAY);
         } else {
            say(player, "message.lte.pids.unlinked", ChatFormatting.YELLOW);
         }

         clearPending(nbt);
         return InteractionResult.SUCCESS;
      }
   }

   private InteractionResult onNodeClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos node) {
      CompoundTag nbt = stack.getOrCreateTag();
      String dim = world.dimension().location().toString();
      if (nbt.contains("lte_pids")) {
         if (!dim.equals(nbt.getString("lte_dim"))) {
            say(player, "message.lte.pids.wrong_dim", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            BlockPos pidsPos = BlockPos.of(nbt.getLong("lte_pids"));
            clearPending(nbt);
            List<MtrTiles.PidsTile> pids = BindTargets.pidsAt(world, pidsPos);
            if (pids.isEmpty()) {
               say(player, "message.lte.pids.pids_gone", ChatFormatting.RED);
               return InteractionResult.SUCCESS;
            } else {
               return this.bind(world, player, pidsPos, pids, node);
            }
         }
      } else {
         nbt.putLong("lte_node", node.asLong());
         nbt.putString("lte_dim", dim);
         say(player, "message.lte.pids.node_set", ChatFormatting.AQUA, posText(node));
         return InteractionResult.SUCCESS;
      }
   }

   private InteractionResult bind(Level world, ServerPlayer player, BlockPos pidsPos, List<MtrTiles.PidsTile> pids, BlockPos node) {
      long platformId = LteMtrLookup.platformIdOnRail(world, node, "pids");
      if (platformId == 0L) {
         LteLog.LOGGER.info("[lte] bind: pids pos={} node={} -> platforms=[]", pidsPos, node);
         say(player, "message.lte.pids.no_platform", ChatFormatting.RED);
         return InteractionResult.SUCCESS;
      } else {
         LteLog.LOGGER.info("[lte] bind: pids pos={} -> platforms={}", pidsPos, Collections.singletonList(platformId));
         String platformName = LteMtrLookup.platformName(world, platformId);
         Station station = LteMtrLookup.stationOfPlatform(world, platformId);
         ServerLevel var10000;
         if (world instanceof ServerLevel) {
            ServerLevel sw = (ServerLevel)world;
            var10000 = sw;
         } else {
            var10000 = null;
         }

         ServerLevel serverWorld = var10000;
         int written = 0;
         StringBuilder writtenAt = new StringBuilder();

         for(MtrTiles.PidsTile entry : pids) {
            if (MtrTiles.pidsBindSingle(entry.tile(), platformId)) {
               ++written;
               if (writtenAt.length() > 0) {
                  writtenAt.append(", ");
               }

               writtenAt.append(entry.pos());
               if (serverWorld != null) {
                  MtrTiles.pushToClients(serverWorld, entry.tile(), entry.pos());
                  MtrTiles.markChunkSaved(serverWorld, entry.pos());
               }
            }
         }

         if (written == 0) {
            say(player, "message.lte.pids.pids_gone", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            PidsLinkStore.get((ServerLevel)world).put(pidsPos, node, platformId, platformName);
            Set<Long> verify = new TreeSet();

            for(MtrTiles.PidsTile entry : pids) {
               verify.addAll(MtrTiles.pidsPlatformIds(entry.tile()));
            }

            LteLog.LOGGER.info("[lte] bind: pids verify platforms={} tiles=[{}]", verify, writtenAt);
            boolean persisted = verify.contains(platformId);
            String stationName = station != null && station.name != null && !station.name.isEmpty() ? station.name : "-";
            if (persisted) {
               say(player, "message.lte.pids.linked", ChatFormatting.GREEN, platformName, stationName);
            } else {
               say(player, "message.lte.pids.verify_failed", ChatFormatting.RED, MtrTiles.join(verify), MtrTiles.describe(((MtrTiles.PidsTile)pids.get(0)).tile()));
            }

            Station atPids = LteMtrLookup.stationAt(world, pidsPos);
            if (atPids == null) {
               say(player, "message.lte.pids.outside_station", ChatFormatting.YELLOW);
            } else if (station != null && atPids.id != station.id) {
               say(player, "message.lte.pids.platform_mismatch", ChatFormatting.YELLOW, platformName, stationName);
            }

            return InteractionResult.SUCCESS;
         }
      }
   }

   private static boolean clearPids(List<MtrTiles.PidsTile> pids, Level world) {
      boolean cleared = false;
      ServerLevel var10000;
      if (world instanceof ServerLevel sw) {
         var10000 = sw;
      } else {
         var10000 = null;
      }

      ServerLevel serverWorld = var10000;

      for(MtrTiles.PidsTile entry : pids) {
         if (!MtrTiles.pidsPlatformIds(entry.tile()).isEmpty() && MtrTiles.pidsSetPlatforms(entry.tile(), Collections.emptyList())) {
            cleared = true;
            if (serverWorld != null) {
               MtrTiles.pushToClients(serverWorld, entry.tile(), entry.pos());
               MtrTiles.markChunkSaved(serverWorld, entry.pos());
            }
         }
      }

      return cleared;
   }

   public static boolean isUnbindTarget(Level world, BlockPos pos) {
      try {
         return !BindTargets.pidsAt(world, pos).isEmpty();
      } catch (Throwable var3) {
         return false;
      }
   }

   public static boolean unlinkAt(Level world, ServerPlayer player, BlockPos clicked) {
      if (!(world instanceof ServerLevel server)) {
         return false;
      } else {
         List<MtrTiles.PidsTile> pids = BindTargets.pidsAt(world, clicked);
         if (pids.isEmpty()) {
            return false;
         } else {
            boolean removed = !PidsLinkStore.get(server).removeInvolving(BindTargets.dataPos(world, clicked)).isEmpty();
            boolean cleared = clearPids(pids, world);
            if (!removed && !cleared) {
               say(player, "message.lte.link.none", ChatFormatting.YELLOW);
            } else {
               say(player, "message.lte.link.unlinked", ChatFormatting.GREEN);
            }

            return true;
         }
      }
   }

   public static void unlinkBroken(ServerLevel world, BlockPos pos, boolean brokenLowerHalf) {
      PidsLinkStore store = PidsLinkStore.get(world);
      Set<BlockPos> affected = new LinkedHashSet();
      affected.addAll(store.removeInvolving(pos).keySet());
      if (brokenLowerHalf) {
         affected.addAll(store.removeInvolving(pos.above()).keySet());
      }

      for(BlockPos pidsPos : affected) {
         List<MtrTiles.PidsTile> pids = BindTargets.pidsAt(world, pidsPos);
         if (!pids.isEmpty()) {
            clearPids(pids, world);
         }
      }

   }

   private static void clearPending(CompoundTag nbt) {
      nbt.remove("lte_pids");
      nbt.remove("lte_node");
      nbt.remove("lte_dim");
   }

   private static Component posText(BlockPos pos) {
      return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
   }

   private static void say(ServerPlayer player, String key, ChatFormatting color, Object... args) {
      player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
   }

   public void appendHoverText(ItemStack stack, Level world, List<Component> tooltip, TooltipFlag context) {
      CompoundTag nbt = stack.getTag();
      if (nbt != null && nbt.contains("lte_pids")) {
         tooltip.add(Component.translatable("tooltip.lte.pids.selected_pids", new Object[]{posText(BlockPos.of(nbt.getLong("lte_pids")))}).withStyle(ChatFormatting.AQUA));
      } else if (nbt != null && nbt.contains("lte_node")) {
         tooltip.add(Component.translatable("tooltip.lte.pids.selected_node", new Object[]{posText(BlockPos.of(nbt.getLong("lte_node")))}).withStyle(ChatFormatting.AQUA));
      } else {
         tooltip.add(Component.translatable("tooltip.lte.pids.hint").withStyle(ChatFormatting.GRAY));
      }

      tooltip.add(Component.translatable("tooltip.lte.link.right_click").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("tooltip.lte.link.left_click").withStyle(ChatFormatting.DARK_GRAY));
   }
}

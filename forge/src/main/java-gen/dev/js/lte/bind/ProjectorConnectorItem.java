package dev.js.lte.bind;

import dev.js.lte.LteLog;
import java.util.ArrayList;
import java.util.Collections;
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

public final class ProjectorConnectorItem extends Item {
   private static final String TAG_PROJECTOR = "lte_proj";
   private static final String TAG_STATION = "lte_station_id";
   private static final String TAG_STATION_POS = "lte_station_pos";
   private static final String TAG_STATION_NAME = "lte_station_name";
   private static final String TAG_PLATFORM = "lte_platform_id";
   private static final String TAG_DIM = "lte_dim";

   public ProjectorConnectorItem(Item.Properties settings) {
      super(settings);
   }

   public InteractionResult useOn(UseOnContext context) {
      Level world = context.getLevel();
      if (world.isClientSide) {
         return InteractionResult.SUCCESS;
      } else {
         Player kind = context.getPlayer();
         ServerPlayer var10000;
         if (kind instanceof ServerPlayer) {
            ServerPlayer p = (ServerPlayer)kind;
            var10000 = p;
         } else {
            var10000 = null;
         }

         ServerPlayer player = var10000;
         if (player == null) {
            return InteractionResult.PASS;
         } else {
            BlockPos clicked = context.getClickedPos();
            String kindLte = BindTargets.kind(world, clicked);
            Station stationHere = LteMtrLookup.stationAt(world, clicked);
            long platformHere = LteMtrLookup.platformIdOnRail(world, clicked, "projector");
            LteLog.LOGGER.info("[lte] projector click: pos={} kindLte={} stationAt={} platformId={} tile={}", new Object[]{clicked, kindLte, stationHere == null ? "null" : stationHere.id + "/" + stationHere.name, platformHere, MtrTiles.describe(MtrTiles.tileAt(world, clicked))});
            return "projector".equals(kindLte) ? this.onProjectorClicked(world, player, context.getItemInHand(), clicked) : this.onStationClicked(world, player, context.getItemInHand(), clicked, platformHere);
         }
      }
   }

   private InteractionResult onProjectorClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos projector) {
      CompoundTag nbt = stack.getOrCreateTag();
      String dim = world.dimension().location().toString();
      LteLog.LOGGER.info("[lte] projector click: branch=projector pos={} sneaking={} hasStation={} hasPlatform={}", new Object[]{projector, player.isShiftKeyDown(), nbt.contains("lte_station_id"), nbt.contains("lte_platform_id")});
      if (!player.isShiftKeyDown()) {
         if (!nbt.contains("lte_station_id") && !nbt.contains("lte_platform_id")) {
            nbt.putLong("lte_proj", projector.asLong());
            nbt.putString("lte_dim", dim);
            say(player, "message.lte.projector.projector_set", ChatFormatting.AQUA, posText(projector));
            return InteractionResult.SUCCESS;
         } else if (!dim.equals(nbt.getString("lte_dim"))) {
            say(player, "message.lte.projector.wrong_dim", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            ProjectorConnectorItem.Target target = resolve(world, nbt);
            clearPending(nbt);
            if (target == null) {
               say(player, "message.lte.projector.no_station", ChatFormatting.RED);
               return InteractionResult.SUCCESS;
            } else {
               return this.bind(world, player, projector, target);
            }
         }
      } else {
         boolean removed = ProjectorLinkStore.get((ServerLevel)world).remove(projector);
         boolean cleared = clearProjector(world, projector);
         if (!removed && !cleared) {
            say(player, "message.lte.projector.not_linked", ChatFormatting.GRAY);
         } else {
            say(player, "message.lte.projector.unlinked", ChatFormatting.YELLOW);
         }

         clearPending(nbt);
         return InteractionResult.SUCCESS;
      }
   }

   private InteractionResult onStationClicked(Level world, ServerPlayer player, ItemStack stack, BlockPos clicked, long platformId) {
      if (platformId == 0L) {
         say(player, "message.lte.projector.no_station_here", ChatFormatting.RED);
         return InteractionResult.PASS;
      } else {
         Station station = pickStation(world, clicked, platformId);
         String name = nameOf(world, station, platformId);
         CompoundTag nbt = stack.getOrCreateTag();
         LteLog.LOGGER.info("[lte] projector click: branch=station pos={} platformId={} station={} name={} pendingProjector={}", new Object[]{clicked, platformId, station == null ? "null" : station.id + "/" + station.name, name, nbt.contains("lte_proj")});
         if (nbt.contains("lte_proj")) {
            String dim = world.dimension().location().toString();
            if (!dim.equals(nbt.getString("lte_dim"))) {
               say(player, "message.lte.projector.wrong_dim", ChatFormatting.RED);
               return InteractionResult.SUCCESS;
            } else {
               BlockPos projector = BlockPos.of(nbt.getLong("lte_proj"));
               clearPending(nbt);
               if (!BindTargets.isProjector(world, projector)) {
                  say(player, "message.lte.projector.projector_gone", ChatFormatting.RED);
                  return InteractionResult.SUCCESS;
               } else {
                  return this.bind(world, player, projector, new ProjectorConnectorItem.Target(station, platformId, name));
               }
            }
         } else {
            nbt.putLong("lte_platform_id", platformId);
            if (station != null) {
               nbt.putLong("lte_station_id", station.id);
            }

            nbt.putLong("lte_station_pos", clicked.asLong());
            nbt.putString("lte_station_name", name);
            nbt.putString("lte_dim", world.dimension().location().toString());
            say(player, "message.lte.projector.station_set", ChatFormatting.AQUA, name);
            return InteractionResult.SUCCESS;
         }
      }
   }

   private static Station pickStation(Level world, BlockPos clicked, long platformId) {
      Station byPlatform = LteMtrLookup.stationOfPlatform(world, platformId);
      return byPlatform != null ? byPlatform : LteMtrLookup.stationAt(world, clicked);
   }

   private static String nameOf(Level world, Station station, long platformId) {
      if (station != null && station.name != null && !station.name.isEmpty()) {
         return station.name;
      } else {
         String platformName = LteMtrLookup.platformName(world, platformId);
         return platformName == null ? "" : platformName;
      }
   }

   private static ProjectorConnectorItem.Target resolve(Level world, CompoundTag nbt) {
      long stationId = nbt.getLong("lte_station_id");
      long savedPlatform = nbt.getLong("lte_platform_id");
      BlockPos stationPos = nbt.contains("lte_station_pos") ? BlockPos.of(nbt.getLong("lte_station_pos")) : null;
      Station station = stationPos == null ? null : LteMtrLookup.stationAt(world, stationPos);
      if (station == null && stationId != 0L) {
         station = LteMtrLookup.stationById(world, stationId);
      }

      long platformId = savedPlatform;
      if (savedPlatform == 0L && stationPos != null) {
         platformId = LteMtrLookup.platformIdOnRail(world, stationPos, "resolve");
      }

      if (station == null && platformId != 0L) {
         station = LteMtrLookup.stationOfPlatform(world, platformId);
      }

      if (station == null && platformId == 0L) {
         return null;
      } else {
         String name = nbt.getString("lte_station_name");
         if (name == null || name.isEmpty()) {
            name = nameOf(world, station, platformId);
         }

         return new ProjectorConnectorItem.Target(station, platformId, name);
      }
   }

   private InteractionResult bind(Level world, ServerPlayer player, BlockPos projector, ProjectorConnectorItem.Target target) {
      Object tile = BindTargets.projectorAt(world, projector);
      if (tile == null) {
         say(player, "message.lte.projector.projector_gone", ChatFormatting.RED);
         return InteractionResult.SUCCESS;
      } else {
         List<Long> platforms = new ArrayList();
         if (target.station() != null) {
            platforms.addAll(LteMtrLookup.platformIdsOf(world, target.station()));
         }

         if (platforms.isEmpty() && target.platformId() != 0L) {
            platforms.add(target.platformId());
         }

         LteLog.LOGGER.info("[lte] bind: projector pos={} station={} platform={} -> platforms={}", new Object[]{projector, target.station() == null ? "null" : target.station().id + "/" + target.station().name, target.platformId(), platforms});
         if (!MtrTiles.projectorSetPlatforms(tile, platforms)) {
            LteLog.LOGGER.info("[lte] bind: setData FAILED (see '[lte] projector setData' line above)");
            say(player, "message.lte.projector.projector_gone", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
         } else {
            if (world instanceof ServerLevel) {
               ServerLevel serverWorld = (ServerLevel)world;
               boolean pushed = MtrTiles.pushToClients(serverWorld, tile, projector);
               MtrTiles.markChunkSaved(serverWorld, projector);
               LteLog.LOGGER.info("[lte] bind: pushed to clients={}", pushed);
            }

            Set<Long> verify = new TreeSet(MtrTiles.projectorPlatformIds(tile));
            LteLog.LOGGER.info("[lte] bind: readback platforms={} empty={}", verify, verify.isEmpty());
            long stationId = target.station() == null ? 0L : target.station().id;
            ProjectorLinkStore.get((ServerLevel)world).put(projector, stationId, target.name(), platforms.size());
            String shown = target.name() != null && !target.name().isEmpty() ? target.name() : (target.station() != null && target.station().name != null ? target.station().name : "");
            say(player, "message.lte.projector.linked", ChatFormatting.GREEN, shown, platforms.size());
            if (platforms.isEmpty()) {
               say(player, "message.lte.projector.no_platforms", ChatFormatting.YELLOW);
            }

            Station here = LteMtrLookup.stationAt(world, projector);
            LteLog.LOGGER.info("[lte] bind: projector stationAt={} (cross-station rendering handled client-side)", here == null ? "null" : here.id + "/" + here.name);
            return InteractionResult.SUCCESS;
         }
      }
   }

   private static boolean clearProjector(Level world, BlockPos projector) {
      Object tile = BindTargets.projectorAt(world, projector);
      if (tile == null) {
         return false;
      } else if (MtrTiles.projectorPlatformIds(tile).isEmpty()) {
         return false;
      } else {
         boolean cleared = MtrTiles.projectorSetPlatforms(tile, Collections.emptyList());
         if (cleared && world instanceof ServerLevel) {
            ServerLevel serverWorld = (ServerLevel)world;
            MtrTiles.pushToClients(serverWorld, tile, projector);
            MtrTiles.markChunkSaved(serverWorld, projector);
         }

         return cleared;
      }
   }

   public static boolean isUnbindTarget(Level world, BlockPos pos) {
      try {
         return BindTargets.isProjector(world, pos);
      } catch (Throwable var3) {
         return false;
      }
   }

   public static boolean unlinkAt(Level world, ServerPlayer player, BlockPos clicked) {
      if (world instanceof ServerLevel server) {
         if (!BindTargets.isProjector(world, clicked)) {
            return false;
         } else {
            boolean removed = ProjectorLinkStore.get(server).removeInvolving(clicked);
            boolean cleared = clearProjector(world, clicked);
            if (!removed && !cleared) {
               say(player, "message.lte.link.none", ChatFormatting.YELLOW);
            } else {
               say(player, "message.lte.link.unlinked", ChatFormatting.GREEN);
            }

            return true;
         }
      } else {
         return false;
      }
   }

   public static void unlinkBroken(ServerLevel world, BlockPos pos) {
      ProjectorLinkStore.get(world).removeInvolving(pos);
   }

   private static void clearPending(CompoundTag nbt) {
      nbt.remove("lte_proj");
      nbt.remove("lte_station_id");
      nbt.remove("lte_station_pos");
      nbt.remove("lte_station_name");
      nbt.remove("lte_platform_id");
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
      if (nbt != null && nbt.contains("lte_proj")) {
         tooltip.add(Component.translatable("tooltip.lte.projector.selected_projector", new Object[]{posText(BlockPos.of(nbt.getLong("lte_proj")))}).withStyle(ChatFormatting.AQUA));
      } else if (nbt != null && nbt.contains("lte_station_id")) {
         tooltip.add(Component.translatable("tooltip.lte.projector.selected_station", new Object[]{nbt.getString("lte_station_name")}).withStyle(ChatFormatting.AQUA));
      } else if (nbt != null && nbt.contains("lte_platform_id")) {
         tooltip.add(Component.translatable("tooltip.lte.projector.selected_platform", new Object[]{nbt.getString("lte_station_name")}).withStyle(ChatFormatting.AQUA));
      } else {
         tooltip.add(Component.translatable("tooltip.lte.projector.hint").withStyle(ChatFormatting.GRAY));
      }

      tooltip.add(Component.translatable("tooltip.lte.link.right_click").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("tooltip.lte.link.left_click").withStyle(ChatFormatting.DARK_GRAY));
   }

   private static record Target(Station station, long platformId, String name) {
   }
}

package dev.js.lte.bind;

import dev.js.lte.LteLog;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class DisplayPushTicker {
   private static final double RADIUS = 64.0D;
   private static final double RADIUS_SQ = 4096.0D;
   private static final int INTERVAL_TICKS = 20;
   private static final Map<ResourceKey<Level>, Map<BlockPos, Set<UUID>>> IN_RANGE = new HashMap();
   private static int counter;

   private DisplayPushTicker() {
   }

   public static void tick(MinecraftServer server) {
      if (server != null) {
         ++counter;
         if (counter % 20 == 0) {
            for(ServerLevel world : server.getAllLevels()) {
               try {
                  tickWorld(world);
               } catch (Throwable var4) {
               }
            }

         }
      }
   }

   private static void tickWorld(ServerLevel world) {
      Set<BlockPos> bound = new LinkedHashSet();

      try {
         bound.addAll(PidsLinkStore.get(world).links().keySet());
      } catch (Throwable var13) {
      }

      try {
         bound.addAll(ProjectorLinkStore.get(world).links().keySet());
      } catch (Throwable var12) {
      }

      Map<BlockPos, Set<UUID>> seen = (Map)IN_RANGE.computeIfAbsent(world.dimension(), (key) -> new HashMap());
      seen.keySet().retainAll(bound);
      if (!bound.isEmpty()) {
         List<ServerPlayer> players = world.players();

         for(BlockPos pos : bound) {
            Object tile = MtrTiles.tileAt(world, pos);
            if (tile == null) {
               seen.remove(pos);
            } else {
               Set<UUID> before = (Set)seen.get(pos);
               if (before == null) {
                  before = Collections.emptySet();
               }

               Set<UUID> now = new HashSet();
               boolean entered = false;

               for(int i = 0; i < players.size(); ++i) {
                  Player player = (Player)players.get(i);
                  if (!(squaredDistanceXZ(player, pos) > 4096.0D)) {
                     now.add(player.getUUID());
                     if (!before.contains(player.getUUID())) {
                        entered = true;
                     }
                  }
               }

               if (entered && MtrTiles.pushToClients(world, tile, pos)) {
                  LteLog.LOGGER.info("[lte] pids push: pos={} re-sent to {} player(s) entering range", pos, now.size());
               }

               seen.put(pos, now);
            }
         }

      }
   }

   private static double squaredDistanceXZ(Player player, BlockPos pos) {
      double dx = player.getX() - ((double)pos.getX() + 0.5D);
      double dz = player.getZ() - ((double)pos.getZ() + 0.5D);
      return dx * dx + dz * dz;
   }
}

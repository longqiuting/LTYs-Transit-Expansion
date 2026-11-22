package dev.js.lte.signal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import mtr.data.Rail;
import mtr.data.RailwayData;
import dev.js.lte.forge.platform.PacketByteBufs;
import dev.js.lte.forge.platform.PlayerLookup;
import dev.js.lte.forge.platform.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class SignalLinkNet {
   public static final ResourceLocation ID = new ResourceLocation("lte", "signal_link");
   private static final byte KIND_SYNC = 0;
   private static final byte KIND_PUT = 1;
   private static final byte KIND_REMOVE = 2;
   private static final byte KIND_LAMP_SYNC = 3;
   private static final byte KIND_LAMP_PUT = 4;
   private static final byte KIND_LAMP_REMOVE = 5;
   private static Method getInstance;
   private static boolean getInstanceProbed;

   private SignalLinkNet() {
   }

   public static FriendlyByteBuf sync(String dim, Map<BlockPos, BlockPos> links) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(0);
      buf.writeUtf(dim);
      buf.writeVarInt(links.size());

      for(Map.Entry<BlockPos, BlockPos> entry : links.entrySet()) {
         buf.writeBlockPos((BlockPos)entry.getKey());
         buf.writeBlockPos((BlockPos)entry.getValue());
      }

      return buf;
   }

   public static FriendlyByteBuf put(String dim, BlockPos signal, BlockPos node) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(1);
      buf.writeUtf(dim);
      buf.writeBlockPos(signal);
      buf.writeBlockPos(node);
      return buf;
   }

   public static FriendlyByteBuf remove(String dim, BlockPos signal) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(2);
      buf.writeUtf(dim);
      buf.writeBlockPos(signal);
      return buf;
   }

   public static FriendlyByteBuf lampSync(String dim, Map<BlockPos, BlockPos> links) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(3);
      buf.writeUtf(dim);
      buf.writeVarInt(links.size());

      for(Map.Entry<BlockPos, BlockPos> entry : links.entrySet()) {
         buf.writeBlockPos((BlockPos)entry.getKey());
         buf.writeBlockPos((BlockPos)entry.getValue());
      }

      return buf;
   }

   public static FriendlyByteBuf lampPut(String dim, BlockPos lamp, BlockPos source) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(4);
      buf.writeUtf(dim);
      buf.writeBlockPos(lamp);
      buf.writeBlockPos(source);
      return buf;
   }

   public static FriendlyByteBuf lampRemove(String dim, BlockPos lamp) {
      FriendlyByteBuf buf = PacketByteBufs.create();
      buf.writeByte(5);
      buf.writeUtf(dim);
      buf.writeBlockPos(lamp);
      return buf;
   }

   public static void sendAll(ServerPlayer player, String dim, Map<BlockPos, BlockPos> links, Map<BlockPos, BlockPos> lampLinks) {
      if (player != null && ServerPlayNetworking.canSend(player, ID)) {
         ServerPlayNetworking.send(player, ID, sync(dim, links));
         ServerPlayNetworking.send(player, ID, lampSync(dim, lampLinks));
      }
   }

   public static void broadcastPut(MinecraftServer server, String dim, BlockPos signal, BlockPos node) {
      if (server != null) {
         for(ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ID)) {
               ServerPlayNetworking.send(player, ID, put(dim, signal, node));
            }
         }

      }
   }

   public static void broadcastRemove(MinecraftServer server, String dim, BlockPos signal) {
      if (server != null) {
         for(ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ID)) {
               ServerPlayNetworking.send(player, ID, remove(dim, signal));
            }
         }

      }
   }

   public static void broadcastLampPut(MinecraftServer server, String dim, BlockPos lamp, BlockPos source) {
      if (server != null) {
         for(ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ID)) {
               ServerPlayNetworking.send(player, ID, lampPut(dim, lamp, source));
            }
         }

      }
   }

   public static void broadcastLampRemove(MinecraftServer server, String dim, BlockPos lamp) {
      if (server != null) {
         for(ServerPlayer player : PlayerLookup.all(server)) {
            if (ServerPlayNetworking.canSend(player, ID)) {
               ServerPlayNetworking.send(player, ID, lampRemove(dim, lamp));
            }
         }

      }
   }

   public static String dimensionOf(Level world) {
      return world.dimension().location().toString();
   }

   private static RailwayData railwayData(Level world) {
      if (!getInstanceProbed) {
         getInstanceProbed = true;

         try {
            getInstance = RailwayData.class.getMethod("getInstance", Level.class);
         } catch (Throwable var3) {
            getInstance = null;
         }
      }

      if (getInstance == null) {
         return null;
      } else {
         try {
            Object data = getInstance.invoke((Object)null, world);
            return data instanceof RailwayData ? (RailwayData)data : null;
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   public static boolean hasRail(Level world, BlockPos node) {
      return !railsAt(world, node).isEmpty();
   }

   public static List<Rail> railsAt(Level world, BlockPos node) {
      try {
         RailwayData data = railwayData(world);
         if (data == null) {
            return Collections.emptyList();
         } else {
            Map rails = data.getRailsMap();
            if (rails == null) {
               return Collections.emptyList();
            } else {
               Map atNode = (Map)rails.get(node);
               if (atNode != null && !atNode.isEmpty()) {
                  List<Rail> out = new ArrayList(atNode.size());

                  for(Object value : atNode.values()) {
                     if (value instanceof Rail) {
                        out.add((Rail)value);
                     }
                  }

                  return out;
               } else {
                  return Collections.emptyList();
               }
            }
         }
      } catch (Throwable var8) {
         return Collections.emptyList();
      }
   }
}

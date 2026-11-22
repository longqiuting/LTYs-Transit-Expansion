package dev.js.lte.signal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

public final class SignalLinks {
   private static final Map<String, Map<BlockPos, BlockPos>> NODES = new ConcurrentHashMap();
   private static final Map<String, Map<BlockPos, BlockPos>> LAMPS = new ConcurrentHashMap();

   private SignalLinks() {
   }

   public static void replace(String dimension, Map<BlockPos, BlockPos> links) {
      if (links != null && !links.isEmpty()) {
         NODES.put(dimension, new HashMap(links));
      } else {
         NODES.remove(dimension);
      }

   }

   public static void put(String dimension, BlockPos signal, BlockPos node) {
      ((Map)NODES.computeIfAbsent(dimension, (k) -> new HashMap())).put(signal.immutable(), node.immutable());
   }

   public static void remove(String dimension, BlockPos signal) {
      Map<BlockPos, BlockPos> map = (Map)NODES.get(dimension);
      if (map != null) {
         map.remove(signal);
      }

   }

   public static BlockPos nodeFor(BlockGetter view, BlockPos signal) {
      Map<BlockPos, BlockPos> map = (Map)NODES.get(dimensionOf(view));
      return map != null && !map.isEmpty() ? (BlockPos)map.get(signal) : null;
   }

   public static void replaceLamps(String dimension, Map<BlockPos, BlockPos> links) {
      if (links != null && !links.isEmpty()) {
         LAMPS.put(dimension, new HashMap(links));
      } else {
         LAMPS.remove(dimension);
      }

   }

   public static void putLamp(String dimension, BlockPos lamp, BlockPos source) {
      ((Map)LAMPS.computeIfAbsent(dimension, (k) -> new HashMap())).put(lamp.immutable(), source.immutable());
   }

   public static void removeLamp(String dimension, BlockPos lamp) {
      Map<BlockPos, BlockPos> map = (Map)LAMPS.get(dimension);
      if (map != null) {
         map.remove(lamp);
      }

   }

   public static BlockPos resolveSource(BlockGetter view, BlockPos lamp) {
      Map<BlockPos, BlockPos> map = (Map)LAMPS.get(dimensionOf(view));
      if (map != null && !map.isEmpty()) {
         BlockPos cursor = (BlockPos)map.get(lamp);
         if (cursor == null) {
            return null;
         } else {
            Set<BlockPos> seen = new HashSet(8);
            seen.add(lamp);

            for(int hop = 0; hop < 64; ++hop) {
               if (!seen.add(cursor)) {
                  return null;
               }

               BlockPos next = (BlockPos)map.get(cursor);
               if (next == null) {
                  return cursor;
               }

               cursor = next;
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public static void clear() {
      NODES.clear();
      LAMPS.clear();
   }

   private static String dimensionOf(BlockGetter view) {
      return view instanceof Level ? ((Level)view).dimension().location().toString() : "";
   }
}

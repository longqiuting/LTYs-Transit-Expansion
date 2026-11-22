package dev.js.mtrheadlight.signal;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class SignalLinkStore extends SavedData {
   private static final String ID = "lte_signal_links";
   private static final String KEY_LINKS = "links";
   private static final String KEY_SIGNAL = "signal";
   private static final String KEY_NODE = "node";
   private static final String KEY_LAMP_LINKS = "lamp_links";
   private static final String KEY_LAMP = "lamp";
   private static final String KEY_SOURCE = "source";
   public static final int MAX_HOPS = 64;
   private final Map<BlockPos, BlockPos> links = new HashMap();
   private final Map<BlockPos, BlockPos> lampLinks = new HashMap();

   public static SignalLinkStore get(ServerLevel world) {
      return (SignalLinkStore)world.getDataStorage().computeIfAbsent(SignalLinkStore::new, SignalLinkStore::new, "lte_signal_links");
   }

   public SignalLinkStore() {
   }

   public SignalLinkStore(CompoundTag nbt) {
      ListTag list = nbt.getList("links", 10);

      for(int i = 0; i < list.size(); ++i) {
         CompoundTag entry = list.getCompound(i);
         this.links.put(BlockPos.of(entry.getLong("signal")), BlockPos.of(entry.getLong("node")));
      }

      ListTag lamps = nbt.getList("lamp_links", 10);

      for(int i = 0; i < lamps.size(); ++i) {
         CompoundTag entry = lamps.getCompound(i);
         this.lampLinks.put(BlockPos.of(entry.getLong("lamp")), BlockPos.of(entry.getLong("source")));
      }

   }

   public CompoundTag save(CompoundTag nbt) {
      ListTag list = new ListTag();

      for(Map.Entry<BlockPos, BlockPos> entry : this.links.entrySet()) {
         CompoundTag one = new CompoundTag();
         one.putLong("signal", ((BlockPos)entry.getKey()).asLong());
         one.putLong("node", ((BlockPos)entry.getValue()).asLong());
         list.add(one);
      }

      nbt.put("links", list);
      ListTag lamps = new ListTag();

      for(Map.Entry<BlockPos, BlockPos> entry : this.lampLinks.entrySet()) {
         CompoundTag one = new CompoundTag();
         one.putLong("lamp", ((BlockPos)entry.getKey()).asLong());
         one.putLong("source", ((BlockPos)entry.getValue()).asLong());
         lamps.add(one);
      }

      nbt.put("lamp_links", lamps);
      return nbt;
   }

   public Map<BlockPos, BlockPos> links() {
      return this.links;
   }

   public Map<BlockPos, BlockPos> lampLinks() {
      return this.lampLinks;
   }

   public BlockPos nodeAt(BlockPos signal) {
      return (BlockPos)this.links.get(signal);
   }

   public BlockPos lampSourceAt(BlockPos lamp) {
      return (BlockPos)this.lampLinks.get(lamp);
   }

   public boolean hasAny(BlockPos signal) {
      return this.links.containsKey(signal) || this.lampLinks.containsKey(signal);
   }

   public void put(BlockPos signal, BlockPos node) {
      this.links.put(signal.immutable(), node.immutable());
      this.lampLinks.remove(signal);
      this.setDirty();
   }

   public boolean putLamp(BlockPos lamp, BlockPos source) {
      if (lamp.equals(source)) {
         return false;
      } else if (this.reaches(source, lamp)) {
         return false;
      } else {
         this.lampLinks.put(lamp.immutable(), source.immutable());
         this.links.remove(lamp);
         this.setDirty();
         return true;
      }
   }

   public boolean remove(BlockPos signal) {
      BlockPos oldNode = (BlockPos)this.links.remove(signal);
      BlockPos oldLamp = (BlockPos)this.lampLinks.remove(signal);
      if (oldNode == null && oldLamp == null) {
         return false;
      } else {
         this.setDirty();
         return true;
      }
   }

   public SignalLinkStore.Removed removeInvolving(BlockPos pos) {
      SignalLinkStore.Removed out = new SignalLinkStore.Removed();
      if (pos == null) {
         return out;
      } else {
         this.links.entrySet().removeIf((entry) -> {
            if (!((BlockPos)entry.getKey()).equals(pos) && !((BlockPos)entry.getValue()).equals(pos)) {
               return false;
            } else {
               out.signals.add((BlockPos)entry.getKey());
               return true;
            }
         });
         this.lampLinks.entrySet().removeIf((entry) -> {
            if (!((BlockPos)entry.getKey()).equals(pos) && !((BlockPos)entry.getValue()).equals(pos)) {
               return false;
            } else {
               out.lamps.add((BlockPos)entry.getKey());
               return true;
            }
         });
         if (out.any()) {
            this.setDirty();
         }

         return out;
      }
   }

   private boolean reaches(BlockPos from, BlockPos target) {
      BlockPos cursor = from;

      for(int hop = 0; hop < 64 && cursor != null; ++hop) {
         if (cursor.equals(target)) {
            return true;
         }

         cursor = (BlockPos)this.lampLinks.get(cursor);
      }

      return false;
   }

   public static final class Removed {
      public final Set<BlockPos> signals = new LinkedHashSet();
      public final Set<BlockPos> lamps = new LinkedHashSet();

      public boolean any() {
         return !this.signals.isEmpty() || !this.lamps.isEmpty();
      }

      public int count() {
         return this.signals.size() + this.lamps.size();
      }
   }
}

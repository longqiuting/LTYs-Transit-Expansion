package dev.js.lte.bind;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PidsLinkStore extends SavedData {
   private static final String ID = "lte_pids_links";
   private static final String KEY_LINKS = "links";
   private static final String KEY_POS = "pos";
   private static final String KEY_NODE = "node";
   private static final String KEY_PLATFORM = "platform";
   private static final String KEY_NAME = "name";
   private final Map<BlockPos, PidsLinkStore.Link> links = new LinkedHashMap();

   public static PidsLinkStore get(ServerLevel world) {
      return (PidsLinkStore)world.getDataStorage().computeIfAbsent(PidsLinkStore::new, PidsLinkStore::new, "lte_pids_links");
   }

   public PidsLinkStore() {
   }

   public PidsLinkStore(CompoundTag nbt) {
      ListTag list = nbt.getList("links", 10);

      for(int i = 0; i < list.size(); ++i) {
         CompoundTag entry = list.getCompound(i);
         this.links.put(BlockPos.of(entry.getLong("pos")), new PidsLinkStore.Link(entry.getLong("node"), entry.getLong("platform"), entry.getString("name")));
      }

   }

   public CompoundTag save(CompoundTag nbt) {
      ListTag list = new ListTag();

      for(Map.Entry<BlockPos, PidsLinkStore.Link> entry : this.links.entrySet()) {
         CompoundTag one = new CompoundTag();
         one.putLong("pos", ((BlockPos)entry.getKey()).asLong());
         one.putLong("node", ((PidsLinkStore.Link)entry.getValue()).node());
         one.putLong("platform", ((PidsLinkStore.Link)entry.getValue()).platformId());
         one.putString("name", ((PidsLinkStore.Link)entry.getValue()).platformName());
         list.add(one);
      }

      nbt.put("links", list);
      return nbt;
   }

   public PidsLinkStore.Link at(BlockPos pids) {
      return (PidsLinkStore.Link)this.links.get(pids);
   }

   public Map<BlockPos, PidsLinkStore.Link> links() {
      return this.links;
   }

   public void put(BlockPos pids, BlockPos node, long platformId, String platformName) {
      this.links.put(pids.immutable(), new PidsLinkStore.Link(node.immutable().asLong(), platformId, platformName == null ? "" : platformName));
      this.setDirty();
   }

   public boolean remove(BlockPos pids) {
      if (this.links.remove(pids) != null) {
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public Map<BlockPos, PidsLinkStore.Link> removeInvolving(BlockPos pos) {
      Map<BlockPos, PidsLinkStore.Link> out = new LinkedHashMap();
      if (pos == null) {
         return out;
      } else {
         this.links.entrySet().removeIf((entry) -> {
            if (!((BlockPos)entry.getKey()).equals(pos) && !((PidsLinkStore.Link)entry.getValue()).nodePos().equals(pos)) {
               return false;
            } else {
               out.put((BlockPos)entry.getKey(), (PidsLinkStore.Link)entry.getValue());
               return true;
            }
         });
         if (!out.isEmpty()) {
            this.setDirty();
         }

         return out;
      }
   }

   public static record Link(long node, long platformId, String platformName) {
      public BlockPos nodePos() {
         return BlockPos.of(this.node);
      }
   }
}

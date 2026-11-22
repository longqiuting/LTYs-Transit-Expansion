package dev.js.lte.bind;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ProjectorLinkStore extends SavedData {
   private static final String ID = "lte_projector_links";
   private static final String KEY_LINKS = "links";
   private static final String KEY_POS = "pos";
   private static final String KEY_STATION = "station";
   private static final String KEY_NAME = "name";
   private static final String KEY_PLATFORMS = "platforms";
   private final Map<BlockPos, ProjectorLinkStore.Link> links = new LinkedHashMap();

   public static ProjectorLinkStore get(ServerLevel world) {
      return (ProjectorLinkStore)world.getDataStorage().computeIfAbsent(ProjectorLinkStore::new, ProjectorLinkStore::new, "lte_projector_links");
   }

   public ProjectorLinkStore() {
   }

   public ProjectorLinkStore(CompoundTag nbt) {
      ListTag list = nbt.getList("links", 10);

      for(int i = 0; i < list.size(); ++i) {
         CompoundTag entry = list.getCompound(i);
         BlockPos pos = BlockPos.of(entry.getLong("pos"));
         this.links.put(pos, new ProjectorLinkStore.Link(entry.getLong("station"), entry.getString("name"), entry.getInt("platforms")));
      }

   }

   public CompoundTag save(CompoundTag nbt) {
      ListTag list = new ListTag();

      for(Map.Entry<BlockPos, ProjectorLinkStore.Link> entry : this.links.entrySet()) {
         CompoundTag one = new CompoundTag();
         one.putLong("pos", ((BlockPos)entry.getKey()).asLong());
         one.putLong("station", ((ProjectorLinkStore.Link)entry.getValue()).stationId());
         one.putString("name", ((ProjectorLinkStore.Link)entry.getValue()).stationName());
         one.putInt("platforms", ((ProjectorLinkStore.Link)entry.getValue()).platformCount());
         list.add(one);
      }

      nbt.put("links", list);
      return nbt;
   }

   public ProjectorLinkStore.Link at(BlockPos projector) {
      return (ProjectorLinkStore.Link)this.links.get(projector);
   }

   public Map<BlockPos, ProjectorLinkStore.Link> links() {
      return this.links;
   }

   public void put(BlockPos projector, long stationId, String stationName, int platformCount) {
      this.links.put(projector.immutable(), new ProjectorLinkStore.Link(stationId, stationName == null ? "" : stationName, platformCount));
      this.setDirty();
   }

   public boolean remove(BlockPos projector) {
      if (this.links.remove(projector) != null) {
         this.setDirty();
         return true;
      } else {
         return false;
      }
   }

   public boolean removeInvolving(BlockPos pos) {
      return this.remove(pos);
   }

   public static record Link(long stationId, String stationName, int platformCount) {
   }
}

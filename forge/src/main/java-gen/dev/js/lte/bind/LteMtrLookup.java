package dev.js.lte.bind;

import dev.js.lte.LteLog;
import dev.js.lte.MtrCompat;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mtr.data.DataCache;
import mtr.data.Platform;
import mtr.data.RailwayData;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class LteMtrLookup {
   private static boolean probed;
   private static Method getInstance;
   private static Method getStation;
   private static Method getClosePlatformId;
   private static Method getPlatformByPos;
   private static final int RAIL_WALK_NODES = 256;
   private static final int RAIL_WALK_RANGE = 256;
   private static final int NODE_SEARCH = 3;

   private LteMtrLookup() {
   }

   private static void probe() {
      if (!probed) {
         probed = true;

         try {
            getInstance = RailwayData.class.getMethod("getInstance", Level.class);
         } catch (Throwable var4) {
            getInstance = null;
         }

         try {
            getStation = RailwayData.class.getMethod("getStation", Set.class, DataCache.class, BlockPos.class);
         } catch (Throwable var3) {
            getStation = null;
         }

         try {
            getClosePlatformId = RailwayData.class.getMethod("getClosePlatformId", Set.class, DataCache.class, BlockPos.class);
         } catch (Throwable var2) {
            getClosePlatformId = null;
         }

         try {
            getPlatformByPos = RailwayData.class.getMethod("getPlatformByPos", Set.class, BlockPos.class);
         } catch (Throwable var1) {
            getPlatformByPos = null;
         }

      }
   }

   public static RailwayData data(Level world) {
      probe();
      if (getInstance != null && world != null) {
         try {
            Object value = getInstance.invoke((Object)null, world);
            return value instanceof RailwayData ? (RailwayData)value : null;
         } catch (Throwable var2) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static Station stationAt(Level world, BlockPos pos) {
      probe();
      RailwayData data = data(world);
      if (data != null && getStation != null && pos != null) {
         try {
            Object value = getStation.invoke((Object)null, data.stations, data.dataCache, pos);
            return value instanceof Station ? (Station)value : null;
         } catch (Throwable var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static long platformIdAt(Level world, BlockPos pos) {
      probe();
      RailwayData data = data(world);
      if (data != null && getClosePlatformId != null && pos != null) {
         try {
            Object value = getClosePlatformId.invoke((Object)null, data.platforms, data.dataCache, pos);
            return value instanceof Long ? (Long)value : 0L;
         } catch (Throwable var4) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public static List<Long> platformIdsOf(Level world, long stationId) {
      return platformIdsOf(world, stationById(world, stationId));
   }

   public static long platformIdAtExact(Level world, BlockPos pos) {
      probe();
      RailwayData data = data(world);
      if (data != null && getPlatformByPos != null && pos != null) {
         try {
            Object value = getPlatformByPos.invoke((Object)null, data.platforms, pos);
            return value instanceof Platform ? ((Platform)value).id : 0L;
         } catch (Throwable var4) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   public static long platformIdOnRail(Level world, BlockPos pos) {
      return platformIdOnRail(world, pos, "default");
   }

   public static long platformIdOnRail(Level world, BlockPos pos, String tag) {
      if (pos == null) {
         LteLog.LOGGER.info("[lte] rail lookup[{}]: pos=null -> 0", tag);
         return 0L;
      } else {
         RailwayData data = data(world);
         if (data == null) {
            LteLog.LOGGER.info("[lte] rail lookup[{}]: pos={} railwayData=null -> 0", tag, pos);
            return 0L;
         } else {
            long direct = platformIdAt(world, pos);
            if (direct != 0L) {
               LteLog.LOGGER.info("[lte] rail lookup[{}]: pos={} s1(getClosePlatformId)={} HIT", new Object[]{tag, pos, direct});
               return direct;
            } else {
               long exact = platformIdAtExact(world, pos);
               if (exact != 0L) {
                  LteLog.LOGGER.info("[lte] rail lookup[{}]: pos={} s1=0 s2(getPlatformByPos)={} HIT", new Object[]{tag, pos, exact});
                  return exact;
               } else {
                  long walked = platformAlongRail(world, pos, tag);
                  LteLog.LOGGER.info("[lte] rail lookup[{}]: pos={} s1=0 s2=0 s3(railWalk)={} {}", new Object[]{tag, pos, walked, walked == 0L ? "MISS" : "HIT"});
                  return walked;
               }
            }
         }
      }
   }

   private static long platformAlongRail(Level world, BlockPos pos, String tag) {
      RailwayData data = data(world);
      if (data == null) {
         LteLog.LOGGER.info("[lte] rail walk[{}]: railwayData=null", tag);
         return 0L;
      } else {
         Map rails;
         try {
            rails = MtrCompat.railsMap(data);
         } catch (Throwable var17) {
            LteLog.LOGGER.info("[lte] rail walk[{}]: getRailsMap threw {}", tag, var17.toString());
            return 0L;
         }

         if (rails != null && !rails.isEmpty()) {
            BlockPos start = nodeNear(rails, pos);
            if (start == null) {
               LteLog.LOGGER.info("[lte] rail walk[{}]: pos={} no track node within {} blocks (railsMap={} nodes)", new Object[]{tag, pos, 3, rails.size()});
               return 0L;
            } else {
               Set<BlockPos> seen = new HashSet();
               Deque<BlockPos> queue = new ArrayDeque();
               seen.add(start);
               queue.add(start);
               double rangeSq = 65536.0D;

               label63:
               while(true) {
                  if (!queue.isEmpty() && seen.size() <= 256) {
                     BlockPos node = (BlockPos)queue.poll();
                     long hit = platformIdAt(world, node);
                     if (hit != 0L) {
                        LteLog.LOGGER.info("[lte] rail walk[{}]: start={} visited={} hit at node={} platform={}", new Object[]{tag, start, seen.size(), node, hit});
                        return hit;
                     }

                     Object neighbours;
                     try {
                        neighbours = rails.get(node);
                     } catch (Throwable var18) {
                        continue;
                     }

                     if (!(neighbours instanceof Map)) {
                        continue;
                     }

                     Iterator t = ((Map)neighbours).keySet().iterator();

                     while(true) {
                        if (!t.hasNext()) {
                           continue label63;
                        }

                        Object key = t.next();
                        if (key instanceof BlockPos) {
                           BlockPos next = (BlockPos)key;
                           if (!(next.distSqr(pos) > 65536.0D) && seen.add(next)) {
                              queue.add(next);
                           }
                        }
                     }
                  }

                  LteLog.LOGGER.info("[lte] rail walk[{}]: start={} visited={} no platform within {} nodes / {} blocks", new Object[]{tag, start, seen.size(), 256, 256});
                  return 0L;
               }
            }
         } else {
            LteLog.LOGGER.info("[lte] rail walk[{}]: railsMap empty ({} entries)", tag, rails == null ? "null" : rails.size());
            return 0L;
         }
      }
   }

   private static BlockPos nodeNear(Map rails, BlockPos pos) {
      for(BlockPos candidate : new BlockPos[]{pos, pos.above(), pos.below()}) {
         try {
            if (rails.containsKey(candidate)) {
               return candidate;
            }
         } catch (Throwable var13) {
         }
      }

      BlockPos best = null;
      double bestDist = Double.MAX_VALUE;

      for(int dx = -3; dx <= 3; ++dx) {
         for(int dy = -3; dy <= 3; ++dy) {
            for(int dz = -3; dz <= 3; ++dz) {
               BlockPos cursor = pos.offset(dx, dy, dz);

               boolean present;
               try {
                  present = rails.containsKey(cursor);
               } catch (Throwable var12) {
                  present = false;
               }

               if (present) {
                  double distance = cursor.distSqr(pos);
                  if (distance < bestDist) {
                     bestDist = distance;
                     best = cursor.immutable();
                  }
               }
            }
         }
      }

      return best;
   }

   public static Station stationNearRail(Level world, BlockPos pos) {
      Station inside = stationAt(world, pos);
      if (inside != null) {
         return inside;
      } else {
         long platformId = platformIdOnRail(world, pos);
         return platformId == 0L ? null : stationOfPlatform(world, platformId);
      }
   }

   public static List<Long> platformIdsOf(Level world, Station station) {
      RailwayData data = data(world);
      if (data != null && station != null) {
         List<Long> out = new ArrayList();

         try {
            Map<Long, Station> toStation = data.dataCache.platformIdToStation;

            for(Platform platform : data.platforms) {
               Station owner = (Station)toStation.get(platform.id);
               if (owner != null) {
                  if (owner.id == station.id) {
                     out.add(platform.id);
                  }
               } else {
                  BlockPos mid = midPos(platform);
                  if (mid != null && station.inArea(mid.getX(), mid.getZ())) {
                     out.add(platform.id);
                  }
               }
            }

            return out;
         } catch (Throwable var9) {
            return Collections.emptyList();
         }
      } else {
         return Collections.emptyList();
      }
   }

   public static Station stationById(Level world, long stationId) {
      RailwayData data = data(world);
      if (data != null && stationId != 0L) {
         try {
            return (Station)data.dataCache.stationIdMap.get(stationId);
         } catch (Throwable var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static BlockPos midPos(Platform platform) {
      try {
         Object value = platform.getClass().getMethod("getMidPos").invoke(platform);
         return value instanceof BlockPos ? (BlockPos)value : null;
      } catch (Throwable var2) {
         return null;
      }
   }

   public static Station stationOfPlatform(Level world, long platformId) {
      RailwayData data = data(world);
      if (data != null && platformId != 0L) {
         try {
            Station cached = (Station)data.dataCache.platformIdToStation.get(platformId);
            if (cached != null) {
               return cached;
            }
         } catch (Throwable var9) {
         }

         try {
            for(Platform platform : data.platforms) {
               if (platform.id == platformId) {
                  BlockPos mid = midPos(platform);
                  if (mid == null) {
                     return null;
                  }

                  for(Station station : data.stations) {
                     if (station.inArea(mid.getX(), mid.getZ())) {
                        return station;
                     }
                  }
               }
            }

            return null;
         } catch (Throwable var10) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String platformName(Level world, long platformId) {
      RailwayData data = data(world);
      if (data != null && platformId != 0L) {
         try {
            for(Platform platform : data.platforms) {
               if (platform.id == platformId) {
                  return platform.name == null ? "" : platform.name;
               }
            }

            return "";
         } catch (Throwable var6) {
            return "";
         }
      } else {
         return "";
      }
   }
}

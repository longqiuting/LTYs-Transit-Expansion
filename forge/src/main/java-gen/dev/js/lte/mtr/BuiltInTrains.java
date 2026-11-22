package dev.js.lte.mtr;

import dev.js.lte.HeadlightConfig;
import dev.js.lte.MtrHeadlightClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mtr.client.ClientData;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.data.Siding;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class BuiltInTrains {
   private static final int JOIN_GRACE_TICKS = 40;
   private static final int RESET_COOLDOWN_TICKS = 60;
   private static final int EMPTY_DATA_GRACE_TICKS = 200;
   private static final int DIAG_ID_COUNT = 5;
   private static final Set<String> BUILT_IN = new LinkedHashSet();
   private static final Map<String, TrainProperties> EXTERNAL = new LinkedHashMap();
   private static boolean inReset;
   private static boolean ourReset;
   private static boolean ourResetPending;
   private static Set<String> appliedUsed;
   private static final Set<String> SEEN_USED = new HashSet();
   private static boolean trimmed;
   private static boolean disabled;
   private static boolean pruneRan;
   private static int lastRemoved;
   private static int lastKept;
   private static int lastExternalPresent;
   private static boolean lastPruneSawWorld = true;
   private static ClientLevel lastWorld;
   private static long worldTicks;
   private static long lastResetTick = -60L;
   private static boolean loggedFirstTrim;
   private static boolean loggedWaitingForWorld;
   private static boolean loggedSnapshot;

   private BuiltInTrains() {
   }

   public static void beforeReset() {
      inReset = true;
      ourReset = ourResetPending;
      if (!ourReset) {
         EXTERNAL.clear();
      }

   }

   public static void afterReset(Map<String, TrainProperties> registry) {
      try {
         if (registry != null) {
            if (BUILT_IN.isEmpty()) {
               for(String key : registry.keySet()) {
                  if (key != null) {
                     BUILT_IN.add(key.toLowerCase(Locale.ENGLISH));
                  }
               }

               if (!loggedSnapshot && !BUILT_IN.isEmpty()) {
                  loggedSnapshot = true;
                  MtrHeadlightClient.LOGGER.info("[lte] builtin trains: snapshot={} (first reset, built-in ids only)", BUILT_IN.size());
               }
            }

            if (ourReset) {
               restoreExternal(registry);
            }
         }
      } catch (Throwable var6) {
         fail(var6);
      } finally {
         inReset = false;
         ourReset = false;
      }

   }

   public static void onRegister(String id, TrainProperties props) {
      if (!disabled && id != null && props != null) {
         if (!inReset) {
            try {
               String key = id.toLowerCase(Locale.ENGLISH);
               if (BUILT_IN.contains(key)) {
                  return;
               }

               EXTERNAL.put(key, props);
            } catch (Throwable var3) {
               fail(var3);
            }

         }
      }
   }

   private static int restoreExternal(Map<String, TrainProperties> registry) {
      if (EXTERNAL.isEmpty()) {
         return 0;
      } else {
         int restored = 0;

         for(Map.Entry<String, TrainProperties> entry : EXTERNAL.entrySet()) {
            String id = (String)entry.getKey();
            if (id != null && !registry.containsKey(id)) {
               try {
                  TrainClientRegistry.register(id, (TrainProperties)entry.getValue());
                  ++restored;
               } catch (Throwable var6) {
               }
            }
         }

         if (restored > 0) {
            MtrHeadlightClient.LOGGER.info("[lte] builtin trains: restored {} resource-pack train(s) after reset (external={})", restored, EXTERNAL.size());
         }

         return restored;
      }
   }

   public static void prune(Map<String, TrainProperties> registry, Map<TransportMode, List<String>> orders) {
      pruneRan = true;
      lastRemoved = 0;
      lastPruneSawWorld = true;
      if (!disabled && registry != null) {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         if (cfg != null && cfg.disableBuiltInTrains && !BUILT_IN.isEmpty()) {
            Set<String> used = usedIds();
            if (used == null) {
               lastPruneSawWorld = false;
               lastKept = countPresent(registry, BUILT_IN);
               lastExternalPresent = countPresent(registry, EXTERNAL.keySet());
               if (cfg.builtinTrainsDiagnostics && !loggedWaitingForWorld) {
                  loggedWaitingForWorld = true;
                  MtrHeadlightClient.LOGGER.info("[lte] builtin trains: snapshot={} external={} waiting for world (nothing pruned)", BUILT_IN.size(), EXTERNAL.size());
               }

            } else {
               loggedWaitingForWorld = false;
               List<String> removedIds = new ArrayList();

               try {
                  Iterator<Map.Entry<String, TrainProperties>> it = registry.entrySet().iterator();

                  while(it.hasNext()) {
                     Map.Entry<String, TrainProperties> entry = (Map.Entry)it.next();
                     String key = (String)entry.getKey();
                     if (key != null) {
                        String id = key.toLowerCase(Locale.ENGLISH);
                        if (BUILT_IN.contains(id) && !EXTERNAL.containsKey(id) && !used.contains(id)) {
                           it.remove();
                           removedIds.add(id);
                        }
                     }
                  }

                  if (!removedIds.isEmpty() && orders != null) {
                     for(List<String> order : orders.values()) {
                        if (order != null) {
                           order.removeAll(removedIds);
                        }
                     }
                  }
               } catch (Throwable var9) {
                  fail(var9);
                  return;
               }

               appliedUsed = new HashSet(used);
               if (!removedIds.isEmpty()) {
                  trimmed = true;
               }

               lastRemoved = removedIds.size();
               lastKept = countPresent(registry, BUILT_IN);
               lastExternalPresent = countPresent(registry, EXTERNAL.keySet());
               if (cfg.builtinTrainsDiagnostics) {
                  MtrHeadlightClient.LOGGER.info("[lte] builtin trains: snapshot={} builtin={} external={}/{} used={} removed={} kept={}", new Object[]{BUILT_IN.size(), BUILT_IN.size(), lastExternalPresent, EXTERNAL.size(), used.size(), lastRemoved, lastKept});
                  if (!removedIds.isEmpty()) {
                     MtrHeadlightClient.LOGGER.info("[lte] builtin trains: removed ids {}", removedIds.subList(0, Math.min(5, removedIds.size())));
                  }
               } else if (!removedIds.isEmpty() && !loggedFirstTrim) {
                  loggedFirstTrim = true;
                  MtrHeadlightClient.LOGGER.info("[lte] builtin trains: {} ids, kept {} (in use), removed {}", new Object[]{BUILT_IN.size(), used.size(), removedIds.size()});
               }

            }
         } else {
            lastKept = countPresent(registry, BUILT_IN);
            lastExternalPresent = countPresent(registry, EXTERNAL.keySet());
         }
      }
   }

   public static void tick(HeadlightConfig cfg, Minecraft client) {
      if (!disabled && cfg != null && client != null) {
         if (!cfg.disableBuiltInTrains) {
            if (trimmed) {
               trimmed = false;
               appliedUsed = null;
               loggedFirstTrim = false;
               resetSafely();
            }

         } else if (!BUILT_IN.isEmpty()) {
            ClientLevel world = client.level;
            if (world == null) {
               lastWorld = null;
               worldTicks = 0L;
            } else {
               if (world != lastWorld) {
                  lastWorld = world;
                  worldTicks = 0L;
                  SEEN_USED.clear();
                  appliedUsed = null;
               }

               ++worldTicks;
               if (worldTicks >= 40L || dataReady()) {
                  if (dataReady() || worldTicks >= 200L) {
                     Set<String> used = usedIds();
                     if (used != null && !used.equals(appliedUsed)) {
                        if (worldTicks - lastResetTick >= 60L) {
                           lastResetTick = worldTicks;
                           resetSafely();
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Set<String> usedIds() {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.level != null) {
         try {
            for(TrainClient train : ClientData.TRAINS) {
               if (train != null && train.trainId != null) {
                  SEEN_USED.add(train.trainId.toLowerCase(Locale.ENGLISH));
               }
            }

            for(Siding siding : ClientData.SIDINGS) {
               if (siding != null) {
                  String id = siding.getTrainId();
                  if (id != null && !id.isEmpty()) {
                     SEEN_USED.add(id.toLowerCase(Locale.ENGLISH));
                  }
               }
            }
         } catch (RuntimeException var4) {
            return null;
         }

         return SEEN_USED;
      } else {
         return null;
      }
   }

   private static boolean dataReady() {
      try {
         return !ClientData.TRAINS.isEmpty() || !ClientData.SIDINGS.isEmpty();
      } catch (RuntimeException var1) {
         return false;
      }
   }

   public static String reloadNow() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if (disabled) {
         return "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u8fc7\u6ee4\u5df2\u505c\u7528\uff08mixin \u672a\u751f\u6548\uff0c\u8be6\u89c1\u65e5\u5fd7\uff09";
      } else if (cfg == null) {
         return "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u91cd\u8f7d\u5931\u8d25\uff08\u914d\u7f6e\u5c1a\u672a\u52a0\u8f7d\uff09";
      } else {
         boolean trim = cfg.disableBuiltInTrains;
         trimmed = false;
         appliedUsed = null;
         loggedFirstTrim = false;
         loggedWaitingForWorld = false;
         lastResetTick = worldTicks;
         resetSafely();
         if (disabled) {
            return "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u91cd\u8f7d\u5931\u8d25\uff08TrainClientRegistry.reset() \u7684 mixin \u672a\u751f\u6548\uff09";
         } else if (!trim) {
            return "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u5df2\u6062\u590d \u2014 \u4fdd\u7559 " + lastKept + " / \u5220\u9664 0\uff08\u5185\u7f6e " + BUILT_IN.size() + "\uff0c\u8d44\u6e90\u5305 " + lastExternalPresent + "/" + EXTERNAL.size() + "\uff09";
         } else {
            return !lastPruneSawWorld ? "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u5df2\u91cd\u8f7d \u2014 \u672a\u8fdb\u5165\u5b58\u6863\uff0c\u6682\u4e0d\u88c1\u526a\uff08\u5185\u7f6e " + BUILT_IN.size() + "\uff0c\u8d44\u6e90\u5305 " + lastExternalPresent + "/" + EXTERNAL.size() + "\uff09" : "LTE\uff1a\u5185\u7f6e\u8f66\u578b\u5df2\u91cd\u8f7d \u2014 \u4fdd\u7559 " + lastKept + " / \u5220\u9664 " + lastRemoved + "\uff08\u5185\u7f6e " + BUILT_IN.size() + "\uff0c\u8d44\u6e90\u5305 " + lastExternalPresent + "/" + EXTERNAL.size() + "\uff09";
         }
      }
   }

   private static int countPresent(Map<String, TrainProperties> registry, Iterable<String> ids) {
      int present = 0;

      try {
         for(String id : ids) {
            if (id != null && registry.containsKey(id)) {
               ++present;
            }
         }
      } catch (RuntimeException var5) {
      }

      return present;
   }

   private static void resetSafely() {
      pruneRan = false;
      ourResetPending = true;

      label44: {
         try {
            TrainClientRegistry.reset();
            break label44;
         } catch (Throwable var4) {
            fail(var4);
         } finally {
            ourResetPending = false;
         }

         return;
      }

      if (!pruneRan) {
         fail(new IllegalStateException("TrainClientRegistry.reset() ran but the mixin never fired"));
      }

   }

   public static void fail(Throwable t) {
      if (!disabled) {
         disabled = true;

         try {
            MtrHeadlightClient.LOGGER.warn("[lte] built-in train filter disabled: {}", String.valueOf(t));
         } catch (Throwable var2) {
         }

      }
   }
}

package dev.js.mtrheadlight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import dev.js.mtrheadlight.forge.platform.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;

public final class BeamRelight {
   private static final double MOVE_BLOCKS = 1.0D;
   private static final int EVERY_TICKS = 10;
   private static final long COOLDOWN_MS = 200L;
   private static final int MAX_PER_SEC = 40;
   private static final int MAX_CANDIDATES = 96;
   private static final int LOG_TICKS = 100;
   private static final int MAX_CACHED = 256;
   private static String skipReason;
   private static boolean installed;
   private static String announcedSkip;
   private static long ticks;
   private static long lastSweepTick = Long.MIN_VALUE;
   private static long lastVersion = Long.MIN_VALUE;
   private static double[] lastStarts = new double[0];
   private static final Map<Long, Integer> baked = new HashMap();
   private static final Map<Long, Long> lastMarkMs = new HashMap();
   private static long windowStartTick = Long.MIN_VALUE;
   private static long windowStartMs;
   private static int windowSections;
   private static int windowThrottled;
   private static int windowSweeps;
   private static long quotaUsed;
   private static long quotaWindowStartMs;

   private BeamRelight() {
   }

   public static boolean available() {
      return installed;
   }

   public static void install() {
      String sodium = modPresent("sodium") ? "sodium" : (modPresent("embeddium") ? "embeddium" : null);
      if (sodium != null) {
         skipReason = sodium + " present (replaces the vanilla section mesher)";
         installed = false;
      } else {
         installed = true;
      }
   }

   private static boolean modPresent(String id) {
      try {
         return FabricLoader.getInstance().isModLoaded(id);
      } catch (Throwable var2) {
         return false;
      }
   }

   public static void tick(HeadlightConfig cfg) {
      long tick = ++ticks;
      boolean on = cfg != null && cfg.drawBlockLight();
      if (!on) {
         if (!baked.isEmpty()) {
            flushAll();
         }

         lastStarts = new double[0];
         lastVersion = Long.MIN_VALUE;
      } else if (skipReason != null) {
         announceSkip();
      } else if (installed && BeamBlockLight.active()) {
         if (BeamBlockLight.alive() && BeamBlockLight.regionSeen()) {
            maybeLog(tick);
            long version = BeamBlockLight.version();
            boolean moved = version != lastVersion;
            int sinceSweep = lastSweepTick == Long.MIN_VALUE ? Integer.MAX_VALUE : (int)(tick - lastSweepTick);
            if (!moved) {
               if (sinceSweep < 10) {
                  return;
               }
            } else if (sinceSweep < 10 && maxMove() < 1.0D) {
               return;
            }

            lastVersion = version;
            lastSweepTick = tick;
            lastStarts = starts();
            ++windowSweeps;
            if (windowStartTick == Long.MIN_VALUE) {
               windowStartTick = tick;
               windowStartMs = System.currentTimeMillis();
            }

            LevelRenderer renderer = renderer();
            if (renderer != null) {
               sweep(renderer, cfg);
            }
         } else {
            if (announcedSkip == null) {
               announcedSkip = "hook";
               MtrHeadlightClient.LOGGER.warn("[lte] beam light: vanilla lightmap hook unusable (called={} regionSeen={}) - the beam stays a drawn sheet only", BeamBlockLight.calls(), BeamBlockLight.regionSeen());
            }

         }
      } else {
         if (!baked.isEmpty()) {
            flushAll();
         }

      }
   }

   private static void sweep(LevelRenderer renderer, HeadlightConfig cfg) {
      Set<Long> candidates = new HashSet(baked.keySet());
      if (!addRange(candidates, cfg.beamBlockLightRadius)) {
         ++windowThrottled;
      } else if (!candidates.isEmpty()) {
         if (candidates.size() > 96) {
            ++windowThrottled;
         } else {
            long now = System.currentTimeMillis();

            for(Long key : candidates) {
               int sx = SectionPos.x(key);
               int sy = SectionPos.y(key);
               int sz = SectionPos.z(key);
               int signature = BeamBlockLight.signature(sx, sy, sz);
               Integer previous = (Integer)baked.get(key);
               if (signature == 0) {
                  if (previous != null) {
                     if (mayMark(key, now)) {
                        mark(renderer, sx, sy, sz);
                        baked.remove(key);
                     } else {
                        ++windowThrottled;
                     }
                  }
               } else if (previous == null || previous != signature) {
                  if (!mayMark(key, now)) {
                     ++windowThrottled;
                  } else {
                     mark(renderer, sx, sy, sz);
                     baked.put(key, signature);
                  }
               }
            }

         }
      }
   }

   private static boolean addRange(Set<Long> out, int radius) {
      double[] all = beamsSnapshot();

      for(int i = 0; i + 8 <= all.length; i += 8) {
         double sx = all[i];
         double sy = all[i + 1];
         double sz = all[i + 2];
         double ex = all[i + 3];
         double ey = all[i + 4];
         double ez = all[i + 5];
         double r = all[i + 6];
         int minX = SectionPos.blockToSectionCoord((int)Math.floor(min(sx, ex) - r));
         int maxX = SectionPos.blockToSectionCoord((int)Math.floor(max(sx, ex) + r));
         int minZ = SectionPos.blockToSectionCoord((int)Math.floor(min(sz, ez) - r));
         int maxZ = SectionPos.blockToSectionCoord((int)Math.floor(max(sz, ez) + r));
         int minY = SectionPos.blockToSectionCoord((int)Math.floor(min(sy, ey) - 4.0D - 12.0D));
         int maxY = SectionPos.blockToSectionCoord((int)Math.floor(max(sy, ey) + 4.0D + 4.0D));

         for(int x = minX; x <= maxX; ++x) {
            for(int z = minZ; z <= maxZ; ++z) {
               for(int y = minY; y <= maxY; ++y) {
                  out.add(SectionPos.asLong(x, y, z));
                  if (out.size() > 96) {
                     return false;
                  }
               }
            }
         }
      }

      return true;
   }

   private static double[] beamsSnapshot() {
      return BeamBlockLight.snapshot();
   }

   private static boolean mayMark(Long key, long now) {
      Long last = (Long)lastMarkMs.get(key);
      if (last != null && now - last < 200L) {
         return false;
      } else {
         if (now - quotaWindowStartMs >= 1000L) {
            quotaWindowStartMs = now;
            quotaUsed = 0L;
         }

         if (quotaUsed >= 40L) {
            return false;
         } else {
            ++quotaUsed;
            if (lastMarkMs.size() > 256) {
               lastMarkMs.clear();
            }

            lastMarkMs.put(key, now);
            return true;
         }
      }
   }

   private static void mark(LevelRenderer renderer, int sx, int sy, int sz) {
      try {
         renderer.setBlocksDirty(sx << 4, sy << 4, sz << 4, (sx << 4) + 15, (sy << 4) + 15, (sz << 4) + 15);
         ++windowSections;
      } catch (Throwable var5) {
      }

   }

   private static void flushAll() {
      LevelRenderer renderer = renderer();
      if (renderer != null) {
         for(Long key : baked.keySet()) {
            try {
               int sx = SectionPos.x(key);
               int sy = SectionPos.y(key);
               int sz = SectionPos.z(key);
               renderer.setBlocksDirty(sx << 4, sy << 4, sz << 4, (sx << 4) + 15, (sy << 4) + 15, (sz << 4) + 15);
            } catch (Throwable var6) {
               break;
            }
         }
      }

      baked.clear();
      lastMarkMs.clear();
   }

   public static void reportNow() {
      if (windowStartTick != Long.MIN_VALUE) {
         windowStartTick = ticks - 100L;
         maybeLog(ticks);
         windowStartTick = ticks;
      }
   }

   private static void maybeLog(long tick) {
      if (windowStartTick != Long.MIN_VALUE) {
         if (tick - windowStartTick >= 100L) {
            long[] stats = BeamBlockLight.drainStats();
            double ms = (double)stats[1] / 1000000.0D;
            MtrHeadlightClient.LOGGER.info("[lte] beam light: sections={} verts={} ms={} sweeps={} cand={} throttled={} beams={}", new Object[]{windowSections, stats[0], String.format(Locale.ROOT, "%.2f", ms), windowSweeps, baked.size(), windowThrottled, BeamBlockLight.count()});
            windowSections = 0;
            windowSweeps = 0;
            windowThrottled = 0;
            windowStartTick = tick;
            windowStartMs = System.currentTimeMillis();
         }
      }
   }

   private static void announceSkip() {
      if (announcedSkip == null) {
         announcedSkip = skipReason;
         MtrHeadlightClient.LOGGER.warn("[lte] beam light: skipped, {} - the beam stays a drawn sheet only", skipReason);
      }

   }

   private static LevelRenderer renderer() {
      try {
         Minecraft client = Minecraft.getInstance();
         return client == null ? null : client.levelRenderer;
      } catch (Throwable var1) {
         return null;
      }
   }

   private static double[] starts() {
      double[] all = beamsSnapshot();
      double[] out = new double[all.length / 8 * 3];
      int o = 0;

      for(int i = 0; i + 8 <= all.length; i += 8) {
         out[o++] = all[i];
         out[o++] = all[i + 1];
         out[o++] = all[i + 2];
      }

      return out;
   }

   private static double maxMove() {
      double[] now = starts();
      if (now.length != lastStarts.length) {
         return Double.MAX_VALUE;
      } else {
         double best = 0.0D;

         for(int i = 0; i + 2 < now.length; i += 3) {
            double dx = now[i] - lastStarts[i];
            double dy = now[i + 1] - lastStarts[i + 1];
            double dz = now[i + 2] - lastStarts[i + 2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > best) {
               best = d;
            }
         }

         return best;
      }
   }

   private static double min(double a, double b) {
      return a < b ? a : b;
   }

   private static double max(double a, double b) {
      return a > b ? a : b;
   }
}

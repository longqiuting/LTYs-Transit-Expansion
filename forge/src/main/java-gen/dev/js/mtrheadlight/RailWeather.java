package dev.js.mtrheadlight;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public final class RailWeather {
   public static final double PROBE_LIFT = 1.0D;
   public static final int SKY_LIGHT_MIN = 14;
   public static final int DEBOUNCE_TICKS = 10;
   public static final long DEBOUNCE_MS = 500L;
   public static final long NO_KEY = Long.MIN_VALUE;
   private static final int DEBOUNCE_MAX_ENTRIES = 256;
   private static final Map<Long, RailWeather.Debounce> DEBOUNCE = new ConcurrentHashMap();
   public static final long NIGHT_START = 13000L;
   public static final long NIGHT_END = 23000L;
   public static final long DAY_LENGTH = 24000L;
   public static final float DARK_SKY = 0.5F;
   public static final float RAIN_GRADIENT = 0.2F;

   private RailWeather() {
   }

   public static int skyLevel(ClientLevel world, Vec3 pos) {
      if (world != null && pos != null) {
         int x = (int)Math.floor(pos.x);
         int z = (int)Math.floor(pos.z);

         try {
            if (!world.hasChunk(x >> 4, z >> 4)) {
               return -1;
            }
         } catch (Throwable var10) {
         }

         int py = (int)Math.floor(pos.y + 1.0D);
         BlockPos probe = new BlockPos(x, py, z);

         try {
            return world.getBrightness(LightLayer.SKY, probe);
         } catch (Throwable var9) {
            try {
               return probe.getY() >= world.getHeight(Types.MOTION_BLOCKING, x, z) ? 15 : 0;
            } catch (Throwable var8) {
               return -1;
            }
         }
      } else {
         return -1;
      }
   }

   public static boolean openSky(ClientLevel world, Vec3 pos) {
      int level = skyLevel(world, pos);
      return level < 0 || level >= 14;
   }

   private static Vec3 midProbe(TrainPose pose) {
      int cars = pose.cars();
      if (cars <= 0) {
         return null;
      } else {
         int mid = cars / 2;
         return pose.carStart(mid).add(pose.carEnd(mid)).scale(0.5D);
      }
   }

   public static int skyLevelNose(ClientLevel world, TrainPose pose) {
      return pose == null ? -1 : skyLevel(world, pose.front());
   }

   public static int skyLevelMid(ClientLevel world, TrainPose pose) {
      Vec3 centre = pose == null ? null : midProbe(pose);
      return centre == null ? -1 : skyLevel(world, centre);
   }

   public static boolean openSkyNow(ClientLevel world, TrainPose pose) {
      if (world != null && pose != null) {
         int nose = skyLevelNose(world, pose);
         if (nose >= 0 && nose < 14) {
            return false;
         } else {
            int mid = skyLevelMid(world, pose);
            return mid < 0 || mid >= 14;
         }
      } else {
         return true;
      }
   }

   public static boolean openSky(ClientLevel world, TrainPose pose, long trainId) {
      boolean raw = openSkyNow(world, pose);
      if (trainId == Long.MIN_VALUE) {
         return raw;
      } else {
         long now = System.currentTimeMillis();
         RailWeather.Debounce state = (RailWeather.Debounce)DEBOUNCE.get(trainId);
         if (state == null) {
            if (DEBOUNCE.size() >= 256) {
               DEBOUNCE.clear();
            }

            state = new RailWeather.Debounce();
            state.value = raw;
            state.candidate = raw;
            state.candidateSince = now;
            DEBOUNCE.put(trainId, state);
            return raw;
         } else if (raw == state.value) {
            state.candidate = raw;
            state.candidateSince = now;
            return state.value;
         } else if (raw != state.candidate) {
            state.candidate = raw;
            state.candidateSince = now;
            return state.value;
         } else {
            if (now - state.candidateSince >= 500L) {
               state.value = raw;
            }

            return state.value;
         }
      }
   }

   public static void reset() {
      DEBOUNCE.clear();
   }

   public static boolean isNight(ClientLevel world) {
      try {
         if (world == null) {
            return false;
         } else {
            long tod = world.getDayTime() % 24000L;
            if (tod < 0L) {
               tod += 24000L;
            }

            return tod >= 13000L && tod < 23000L;
         }
      } catch (Throwable var3) {
         return false;
      }
   }

   public static float skyBrightness(ClientLevel world) {
      try {
         if (world == null) {
            return 1.0F;
         } else {
            float angle = world.getSunAngle(1.0F);
            float brightness = 1.0F - (Mth.cos(angle) * 2.0F + 0.2F);
            brightness = Mth.clamp(brightness, 0.0F, 1.0F);
            brightness = 1.0F - brightness;
            brightness *= 1.0F - world.getRainLevel(1.0F) * 5.0F / 16.0F;
            brightness *= 1.0F - world.getThunderLevel(1.0F) * 5.0F / 16.0F;
            return brightness * 0.8F + 0.2F;
         }
      } catch (Throwable var3) {
         return 1.0F;
      }
   }

   public static boolean raining(ClientLevel world) {
      try {
         return world != null && world.isRaining() && world.getRainLevel(1.0F) > 0.2F;
      } catch (Throwable var2) {
         return false;
      }
   }

   public static float rainGradient(ClientLevel world) {
      try {
         return world == null ? 0.0F : world.getRainLevel(1.0F);
      } catch (Throwable var2) {
         return -1.0F;
      }
   }

   public static boolean weatherRaining(ClientLevel world) {
      try {
         return world != null && world.isRaining();
      } catch (Throwable var2) {
         return false;
      }
   }

   public static boolean thundering(ClientLevel world) {
      try {
         return world != null && world.isThundering() && world.getThunderLevel(1.0F) > 0.2F;
      } catch (Throwable var2) {
         return false;
      }
   }

   public static boolean dark(ClientLevel world) {
      return isNight(world) || skyBrightness(world) < 0.5F || raining(world) || thundering(world);
   }

   public static boolean lightsNeeded(ClientLevel world, TrainPose pose, long trainId) {
      if (dark(world)) {
         return true;
      } else {
         return !openSky(world, pose, trainId);
      }
   }

   private static final class Debounce {
      boolean value;
      boolean candidate;
      long candidateSince;
   }
}

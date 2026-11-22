package dev.js.lte;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class DebugProbe {
   public static final int SPRAY_DURATION_TICKS = 100;
   public static final int SPRAY_PER_TICK = 8;
   public static final double SPRAY_AHEAD = 2.0D;
   private static final double SPRAY_AHEAD_OLD = 3.0D;
   private static final int LOG_TICKS = 100;
   private static final int MAX_MODEL_LINES = 3;
   private static volatile boolean cone;
   private static volatile boolean coneBoost;
   private static boolean spray;
   private static int sprayTicksLeft;
   private static long sprayedTotal;
   private static long lastSprayed;
   private static long lastCalls;
   private static long lastQuads;
   private static long lastProbeQuads;
   private static long lastVolumeBeams;
   private static long flameEyeTotal;
   private static long flameSelfTotal;
   private static long cloudOldTotal;
   private static long pmDirectTotal;
   private static long airSkippedTotal;
   private static long lastFlameEye;
   private static long lastFlameSelf;
   private static long lastCloudOld;
   private static long lastPmDirect;
   private static long lastAirSkipped;
   private static final long[] lastGates = new long[10];
   private static int ticks;
   private static int autoSprayAt;
   private static boolean autoChecked;

   private DebugProbe() {
   }

   public static boolean coneEnabled() {
      return cone;
   }

   public static void setCone(boolean on) {
      cone = on;
      LteLog.LOGGER.info("[lte] debug: cone probe = {}", on);
   }

   public static boolean coneBoostEnabled() {
      return coneBoost;
   }

   public static void setConeBoost(boolean on) {
      coneBoost = on;
      LteLog.LOGGER.info("[lte] debug: coneBoost = {} (0.45 flat alpha, no attenuation)", on);
   }

   public static boolean sprayEnabled() {
      return spray;
   }

   public static int setSpray(boolean on) {
      spray = on;
      sprayTicksLeft = on ? 100 : 0;
      if (on) {
         flameEyeTotal = 0L;
         flameSelfTotal = 0L;
         cloudOldTotal = 0L;
         pmDirectTotal = 0L;
         airSkippedTotal = 0L;
         lastFlameEye = 0L;
         lastFlameSelf = 0L;
         lastCloudOld = 0L;
         lastPmDirect = 0L;
         lastAirSkipped = 0L;
      }

      LteLog.LOGGER.info("[lte] debug spray: spray probe = {}", on);
      return sprayTicksLeft;
   }

   public static int sprayTicksLeft() {
      return Math.max(0, sprayTicksLeft);
   }

   public static void tick() {
      Minecraft client = Minecraft.getInstance();
      ++ticks;
      autoStart(client);
      if (spray) {
         spawn(client);
      }

      if (ticks % 100 == 0) {
         log(client);
      }

   }

   private static void autoStart(Minecraft client) {
      if (autoChecked) {
         if (autoSprayAt > 0) {
            if (client != null && client.player != null && client.level != null) {
               if (ticks >= autoSprayAt) {
                  autoSprayAt = 0;
                  setSpray(true);
                  LteLog.LOGGER.info("[lte] debug spray: auto-started by -Dlte.debugSpray");
               }
            }
         }
      } else {
         autoChecked = true;

         try {
            String raw = System.getProperty("lte.debugSpray");
            if (raw == null || raw.isEmpty() || "0".equals(raw) || "off".equalsIgnoreCase(raw)) {
               return;
            }

            int delay;
            try {
               delay = Integer.parseInt(raw.trim());
            } catch (NumberFormatException var4) {
               delay = 120;
            }

            autoSprayAt = ticks + Math.max(20, delay);
            LteLog.LOGGER.info("[lte] debug spray: armed by -Dlte.debugSpray, fires in {} ticks", autoSprayAt - ticks);
         } catch (Throwable var5) {
         }

      }
   }

   private static void spawn(Minecraft client) {
      LocalPlayer player = client.player;
      ClientLevel world = client.level;
      if (player != null && world != null) {
         Vec3 eye = player.getEyePosition();
         Vec3 look = player.getViewVector(1.0F);
         Vec3 flat = new Vec3(look.x, 0.0D, look.z);
         flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
         double cx = eye.x + flat.x * 2.0D;
         double cy = eye.y;
         double cz = eye.z + flat.z * 2.0D;

         for(int i = 0; i < 8; ++i) {
            double angle = (Math.PI * 2D) * (double)i / 8.0D + tickPhase(world.random.nextDouble());
            double radius = 0.45D + 0.35D * world.random.nextDouble();
            double x = cx + Math.cos(angle) * radius;
            double y = cy + (world.random.nextDouble() - 0.5D) * 0.7D;
            double z = cz + Math.sin(angle) * radius;
            if (!isAir(world, x, y, z)) {
               ++airSkippedTotal;
            } else {
               world.addParticle(ParticleTypes.FLAME, true, x, y, z, 0.0D, 0.004D, 0.0D);
               ++flameEyeTotal;
               ++sprayedTotal;
            }
         }

         double sx = eye.x + flat.x * 0.8D;
         double sy = eye.y - 0.05D;
         double sz = eye.z + flat.z * 0.8D;
         world.addParticle(ParticleTypes.FLAME, true, sx, sy, sz, 0.0D, 0.0D, 0.0D);
         ++flameSelfTotal;
         ++sprayedTotal;
         double ox = player.getX() + flat.x * 3.0D;
         double oy = player.getY() + 0.15D;
         double oz = player.getZ() + flat.z * 3.0D;
         world.addParticle(ParticleTypes.CLOUD, true, ox, oy, oz, 0.0D, 0.02D, 0.0D);
         ++cloudOldTotal;
         ++sprayedTotal;
         if (addDirect(client, ParticleTypes.FLAME, cx, cy, cz)) {
            ++pmDirectTotal;
         }

         if (--sprayTicksLeft <= 0) {
            spray = false;
            sprayTicksLeft = 0;
            say(player, "[LTE] \u6c34\u96fe\u63a2\u9488\u7ed3\u675f\uff085 \u79d2\uff09\uff1aFLAME \u773c\u5e73\u5708 " + flameEyeTotal + " \u9897\uff08\u7a7a\u6c14\u8df3\u8fc7 " + airSkippedTotal + "\uff09\u3001\u773c\u524d\u5bf9\u7167 " + flameSelfTotal + "\u3001\u65e7\u4f4d\u7f6e CLOUD " + cloudOldTotal + "\u3001\u76f4\u63d2 ParticleManager " + pmDirectTotal);
            String culled = ParticleCull.reason();
            if (culled != null) {
               say(player, "[LTE] " + culled);
            }

            LteLog.LOGGER.info("[lte] debug spray: done eye={} self={} cloudOld={} pmDirect={} airSkipped={} pm={} culled={}", new Object[]{flameEyeTotal, flameSelfTotal, cloudOldTotal, pmDirectTotal, airSkippedTotal, particleManagerDebug(client), ParticleCull.culled().isEmpty() ? "none" : ParticleCull.culled()});
         }

      }
   }

   private static double tickPhase(double random) {
      return (double)(ticks % 40) * 0.157D + random * 0.1D;
   }

   private static boolean isAir(ClientLevel world, double x, double y, double z) {
      try {
         return world.isEmptyBlock(new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)));
      } catch (Throwable var8) {
         return true;
      }
   }

   private static boolean addDirect(Minecraft client, ParticleOptions effect, double x, double y, double z) {
      if (client != null && client.particleEngine != null) {
         Method m = findAddParticle(client.particleEngine);
         if (m == null) {
            return false;
         } else {
            try {
               m.invoke(client.particleEngine, effect, x, y, z, 0.0D, 0.01D, 0.0D);
               return true;
            } catch (Throwable var10) {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   private static Method findAddParticle(Object manager) {
      for(Method m : manager.getClass().getMethods()) {
         if ("addParticle".equals(m.getName())) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 7 && p[0] == ParticleOptions.class) {
               boolean ok = true;

               for(int i = 1; i < 7; ++i) {
                  if (p[i] != Double.TYPE) {
                     ok = false;
                     break;
                  }
               }

               if (ok) {
                  return m;
               }
            }
         }
      }

      return null;
   }

   private static String particleManagerDebug(Minecraft client) {
      if (client != null && client.particleEngine != null) {
         try {
            Method m = client.particleEngine.getClass().getMethod("getDebugString");
            Object value = m.invoke(client.particleEngine);
            return value == null ? "-" : value.toString().replace(' ', '_');
         } catch (Throwable var9) {
            try {
               Field f = client.particleEngine.getClass().getField("particles");
               Object value = f.get(client.particleEngine);
               if (value instanceof Map) {
                  Map<?, ?> map = (Map)value;
                  int total = 0;

                  for(Object queue : map.values()) {
                     if (queue instanceof Collection) {
                        Collection<?> c = (Collection)queue;
                        total += c.size();
                     }
                  }

                  return Integer.toString(total);
               }
            } catch (Throwable var8) {
            }

            return "?";
         }
      } else {
         return "-";
      }
   }

   private static void log(Minecraft client) {
      long calls = ProtoBeamCone.callsTotal();
      long quads = ProtoBeamCone.quadsTotal();
      long probeQuads = ProtoBeamCone.probeQuadsTotal();
      long volumeBeams = ProtoBeamCone.volumeBeamsTotal();
      int beams = ProtoBeamCone.liveBeams();
      long[] gates = new long[10];

      for(int i = 1; i <= 9; ++i) {
         gates[i] = ProtoBeamCone.gateTotal(i) - lastGates[i];
      }

      int slots = 0;
      List<String> models = new ArrayList();
      Set<String> seenModels = new LinkedHashSet();

      try {
         for(TrainClient train : new ArrayList<TrainClient>(ClientData.TRAINS)) {
            if (train != null) {
               slots += VehicleLights.slotCount(train);
               if (models.size() < 3) {
                  String id = train.baseTrainType == null ? "?" : train.baseTrainType;
                  if (seenModels.add(id)) {
                     models.add(VehicleLights.debugLine(train));
                  }
               }
            }
         }
      } catch (Throwable var17) {
         LteLog.LOGGER.warn("[lte] debug: model scan failed", var17);
      }

      LteLog.LOGGER.info("[lte] debug: coneCalls={} coneQuads={} probeQuads={} volBeams={} spraySpawned={} beams={} slots={} dmin={} range={} g1={} g2={} g3={} g4={} g5={} g6={} g7={} g8={} g9={} path={} source={}", new Object[]{calls - lastCalls, quads - lastQuads, probeQuads - lastProbeQuads, volumeBeams - lastVolumeBeams, sprayedTotal - lastSprayed, beams, slots, String.format(Locale.ROOT, "%.1f", ProtoBeamCone.nearestBeamDistance()), String.format(Locale.ROOT, "%.0f", ProtoBeamCone.beamRange()), gates[1], gates[2], gates[3], gates[4], gates[5], gates[6], gates[7], gates[8], gates[9], ProtoBeamCone.pathName(), ProtoBeamCone.sourceName()});
      if (spray || flameEyeTotal > 0L || flameSelfTotal > 0L || pmDirectTotal > 0L) {
         LteLog.LOGGER.info("[lte] debug spray: eye={} self={} cloudOld={} pmDirect={} airSkipped={} pm={} culled={}", new Object[]{flameEyeTotal - lastFlameEye, flameSelfTotal - lastFlameSelf, cloudOldTotal - lastCloudOld, pmDirectTotal - lastPmDirect, airSkippedTotal - lastAirSkipped, particleManagerDebug(client), ParticleCull.culled().isEmpty() ? "none" : ParticleCull.culled()});
         lastFlameEye = flameEyeTotal;
         lastFlameSelf = flameSelfTotal;
         lastCloudOld = cloudOldTotal;
         lastPmDirect = pmDirectTotal;
         lastAirSkipped = airSkippedTotal;
      }

      lastCalls = calls;
      lastQuads = quads;
      lastProbeQuads = probeQuads;
      lastVolumeBeams = volumeBeams;
      lastSprayed = sprayedTotal;

      for(int i = 1; i <= 9; ++i) {
         lastGates[i] = ProtoBeamCone.gateTotal(i);
      }

      if (beams == 0 && gates[3] + gates[4] + gates[5] > 0L) {
         for(String line : models) {
            LteLog.LOGGER.info("[lte] debug: {}", line);
         }
      }

   }

   private static void say(LocalPlayer player, String message) {
      try {
         player.displayClientMessage(Component.nullToEmpty(message), false);
      } catch (Throwable var3) {
      }

   }

   public static String status() {
      return "cone=" + (cone ? "on" : "off") + " coneBoost=" + (coneBoost ? "on" : "off") + " spray=" + (spray ? "on(" + sprayTicksLeft + "t)" : "off") + " beams=" + ProtoBeamCone.liveBeams();
   }
}

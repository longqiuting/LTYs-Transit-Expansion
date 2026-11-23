package dev.js.lte;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import dev.js.lte.forge.platform.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;

public final class BeamGlow {
   private static final int LAT_SEGMENTS = 4;
   private static final int MAX_LONG = 12;
   private static final int MIN_LONG = 3;
   private static final double SEGMENT_BLOCKS = 1.6D;
   private static final double LENGTH_DECAY = 2.8D;
   private static final double HEAD_FADE_BLOCKS = 2.0D;
   private static final double TAIL_FADE_BLOCKS = 1.5D;
   private static final int SAMPLES = 8;
   private static final double MAX_DEVIATION = 3.0D;
   private static final int VERTEX_STRIDE = 7;
   private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
   private static float[] vertices = new float[0];
   private static int vertexCount;
   private static final double[] sampleHeight = new double[8];
   private static final boolean DIAG = false;
   private static long diagAt = System.currentTimeMillis();
   private static int dCalls;
   private static int dTrains;
   private static int dPoseOk;
   private static int dDrawn;
   private static int dSubmit;
   private static int dCars;
   private static int dBeams;
   private static int dDirFix;
   private static boolean windowOn;
   private static int sCfg;
   private static int sProbe;
   private static int sPass;
   private static int sWorld;
   private static int sMatrix;
   private static int sNotDrawable;
   private static int sEff;
   private static int sParked;
   private static int sNoPose;
   private static int sFar;
   private static int sNoDir;
   private static int sAuto;

   private static void diag(String gate) {
      long now = System.currentTimeMillis();
      if (now - diagAt >= 5000L) {
         diagAt = now;
         if (windowOn && LteDebug.lights()) {
            HeadlightConfig cfg = MtrHeadlightClient.config();
            if (cfg == null) {
               MtrHeadlightClient.LOGGER.info("[lte] glow: cars={} beams={} dirFix={}", new Object[]{dCars, dBeams, dDirFix});
            } else {
               MtrHeadlightClient.LOGGER.info("[lte] glow: band={} flare={} cars={} beams={} dirFix={}", new Object[]{cfg.drawBand(), cfg.drawFlare(), dCars, dBeams, dDirFix});
            }
         }

         windowOn = false;
         dCars = 0;
         dBeams = 0;
         dDirFix = 0;
      }
   }

   private BeamGlow() {
   }

   public static void render(WorldRenderContext context, boolean latePass) {
      ++dCalls;
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if (cfg != null && cfg.drawBand() && !(cfg.beamAlpha <= 0.0D)) {
         windowOn = true;

         boolean shaders;
         try {
            shaders = ShaderPackProbe.active();
         } catch (Throwable var26) {
            diag("probe");
            return;
         }

         if (shaders != latePass) {
            diag("pass");
         } else {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.player != null && client.gameRenderer != null) {
               PoseStack matrices = context.matrixStack();
               if (matrices == null) {
                  diag("matrix");
               } else {
                  ClientLevel world = client.level;
                  Vec3 shift = client.gameRenderer.getMainCamera().getPosition();
                  Vec3 eye = client.player.position();
                  vertexCount = 0;

                  for(TrainClient train : snapshotTrains()) {
                     ++dTrains;
                     if (!drawable(cfg, train)) {
                        diag("drawable");
                     } else {
                        HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
                        if (eff.enabled && eff.beamBand) {
                           if (!DepartureState.departed(train)) {
                              diag("parked");
                           } else {
                              boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
                              TrainPose pose = TrainPose.ofShared(train, reversed);
                              Vec3 nose = pose.front();
                              Vec3 rear = pose.rear();
                              if (nose == Vec3.ZERO && rear == Vec3.ZERO) {
                                 diag("pose");
                              } else {
                                 ++dPoseOk;
                                 if (!HeadlightSwitch.judge(world, pose, cfg, train).lit()) {
                                    diag("auto");
                                 } else if (nose.distanceTo(eye) > eff.beamDistance && rear.distanceTo(eye) > eff.beamDistance) {
                                    diag("far");
                                 } else {
                                    int cars = Math.max(1, pose.cars());
                                    BeamGeom headGeom = headGeom(cfg, eff, pose);
                                    BeamGeom tailGeom = tailGeom(cfg, eff, pose);
                                    Vec3 headDir = headGeom.axis;
                                    Vec3 tailDir = tailGeom.axis;
                                    if (headDir == Vec3.ZERO && tailDir == Vec3.ZERO) {
                                       diag("dir");
                                    } else {
                                       dCars += cars;
                                       if (pose.carBodyFromChord(0)) {
                                          ++dDirFix;
                                       }

                                       if (pose.carBodyFromChord(cars - 1)) {
                                          ++dDirFix;
                                       }

                                       double halfWidth = Math.max(0.25D, eff.beamWidth) * 0.5D;
                                       float peak = (float)Math.min(1.0D, cfg.beamAlpha);
                                       int before = vertexCount;
                                       beam(world, headGeom, halfWidth, peak, shift, 2.0D);
                                       if (vertexCount > before) {
                                          ++dBeams;
                                       }

                                       int afterHead = vertexCount;
                                       beam(world, tailGeom, halfWidth, peak, shift, 1.5D);
                                       if (vertexCount > afterHead) {
                                          ++dBeams;
                                       }

                                       if (vertexCount > before) {
                                          ++dDrawn;
                                       }
                                    }
                                 }
                              }
                           }
                        } else {
                           diag("eff");
                        }
                     }
                  }

                  if (vertexCount > 0) {
                     GlowCompat.draw(vertices, vertexCount, matrices);
                     ++dSubmit;
                     vertexCount = 0;
                  }

                  diag((String)null);
               }
            } else {
               diag("world");
            }
         }
      } else {
         diag("cfg");
      }
   }

   static Vec3 endDir(TrainPose pose, int car, boolean head) {
      Vec3 d = pose.carBodyForward(car);
      if (d == Vec3.ZERO) {
         d = pose.carForward(car);
      }

      if (d == Vec3.ZERO) {
         return Vec3.ZERO;
      } else {
         return head ? d : d.scale(-1.0D);
      }
   }

   static BeamGeom headGeom(HeadlightConfig cfg, HeadlightConfig.Effective eff, TrainPose pose) {
      return BeamGeom.of(pose.front(), endDir(pose, 0, true), cfg.headOffsetClamped(), eff.beamHeadLength, Math.max(0.0D, cfg.beamLift) + eff.beamHeadHeight, eff.beamHeadColor);
   }

   static BeamGeom tailGeom(HeadlightConfig cfg, HeadlightConfig.Effective eff, TrainPose pose) {
      int last = Math.max(0, pose.cars() - 1);
      return BeamGeom.of(pose.rear(), endDir(pose, last, false), cfg.tailOffsetClamped(), eff.beamTailLength, Math.max(0.0D, cfg.beamLift) + eff.beamTailHeight, eff.beamTailColor);
   }

   private static void beam(ClientLevel world, BeamGeom geom, double halfWidth, float peak, Vec3 shift, double fadeInBlocks) {
      Vec3 origin = geom.origin;
      Vec3 dir = geom.axis;
      double length = geom.length;
      double extraHeight = geom.height;
      int rgb = geom.rgb;
      if (!(length <= 0.05D)) {
         double fadeIn = Math.max(0.0D, Math.min(length * 0.5D, fadeInBlocks));
         Vec3 right = dir.cross(UP);
         if (!(right.lengthSqr() < 1.0E-8D)) {
            right = right.normalize().scale(halfWidth);
            sampleHeight[0] = startHeight(world, origin);

            for(int k = 1; k < 8; ++k) {
               double f = (double)k / 7.0D;
               double t = length * f;
               double x = origin.x + dir.x * t;
               double z = origin.z + dir.z * t;
               double expected = origin.y + dir.y * t;
               double ground = groundAt(world, x, z);
               sampleHeight[k] = !Double.isNaN(ground) && Math.abs(ground - expected) <= 3.0D ? ground : origin.y;
            }

            int along = Math.max(3, Math.min(12, (int)Math.ceil(length / 1.6D)));
            float red = (float)(rgb >> 16 & 255) / 255.0F;
            float green = (float)(rgb >> 8 & 255) / 255.0F;
            float blue = (float)(rgb & 255) / 255.0F;

            for(int i = 0; i < along; ++i) {
               double f0 = (double)i / (double)along;
               double f1 = (double)(i + 1) / (double)along;
               float a0 = peak * lengthAlpha(f0) * rampAlpha(length * f0, fadeIn);
               float a1 = peak * lengthAlpha(f1) * rampAlpha(length * f1, fadeIn);
               if (!(a0 <= 0.001F) || !(a1 <= 0.001F)) {
                  float x0 = (float)(origin.x + dir.x * length * f0 - shift.x);
                  float z0 = (float)(origin.z + dir.z * length * f0 - shift.z);
                  float y0 = (float)(interpolated(f0) + extraHeight - shift.y);
                  float x1 = (float)(origin.x + dir.x * length * f1 - shift.x);
                  float z1 = (float)(origin.z + dir.z * length * f1 - shift.z);
                  float y1 = (float)(interpolated(f1) + extraHeight - shift.y);

                  for(int j = 0; j < 4; ++j) {
                     double u0 = -1.0D + 2.0D * (double)j / 4.0D;
                     double u1 = -1.0D + 2.0D * (double)(j + 1) / 4.0D;
                     float lat0 = lateralAlpha(u0);
                     float lat1 = lateralAlpha(u1);
                     if (!(a0 * lat0 <= 0.001F) || !(a0 * lat1 <= 0.001F) || !(a1 * lat0 <= 0.001F) || !(a1 * lat1 <= 0.001F)) {
                        float ax = (float)(right.x * -u0);
                        float az = (float)(right.z * -u0);
                        float bx = (float)(right.x * -u1);
                        float bz = (float)(right.z * -u1);
                        quad(x0 + ax, y0, z0 + az, x0 + bx, y0, z0 + bz, x1 + bx, y1, z1 + bz, x1 + ax, y1, z1 + az, a0 * lat0, a0 * lat1, a1 * lat1, a1 * lat0, red, green, blue);
                     }
                  }
               }
            }

         }
      }
   }

   static double startHeight(ClientLevel world, Vec3 origin) {
      double ground = groundAt(world, origin.x, origin.z);
      return !Double.isNaN(ground) && Math.abs(ground - origin.y) <= 3.0D ? ground : origin.y;
   }

   private static double groundAt(ClientLevel world, double x, double z) {
      try {
         return (double)world.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
      } catch (Throwable var6) {
         return Double.NaN;
      }
   }

   private static double interpolated(double f) {
      double scaled = Math.max(0.0D, Math.min(1.0D, f)) * 7.0D;
      int k = Math.min(6, (int)Math.floor(scaled));
      double frac = Math.max(0.0D, Math.min(1.0D, scaled - (double)k));
      return sampleHeight[k] + (sampleHeight[k + 1] - sampleHeight[k]) * frac;
   }

   private static float lengthAlpha(double f) {
      double clamped = Math.max(0.0D, Math.min(1.0D, f));
      return (float)(Math.exp(-2.8D * clamped) * (1.0D - clamped * clamped * clamped));
   }

   private static float lateralAlpha(double u) {
      return (float)Math.max(0.0D, 1.0D - Math.abs(u));
   }

   private static float rampAlpha(double t, double fadeIn) {
      if (fadeIn <= 1.0E-4D) {
         return 1.0F;
      } else {
         double x = Math.max(0.0D, Math.min(1.0D, t / fadeIn));
         return (float)(x * x * (3.0D - 2.0D * x));
      }
   }

   private static void quad(float xa, float ya, float za, float xb, float yb, float zb, float xc, float yc, float zc, float xd, float yd, float zd, float alpha00, float alpha01, float alpha11, float alpha10, float red, float green, float blue) {
      int needed = vertexCount + 28;
      if (needed > vertices.length) {
         float[] grown = new float[Math.max(needed, vertices.length * 2 + 64)];
         System.arraycopy(vertices, 0, grown, 0, vertexCount);
         vertices = grown;
      }

      vertexCount = put(vertices, vertexCount, xa, ya, za, red, green, blue, alpha00);
      vertexCount = put(vertices, vertexCount, xb, yb, zb, red, green, blue, alpha01);
      vertexCount = put(vertices, vertexCount, xc, yc, zc, red, green, blue, alpha11);
      vertexCount = put(vertices, vertexCount, xd, yd, zd, red, green, blue, alpha10);
   }

   private static int put(float[] out, int index, float x, float y, float z, float red, float green, float blue, float alpha) {
      int i = index + 1;
      out[index] = x;
      out[i++] = y;
      out[i++] = z;
      out[i++] = red;
      out[i++] = green;
      out[i++] = blue;
      out[i++] = alpha;
      return i;
   }

   private static boolean drawable(HeadlightConfig cfg, TrainClient train) {
      if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0) {
         return !cfg.onlyTrains || train.transportMode == TransportMode.TRAIN;
      } else {
         return false;
      }
   }

   private static List<TrainClient> snapshotTrains() {
      try {
         return new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var1) {
         return List.of();
      }
   }
}

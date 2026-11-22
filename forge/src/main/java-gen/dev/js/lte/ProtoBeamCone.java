package dev.js.lte;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mtr.client.ClientData;
import mtr.client.DynamicTrainModel;
import mtr.data.TrainClient;
import dev.js.lte.forge.platform.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class ProtoBeamCone {
   private static final double HALF_ANGLE_DEG = 13.0D;
   private static final int SEGMENTS = 14;
   private static final double[] RINGS = new double[]{0.03D, 0.14D, 0.3D, 0.48D, 0.68D, 0.85D, 1.0D};
   private static final double[] SHELLS = new double[]{1.0D, 0.72D, 0.45D, 0.18D};
   private static final float[] SHELL_ALPHA = shellWeights(SHELLS);
   private static final double LENGTH_FALLOFF = 2.5D;
   private static final double VIEW_POWER = 1.5D;
   private static final double VIEW_FLOOR = 0.45D;
   private static final float MIN_ATTENUATION = 0.25F;
   private static final double GROUND_FADE_BLOCKS = 1.0D;
   private static final int GROUND_SCAN_BLOCKS = 3;
   private static final int VERTEX_STRIDE = 7;
   private static final boolean PROBE = Boolean.getBoolean("lte.coneprobe");
   private static final double PROBE_LENGTH = 7.0D;
   private static final double DEBUG_CONE_AHEAD = 6.0D;
   private static final double DEBUG_CONE_LENGTH = 12.0D;
   private static final double DEBUG_CONE_HALF_ANGLE_DEG = 20.0D;
   private static final double DEBUG_CONE_TILT_DEG = 12.0D;
   private static final float DEBUG_CONE_ALPHA = 0.45F;
   private static final List<ConeVolumeBeam> BEAMS = new ArrayList();
   private static final double MAX_BEAM_DISTANCE = 64.0D;
   private static final double MIN_BEAM_DISTANCE = 16.0D;
   private static final double MAX_BEAM_DISTANCE_LIMIT = 256.0D;
   private static final int MAX_CONE_BEAMS = 8;
   private static final int MAX_BEAM_CANDIDATES = 64;
   private static float[] vertices = new float[0];
   private static int vertexCount;
   private static double[] groundScratch = new double[0];
   private static final BlockPos.MutableBlockPos GROUND_POS = new BlockPos.MutableBlockPos();
   private static long lastLog = System.currentTimeMillis();
   private static int cCalls;
   private static int cTrains;
   private static int cModels;
   private static int cNoModel;
   private static int cNoSlot;
   private static int cDrawn;
   private static int cQuads;
   private static int cProbe;
   private static int cProbeQuads;
   private static int cVolumeBeams;
   private static int cNear;
   private static double cDmin = Double.MAX_VALUE;
   private static double cRange;
   private static final Set<Long> GLOW_LOGGED = new HashSet();
   private static boolean glowLast;
   private static final Set<String> BEAM_LOGGED = new HashSet();
   private static boolean boostLast;
   private static final int[] WC = new int[10];
   private static final long[] GATE = new long[10];
   private static long callsTotal;
   private static long quadsTotal;
   private static long probeQuadsTotal;
   private static long volumeBeamsTotal;
   private static int liveBeams;

   public static long callsTotal() {
      return callsTotal;
   }

   public static long quadsTotal() {
      return quadsTotal;
   }

   public static long probeQuadsTotal() {
      return probeQuadsTotal;
   }

   public static long volumeBeamsTotal() {
      return volumeBeamsTotal;
   }

   public static long gateTotal(int index) {
      return index >= 1 && index < GATE.length ? GATE[index] : 0L;
   }

   public static int liveBeams() {
      return liveBeams;
   }

   public static double nearestBeamDistance() {
      return cDmin == Double.MAX_VALUE ? -1.0D : cDmin;
   }

   public static double beamRange() {
      return cRange;
   }

   private static void gate(int index) {
      GATE[index]++;
      WC[index]++;
   }

   private ProtoBeamCone() {
   }

   public static void render(WorldRenderContext context, boolean latePass) {
      ++cCalls;
      ++callsTotal;
      Minecraft client = Minecraft.getInstance();
      if (client.level != null && client.player != null && client.gameRenderer != null) {
         boolean shaders;
         try {
            shaders = ShaderPackProbe.active();
         } catch (Throwable var49) {
            return;
         }

         if (shaders == latePass) {
            PoseStack matrices = context.matrixStack();
            if (matrices == null) {
               matrices = new PoseStack();
            }

            Vec3 shift = client.gameRenderer.getMainCamera().getPosition();
            Vec3 eye = client.player.getEyePosition();
            ClientLevel world = client.level;
            HeadlightConfig cfg = MtrHeadlightClient.config();
            if (DebugProbe.coneEnabled()) {
               debugCone(matrices, shift, eye, client.player.getViewVector(1.0F));
            }

            liveBeams = 0;
            if (cfg != null && !cfg.enabled) {
               gate(1);
            } else if (cfg != null && !cfg.beamCone && !cfg.beamConeAnySiding()) {
               gate(2);
            } else {
               double range = beamRange(cfg);
               boolean tailCones = cfg != null && cfg.coneOnTail;
               boolean boost = DebugProbe.coneBoostEnabled();
               if (boost != boostLast) {
                  boostLast = boost;
                  BEAM_LOGGED.clear();
               }

               float peak = boost ? 0.45F : (cfg == null ? 0.3F : (float)Math.max(0.02D, Math.min(0.5D, cfg.beamConeAlpha)));
               double length = cfg == null ? 16.0D : Math.max(4.0D, cfg.beamHeadLength);
               int defaultSteps = cfg == null ? 10 : cfg.beamConeSteps();
               boolean volume = false;
               BEAMS.clear();
               vertexCount = 0;
               if (PROBE) {
                  Vec3 look = client.player.getViewVector(1.0F).normalize();
                  Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
                  Vec3 apex = eye.add(look.scale(1.6D)).subtract(right.scale(1.3D));
                  Vec3 dir = right.scale(0.92D).add(look.scale(0.4D)).add(0.0D, -0.18D, 0.0D).normalize();
                  emit(false, true, apex, dir, 7.0D, 1.3D, 16771520, Math.min(0.5F, peak * 1.4F), defaultSteps, range, false, (String)null, shift, eye, world);
                  ++cProbe;
               }

               boolean glowSource = cfg != null && cfg.coneSourceGlow();
               if (glowSource != glowLast) {
                  glowLast = glowSource;
                  GLOW_LOGGED.clear();
               }

               for(TrainClient train : snapshotTrains()) {
                  ++cTrains;
                  if (train != null && !train.isRemoved && !train.path.isEmpty()) {
                     HeadlightConfig.Effective eff = cfg == null ? null : cfg.effectiveFor(train.sidingId);
                     VehicleLights.Layout layout = null;
                     if (!glowSource) {
                        DynamicTrainModel model = VehicleLights.modelOf(train);
                        if (model == null) {
                           layout = VehicleLights.layoutFor(train);
                           if (layout == null) {
                              ++cNoModel;
                              gate(3);
                              continue;
                           }
                        } else {
                           ++cModels;
                           layout = VehicleLights.layoutFor(train);
                        }

                        if (layout == null || layout.head == null || layout.tail == null) {
                           ++cNoSlot;
                           gate(4);
                           continue;
                        }
                     }

                     if (eff != null && !eff.beamCone) {
                        gate(5);
                     } else {
                        double trainRange = eff == null ? range : clampRange(eff.beamDistance);
                        float trainPeak = boost ? 0.45F : (eff == null ? peak : (float)eff.beamConeAlpha);
                        int steps = eff == null ? defaultSteps : eff.beamConeSteps;
                        boolean trainVolume = false;
                        double headLength = eff == null ? length : Math.max(4.0D, eff.beamHeadLength);
                        double tailLength = eff == null ? 4.0D : Math.max(2.0D, eff.beamTailLength);
                        int headColor = eff == null ? 16774102 : eff.beamHeadColor;
                        int tailColor = eff == null ? 16722458 : eff.beamTailColor;
                        boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ (cfg != null && cfg.swapEnds);
                        TrainPose pose = TrainPose.ofShared(train, reversed);
                        Vec3 nose = pose.front();
                        Vec3 rear = pose.rear();
                        if (nose == Vec3.ZERO && rear == Vec3.ZERO) {
                           gate(6);
                        } else {
                           int cars = pose.cars();
                           if (cars <= 0) {
                              gate(6);
                           } else {
                              double noseDist = Math.sqrt(nose.distanceToSqr(eye));
                              if (noseDist < cDmin) {
                                 cDmin = noseDist;
                              }

                              if (noseDist <= trainRange) {
                                 ++cNear;
                              }

                              if (!HeadlightSwitch.judge(world, pose, cfg, train).lit()) {
                                 gate(9);
                              } else {
                                 int before = vertexCount;
                                 int beamsBefore = BEAMS.size();
                                 if (glowSource) {
                                    BeamGeom headGeom = BeamGlow.headGeom(cfg, eff, pose);
                                    BeamGeom tailGeom = BeamGlow.tailGeom(cfg, eff, pose);
                                    Vec3 headApex = headGeom.apex(world);
                                    Vec3 tailApex = tailGeom.apex(world);
                                    logGlowOnce(train.id, headGeom, headApex, tailGeom, tailApex);
                                    liveBeams += emit(trainVolume, false, headApex, headGeom.axis, headGeom.length, coneRadius(headGeom.length, true), headGeom.rgb, trainPeak, steps, trainRange, boost, "train=" + train.id + " end=head lamp=0", shift, eye, world) ? 1 : 0;
                                    if (tailCones) {
                                       liveBeams += emit(trainVolume, false, tailApex, tailGeom.axis, tailGeom.length, coneRadius(tailGeom.length, false), tailGeom.rgb, trainPeak * 0.55F, steps, trainRange, boost, "train=" + train.id + " end=tail lamp=0", shift, eye, world) ? 1 : 0;
                                    }
                                 } else {
                                    List<VehicleLightOverrides.Resolved> declared = VehicleLightOverrides.resolvedDeclared(vehicleIdOf(train), layout);
                                    if (!declared.isEmpty()) {
                                       liveBeams += emitDeclared(trainVolume, pose, cars, declared, reversed, steps, trainRange, train.id, boost, tailCones, shift, eye, world);
                                    } else {
                                       liveBeams += emitSlot(trainVolume, pose, 0, true, layout.head, reversed, headLength, headColor, trainPeak, steps, trainRange, train.id, boost, shift, eye, world);
                                       if (tailCones) {
                                          liveBeams += emitSlot(trainVolume, pose, cars - 1, false, layout.tail, reversed, tailLength, tailColor, trainPeak * 0.55F, steps, trainRange, train.id, boost, shift, eye, world);
                                       }
                                    }
                                 }

                                 if (vertexCount > before || BEAMS.size() > beamsBefore) {
                                    ++cDrawn;
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (vertexCount > 0) {
                  try {
                     GlowCompat.draw(vertices, vertexCount, matrices);
                  } catch (Throwable var48) {
                     MtrHeadlightClient.LOGGER.error("[lte] cone draw failed", var48);
                  }

                  vertexCount = 0;
               }

               long now = System.currentTimeMillis();
               if (now - lastLog >= 5000L) {
                  lastLog = now;
                  String depth = "n/a";

                  try {
                     depth = String.valueOf(client.getMainRenderTarget().getDepthTextureId());
                  } catch (Throwable var47) {
                     depth = "err:" + var47.getClass().getSimpleName();
                  }

                  MtrHeadlightClient.LOGGER.info("[lte] cone: calls={} trains={} models={} noModel={} noSlot={} drawn={} quads={} probeQuads={} volBeams={} probe={} near={} dmin={} range={} g1={} g2={} g3={} g4={} g5={} g6={} g7={} g8={} g9={} path={} source={} shaders={} peak={} attMin={} boost={} depthTex={} volume={}", new Object[]{cCalls, cTrains, cModels, cNoModel, cNoSlot, cDrawn, cQuads, cProbeQuads, cVolumeBeams, cProbe, cNear, String.format(Locale.ROOT, "%.1f", cDmin == Double.MAX_VALUE ? -1.0D : cDmin), String.format(Locale.ROOT, "%.0f", cRange), WC[1], WC[2], WC[3], WC[4], WC[5], WC[6], WC[7], WC[8], WC[9], pathName(), sourceName(), shaders, String.format(Locale.ROOT, "%.3f", peak), String.format(Locale.ROOT, "%.2f", peak * 0.25F), boost ? "on(0.45 flat)" : "off", depth, "removed"});
                  cCalls = 0;
                  cTrains = 0;
                  cModels = 0;
                  cNoModel = 0;
                  cNoSlot = 0;
                  cDrawn = 0;
                  cQuads = 0;
                  cProbe = 0;
                  cProbeQuads = 0;
                  cVolumeBeams = 0;
                  cNear = 0;
                  cDmin = Double.MAX_VALUE;
                  Arrays.fill(WC, 0);
               }

            }
         }
      }
   }

   private static double beamRange(HeadlightConfig cfg) {
      double value = cfg == null ? 64.0D : cfg.beamDistance;
      double clamped = clampRange(value);
      cRange = clamped;
      return clamped;
   }

   private static double clampRange(double value) {
      return !(value > 0.0D) ? 64.0D : Math.max(16.0D, Math.min(256.0D, value));
   }

   private static int emitSlot(boolean volume, TrainPose pose, int car, boolean head, VehicleLights.Slot slot, boolean reversed, double length, int rgb, float alpha, int steps, double range, long trainId, boolean boost, Vec3 shift, Vec3 eye, ClientLevel world) {
      if (slot == null) {
         gate(4);
         return 0;
      } else {
         Vec3 base = head ? pose.carStart(car) : pose.carEnd(car);
         Vec3 dir = pose.carBodyForward(car);
         if (dir == Vec3.ZERO) {
            dir = pose.carForward(car);
         }

         if (dir == Vec3.ZERO) {
            gate(6);
            return 0;
         } else {
            if (!head) {
               dir = dir.scale(-1.0D);
            }

            Vec3 flat = new Vec3(dir.x, 0.0D, dir.z);
            Vec3 right = flat.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize().cross(new Vec3(0.0D, 1.0D, 0.0D));
            double flip = reversed ? -1.0D : 1.0D;
            int lamps = slot.lateral.length;
            float each = lamps <= 1 ? alpha : (float)((double)alpha / Math.sqrt((double)lamps));
            double push = 0.05D - slot.inset;
            int emitted = 0;

            for(int i = 0; i < lamps; ++i) {
               Vec3 apex = base.add(dir.scale(push)).add(right.scale(slot.lateral[i] * flip)).add(0.0D, slot.height, 0.0D);
               BeamGeom geom = BeamGeom.at(apex, dir, length, rgb);
               if (emit(volume, false, geom.apex(world), geom.axis, geom.length, coneRadius(geom.length, head), geom.rgb, each, steps, range, boost, "train=" + trainId + " end=" + (head ? "head" : "tail") + " lamp=" + i + " car=" + car, shift, eye, world)) {
                  ++emitted;
               }
            }

            return emitted;
         }
      }
   }

   private static int emitDeclared(boolean volume, TrainPose pose, int cars, List<VehicleLightOverrides.Resolved> beams, boolean reversed, int steps, double range, long trainId, boolean boost, boolean tailCones, Vec3 shift, Vec3 eye, ClientLevel world) {
      double flip = reversed ? -1.0D : 1.0D;
      int emitted = 0;

      for(VehicleLightOverrides.Resolved r : beams) {
         if (r != null) {
            boolean head = r.isHead();
            if (!head && !tailCones) {
               gate(5);
            } else {
               int car = head ? 0 : Math.max(0, cars - 1);
               Vec3 base = head ? pose.carStart(car) : pose.carEnd(car);
               Vec3 forward = pose.carBodyForward(car);
               if (forward == Vec3.ZERO) {
                  forward = pose.carForward(car);
               }

               if (forward == Vec3.ZERO) {
                  gate(6);
               } else {
                  Vec3 flat = new Vec3(forward.x, 0.0D, forward.z);
                  Vec3 right = flat.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize().cross(new Vec3(0.0D, 1.0D, 0.0D));
                  double push = 0.05D - r.inset;
                  Vec3 apex = base.add(forward.scale(push)).add(right.scale(r.lateral * flip)).add(0.0D, r.height, 0.0D);
                  Vec3 axis = declaredAxis(forward, right, r.rot, head);
                  double radius = r.width > 0.02D ? r.width * 0.5D : coneRadius(r.length, head);
                  float alpha = (float)Math.max(0.0D, Math.min(1.0D, r.alpha));
                  if (emit(volume, false, apex, axis, r.length, radius, r.color, alpha, steps, range, boost, "train=" + trainId + " end=" + r.type + " pack car=" + car, shift, eye, world)) {
                     ++emitted;
                  }
               }
            }
         }
      }

      return emitted;
   }

   private static Vec3 declaredAxis(Vec3 forward, Vec3 right, double[] rot, boolean head) {
      double yaw = Math.toRadians(rot == null ? 0.0D : rot[0]);
      double pitch = Math.toRadians(rot == null ? 0.0D : rot[1]);
      double cosPitch = Math.cos(pitch);
      Vec3 dir = forward.scale(Math.cos(yaw) * cosPitch).add(right.scale(-Math.sin(yaw) * cosPitch)).add(0.0D, -Math.sin(pitch), 0.0D);
      if (!head) {
         dir = dir.scale(-1.0D);
      }

      return dir.lengthSqr() < 1.0E-9D ? forward : dir.normalize();
   }

   private static String vehicleIdOf(TrainClient train) {
      try {
         if (train.baseTrainType != null && !train.baseTrainType.isBlank()) {
            return train.baseTrainType;
         }
      } catch (Throwable var3) {
      }

      try {
         return train.trainId;
      } catch (Throwable var2) {
         return null;
      }
   }

   private static double coneRadius(double length, boolean head) {
      return Math.tan(Math.toRadians(13.0D)) * length * (head ? 0.6D : 0.5D);
   }

   private static void logGlowOnce(long trainId, BeamGeom head, Vec3 headApex, BeamGeom tail, Vec3 tailApex) {
      if (GLOW_LOGGED.size() > 512) {
         GLOW_LOGGED.clear();
      }

      if (GLOW_LOGGED.add(trainId)) {
         MtrHeadlightClient.LOGGER.info(String.format(Locale.ROOT, "[lte] coneSource glow: train=%d base=(%.2f, %.2f, %.2f) axis=(%.3f, %.3f, %.3f) len=%.2f tailBase=(%.2f, %.2f, %.2f) tailLen=%.2f", trainId, headApex.x, headApex.y, headApex.z, head.axis.x, head.axis.y, head.axis.z, head.length, tailApex.x, tailApex.y, tailApex.z, tail.length));
      }
   }

   static void clearGlowLogged() {
      GLOW_LOGGED.clear();
   }

   public static String sourceName() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      return cfg == null ? "MODEL" : cfg.resolvedConeSource();
   }

   public static String pathName() {
      return "shell";
   }

   private static float[] shellWeights(double[] radii) {
      float[] weights = new float[radii.length];
      double outer = 0.0D;

      for(int i = 0; i < radii.length; ++i) {
         double inner = i + 1 < radii.length ? radii[i + 1] : 0.0D;
         double mid = (radii[i] + inner) * 0.5D;
         double value = radial(mid);
         weights[i] = (float)Math.max(0.0D, value - outer);
         outer = value;
      }

      return weights;
   }

   private static double radial(double d) {
      double rest = 1.0D - d * d;
      return rest * rest;
   }

   private static boolean emit(boolean volume, boolean probe, Vec3 apex, Vec3 dir, double length, double radius, int rgb, float alpha, int steps, double range, boolean boost, String beamKey, Vec3 shift, Vec3 eye, ClientLevel world) {
      double gateRange = range > 0.0D && !Double.isNaN(range) ? range : 64.0D;
      if (apex.distanceToSqr(eye) > gateRange * gateRange) {
         gate(7);
         return false;
      } else {
         float effAlpha = boost && !probe ? 0.45F : alpha;
         if (!(length <= 0.1D) && !(radius <= 0.01D) && !(effAlpha <= 1.0E-4F)) {
            logBeamOnce(beamKey, effAlpha, length, radius, apex, dir, boost, coneEffectiveMinAlpha(effAlpha, boost));
            if (!volume) {
               if (!cone(apex, dir, length, radius, rgb, effAlpha, shift, eye, world, probe, boost)) {
                  gate(8);
                  return false;
               } else {
                  return true;
               }
            } else if (BEAMS.size() >= 64) {
               gate(8);
               return false;
            } else {
               BEAMS.add(new ConeVolumeBeam(apex, dir, length, radius, rgb, effAlpha, steps));
               ++volumeBeamsTotal;
               ++cVolumeBeams;
               return true;
            }
         } else {
            gate(8);
            return false;
         }
      }
   }

   private static boolean cone(Vec3 apex, Vec3 dir, double length, double radius, int rgb, float alpha, Vec3 shift, Vec3 eye, ClientLevel world, boolean probe, boolean boost) {
      if (!(length <= 0.1D) && !(radius <= 0.01D) && !(alpha <= 1.0E-4F)) {
         Vec3 axis = dir.normalize();
         Vec3 up = Math.abs(axis.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
         Vec3 right = axis.cross(up).normalize();
         up = right.cross(axis).normalize();
         float red = (float)(rgb >> 16 & 255) / 255.0F;
         float green = (float)(rgb >> 8 & 255) / 255.0F;
         float blue = (float)(rgb & 255) / 255.0F;
         double slope = radius / length;
         double invNorm = 1.0D / Math.sqrt(1.0D + slope * slope);
         if (groundScratch.length < RINGS.length) {
            groundScratch = new double[RINGS.length];
         }

         for(int r = 0; r < RINGS.length; ++r) {
            double d = length * RINGS[r];
            groundScratch[r] = groundY(world, apex.x + axis.x * d, apex.y + axis.y * d, apex.z + axis.z * d);
         }

         boolean plain = boost || probe;

         for(int shell = 0; shell < SHELLS.length; ++shell) {
            double scale = SHELLS[shell];
            float share = SHELL_ALPHA[shell];

            for(int r = 0; r + 1 < RINGS.length; ++r) {
               double t0 = RINGS[r];
               double t1 = RINGS[r + 1];
               double rad0 = radius * t0 * scale;
               double rad1 = radius * t1 * scale;
               float fade0 = plain ? 1.0F : fade(t0);
               float fade1 = plain ? 1.0F : fade(t1);
               double d0 = length * t0;
               double d1 = length * t1;
               double gy0 = groundScratch[r];
               double gy1 = groundScratch[r + 1];

               for(int s = 0; s < 14; ++s) {
                  double a = (Math.PI * 2D) * (double)s / 14.0D;
                  double b = (Math.PI * 2D) * (double)(s + 1) / 14.0D;
                  double ca = Math.cos(a);
                  double sa = Math.sin(a);
                  double cb = Math.cos(b);
                  double sb = Math.sin(b);
                  double nax = (right.x * ca + up.x * sa - axis.x * slope) * invNorm;
                  double nay = (right.y * ca + up.y * sa - axis.y * slope) * invNorm;
                  double naz = (right.z * ca + up.z * sa - axis.z * slope) * invNorm;
                  double nbx = (right.x * cb + up.x * sb - axis.x * slope) * invNorm;
                  double nby = (right.y * cb + up.y * sb - axis.y * slope) * invNorm;
                  double nbz = (right.z * cb + up.z * sb - axis.z * slope) * invNorm;
                  double wx0 = apex.x + axis.x * d0 + right.x * ca * rad0 + up.x * sa * rad0;
                  double wy0 = apex.y + axis.y * d0 + right.y * ca * rad0 + up.y * sa * rad0;
                  double wz0 = apex.z + axis.z * d0 + right.z * ca * rad0 + up.z * sa * rad0;
                  double wx1 = apex.x + axis.x * d0 + right.x * cb * rad0 + up.x * sb * rad0;
                  double wy1 = apex.y + axis.y * d0 + right.y * cb * rad0 + up.y * sb * rad0;
                  double wz1 = apex.z + axis.z * d0 + right.z * cb * rad0 + up.z * sb * rad0;
                  double wx2 = apex.x + axis.x * d1 + right.x * cb * rad1 + up.x * sb * rad1;
                  double wy2 = apex.y + axis.y * d1 + right.y * cb * rad1 + up.y * sb * rad1;
                  double wz2 = apex.z + axis.z * d1 + right.z * cb * rad1 + up.z * sb * rad1;
                  double wx3 = apex.x + axis.x * d1 + right.x * ca * rad1 + up.x * sa * rad1;
                  double wy3 = apex.y + axis.y * d1 + right.y * ca * rad1 + up.y * sa * rad1;
                  double wz3 = apex.z + axis.z * d1 + right.z * ca * rad1 + up.z * sa * rad1;
                  quad((float)(wx0 - shift.x), (float)(wy0 - shift.y), (float)(wz0 - shift.z), (float)(wx1 - shift.x), (float)(wy1 - shift.y), (float)(wz1 - shift.z), (float)(wx2 - shift.x), (float)(wy2 - shift.y), (float)(wz2 - shift.z), (float)(wx3 - shift.x), (float)(wy3 - shift.y), (float)(wz3 - shift.z), plain ? alpha : alpha * weight(share, soften(nax, nay, naz, wx0, wy0, wz0, eye) * groundFade(wy0, gy0)) * fade0, plain ? alpha : alpha * weight(share, soften(nbx, nby, nbz, wx1, wy1, wz1, eye) * groundFade(wy1, gy0)) * fade0, plain ? alpha : alpha * weight(share, soften(nbx, nby, nbz, wx2, wy2, wz2, eye) * groundFade(wy2, gy1)) * fade1, plain ? alpha : alpha * weight(share, soften(nax, nay, naz, wx3, wy3, wz3, eye) * groundFade(wy3, gy1)) * fade1, red, green, blue, probe);
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private static void debugCone(PoseStack matrices, Vec3 shift, Vec3 eye, Vec3 look) {
      Vec3 flat = new Vec3(look.x, 0.0D, look.z);
      flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
      Vec3 apex = new Vec3(eye.x + flat.x * 6.0D, eye.y, eye.z + flat.z * 6.0D);
      double tilt = Math.toRadians(12.0D);
      Vec3 axis = (new Vec3(flat.x * Math.cos(tilt), -Math.sin(tilt), flat.z * Math.cos(tilt))).normalize();
      double radius = Math.tan(Math.toRadians(20.0D)) * 12.0D;
      Vec3 up = Math.abs(axis.y) > 0.99D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
      Vec3 right = axis.cross(up).normalize();
      up = right.cross(axis).normalize();
      vertexCount = 0;

      for(double shell : SHELLS) {
         for(int r = 0; r + 1 < RINGS.length; ++r) {
            double t0 = RINGS[r];
            double t1 = RINGS[r + 1];
            double rad0 = radius * t0 * shell;
            double rad1 = radius * t1 * shell;
            double d0 = 12.0D * t0;
            double d1 = 12.0D * t1;

            for(int s = 0; s < 14; ++s) {
               double a = (Math.PI * 2D) * (double)s / 14.0D;
               double b = (Math.PI * 2D) * (double)(s + 1) / 14.0D;
               double ca = Math.cos(a);
               double sa = Math.sin(a);
               double cb = Math.cos(b);
               double sb = Math.sin(b);
               quad((float)(apex.x + axis.x * d0 + right.x * ca * rad0 + up.x * sa * rad0 - shift.x), (float)(apex.y + axis.y * d0 + right.y * ca * rad0 + up.y * sa * rad0 - shift.y), (float)(apex.z + axis.z * d0 + right.z * ca * rad0 + up.z * sa * rad0 - shift.z), (float)(apex.x + axis.x * d0 + right.x * cb * rad0 + up.x * sb * rad0 - shift.x), (float)(apex.y + axis.y * d0 + right.y * cb * rad0 + up.y * sb * rad0 - shift.y), (float)(apex.z + axis.z * d0 + right.z * cb * rad0 + up.z * sb * rad0 - shift.z), (float)(apex.x + axis.x * d1 + right.x * cb * rad1 + up.x * sb * rad1 - shift.x), (float)(apex.y + axis.y * d1 + right.y * cb * rad1 + up.y * sb * rad1 - shift.y), (float)(apex.z + axis.z * d1 + right.z * cb * rad1 + up.z * sb * rad1 - shift.z), (float)(apex.x + axis.x * d1 + right.x * ca * rad1 + up.x * sa * rad1 - shift.x), (float)(apex.y + axis.y * d1 + right.y * ca * rad1 + up.y * sa * rad1 - shift.y), (float)(apex.z + axis.z * d1 + right.z * ca * rad1 + up.z * sa * rad1 - shift.z), 0.45F, 0.45F, 0.45F, 0.45F, 1.0F, 1.0F, 1.0F, true);
            }
         }
      }

      try {
         if (vertexCount > 0) {
            GlowCompat.draw(vertices, vertexCount, matrices);
         }
      } catch (Throwable var47) {
         MtrHeadlightClient.LOGGER.error("[lte] debug cone draw failed", var47);
      } finally {
         vertexCount = 0;
      }

   }

   private static float weight(float share, float softenGround) {
      return Math.max(0.25F, share * softenGround);
   }

   private static float coneEffectiveMinAlpha(float alpha, boolean boost) {
      return boost ? alpha : alpha * 0.25F * fade(RINGS[0]);
   }

   private static void logBeamOnce(String key, float alpha, double length, double radius, Vec3 apex, Vec3 axis, boolean boost, float minVertex) {
      if (key != null) {
         if (BEAM_LOGGED.size() > 512) {
            BEAM_LOGGED.clear();
         }

         if (BEAM_LOGGED.add(key)) {
            MtrHeadlightClient.LOGGER.info(String.format(Locale.ROOT, "[lte] cone beam: %s alphaPeak=%.3f minVertex=%.4f len=%.2f radius=%.2f apex=(%.2f, %.2f, %.2f) axis=(%.3f, %.3f, %.3f) boost=%s", key, alpha, minVertex, length, radius, apex.x, apex.y, apex.z, axis.x, axis.y, axis.z, boost));
         }
      }
   }

   private static float fade(double t) {
      double clamped = Math.max(0.0D, Math.min(1.0D, t));
      return (float)(Math.exp(-2.5D * clamped) * (1.0D - clamped));
   }

   private static float soften(double nx, double ny, double nz, double px, double py, double pz, Vec3 eye) {
      double vx = px - eye.x;
      double vy = py - eye.y;
      double vz = pz - eye.z;
      double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
      if (len < 1.0E-4D) {
         return 1.0F;
      } else {
         double facing = Math.abs((nx * vx + ny * vy + nz * vz) / len);
         if (facing > 1.0D) {
            facing = 1.0D;
         }

         return (float)(0.45D + 0.55D * Math.pow(facing, 1.5D));
      }
   }

   private static float groundFade(double y, double groundY) {
      if (!(groundY > -1.0E30D)) {
         return 1.0F;
      } else {
         double clearance = y - groundY;
         if (clearance <= 0.0D) {
            return 0.0F;
         } else if (clearance >= 1.0D) {
            return 1.0F;
         } else {
            double x = clearance / 1.0D;
            return (float)(x * x * (3.0D - 2.0D * x));
         }
      }
   }

   private static double groundY(ClientLevel world, double x, double y, double z) {
      if (world == null) {
         return Double.NEGATIVE_INFINITY;
      } else {
         int bx = Mth.floor(x);
         int bz = Mth.floor(z);
         int by = Mth.floor(y);

         try {
            int bottom = Math.max(world.getMinBuildHeight(), by - 3);

            for(int yy = by; yy >= bottom; --yy) {
               GROUND_POS.set(bx, yy, bz);
               BlockState state = world.getBlockState(GROUND_POS);
               if (!state.isAir() && state.getFluidState().isEmpty()) {
                  return (double)yy + 1.0D;
               }
            }

            return Double.NEGATIVE_INFINITY;
         } catch (Throwable var13) {
            return Double.NEGATIVE_INFINITY;
         }
      }
   }

   private static void quad(float xa, float ya, float za, float xb, float yb, float zb, float xc, float yc, float zc, float xd, float yd, float zd, float a00, float a01, float a11, float a10, float red, float green, float blue, boolean probe) {
      if (probe) {
         ++probeQuadsTotal;
         ++cProbeQuads;
      } else {
         ++quadsTotal;
         ++cQuads;
      }

      int needed = vertexCount + 28;
      if (needed > vertices.length) {
         float[] grown = new float[Math.max(needed, vertices.length * 2 + 512)];
         System.arraycopy(vertices, 0, grown, 0, vertexCount);
         vertices = grown;
      }

      vertexCount = put(xa, ya, za, red, green, blue, a00);
      vertexCount = put(xb, yb, zb, red, green, blue, a01);
      vertexCount = put(xc, yc, zc, red, green, blue, a11);
      vertexCount = put(xd, yd, zd, red, green, blue, a10);
   }

   private static int put(float x, float y, float z, float red, float green, float blue, float alpha) {
      int i = vertexCount;
      vertices[i++] = x;
      vertices[i++] = y;
      vertices[i++] = z;
      vertices[i++] = red;
      vertices[i++] = green;
      vertices[i++] = blue;
      vertices[i++] = alpha;
      return i;
   }

   private static List<TrainClient> snapshotTrains() {
      try {
         return new ArrayList(ClientData.TRAINS);
      } catch (RuntimeException var1) {
         return List.of();
      }
   }

   public static void announce() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      MtrHeadlightClient.LOGGER.info(String.format(Locale.ROOT, "[lte] cone active: halfAngle=%.0fdeg segments=%d rings=%d shells=%d probe=%s blend=additive path=%s volume=%s steps=%d source=%s", 13.0D, 14, RINGS.length, SHELLS.length, PROBE, pathName(), "removed", cfg == null ? 10 : cfg.beamConeSteps(), sourceName()));
   }
}

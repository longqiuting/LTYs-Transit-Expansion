package dev.js.lte;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import mtr.client.ClientData;
import mtr.data.TrainClient;
import mtr.data.TransportMode;
import dev.js.lte.forge.platform.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class LightFlare {
   private static final ResourceLocation TEXTURE = new ResourceLocation("lte", "textures/vehicle/light_flare.png");
   private static final double MAX_PULL = 0.6D;
   private static final double PULL_FRACTION = 0.05D;
   private static final double MIN_FACING = -0.15D;
   private static final float MIN_ALPHA_SHARE = 0.35F;
   private static final double FADE_FROM = 0.7D;
   private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
   private static final int FLARE_STRIDE = 9;
   private static final int TEST_STRIDE = 7;
   private static final double TEST_HALF = 1.5D;
   private static final double TEST_AHEAD = 2.0D;
   private static float[] flareVerts = new float[0];
   private static int flareCount;
   private static float[] testVerts = new float[0];
   private static int testCount;
   private static long diagAt = System.currentTimeMillis();
   private static int dTrains;
   private static int dLamps;
   private static int dQuads;
   private static int dNoLamp;
   private static boolean windowOn;
   private static final Set<Long> SKIP_OFF_ROUTE = new HashSet();
   private static final Set<Long> SKIP_DARK = new HashSet();
   private static final Set<Long> SKIP_PARKED = new HashSet();
   private static String lastSkipText = "skipped=offRoute=0 dark=0 parked=0";
   private static final int NEAR_REPORT = 3;
   private static double[] nearDist = new double[3];
   private static double[] nearFacing = new double[3];
   private static double[] nearWorld = new double[3];
   private static double[] nearPx = new double[3];
   private static double[] nearAlpha = new double[3];
   private static double[] nearFade = new double[3];
   private static String[] nearWhere = new String[3];
   private static boolean dDrawn;
   private static String dDrawError;
   private static String dTexture = "unknown";
   private static boolean dTestWindow;
   private static int dDrawQuads;
   private static int dDrawDepthFunc = -1;
   private static int dDrawDepthEntry = -1;
   private static int dDrawBlendSrc = -1;
   private static int dDrawBlendDst = -1;
   private static boolean dDrawBlend;
   private static boolean dDrawCull;
   private static int dDrawTextureId = -1;
   private static boolean dDrawForceOcclusion;
   private static boolean dDrawLatePass;
   private static boolean dPackActive;
   private static int dEarlyOffers;
   private static int dEarlyDraws;
   private static int dLateOffers;
   private static int dLateDraws;

   private LightFlare() {
   }

   public static String skipText() {
      return lastSkipText;
   }

   private static long skipKey(TrainClient train) {
      return train.id != 0L ? train.id : 4294967296L | (long)System.identityHashCode(train) & 4294967295L;
   }

   public static void render(WorldRenderContext context, boolean latePass) {
      LightTest.report();
      if (latePass) {
         ++dLateOffers;
      } else {
         ++dEarlyOffers;
      }

      HeadlightConfig cfg = MtrHeadlightClient.config();
      if (cfg != null) {
         boolean test = LightTest.enabled();
         if (test || cfg.drawFlare()) {
            windowOn = true;

            try {
               dPackActive = ShaderPackProbe.active();
            } catch (Throwable var10) {
               return;
            }

            if (latePass) {
               Minecraft client = Minecraft.getInstance();
               if (client.level != null && client.player != null && client.gameRenderer != null) {
                  PoseStack matrices = context.matrixStack();
                  if (matrices != null) {
                     Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
                     Vec3 eye = client.player.position();
                     flareCount = 0;
                     testCount = 0;
                     int quads = 0;
                     if (test) {
                        quads = lightTest(cfg, camera);
                     } else {
                        quads = lamps(cfg, client, camera, eye);
                        dQuads += quads;
                     }

                     dTestWindow = test;
                     if (quads > 0) {
                        try {
                           if (testCount > 0) {
                              GlowCompat.draw(testVerts, testCount, matrices);
                           } else if (flareCount > 0) {
                              GlowCompat.drawTextured(flareVerts, flareCount, matrices, TEXTURE, true);
                              dDrawn = true;
                              captureDraw(quads, latePass);
                           }
                        } catch (Throwable var11) {
                           dDrawError = var11.getClass().getSimpleName() + ":" + firstLine(var11);
                           LightTest.flareFailure(dDrawError);
                           if (!test) {
                              MtrHeadlightClient.LOGGER.error("[lte] light flare draw failed", var11);
                           }
                        }
                     }

                     if (!test && flareCount > 0 && "unknown".equals(dTexture)) {
                        dTexture = textureState(client);
                     }

                     diag();
                  }
               }
            }
         }
      }
   }

   private static int lamps(HeadlightConfig cfg, Minecraft client, Vec3 camera, Vec3 eye) {
      double base = cfg.flareSizeClamped();
      int screenH = Math.max(1, client.getWindow().getGuiScaledHeight());
      int minPx = cfg.beamFlareMinPxFor(screenH);
      float peak = (float)clamp(cfg.beamAlpha, 0.0D, 1.0D);
      double fov = verticalFov(client);
      nearClear();
      int quads = 0;

      for(TrainClient train : snapshotTrains()) {
         ++dTrains;
         if (drawable(cfg, train)) {
            HeadlightConfig.Effective eff = cfg.effectiveFor(train.sidingId);
            if (eff.enabled && eff.beamFlare) {
               boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
               TrainPose pose = TrainPose.ofShared(train, reversed);
               if (pose.cars() > 0) {
                  HeadlightSwitch.Verdict verdict = HeadlightSwitch.judge(client.level, pose, cfg, train);
                  if (!verdict.lit()) {
                     Long key = skipKey(train);
                     switch (verdict) {
                        case OFF_ROUTE:
                           SKIP_OFF_ROUTE.add(key);
                           break;
                        case DARK:
                           SKIP_DARK.add(key);
                           break;
                        case PARKED:
                           SKIP_PARKED.add(key);
                     }
                  } else {
                     List<TrainLamps.Lamp> lamps = TrainLamps.of(train, pose, cfg, eff);
                     if (lamps.isEmpty()) {
                        ++dNoLamp;
                     } else {
                        dLamps += lamps.size();

                        for(TrainLamps.Lamp lamp : lamps) {
                           if (quad(lamp, camera, eye, eff.beamDistance, base, minPx, peak, fov, screenH)) {
                              ++quads;
                           }
                        }
                     }
                  }
               }
            }
         }
      }

      return quads;
   }

   private static int lightTest(HeadlightConfig cfg, Vec3 camera) {
      int quads = 0;

      for(TrainClient train : snapshotTrains()) {
         if (train != null && !train.isRemoved && !train.path.isEmpty() && train.trainCars > 0) {
            boolean reversed = DepartureState.reversed(train, train.isReversed()) ^ cfg.swapEnds;
            TrainPose pose = TrainPose.ofShared(train, reversed);
            if (pose.cars() > 0) {
               quads += testQuad(pose.front(), endDir(pose, 0, true), camera);
               quads += testQuad(pose.rear(), endDir(pose, Math.max(0, pose.cars() - 1), false), camera);
            }
         }
      }

      return quads;
   }

   private static int testQuad(Vec3 anchor, Vec3 dir, Vec3 camera) {
      Vec3 toCam = camera.subtract(anchor);
      Vec3 view = toCam.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : toCam.normalize();
      Vec3 axis = dir != null && !(dir.lengthSqr() < 1.0E-8D) ? dir.normalize() : view;
      Vec3 center = anchor.add(axis.scale(2.0D));
      Vec3 right = view.cross(UP);
      if (right.lengthSqr() < 1.0E-8D) {
         right = new Vec3(1.0D, 0.0D, 0.0D);
      }

      right = right.normalize().scale(1.5D);
      Vec3 up = right.cross(view).normalize().scale(1.5D);
      return testBillboard(center, right, up, camera, 1.0F, 0.0F, 1.0F, 0.85F);
   }

   private static boolean quad(TrainLamps.Lamp lamp, Vec3 camera, Vec3 eye, double distance, double base, int minPx, float peakAlpha, double fov, int screenH) {
      Vec3 toCam = camera.subtract(lamp.pos);
      double camDist = toCam.length();
      if (camDist < 0.001D) {
         return false;
      } else {
         Vec3 view = toCam.scale(1.0D / camDist);
         double facing = -lamp.dir.dot(view);
         double facing3 = facing * facing * facing;
         double world = base * (0.45D + 1.4D * facing3);
         double tanHalf = Math.tan(Math.toRadians(clamp(fov, 10.0D, 110.0D)) * 0.5D);
         double pixels = 0.0D;
         if (tanHalf > 0.001D) {
            pixels = world / (2.0D * camDist * tanHalf) * (double)screenH;
            if (pixels > 1.0E-6D && pixels < (double)minPx) {
               world *= (double)minPx / pixels;
               pixels = (double)minPx;
            }
         }

         double d = lamp.pos.distanceTo(eye);
         double fade = distanceFade(d, distance);
         double brightness = clamp((double)peakAlpha * lamp.alpha * (0.35D + 0.65D * facing3), 0.0D, 1.0D);
         float alpha = (float)clamp(brightness * fade, 0.0D, 1.0D);
         String why;
         if (d > distance) {
            why = "far";
         } else if (facing < -0.15D) {
            why = "back";
         } else if (brightness <= 0.004D) {
            why = "dim";
         } else {
            why = "ok";
         }

         boolean gateOk = "ok".equals(why);
         alpha = (float)clamp((gateOk ? Math.max(brightness, (double)(peakAlpha * 0.35F)) : brightness) * fade, 0.0D, 1.0D);
         if (gateOk && alpha <= 0.004F) {
            why = "faded";
         }

         boolean ok = "ok".equals(why);
         nearAdd(camDist, facing, world, pixels, alpha, fade, why + "/" + lamp.source + (lamp.head ? ":head" : ":tail"));
         if (!ok) {
            return false;
         } else {
            double pull = Math.min(0.6D, camDist * 0.05D);
            double cx = lamp.pos.x + view.x * pull;
            double cy = lamp.pos.y + view.y * pull;
            double cz = lamp.pos.z + view.z * pull;
            Vec3 right = view.cross(UP);
            if (right.lengthSqr() < 1.0E-8D) {
               right = new Vec3(1.0D, 0.0D, 0.0D);
            }

            right = right.normalize().scale(world * 0.5D);
            Vec3 up = right.cross(view).normalize().scale(world * 0.5D);
            float red = (float)(lamp.rgb >> 16 & 255) / 255.0F;
            float green = (float)(lamp.rgb >> 8 & 255) / 255.0F;
            float blue = (float)(lamp.rgb & 255) / 255.0F;
            billboard(new Vec3(cx, cy, cz), right, up, camera, red, green, blue, alpha);
            return true;
         }
      }
   }

   private static void nearAdd(double camDist, double facing, double world, double pixels, float alpha, double fade, String where) {
      if (nearWhere.length < 3) {
         nearClear();
      }

      int slot = -1;

      for(int i = 0; i < 3; ++i) {
         if (nearWhere[i] == null || camDist < nearDist[i]) {
            slot = i;
            break;
         }
      }

      if (slot >= 0) {
         for(int i = 2; i > slot; --i) {
            nearDist[i] = nearDist[i - 1];
            nearFacing[i] = nearFacing[i - 1];
            nearWorld[i] = nearWorld[i - 1];
            nearPx[i] = nearPx[i - 1];
            nearAlpha[i] = nearAlpha[i - 1];
            nearFade[i] = nearFade[i - 1];
            nearWhere[i] = nearWhere[i - 1];
         }

         nearDist[slot] = camDist;
         nearFacing[slot] = facing;
         nearWorld[slot] = world;
         nearPx[slot] = pixels;
         nearAlpha[slot] = (double)alpha;
         nearFade[slot] = fade;
         nearWhere[slot] = where;
      }
   }

   private static void nearClear() {
      if (nearWhere != null && nearWhere.length >= 3 && nearFacing.length >= 3 && nearWorld.length >= 3 && nearPx.length >= 3 && nearAlpha.length >= 3 && nearFade.length >= 3) {
         Arrays.fill(nearWhere, (Object)null);
      } else {
         nearDist = new double[3];
         nearFacing = new double[3];
         nearWorld = new double[3];
         nearPx = new double[3];
         nearAlpha = new double[3];
         nearFade = new double[3];
         nearWhere = new String[3];
      }
   }

   private static void billboard(Vec3 center, Vec3 right, Vec3 up, Vec3 camera, float r, float g, float b, float a) {
      double rx = right.x;
      double ry = right.y;
      double rz = right.z;
      double ux = up.x;
      double uy = up.y;
      double uz = up.z;
      double px = center.x - camera.x;
      double py = center.y - camera.y;
      double pz = center.z - camera.z;
      pushFlare(px - rx - ux, py - ry - uy, pz - rz - uz, 0.0F, 1.0F, r, g, b, a);
      pushFlare(px - rx + ux, py - ry + uy, pz - rz + uz, 0.0F, 0.0F, r, g, b, a);
      pushFlare(px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 0.0F, r, g, b, a);
      pushFlare(px + rx - ux, py + ry - uy, pz + rz - uz, 1.0F, 1.0F, r, g, b, a);
   }

   private static void pushFlare(double x, double y, double z, float u, float v, float r, float g, float b, float a) {
      if (flareCount + 9 > flareVerts.length) {
         flareVerts = Arrays.copyOf(flareVerts, Math.max(36, flareVerts.length * 2 + 9));
      }

      int i = flareCount;
      float[] out = flareVerts;
      out[i] = (float)x;
      out[i + 1] = (float)y;
      out[i + 2] = (float)z;
      out[i + 3] = u;
      out[i + 4] = v;
      out[i + 5] = r;
      out[i + 6] = g;
      out[i + 7] = b;
      out[i + 8] = a;
      flareCount = i + 9;
   }

   private static void pushTest(double x, double y, double z, float r, float g, float b, float a) {
      if (testCount + 7 > testVerts.length) {
         testVerts = Arrays.copyOf(testVerts, Math.max(28, testVerts.length * 2 + 7));
      }

      int i = testCount;
      float[] out = testVerts;
      out[i] = (float)x;
      out[i + 1] = (float)y;
      out[i + 2] = (float)z;
      out[i + 3] = r;
      out[i + 4] = g;
      out[i + 5] = b;
      out[i + 6] = a;
      testCount = i + 7;
   }

   private static int testBillboard(Vec3 center, Vec3 right, Vec3 up, Vec3 camera, float r, float g, float b, float a) {
      double px = center.x - camera.x;
      double py = center.y - camera.y;
      double pz = center.z - camera.z;
      pushTest(px - right.x - up.x, py - right.y - up.y, pz - right.z - up.z, r, g, b, a);
      pushTest(px - right.x + up.x, py - right.y + up.y, pz - right.z + up.z, r, g, b, a);
      pushTest(px + right.x + up.x, py + right.y + up.y, pz + right.z + up.z, r, g, b, a);
      pushTest(px + right.x - up.x, py + right.y - up.y, pz + right.z - up.z, r, g, b, a);
      LightTest.flareQuad();
      return 1;
   }

   private static double distanceFade(double d, double limit) {
      if (limit <= 0.0D) {
         return 1.0D;
      } else {
         double from = limit * 0.7D;
         if (d <= from) {
            return 1.0D;
         } else if (d >= limit) {
            return 0.0D;
         } else {
            double t = (d - from) / (limit - from);
            double inv = 1.0D - t;
            return inv * inv;
         }
      }
   }

   private static Vec3 endDir(TrainPose pose, int car, boolean head) {
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

   private static double verticalFov(Minecraft client) {
      try {
         Object value = client.options.fov().get();
         double var10000;
         if (value instanceof Number number) {
            var10000 = number.doubleValue();
         } else {
            var10000 = 70.0D;
         }

         return var10000;
      } catch (Throwable var3) {
         return 70.0D;
      }
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

   private static void diag() {
      long now = System.currentTimeMillis();
      if (now - diagAt >= 5000L) {
         diagAt = now;
         HeadlightConfig cfg = MtrHeadlightClient.config();
         lastSkipText = "skipped=offRoute=" + SKIP_OFF_ROUTE.size() + " dark=" + SKIP_DARK.size() + " parked=" + SKIP_PARKED.size();
         if (windowOn && cfg != null && LteDebug.lights()) {
            MtrHeadlightClient.LOGGER.info("[lte] flare: band={} flare={} trains={} lamps={} quads={} noLamp={} {} drawn={} tex={}{}", new Object[]{cfg.beamBand, cfg.beamFlare, dTrains, dLamps, dQuads, dNoLamp, lastSkipText, dDrawn, dTexture, dDrawError == null ? "" : " err=" + dDrawError});
            MtrHeadlightClient.LOGGER.info("[lte] flare draw: quads={} depthFunc={} blend={} cull={} tex={} latePass={} passes=early {}/{} late {}/{} texId={} entered={} pack={}{}", new Object[]{dDrawQuads, funcName(dDrawDepthFunc), blendName(), dDrawCull ? "on" : "off", dTexture, dDrawLatePass, dEarlyOffers, dEarlyDraws, dLateOffers, dLateDraws, dDrawTextureId, funcName(dDrawDepthEntry), dPackActive, dDrawForceOcclusion ? "" : " (forceOcclusion=false: the depth func is whatever the previous caller left!)"});
            if (!dTestWindow) {
               StringBuilder near = new StringBuilder();

               for(int i = 0; i < 3; ++i) {
                  if (nearWhere[i] != null) {
                     if (near.length() > 0) {
                        near.append(" | ");
                     }

                     near.append('#').append(i).append(" d=").append(fmt(nearDist[i])).append(" f=").append(fmt(nearFacing[i])).append(" w=").append(fmt(nearWorld[i])).append(" px=").append(fmt(nearPx[i])).append(" a=").append(fmt(nearAlpha[i])).append(" fade=").append(fmt(nearFade[i])).append(' ').append(nearWhere[i]);
                  }
               }

               MtrHeadlightClient.LOGGER.info("[lte] flare near: {}", near.length() == 0 ? "(no lamp reached the size/alpha stage)" : near.toString());
            }
         }

         windowOn = false;
         dTrains = 0;
         dLamps = 0;
         dQuads = 0;
         dNoLamp = 0;
         SKIP_OFF_ROUTE.clear();
         SKIP_DARK.clear();
         SKIP_PARKED.clear();
         dDrawn = false;
         dDrawError = null;
         dDrawQuads = 0;
         dDrawDepthFunc = -1;
         dDrawDepthEntry = -1;
         dDrawBlendSrc = -1;
         dDrawBlendDst = -1;
         dDrawBlend = false;
         dDrawCull = false;
         dDrawTextureId = -1;
         dDrawLatePass = false;
         dEarlyOffers = 0;
         dEarlyDraws = 0;
         dLateOffers = 0;
         dLateDraws = 0;
      }
   }

   private static void captureDraw(int quads, boolean latePass) {
      dDrawQuads = quads;
      dDrawDepthFunc = GlowCompat.diagDepthFunc;
      dDrawDepthEntry = GlowCompat.diagDepthFuncOnEntry;
      dDrawBlendSrc = GlowCompat.diagBlendSrc;
      dDrawBlendDst = GlowCompat.diagBlendDst;
      dDrawBlend = GlowCompat.diagBlendEnabled;
      dDrawCull = GlowCompat.diagCullEnabled;
      dDrawTextureId = GlowCompat.diagTextureId;
      dDrawForceOcclusion = GlowCompat.diagForceOcclusion;
      dDrawLatePass = latePass;
      if (latePass) {
         ++dLateDraws;
      } else {
         ++dEarlyDraws;
      }

   }

   private static String funcName(int func) {
      switch (func) {
         case -1:
            return "none";
         case 512:
            return "NEVER";
         case 513:
            return "LEQUAL";
         case 514:
            return "EQUAL";
         case 515:
            return "LESS";
         case 516:
            return "GREATER";
         case 517:
            return "GEQUAL";
         case 518:
            return "NOTEQUAL";
         case 519:
            return "ALWAYS";
         default:
            return "0x" + Integer.toHexString(func);
      }
   }

   private static String blendName() {
      if (!dDrawBlend) {
         return "off";
      } else if (dDrawBlendSrc == 770 && dDrawBlendDst == 1) {
         return "ADD";
      } else {
         return dDrawBlendSrc == 770 && dDrawBlendDst == 771 ? "ALPHA" : "0x" + Integer.toHexString(dDrawBlendSrc) + ":0x" + Integer.toHexString(dDrawBlendDst);
      }
   }

   private static String textureState(Minecraft client) {
      try {
         if (client.getResourceManager().getResource(TEXTURE).isEmpty()) {
            return "missing";
         } else {
            AbstractTexture texture = client.getTextureManager().getTexture(TEXTURE);
            return texture != null && texture.getId() != -1 ? "ok" : "notuploaded";
         }
      } catch (Throwable var2) {
         return "error:" + var2.getClass().getSimpleName();
      }
   }

   private static String fmt(double value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   private static double clamp(double value, double min, double max) {
      if (Double.isNaN(value)) {
         return min;
      } else {
         return value < min ? min : (value > max ? max : value);
      }
   }

   private static String firstLine(Throwable t) {
      String text = t == null ? "unknown" : String.valueOf(t.getMessage());
      int newline = text.indexOf(10);
      String head = newline < 0 ? text : text.substring(0, newline);
      return head.length() > 120 ? head.substring(0, 120) : head;
   }
}

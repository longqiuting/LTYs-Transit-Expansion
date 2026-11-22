package dev.js.mtrheadlight.sss;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LightTest;
import dev.js.mtrheadlight.MtrHeadlightClient;
import dev.js.mtrheadlight.ShaderPackProbe;
import java.util.Locale;
import dev.js.mtrheadlight.forge.platform.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public final class ScatterPass {
   static final float MAX_RAY = 96.0F;
   private static final float COS_INNER = 0.94F;
   private static final float COS_OUTER = 0.55F;
   private static final float ATTENUATION_REF = 9.0F;
   private static final long LOG_INTERVAL_MS = 5000L;
   private static final float[] LIGHT_POS = new float[64];
   private static final float[] LIGHT_DIR = new float[64];
   private static final float[] LIGHT_COLOR = new float[64];
   private static final Matrix4f VIEW_PROJ = new Matrix4f();
   private static final Matrix4f INVERSE = new Matrix4f();
   private static boolean packWarned;
   private static boolean depthWarned;
   private static boolean colorWarned;
   private static boolean startupLogged;
   private static boolean offAnnounced;
   private static String viewSource;
   private static long windowStart = System.currentTimeMillis();
   private static int frames;
   private static int lastLights;
   private static int maxLights;
   private static double msSum;
   private static int msCount;
   private static boolean probeRequested;
   private static int lastLitPixels = -1;
   private static long lastProbeMs;
   private static boolean uploadLogWanted;
   private static int calls;
   private static String lastReason = "no-frame";
   private static String lastMatrix = "unknown";
   private static int lastDepthError = -1;
   private static String light0 = "(no lamp)";
   private static int debugMode;
   private static final String[] DEBUG_NAMES = new String[]{"off", "surface", "normal", "cone"};
   private static boolean diagBlendEnabled;
   private static int diagBlendSrc = -1;
   private static int diagBlendDst = -1;
   private static String depthState = "none";
   private static boolean deferredArmed;
   private static long deferredStamp;
   private static int deferredLights;
   private static boolean deferredCoverage;
   private static int deferredWidth = -1;
   private static int deferredHeight = -1;
   private static int deferredFrames;
   private static String deferredReason = "idle(no deferred attempt yet)";
   private static boolean deferredCalledOnce;
   private static boolean deferredSizeWarned;
   private static boolean deferredWarned;
   private static final long DEPTH_FRESH_NANOS = 200000000L;

   private ScatterPass() {
   }

   public static String setDebug(String what) {
      String key = what == null ? "" : what.trim().toLowerCase(Locale.ROOT);
      int mode = -1;

      for(int i = 0; i < DEBUG_NAMES.length; ++i) {
         if (DEBUG_NAMES[i].equals(key)) {
            mode = i;
         }
      }

      if (mode < 0) {
         return "scatter debug " + DEBUG_NAMES[debugMode] + "\uff08\u53ef\u9009\uff1aoff / surface / normal / cone\uff09";
      } else {
         debugMode = mode;
         if (!ScatterProgram.debugAvailable() && mode > 0) {
            return "scatter debug=" + DEBUG_NAMES[mode] + " \u4f46\u7740\u8272\u5668\u91cc\u6ca1\u6709 uDebug\uff1a\u753b\u4e0d\u51fa\u6765\uff08pass \u672c\u8eab\u4e0d\u53d7\u5f71\u54cd\uff0c\u89c1\u65e5\u5fd7\u91cc\u7684 uniform-missing \u8b66\u544a\uff09";
         } else {
            String var10000 = DEBUG_NAMES[mode];
            String var10001;
            if (mode == 0) {
               var10001 = "";
            } else {
               switch (mode) {
                  case 1:
                     var10001 = "\uff08R = N\u00b7L\uff0cG = \u9525\u5185\uff0cB = \u8ddd\u79bb\u8870\u51cf\uff09";
                     break;
                  case 2:
                     var10001 = "\uff08RGB = \u6cd5\u7ebf\uff0c\u6d0b\u7ea2 = \u8f6e\u5ed3\u4f2a\u5f71\uff0c\u9ed1 = \u5929\u7a7a\uff09";
                     break;
                  default:
                     var10001 = "\uff08R = \u706f\u8f74\u4f59\u5f26\uff0cG = \u9525\u5185\u6743\u91cd\uff0cB = \u6709\u706f\uff09";
               }
            }

            return "scatter debug=" + var10000 + var10001;
         }
      }
   }

   public static String debugName() {
      return DEBUG_NAMES[debugMode];
   }

   public static void tick(Minecraft client) {
      try {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         if (cfg == null || !cfg.enabled || !cfg.beamScatter) {
            return;
         }

         if (client == null || client.getWindow() == null) {
            return;
         }

         if (ScatterProgram.ensure(client) && !startupLogged) {
            startupLogged = true;
            MtrHeadlightClient.LOGGER.info("[lte] scatter: lights=0/{} program=ok depth=none surface={} frames=0 candidates=0 (shader compiled; the pass runs once a world with lit lamps is drawn)", 16, cfg.scatterSurface ? "on" : "off");
         }
      } catch (Throwable var2) {
         MtrHeadlightClient.LOGGER.warn("[lte] scatter: shader probe failed", var2);
      }

   }

   public static void render(WorldRenderContext context) {
      LightTest.report();
      ++calls;
      if (deferredArmed) {
         deferredArmed = false;
         depthState = "stale(unconsumed)";
         deferredReason = "not-consumed";
      }

      heartbeat(MtrHeadlightClient.config());

      try {
         Minecraft client = Minecraft.getInstance();
         if (client == null || client.level == null || client.player == null) {
            lastReason = "no-world";
            return;
         }

         if (LightTest.enabled()) {
            lastReason = "lighttest";
            tintThroughProgram(client);
            LightTest.drawFallbackTint();
            return;
         }

         HeadlightConfig cfg = MtrHeadlightClient.config();
         if (cfg == null || !cfg.drawScatter()) {
            lastReason = cfg == null ? "no-config" : (!cfg.enabled ? "off(master)" : "off(beamScatter=false)");
            return;
         }

         boolean pack = shaderPackActive();
         if (pack && !cfg.scatterUnderShaders) {
            lastReason = "shader-pack(scatterUnderShaders=false)";
            if (!packWarned) {
               packWarned = true;
               MtrHeadlightClient.LOGGER.info("[lte] scatter: shader pack active and scatterUnderShaders=false - the screen-space scattering pass stays out of the pipeline (the pack owns the framebuffer and the depth buffer); turn \"air scatter under shaders\" on to composite after the pack");
            }

            return;
         }

         if (!ScatterProgram.ensure(client)) {
            lastReason = "program=" + ScatterProgram.status();
            return;
         }

         lastMatrix = viewSource == null ? "unknown" : viewSource;
         int lights = ScatterLights.collect(client, cfg, LIGHT_POS, LIGHT_DIR, LIGHT_COLOR);
         if (lights == 0) {
            lastLights = 0;
            light0 = "(no lamp this frame; candidates=" + ScatterLights.candidates() + ")";
            lastReason = "no-lights";
            return;
         }

         describeFirstLight(lights);
         RenderTarget framebuffer = client.getMainRenderTarget();
         if (framebuffer == null) {
            lastReason = "no-framebuffer";
            return;
         }

         long started = System.nanoTime();
         if (!DepthCopy.update(framebuffer)) {
            lastDepthError = DepthCopy.lastError();
            lastReason = "depth-copy-failed(err=0x" + Integer.toHexString(Math.max(0, lastDepthError)) + ")";
            if (!depthWarned) {
               depthWarned = true;
               MtrHeadlightClient.LOGGER.warn("[lte] scatter: the scene depth could not be copied this frame - the pass stays off (it never draws against a stale or empty depth buffer)");
            }

            return;
         }

         lastDepthError = DepthCopy.lastError();
         depthState = "fresh";
         Matrix4f view = viewMatrix(context, client);
         VIEW_PROJ.set(RenderSystem.getProjectionMatrix()).mul(view);
         INVERSE.set(VIEW_PROJ).invert();
         lastMatrix = viewSource == null ? "unknown" : viewSource;
         boolean coverage = debugMode == 0 && (probeRequested || cfg.scatterSurface && litProbeDue());
         if (pack) {
            deferredLights = lights;
            deferredCoverage = coverage;
            deferredWidth = DepthCopy.width();
            deferredHeight = DepthCopy.height();
            deferredStamp = System.nanoTime();
            deferredArmed = true;
            deferredReason = "armed(waiting for renderWorld return)";
            lastLights = lights;
            maxLights = Math.max(maxLights, lights);
            lastReason = "pack(deferred)";
            return;
         }

         if (!ColorCopy.update(framebuffer)) {
            lastReason = "color-copy-failed";
            if (!colorWarned) {
               colorWarned = true;
               MtrHeadlightClient.LOGGER.warn("[lte] scatter: the scene colour could not be copied this frame - the pass stays off (there is no albedo to light)");
            }

            return;
         }

         ScatterProgram.uniforms(INVERSE, 96.0F, 0.94F, 0.55F, strength(cfg), 9.0F, cfg.scatterSurface, coverage, debugMode, lights, LIGHT_POS, LIGHT_DIR, LIGHT_COLOR);
         draw();
         if (uploadLogWanted) {
            uploadLogWanted = false;
            logUpload(lights);
         }

         if (coverage) {
            probeRequested = false;
            lastLitPixels = ColorCopy.probeLitPixels(framebuffer);
            lastProbeMs = System.currentTimeMillis();
         }

         lastLights = lights;
         maxLights = Math.max(maxLights, lights);
         ++frames;
         msSum += (double)(System.nanoTime() - started) / 1000000.0D;
         ++msCount;
         lastReason = "ok";
      } catch (Throwable var10) {
         lastReason = "exception:" + var10.getClass().getSimpleName();
         MtrHeadlightClient.LOGGER.error("[lte] scatter: pass failed", var10);
      }

   }

   private static boolean litProbeDue() {
      return System.currentTimeMillis() - lastProbeMs >= 5000L;
   }

   public static void renderDeferred() {
      if (!deferredCalledOnce) {
         deferredCalledOnce = true;
         MtrHeadlightClient.LOGGER.info("[lte] scatter: deferred hook reached (renderWorld return) - the shader-pack path composites after the pack, not in LAST");
      }

      if (deferredArmed) {
         deferredArmed = false;
         long started = System.nanoTime();

         try {
            HeadlightConfig cfg = MtrHeadlightClient.config();
            if (cfg == null || !cfg.drawScatter()) {
               deferredReason = "off(beamScatter)";
               return;
            }

            if (!cfg.scatterUnderShaders) {
               deferredReason = "off(scatterUnderShaders=false)";
               return;
            }

            if (!shaderPackActive()) {
               deferredReason = "no-pack(already drawn in LAST)";
               return;
            }

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) {
               deferredReason = "no-world";
               return;
            }

            if (!ScatterProgram.ready()) {
               deferredReason = "program=" + ScatterProgram.status();
               return;
            }

            long age = System.nanoTime() - deferredStamp;
            if (DepthCopy.texture() == -1 || DepthCopy.width() <= 0 || DepthCopy.height() <= 0 || DepthCopy.lastError() != 0 || age < 0L || age > 200000000L) {
               depthState = "stale(" + (age < 0L ? "clock" : "age=" + age / 1000000L + "ms") + (DepthCopy.texture() == -1 ? ",no-texture" : "") + (DepthCopy.lastError() != 0 ? ",err=0x" + Integer.toHexString(Math.max(0, DepthCopy.lastError())) : "") + ")";
               deferredReason = "depth-stale";
               warnDeferredOnce("the depth copy for the deferred composite was stale");
               return;
            }

            RenderTarget framebuffer = client.getMainRenderTarget();
            if (framebuffer == null) {
               deferredReason = "no-framebuffer";
               return;
            }

            if (!deferredSizeWarned && (deferredWidth != framebuffer.viewWidth || deferredHeight != framebuffer.viewHeight)) {
               deferredSizeWarned = true;
               MtrHeadlightClient.LOGGER.info("[lte] scatter: deferred draw at {}x{} but the depth copy is {}x{} - the quad is NDC so this only stretches the light (Iris resized its framebuffer after LAST)", new Object[]{framebuffer.viewWidth, framebuffer.viewHeight, deferredWidth, deferredHeight});
            }

            if (!ColorCopy.update(framebuffer)) {
               deferredReason = "color-copy-failed";
               warnDeferredOnce("the scene colour could not be copied after the shader pack composited");
               return;
            }

            ScatterProgram.uniforms(INVERSE, 96.0F, 0.94F, 0.55F, strength(cfg), 9.0F, cfg.scatterSurface, deferredCoverage, debugMode, deferredLights, LIGHT_POS, LIGHT_DIR, LIGHT_COLOR);
            if (!drawInto(framebuffer)) {
               deferredReason = "framebuffer-not-writable";
               warnDeferredOnce("the framebuffer the shader pack left bound cannot be drawn into");
               return;
            }

            if (deferredCoverage) {
               probeRequested = false;
               lastLitPixels = ColorCopy.probeLitPixels(framebuffer);
               lastProbeMs = System.currentTimeMillis();
            }

            ++deferredFrames;
            ++frames;
            msSum += (double)(System.nanoTime() - started) / 1000000.0D;
            ++msCount;
            deferredReason = "ok";
            lastReason = "ok(deferred)";
         } catch (Throwable var7) {
            deferredReason = "exception:" + var7.getClass().getSimpleName();
            warnDeferredOnce("the deferred composite failed (the world render itself is untouched)");
         }

      }
   }

   private static void warnDeferredOnce(String what) {
      if (!deferredWarned) {
         deferredWarned = true;
         MtrHeadlightClient.LOGGER.warn("[lte] scatter: {} - the deferred pass stays off until it can be done safely (see the 5 s \"defer=\" field for the current reason)", what);
      }
   }

   private static void drainErrors() {
      for(int i = 0; i < 16 && GL11.glGetError() != 0; ++i) {
      }

   }

   private static boolean drawInto(RenderTarget framebuffer) {
      int previousFbo = GL11.glGetInteger(36006);
      int[] viewport = new int[4];
      GL11.glGetIntegerv(2978, viewport);
      int width = Math.max(1, framebuffer.viewWidth);
      int height = Math.max(1, framebuffer.viewHeight);

      boolean var5;
      try {
         GlStateManager._glBindFramebuffer(36160, framebuffer.frameBufferId);
         if (GlStateManager.glCheckFramebufferStatus(36160) == 36053) {
            GlStateManager._viewport(0, 0, width, height);
            drainErrors();
            draw();
            return GL11.glGetError() == 0;
         }

         var5 = false;
      } finally {
         try {
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GlStateManager._glBindFramebuffer(36160, previousFbo);
         } catch (Throwable var13) {
         }

      }

      return var5;
   }

   private static void describeFirstLight(int lights) {
      try {
         float r = LIGHT_POS[3];
         float beam = LIGHT_DIR[3];
         light0 = String.format(Locale.ROOT, "pos=(%.2f,%.2f,%.2f) dir=(%.2f,%.2f,%.2f) rgb=(%.2f,%.2f,%.2f) power=%.2f radius=%.1f beam=%.1f coneDeg=%.1f/%.1f lamps=%d", LIGHT_POS[0], LIGHT_POS[1], LIGHT_POS[2], LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2], LIGHT_COLOR[0], LIGHT_COLOR[1], LIGHT_COLOR[2], LIGHT_COLOR[3], r, beam, Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, (double)0.94F)))), Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, (double)0.55F)))), lights);
      } catch (Throwable var3) {
         light0 = "(unreadable:" + var3.getClass().getSimpleName() + ")";
      }

   }

   private static void heartbeat(HeadlightConfig cfg) {
      if (cfg != null) {
         long now = System.currentTimeMillis();
         if (!cfg.beamScatter) {
            if (!offAnnounced) {
               offAnnounced = true;
               MtrHeadlightClient.LOGGER.info("[lte] scatter: off (beamScatter=false) - nothing is drawn and nothing is measured; /mtr_lte scatter on \u6253\u5f00");
            }

            windowStart = now;
            calls = 0;
         } else if (now - windowStart >= 5000L) {
            windowStart = now;
            double avg = msCount == 0 ? 0.0D : msSum / (double)msCount;
            uploadLogWanted = true;
            ScatterProgram.requestReadback();
            MtrHeadlightClient.LOGGER.info(String.format(Locale.ROOT, "[lte] scatter pass: frames=%d pack=%s deferred=%s depth=%s lights=%d color=%s blend=%s litPx=%s matrix=%s calls=%d maxLights=%d avgMs=%.2f surface=%s debug=%s defer=%s reason=%s", frames, shaderPackActive(), deferredFrames > 0, depthText(), lastLights, colorText(), blendText(), litPixels(cfg), lastMatrix, calls, maxLights, avg, cfg.scatterSurface ? "on" : "off", debugName(), deferredCalledOnce ? deferredReason : "never-called", lastReason));
            MtrHeadlightClient.LOGGER.info("[lte] scatter light0: {} depthErr={}", light0, lastDepthError < 0 ? "none" : (lastDepthError == 0 ? "ok" : "0x" + Integer.toHexString(lastDepthError)));
            frames = 0;
            calls = 0;
            maxLights = 0;
            msSum = 0.0D;
            msCount = 0;
            deferredFrames = 0;
         }
      }
   }

   private static void logUpload(int lights) {
      String staged = String.format(Locale.ROOT, "pos=(%.2f,%.2f,%.2f) dir=(%.2f,%.2f,%.2f) rgb=(%.2f,%.2f,%.2f) cosIn=%.2f cosOut=%.2f power=%.2f radius=%.1f beam=%.1f lamps=%d", LIGHT_POS[0], LIGHT_POS[1], LIGHT_POS[2], LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2], LIGHT_COLOR[0], LIGHT_COLOR[1], LIGHT_COLOR[2], 0.94F, 0.55F, LIGHT_COLOR[3], LIGHT_POS[3], LIGHT_DIR[3], lights);
      float[] gl = ScatterProgram.lastReadback();
      String back;
      if (gl == null) {
         back = "gl[0]=missing(readback not taken)";
      } else {
         back = String.format(Locale.ROOT, "gl[0] pos=(%.2f,%.2f,%.2f) dir=(%.2f,%.2f,%.2f) rgb=(%.2f,%.2f,%.2f) power=%.2f %s", gl[0], gl[1], gl[2], gl[4], gl[5], gl[6], gl[8], gl[9], gl[10], gl[11], gl[11] > 1.0E-4F ? "" : " <- the GPU never got the lamps: power is still 0, the per-lamp loop is skipped entirely");
      }

      MtrHeadlightClient.LOGGER.info("[lte] scatter upload: {} {}", staged, back);
   }

   private static String blendText() {
      if (diagBlendSrc == -1) {
         return "none";
      } else if (!diagBlendEnabled) {
         return "replace";
      } else if (diagBlendSrc == 770 && diagBlendDst == 1) {
         return "add";
      } else {
         return diagBlendSrc == 770 && diagBlendDst == 771 ? "alpha" : "on(0x" + Integer.toHexString(diagBlendSrc) + ":0x" + Integer.toHexString(diagBlendDst) + ")";
      }
   }

   private static String depthText() {
      if (DepthCopy.texture() == -1) {
         return depthState.equals("none") ? "none" : depthState + " none";
      } else {
         return depthState + " " + DepthCopy.width() + "x" + DepthCopy.height() + (lastDepthError == 0 ? " ok" : " err=0x" + Integer.toHexString(Math.max(0, lastDepthError)));
      }
   }

   private static String colorText() {
      return ColorCopy.texture() == -1 ? "none" : ColorCopy.width() + "x" + ColorCopy.height() + " ok";
   }

   private static void draw() {
      BufferBuilder buffer = Tesselator.getInstance().getBuilder();
      buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION);
      buffer.vertex(-1.0D, -1.0D, 0.0D).endVertex();
      buffer.vertex(-1.0D, 1.0D, 0.0D).endVertex();
      buffer.vertex(1.0D, 1.0D, 0.0D).endVertex();
      buffer.vertex(1.0D, -1.0D, 0.0D).endVertex();
      BufferBuilder.RenderedBuffer built = buffer.endOrDiscardIfEmpty();
      if (built != null) {
         RenderSystem.setShader(ScatterProgram::program);
         int depth = DepthCopy.texture();
         RenderSystem.setShaderTexture(0, depth == -1 ? 0 : depth);
         int color = ColorCopy.texture();
         RenderSystem.setShaderTexture(1, color == -1 ? 0 : color);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         ShaderInstance program = ScatterProgram.program();
         if (program != null) {
            program.apply();
            ScatterProgram.uploadArrays();
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            try {
               BufferUploader.drawWithShader(built);
               diagBlendEnabled = GL11.glIsEnabled(3042);
               diagBlendSrc = GL11.glGetInteger(32969);
               diagBlendDst = GL11.glGetInteger(32968);
            } finally {
               RenderSystem.depthMask(true);
               RenderSystem.enableDepthTest();
               RenderSystem.enableBlend();
               RenderSystem.defaultBlendFunc();
               RenderSystem.enableCull();
               RenderSystem.setShader(GameRenderer::getPositionColorShader);
               RenderSystem.setShaderTexture(0, 0);
               RenderSystem.setShaderTexture(1, 0);
               RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

         }
      }
   }

   private static void tintThroughProgram(Minecraft client) {
      try {
         RenderTarget framebuffer = client.getMainRenderTarget();
         if (framebuffer == null) {
            LightTest.tintFailure("no-framebuffer");
            return;
         }

         if (!ScatterProgram.ensure(client)) {
            LightTest.tintFailure("program=" + ScatterProgram.status());
            return;
         }

         if (!ScatterProgram.testTint(true)) {
            LightTest.tintFailure("uniform-missing:uTest");
            return;
         }

         int width = Math.max(2, framebuffer.viewWidth);
         int height = Math.max(1, framebuffer.viewHeight);
         GlStateManager._viewport(0, 0, Math.max(1, width / 2), height);

         try {
            draw();
            LightTest.programTintSubmitted();
         } finally {
            GlStateManager._viewport(0, 0, width, height);
            ScatterProgram.testTint(false);
         }
      } catch (Throwable var8) {
         LightTest.tintFailure(var8.getClass().getSimpleName() + ":" + firstLine(var8));
      }

   }

   private static float strength(HeadlightConfig cfg) {
      return (float)cfg.scatterStrengthClamped();
   }

   private static Matrix4f viewMatrix(WorldRenderContext context, Minecraft client) {
      Matrix4f candidate = null;

      try {
         if (context != null && context.matrixStack() != null) {
            candidate = context.matrixStack().last().pose();
         }
      } catch (Throwable var5) {
         candidate = null;
      }

      if (rigid(candidate)) {
         noteViewSource("context");
         return candidate;
      } else {
         Matrix4f renderSystem = RenderSystem.getModelViewMatrix();
         if (rigid(renderSystem)) {
            noteViewSource("render");
            return renderSystem;
         } else {
            noteViewSource("rebuilt");
            Camera camera = client.gameRenderer.getMainCamera();
            return (new Matrix4f()).rotateX((float)Math.toRadians((double)camera.getXRot())).rotateY((float)Math.toRadians((double)(camera.getYRot() + 180.0F)));
         }
      }
   }

   private static boolean rigid(Matrix4f m) {
      if (m == null) {
         return false;
      } else if (!(Math.abs(m.m30()) > 0.001F) && !(Math.abs(m.m31()) > 0.001F) && !(Math.abs(m.m32()) > 0.001F) && !(Math.abs(m.m33() - 1.0F) > 0.001F)) {
         float det = m.m00() * (m.m11() * m.m22() - m.m12() * m.m21()) - m.m01() * (m.m10() * m.m22() - m.m12() * m.m20()) + m.m02() * (m.m10() * m.m21() - m.m11() * m.m20());
         return Math.abs(Math.abs(det) - 1.0F) < 0.05F;
      } else {
         return false;
      }
   }

   private static void noteViewSource(String source) {
      if (viewSource == null) {
         viewSource = source;
         MtrHeadlightClient.LOGGER.info("[lte] scatter: depth reconstruction uses inverse(ProjMat * {}-derived view)", source);
      }
   }

   private static boolean shaderPackActive() {
      try {
         return ShaderPackProbe.active();
      } catch (Throwable var1) {
         return false;
      }
   }

   public static String status(HeadlightConfig cfg) {
      StringBuilder sb = new StringBuilder(224);
      sb.append("scatter ").append(cfg.beamScatter ? "on" : "off");
      sb.append(" draws=").append(cfg.drawScatter() ? "yes" : "no");
      sb.append(" program=").append(ScatterProgram.status());
      sb.append(" strength=").append(String.format(Locale.ROOT, "%.2f", cfg.scatterStrengthClamped()));
      sb.append(" radius=").append(String.format(Locale.ROOT, "%.1f", cfg.scatterRadiusClamped()));
      sb.append(" lights=").append(lastLights).append('/').append(16);
      sb.append(" candidates=").append(ScatterLights.candidates());
      sb.append(" depth=").append(depthText());
      sb.append(" surface=").append(cfg.scatterSurface ? "on" : "off");
      sb.append(" shaders=").append(cfg.scatterUnderShaders ? "on" : "off");
      sb.append(" pack=").append(shaderPackActive() ? "on" : "off");
      sb.append(" defer=").append(deferredCalledOnce ? deferredReason : "never-called");
      sb.append(" deferredFrames=").append(deferredFrames);
      sb.append(" color=").append(colorText());
      sb.append(" litPx=").append(litPixels(cfg));
      sb.append(" matrix=").append(lastMatrix);
      sb.append(" debug=").append(debugName());
      sb.append(" uniforms[").append(ScatterProgram.locations()).append(']');
      sb.append(" reason=").append(lastReason);
      if (ScatterProgram.failure() != null) {
         sb.append(" failure=").append(ScatterProgram.failure());
      }

      if (shaderPackActive()) {
         sb.append(cfg.scatterUnderShaders ? " (shader pack active: composited after the pack, see defer=)" : " (shader pack active: pass skipped, scatterUnderShaders=false)");
      }

      return sb.toString();
   }

   private static String litPixels(HeadlightConfig cfg) {
      if (debugMode != 0) {
         return "n/a(debug " + debugName() + ")";
      } else if (!cfg.scatterSurface) {
         return "n/a(surface off)";
      } else if (lastLights == 0) {
         return "n/a(no lit lamps)";
      } else if (probeRequested) {
         return "measuring(run again)";
      } else {
         return lastLitPixels < 0 ? "n/a(no frame)" : "\u2248" + lastLitPixels;
      }
   }

   public static void requestLitPixelProbe() {
      probeRequested = true;
   }

   private static String firstLine(Throwable t) {
      String text = t == null ? "unknown" : String.valueOf(t.getMessage());
      int newline = text.indexOf(10);
      String head = newline < 0 ? text : text.substring(0, newline);
      return head.length() > 120 ? head.substring(0, 120) : head;
   }

   public static void shutdown() {
      deferredArmed = false;
      deferredStamp = 0L;
      deferredWidth = -1;
      deferredHeight = -1;
      deferredLights = 0;
      depthState = "none";
      DepthCopy.release();
      ColorCopy.release();
      ScatterProgram.close();
   }
}

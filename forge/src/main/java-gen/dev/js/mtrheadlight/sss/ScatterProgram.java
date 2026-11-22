package dev.js.mtrheadlight.sss;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.js.mtrheadlight.MtrHeadlightClient;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

final class ScatterProgram {
   static final String NAME = "light_scatter";
   private static final String NAMESPACE = "lte";
   static final int MAX_LAMPS = 16;
   private static final int STRIDE = 4;
   private static final int ARRAY_FLOATS = 64;
   private static ShaderInstance program;
   private static boolean attempted;
   private static String failure;
   private static Uniform uInvViewProj;
   private static Uniform uMaxRay;
   private static Uniform uCone;
   private static Uniform uSurface;
   private static Uniform uCount;
   private static Uniform uTest;
   private static Uniform uDebug;
   private static int posLoc = -1;
   private static int dirLoc = -1;
   private static int colorLoc = -1;
   private static FloatBuffer posBuf;
   private static FloatBuffer dirBuf;
   private static FloatBuffer colorBuf;
   private static FloatBuffer readBuf;
   private static boolean uploadWarned;
   private static boolean readbackWanted;
   private static float[] lastReadback;

   private ScatterProgram() {
   }

   static boolean ready() {
      return program != null && failure == null;
   }

   static String failure() {
      return failure;
   }

   static String status() {
      if (failure != null) {
         return "failed(" + failure + ")";
      } else if (program != null) {
         return "ok";
      } else {
         return attempted ? "missing" : "not-loaded";
      }
   }

   static boolean ensure(Minecraft client) {
      if (program != null) {
         return true;
      } else if (attempted) {
         return false;
      } else {
         attempted = true;
         if (client != null && client.getResourceManager() != null) {
            try {
               ResourceProvider remap = remapped(client.getResourceManager());
               ShaderInstance loaded = new ShaderInstance(remap, "light_scatter", DefaultVertexFormat.POSITION);
               Uniform invViewProj = require(loaded, "uInvViewProj");
               Uniform maxRay = require(loaded, "uMaxRay");
               Uniform cone = require(loaded, "uCone");
               Uniform surface = require(loaded, "uSurface");
               Uniform count = require(loaded, "uCount");
               Uniform test = require(loaded, "uTest");
               if (invViewProj != null && maxRay != null && cone != null && surface != null && count != null && test != null) {
                  int glRef = loaded.getId();
                  int pLoc = Uniform.glGetUniformLocation(glRef, "uLightPos");
                  int dLoc = Uniform.glGetUniformLocation(glRef, "uLightDir");
                  int cLoc = Uniform.glGetUniformLocation(glRef, "uLightColor");
                  if (pLoc >= 0 && dLoc >= 0 && cLoc >= 0) {
                     uInvViewProj = invViewProj;
                     uMaxRay = maxRay;
                     uCone = cone;
                     uSurface = surface;
                     uCount = count;
                     uTest = test;
                     uDebug = loaded.getUniform("uDebug");
                     if (uDebug == null) {
                        MtrHeadlightClient.LOGGER.warn("[lte] scatter: shader has no uniform uDebug - /mtr_lte scatter debug will do nothing (the pass itself is unaffected)");
                     }

                     posLoc = pLoc;
                     dirLoc = dLoc;
                     colorLoc = cLoc;
                     posBuf = BufferUtils.createFloatBuffer(64);
                     dirBuf = BufferUtils.createFloatBuffer(64);
                     colorBuf = BufferUtils.createFloatBuffer(64);
                     readBuf = BufferUtils.createFloatBuffer(4);
                     readbackWanted = false;
                     lastReadback = null;
                     program = loaded;
                     logUniforms();
                     return true;
                  } else {
                     failure = "uniform-array-missing:pos=" + pLoc + ",dir=" + dLoc + ",color=" + cLoc;
                     MtrHeadlightClient.LOGGER.warn("[lte] scatter: the shader has no readable lamp array (uLightPos={} uLightDir={} uLightColor={}) - the per-lamp loop cannot be fed, so the pass is disabled", new Object[]{pLoc, dLoc, cLoc});
                     closeQuietly(loaded);
                     return false;
                  }
               } else {
                  failure = "uniform-missing";
                  closeQuietly(loaded);
                  return false;
               }
            } catch (Throwable var13) {
               failure = var13.getClass().getSimpleName() + ":" + firstLine(var13);
               MtrHeadlightClient.LOGGER.warn("[lte] scatter: could not load shader {}:{} - the screen-space pass is disabled for this session", new Object[]{"lte", "light_scatter", var13});
               program = null;
               return false;
            }
         } else {
            failure = "no-resource-manager";
            return false;
         }
      }
   }

   private static void logUniforms() {
      MtrHeadlightClient.LOGGER.info("[lte] scatter uniforms: uLightPos[0]={} uLightDir[0]={} uLightColor[0]={} uInvViewProj={} uMaxRay={} uCone={} uSurface={} uCount={} uTest={} uDebug={} (lamp arrays are 16x vec4, uploaded by glUniform4fv after bind; json declares none of them)", new Object[]{posLoc, dirLoc, colorLoc, locOf(uInvViewProj), locOf(uMaxRay), locOf(uCone), locOf(uSurface), locOf(uCount), locOf(uTest), locOf(uDebug)});
   }

   private static int locOf(Uniform uniform) {
      return uniform == null ? -1 : uniform.getLocation();
   }

   private static ResourceProvider remapped(ResourceProvider base) {
      return (id) -> {
         if (id != null && "minecraft".equals(id.getNamespace()) && id.getPath().startsWith("shaders/")) {
            Optional<Resource> own = base.getResource(new ResourceLocation("lte", id.getPath()));
            if (own.isPresent()) {
               return own;
            }
         }

         return base.getResource(id);
      };
   }

   private static Uniform require(ShaderInstance loaded, String name) {
      Uniform uniform = loaded.getUniform(name);
      if (uniform == null) {
         MtrHeadlightClient.LOGGER.warn("[lte] scatter: shader {}:{} has no uniform {} - the json and the GLSL disagree", new Object[]{"lte", "light_scatter", name});
      }

      return uniform;
   }

   static void uniforms(Matrix4f inverseViewProj, float maxRay, float cosInner, float cosOuter, float strength, float attenuationRef, boolean surface, boolean coverage, int debug, int count, float[] pos, float[] dir, float[] color) {
      if (program != null && failure == null) {
         uInvViewProj.set(inverseViewProj);
         uMaxRay.set(maxRay);
         uCone.setMat2x2(cosInner, cosOuter, 0.0F, 0.0F);
         uSurface.setMat2x2(strength, attenuationRef, surface ? 1.0F : 0.0F, coverage ? 1.0F : 0.0F);
         uCount.set(count);
         uTest.set(0);
         if (uDebug != null) {
            uDebug.set(debug);
         }

         stage(posBuf, pos);
         stage(dirBuf, dir);
         stage(colorBuf, color);
      }
   }

   private static void stage(FloatBuffer buffer, float[] values) {
      if (buffer != null && values != null) {
         int n = Math.min(values.length, buffer.capacity());
         buffer.clear();
         buffer.put(values, 0, n);
         buffer.position(0);
      }
   }

   static void uploadArrays() {
      if (program != null && failure == null) {
         try {
            RenderSystem.glUniform4(posLoc, rewind(posBuf));
            RenderSystem.glUniform4(dirLoc, rewind(dirBuf));
            RenderSystem.glUniform4(colorLoc, rewind(colorBuf));
            if (readbackWanted) {
               readbackWanted = false;
               readback();
            }
         } catch (Throwable var1) {
            if (!uploadWarned) {
               uploadWarned = true;
               MtrHeadlightClient.LOGGER.warn("[lte] scatter: lamp array upload failed: {}", firstLine(var1));
            }
         }

      }
   }

   private static FloatBuffer rewind(FloatBuffer buffer) {
      if (buffer != null) {
         buffer.position(0);
      }

      return buffer;
   }

   static void requestReadback() {
      readbackWanted = true;
   }

   private static void readback() {
      if (readBuf != null) {
         try {
            int glRef = program.getId();
            float[] out = lastReadback == null ? new float[12] : lastReadback;
            readVec(glRef, posLoc, out, 0);
            readVec(glRef, dirLoc, out, 4);
            readVec(glRef, colorLoc, out, 8);
            lastReadback = out;
         } catch (Throwable var2) {
            lastReadback = null;
         }

      }
   }

   private static void readVec(int glRef, int location, float[] out, int at) {
      readBuf.clear();
      GL20.glGetUniformfv(glRef, location, readBuf);

      for(int i = 0; i < 4; ++i) {
         out[at + i] = readBuf.get(i);
      }

   }

   static float[] lastReadback() {
      return lastReadback;
   }

   static boolean debugAvailable() {
      return uDebug != null;
   }

   static boolean testTint(boolean on) {
      if (uTest == null) {
         return false;
      } else {
         uTest.set(on ? 1 : 0);
         return true;
      }
   }

   static ShaderInstance program() {
      return program;
   }

   static String locations() {
      return program == null ? "program=" + status() : String.format(Locale.ROOT, "uLightPos[0]=%d uLightDir[0]=%d uLightColor[0]=%d uInvViewProj=%d uMaxRay=%d uCone=%d uSurface=%d uCount=%d uTest=%d uDebug=%d", posLoc, dirLoc, colorLoc, locOf(uInvViewProj), locOf(uMaxRay), locOf(uCone), locOf(uSurface), locOf(uCount), locOf(uTest), locOf(uDebug));
   }

   static void close() {
      ShaderInstance loaded = program;
      program = null;
      failure = null;
      attempted = false;
      uInvViewProj = null;
      uMaxRay = null;
      uCone = null;
      uSurface = null;
      uCount = null;
      uTest = null;
      uDebug = null;
      posLoc = -1;
      dirLoc = -1;
      colorLoc = -1;
      posBuf = null;
      dirBuf = null;
      colorBuf = null;
      readBuf = null;
      uploadWarned = false;
      readbackWanted = false;
      lastReadback = null;
      closeQuietly(loaded);
   }

   private static void closeQuietly(ShaderInstance loaded) {
      if (loaded != null) {
         try {
            loaded.close();
         } catch (Throwable var2) {
         }

      }
   }

   private static String firstLine(Throwable t) {
      String text = t == null ? "unknown" : String.valueOf(t.getMessage());
      int newline = text.indexOf(10);
      String head = newline < 0 ? text : text.substring(0, newline);
      return head.length() > 160 ? head.substring(0, 160) : head;
   }
}

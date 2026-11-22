package dev.js.lte.sss;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.js.lte.MtrHeadlightClient;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

final class ColorCopy {
   static final int PROBE_W = 96;
   static final int PROBE_H = 54;
   private static int texture = -1;
   private static int fbo = -1;
   private static int width = -1;
   private static int height = -1;
   private static int sourceTexture = -1;
   private static boolean failed;
   private static int probeTexture = -1;
   private static int probeFbo = -1;

   private ColorCopy() {
   }

   static int texture() {
      return texture;
   }

   static int width() {
      return width;
   }

   static int height() {
      return height;
   }

   static boolean update(RenderTarget source) {
      if (source != null && !failed) {
         if (!ensureTargets(source)) {
            failed = true;
            MtrHeadlightClient.LOGGER.warn("[lte] scatter: the scene colour could not be copied - the pass stays off this session (there is no albedo to light, and the volume term no longer exists)");
            return false;
         } else {
            return blit(source);
         }
      } else {
         return false;
      }
   }

   private static boolean ensureTargets(RenderTarget source) {
      int src = source.getColorTextureId();
      if (src == 0) {
         return false;
      } else if (texture != -1 && width == source.width && height == source.height && sourceTexture == src) {
         return true;
      } else {
         release();

         int target;
         try {
            GlStateManager._activeTexture(33984);
            int tex = newColorTexture(source.width, source.height);
            if (tex != -1) {
               target = newColorFbo(tex);
               if (target == -1) {
                  GlStateManager._deleteTexture(tex);
                  return false;
               }

               texture = tex;
               fbo = target;
               width = source.width;
               height = source.height;
               sourceTexture = src;
               MtrHeadlightClient.LOGGER.info("[lte] scatter: colour copy {}x{} RGBA8 fbo={}", new Object[]{width, height, fbo});
               return true;
            }

            target = 0;
         } catch (Throwable var8) {
            MtrHeadlightClient.LOGGER.warn("[lte] scatter: colour target failed", var8);
            release();
            return false;
         } finally {
            GlStateManager._glBindFramebuffer(36160, source.frameBufferId);
         }

         return false;
      }
   }

   private static int newColorTexture(int w, int h) {
      int[] ids = new int[1];
      GlStateManager._genTextures(ids);
      GlStateManager._bindTexture(ids[0]);
      drainErrors();
      GlStateManager._texImage2D(3553, 0, 32856, w, h, 0, 6408, 5121, (IntBuffer)null);
      GlStateManager._texParameter(3553, 10241, 9728);
      GlStateManager._texParameter(3553, 10240, 9728);
      GlStateManager._texParameter(3553, 10242, 10496);
      GlStateManager._texParameter(3553, 10243, 10496);
      if (GL11.glGetError() != 0) {
         GlStateManager._deleteTexture(ids[0]);
         return -1;
      } else {
         return ids[0];
      }
   }

   private static int newColorFbo(int tex) {
      int id = GlStateManager.glGenFramebuffers();
      GlStateManager._glBindFramebuffer(36160, id);
      GlStateManager._glFramebufferTexture2D(36160, 36064, 3553, tex, 0);
      if (GlStateManager.glCheckFramebufferStatus(36160) != 36053) {
         GlStateManager._glDeleteFramebuffers(id);
         return -1;
      } else {
         return id;
      }
   }

   private static boolean blit(RenderTarget source) {
      boolean var2;
      try {
         drainErrors();
         GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
         GL11.glReadBuffer(36064);
         GlStateManager._glBindFramebuffer(36009, fbo);
         GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height, 0, 0, width, height, 16384, 9728);
         return GL11.glGetError() == 0;
      } catch (Throwable var6) {
         var2 = false;
      } finally {
         GlStateManager._glBindFramebuffer(36160, source.frameBufferId);
      }

      return var2;
   }

   static int probeLitPixels(RenderTarget source) {
      if (source != null && !failed && ensureProbe()) {
         ByteBuffer pixels = null;

         byte t;
         try {
            drainErrors();
            GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
            GL11.glReadBuffer(36064);
            GlStateManager._glBindFramebuffer(36009, probeFbo);
            GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height, 0, 0, 96, 54, 16384, 9729);
            if (GL11.glGetError() == 0) {
               GlStateManager._glBindFramebuffer(36008, probeFbo);
               GL11.glReadBuffer(36064);
               pixels = MemoryUtil.memAlloc(20736);
               GL11.glPixelStorei(3333, 1);
               GL11.glReadPixels(0, 0, 96, 54, 6408, 5121, pixels);
               if (GL11.glGetError() != 0) {
                  return -1;
               }

               long lit = 0L;

               for(int i = 3; i < 20736; i += 4) {
                  lit += (long)(pixels.get(i) & 255);
               }

               double perTexel = (double)source.width * (double)source.height / 5184.0D;
               double estimate = (double)lit / 255.0D * perTexel;
               return (int)Math.round(estimate);
            }

            t = -1;
         } catch (Throwable var20) {
            return -1;
         } finally {
            if (pixels != null) {
               MemoryUtil.memFree(pixels);
            }

            try {
               GL11.glPixelStorei(3333, 4);
               GlStateManager._glBindFramebuffer(36160, source.frameBufferId);
            } catch (Throwable var19) {
            }

         }

         return t;
      } else {
         return -1;
      }
   }

   private static boolean ensureProbe() {
      if (probeFbo != -1) {
         return true;
      } else {
         try {
            GlStateManager._activeTexture(33984);
            int tex = newColorTexture(96, 54);
            if (tex == -1) {
               return false;
            } else {
               int target = newColorFbo(tex);
               if (target == -1) {
                  GlStateManager._deleteTexture(tex);
                  return false;
               } else {
                  probeTexture = tex;
                  probeFbo = target;
                  return true;
               }
            }
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   private static void drainErrors() {
      for(int i = 0; i < 16 && GL11.glGetError() != 0; ++i) {
      }

   }

   static void release() {
      try {
         if (fbo != -1) {
            GlStateManager._glDeleteFramebuffers(fbo);
         }

         if (texture != -1) {
            GlStateManager._deleteTexture(texture);
         }

         if (probeFbo != -1) {
            GlStateManager._glDeleteFramebuffers(probeFbo);
         }

         if (probeTexture != -1) {
            GlStateManager._deleteTexture(probeTexture);
         }
      } catch (Throwable var1) {
      }

      fbo = -1;
      texture = -1;
      width = -1;
      height = -1;
      sourceTexture = -1;
      probeFbo = -1;
      probeTexture = -1;
      failed = false;
   }
}

package dev.js.mtrheadlight.sss;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.js.mtrheadlight.MtrHeadlightClient;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;

final class DepthCopy {
   private static int texture = -1;
   private static int fbo = -1;
   private static int width = -1;
   private static int height = -1;
   private static int sourceTexture = -1;
   private static int lastError = -1;

   private DepthCopy() {
   }

   static int lastError() {
      return lastError;
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
      if (source == null) {
         return false;
      } else {
         return !ensureTargets(source) ? false : blit(source);
      }
   }

   private static boolean ensureTargets(RenderTarget source) {
      int src = source.getDepthTextureId();
      if (texture != -1 && width == source.width && height == source.height && sourceTexture == src) {
         return true;
      } else {
         release();

         boolean packed;
         try {
            GlStateManager._activeTexture(33984);
            GlStateManager._bindTexture(src);
            int internal = GL11.glGetTexLevelParameteri(3553, 0, 4099);
            if (internal != 0) {
               packed = internal == 35056 || internal == 36013;
               int format = packed ? '\u84f9' : 6402;
               int type = internal == 36013 ? '\u8dad' : (packed ? '\u84fa' : 5126);
               int tex = newDepthTexture(source.width, source.height, internal, format, type);
               if (tex == -1) {
                  return false;
               }

               int target = newDepthFbo(tex);
               if (target == -1) {
                  GlStateManager._deleteTexture(tex);
                  return false;
               }

               texture = tex;
               fbo = target;
               width = source.width;
               height = source.height;
               sourceTexture = src;
               MtrHeadlightClient.LOGGER.info("[lte] scatter: depth copy {}x{} internal=0x{} fbo={}", new Object[]{width, height, Integer.toHexString(internal), fbo});
               return true;
            }

            packed = false;
         } catch (Throwable var12) {
            MtrHeadlightClient.LOGGER.warn("[lte] scatter: depth target failed", var12);
            release();
            return false;
         } finally {
            GlStateManager._glBindFramebuffer(36160, source.frameBufferId);
         }

         return packed;
      }
   }

   private static int newDepthTexture(int w, int h, int internal, int format, int type) {
      int[] ids = new int[1];
      GlStateManager._genTextures(ids);
      GlStateManager._bindTexture(ids[0]);
      drainErrors();
      GlStateManager._texImage2D(3553, 0, internal, w, h, 0, format, type, (IntBuffer)null);
      GlStateManager._texParameter(3553, 10241, 9728);
      GlStateManager._texParameter(3553, 10240, 9728);
      GlStateManager._texParameter(3553, 10242, 10496);
      GlStateManager._texParameter(3553, 10243, 10496);
      GlStateManager._texParameter(3553, 34892, 0);
      if (GL11.glGetError() != 0) {
         GlStateManager._deleteTexture(ids[0]);
         return -1;
      } else {
         return ids[0];
      }
   }

   private static int newDepthFbo(int tex) {
      int id = GlStateManager.glGenFramebuffers();
      GlStateManager._glBindFramebuffer(36160, id);
      GlStateManager._glFramebufferTexture2D(36160, 36096, 3553, tex, 0);
      GL11.glDrawBuffer(0);
      GL11.glReadBuffer(0);
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
         GlStateManager._glBindFramebuffer(36009, fbo);
         GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height, 0, 0, width, height, 256, 9728);
         int error = GL11.glGetError();
         lastError = error;
         return error == 0;
      } catch (Throwable var6) {
         lastError = -2;
         var2 = false;
      } finally {
         GlStateManager._glBindFramebuffer(36160, source.frameBufferId);
      }

      return var2;
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
      } catch (Throwable var1) {
      }

      fbo = -1;
      texture = -1;
      width = -1;
      height = -1;
      sourceTexture = -1;
   }
}

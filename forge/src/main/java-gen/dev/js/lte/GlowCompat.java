package dev.js.lte;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

final class GlowCompat {
   static int diagDepthFunc = -1;
   static int diagDepthFuncOnEntry = -1;
   static int diagBlendSrc = -1;
   static int diagBlendDst = -1;
   static boolean diagBlendEnabled;
   static boolean diagCullEnabled;
   static int diagTextureId = -1;
   static boolean diagForceOcclusion;

   private GlowCompat() {
   }

   static void draw(float[] data, int count, PoseStack stack) {
      Matrix4f matrix = stack.last().pose();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.enableDepthTest();
      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      BufferBuilder buffer = Tesselator.getInstance().getBuilder();
      buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

      for(int i = 0; i + 6 < count; i += 7) {
         buffer.vertex(matrix, data[i], data[i + 1], data[i + 2]).color(data[i + 3], data[i + 4], data[i + 5], data[i + 6]).endVertex();
      }

      BufferUploader.drawWithShader(buffer.end());
      RenderSystem.depthMask(true);
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableCull();
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
   }

   static void drawTextured(float[] data, int count, PoseStack stack, ResourceLocation texture) {
      drawTextured(data, count, stack, texture, false);
   }

   static void drawTextured(float[] data, int count, PoseStack stack, ResourceLocation texture, boolean forceOcclusion) {
      Matrix4f matrix = stack.last().pose();
      RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
      RenderSystem.setShaderTexture(0, texture);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.enableBlend();
      RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
      RenderSystem.enableDepthTest();
      int entryFunc = forceOcclusion ? GL11.glGetInteger(2932) : -1;
      if (forceOcclusion) {
         forceDepthFunc(515);
      }

      RenderSystem.depthMask(false);
      RenderSystem.disableCull();
      BufferBuilder buffer = Tesselator.getInstance().getBuilder();
      buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

      for(int i = 0; i + 8 < count; i += 9) {
         buffer.vertex(matrix, data[i], data[i + 1], data[i + 2]).uv(data[i + 3], data[i + 4]).color(data[i + 5], data[i + 6], data[i + 7], data[i + 8]).endVertex();
      }

      BufferUploader.drawWithShader(buffer.end());
      diagDepthFunc = GL11.glGetInteger(2932);
      diagBlendSrc = GL11.glGetInteger(32969);
      diagBlendDst = GL11.glGetInteger(32968);
      diagBlendEnabled = GL11.glIsEnabled(3042);
      diagCullEnabled = GL11.glIsEnabled(2884);
      diagTextureId = RenderSystem.getShaderTexture(0);
      diagForceOcclusion = forceOcclusion;
      diagDepthFuncOnEntry = entryFunc;
      if (forceOcclusion) {
         forceDepthFunc(entryFunc == -1 ? 515 : entryFunc);
      }

      RenderSystem.depthMask(true);
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableCull();
      RenderSystem.setShaderTexture(0, 0);
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
   }

   private static void forceDepthFunc(int func) {
      GL11.glDepthFunc(func);
      int other = func == 515 ? 519 : 515;
      GlStateManager._depthFunc(other);
      GlStateManager._depthFunc(func);
   }

   static void drawScreenQuad(float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
      Matrix4f projection = RenderSystem.getProjectionMatrix();
      Matrix4f modelView = RenderSystem.getModelViewMatrix();
      Matrix4f savedProjection = new Matrix4f(projection);
      Matrix4f savedModelView = new Matrix4f(modelView);

      try {
         projection.identity();
         modelView.identity();
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
         RenderSystem.enableBlend();
         RenderSystem.blendFunc(SourceFactor.SRC_ALPHA, DestFactor.ONE);
         RenderSystem.disableDepthTest();
         RenderSystem.depthMask(false);
         RenderSystem.disableCull();
         BufferBuilder buffer = Tesselator.getInstance().getBuilder();
         buffer.begin(Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
         Matrix4f identity = new Matrix4f();
         buffer.vertex(identity, x0, y0, 0.0F).color(r, g, b, a).endVertex();
         buffer.vertex(identity, x0, y1, 0.0F).color(r, g, b, a).endVertex();
         buffer.vertex(identity, x1, y1, 0.0F).color(r, g, b, a).endVertex();
         buffer.vertex(identity, x1, y0, 0.0F).color(r, g, b, a).endVertex();
         BufferUploader.drawWithShader(buffer.end());
         RenderSystem.depthMask(true);
         RenderSystem.enableDepthTest();
         RenderSystem.defaultBlendFunc();
         RenderSystem.enableCull();
      } finally {
         modelView.set(savedModelView);
         projection.set(savedProjection);
         RenderSystem.setShader(GameRenderer::getPositionColorShader);
         RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

   }
}

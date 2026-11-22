package dev.js.mtrheadlight;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Locale;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SprayParticle extends TextureSheetParticle {
   static final int MIN_LIFE = 5;
   static final int MAX_LIFE = 9;
   private static final float SCALE_MIN = 0.08F;
   private static final float SCALE_MAX = 0.18F;
   private static final float SHRINK = 0.55F;
   private static final float VERTICAL_STRETCH = 2.2F;
   private static final float ALPHA_MIN = 0.55F;
   private static final float ALPHA_MAX = 0.85F;
   private static final double DRIFT_MAX_DOWN = 0.18D;
   private static final double DRIFT_MAX_UP = 0.02D;
   private static final float GRAVITY_MIN = 0.02F;
   private static final float GRAVITY_MAX = 0.04F;
   private static final float VELOCITY_MULTIPLIER = 0.9F;
   private static final float MAX_TILT = 1.0472F;
   private static final float TINT_R = 0.94F;
   private static final float TINT_G = 0.95F;
   private static final float TINT_B = 0.97F;
   private final float startScale;
   private final float startAlpha;
   private static long bornCount;
   private static long bornMilliSum;

   static String paramsLabel() {
      return String.format(Locale.ROOT, "tint=%.2f,%.2f,%.2f life=%d-%d alpha=%.2f-%.2f vy=-%.2f..-%.2f gravity=%.2f-%.2f", 0.94F, 0.95F, 0.97F, 5, 9, 0.55F, 0.85F, RainSpray.mistDownMin(), RainSpray.mistDownMax(), 0.02F, 0.04F);
   }

   private static synchronized void noteBorn(float scale) {
      ++bornCount;
      bornMilliSum += (long)Math.round(scale * 1000.0F);
   }

   static synchronized double[] drainScaleStats() {
      double avg = bornCount == 0L ? -1.0D : (double)bornMilliSum / 1000.0D / (double)bornCount;
      double[] out = new double[]{avg, (double)bornCount};
      bornCount = 0L;
      bornMilliSum = 0L;
      return out;
   }

   SprayParticle(ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, SpriteSet sprites) {
      super(world, x, y, z);
      this.setSprite(sprites.get(this.random));
      this.lifetime = 5 + this.random.nextInt(5);
      this.gravity = 0.02F + this.random.nextFloat() * 0.02F;
      this.friction = 0.9F;
      this.xd = velocityX;
      this.zd = velocityZ;
      this.yd = drift(velocityY);
      this.startScale = 0.08F + this.random.nextFloat() * 0.10000001F;
      this.quadSize = this.startScale;
      noteBorn(this.startScale);
      this.startAlpha = 0.55F + this.random.nextFloat() * 0.3F;
      this.setAlpha(this.startAlpha);
      this.setColor(0.94F, 0.95F, 0.97F);
   }

   private static double drift(double vy) {
      return Double.isNaN(vy) ? -0.09D : Math.max(-0.18D, Math.min(0.02D, vy));
   }

   public void tick() {
      super.tick();
      if (!this.removed) {
         float life = this.lifetime <= 0 ? 1.0F : Math.min(1.0F, (float)this.age / (float)this.lifetime);
         this.quadSize = this.startScale * (1.0F - 0.55F * life);
         this.setAlpha(this.startAlpha * (1.0F - life * life));
      }
   }

   public void render(VertexConsumer vertexConsumer, Camera camera, float tickDelta) {
      Vec3 cameraPos = camera.getPosition();
      float dx = (float)(Mth.lerp((double)tickDelta, this.xo, this.x) - cameraPos.x());
      float dy = (float)(Mth.lerp((double)tickDelta, this.yo, this.y) - cameraPos.y());
      float dz = (float)(Mth.lerp((double)tickDelta, this.zo, this.z) - cameraPos.z());
      Quaternionf rotation;
      if (this.roll == 0.0F) {
         rotation = camera.rotation();
      } else {
         rotation = new Quaternionf(camera.rotation());
         rotation.rotateZ(Mth.lerp(tickDelta, this.oRoll, this.roll));
      }

      Vector3f[] corners = new Vector3f[]{new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)};
      float size = this.getQuadSize(tickDelta);
      Vector3f vel = (new Vector3f((float)this.xd, (float)this.yd, (float)this.zd)).rotate((new Quaternionf(camera.rotation())).conjugate());
      float horizDotRight = vel.x;
      float planarSpeedSq = vel.x * vel.x + vel.y * vel.y;
      float tilt = planarSpeedSq > 1.0E-8F ? Mth.clamp(Math.copySign((float)Math.atan2((double)Math.abs(vel.x), (double)Math.abs(vel.y)), horizDotRight), -1.0472F, 1.0472F) : 0.0F;

      for(Vector3f corner : corners) {
         corner.y *= 2.2F;
         corner.rotateZ(tilt);
         corner.rotate(rotation);
         corner.mul(size);
         corner.add(dx, dy, dz);
      }

      float minU = this.getU0();
      float maxU = this.getU1();
      float minV = this.getV0();
      float maxV = this.getV1();
      int light = this.getLightColor(tickDelta);
      this.emit(vertexConsumer, corners[0], maxU, maxV, light);
      this.emit(vertexConsumer, corners[1], maxU, minV, light);
      this.emit(vertexConsumer, corners[2], minU, minV, light);
      this.emit(vertexConsumer, corners[3], minU, maxV, light);
   }

   private void emit(VertexConsumer vertexConsumer, Vector3f corner, float u, float v, int light) {
      vertexConsumer.vertex((double)corner.x(), (double)corner.y(), (double)corner.z()).uv(u, v).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
   }

   public ParticleRenderType getRenderType() {
      return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
   }
}

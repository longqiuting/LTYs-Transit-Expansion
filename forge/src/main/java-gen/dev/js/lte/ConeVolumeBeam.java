package dev.js.lte;

import net.minecraft.world.phys.Vec3;

public final class ConeVolumeBeam {
   public final Vec3 apex;
   public final Vec3 axis;
   public final double length;
   public final double radius;
   public final int rgb;
   public final float alpha;
   public final int steps;

   public ConeVolumeBeam(Vec3 apex, Vec3 axis, double length, double radius, int rgb, float alpha, int steps) {
      this.apex = apex;
      this.axis = axis.normalize();
      this.length = length;
      this.radius = radius;
      this.rgb = rgb;
      this.alpha = alpha;
      this.steps = steps;
   }

   public double halfAngle() {
      return Math.atan2(this.radius, this.length);
   }
}

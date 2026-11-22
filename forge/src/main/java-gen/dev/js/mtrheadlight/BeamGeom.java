package dev.js.mtrheadlight;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

final class BeamGeom {
   final Vec3 origin;
   final Vec3 axis;
   final double length;
   final double height;
   final int rgb;
   private final boolean absolute;

   private BeamGeom(Vec3 origin, Vec3 axis, double length, double height, int rgb, boolean absolute) {
      this.origin = origin;
      this.axis = axis;
      this.length = length;
      this.height = height;
      this.rgb = rgb;
      this.absolute = absolute;
   }

   static BeamGeom of(Vec3 railPoint, Vec3 dir, double offset, double length, double height, int rgb) {
      Vec3 origin = new Vec3(railPoint.x + dir.x * offset, railPoint.y + dir.y * offset, railPoint.z + dir.z * offset);
      return new BeamGeom(origin, dir, length, height, rgb, false);
   }

   static BeamGeom at(Vec3 apex, Vec3 dir, double length, int rgb) {
      return new BeamGeom(apex, dir, length, 0.0D, rgb, true);
   }

   Vec3 apex(ClientLevel world) {
      return this.absolute ? this.origin : new Vec3(this.origin.x, BeamGlow.startHeight(world, this.origin) + this.height, this.origin.z);
   }
}

package dev.js.lte;

import net.minecraft.world.phys.Vec3;

final class EndLight {
   final Vec3 start;
   final Vec3 end;

   private EndLight(Vec3 start, Vec3 end) {
      this.start = start;
      this.end = end;
   }

   static EndLight of(Vec3 base, Vec3 dir, double offset, double length, double tipDrop, double height) {
      double y = base.y + height;
      Vec3 start = new Vec3(base.x + dir.x * offset, y, base.z + dir.z * offset);
      Vec3 end = new Vec3(start.x + dir.x * length, start.y - tipDrop, start.z + dir.z * length);
      return new EndLight(start, end);
   }

   static EndLight ofSegment(Vec3 start, Vec3 end) {
      return new EndLight(start, end);
   }
}

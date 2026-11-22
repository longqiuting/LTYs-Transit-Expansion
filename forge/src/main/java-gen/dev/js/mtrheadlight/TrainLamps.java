package dev.js.mtrheadlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mtr.data.TrainClient;
import net.minecraft.world.phys.Vec3;

public final class TrainLamps {
   private static final double FLARE_GAP = 0.08D;
   private static final double MIN_LAMP_HEIGHT = 0.3D;
   private static final double MAX_LAMP_HEIGHT = 3.3D;
   private static final double MAX_LATERAL = 2.4D;
   private static final double MAX_LAMP_AHEAD = 2.0D;
   private static final double FALLBACK_WIDTH = 2.9D;
   private static final double FALLBACK_LATERAL = 0.36D;
   private static final double ALPHA_UNIT = 0.16D;
   private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

   private TrainLamps() {
   }

   public static List<TrainLamps.Lamp> of(TrainClient train, TrainPose pose, HeadlightConfig cfg, HeadlightConfig.Effective eff) {
      List<TrainLamps.Lamp> out = new ArrayList(4);
      if (pose != null && pose.cars() > 0 && train != null) {
         VehicleLights.Layout layout = VehicleLights.layoutFor(train);
         String id = train.baseTrainType == null ? "" : train.baseTrainType;

         List<VehicleLightOverrides.Resolved> resolved;
         try {
            resolved = VehicleLightOverrides.resolved(id, layout);
         } catch (Throwable var12) {
            resolved = new ArrayList(0);
         }

         if (resolved == null || resolved.isEmpty()) {
            resolved = estimated(layout, cfg);
         }

         String source = sourceOf(layout, resolved);

         for(int i = 0; i < resolved.size(); ++i) {
            VehicleLightOverrides.Resolved r = (VehicleLightOverrides.Resolved)resolved.get(i);
            if (r != null) {
               TrainLamps.Lamp lamp = place(pose, r, cfg, eff, source);
               if (lamp != null) {
                  out.add(lamp);
               }
            }
         }

         return out;
      } else {
         return out;
      }
   }

   private static List<VehicleLightOverrides.Resolved> estimated(VehicleLights.Layout layout, HeadlightConfig cfg) {
      double width = layout != null && layout.width > 0.6D ? layout.width : 2.9D;
      double lateral = 0.36D * width;
      double height = clamp(cfg.heightOffset, 0.3D, 3.3D);
      List<VehicleLightOverrides.Resolved> out = new ArrayList(4);
      out.add(beam("head", lateral, height, cfg.beamHeadLength));
      out.add(beam("head", -lateral, height, cfg.beamHeadLength));
      out.add(beam("tail", lateral, height, cfg.beamTailLength));
      out.add(beam("tail", -lateral, height, cfg.beamTailLength));
      return out;
   }

   private static VehicleLightOverrides.Resolved beam(String type, double lateral, double height, double length) {
      VehicleLightOverrides.Beam b = new VehicleLightOverrides.Beam(type);
      b.length = length;
      b.pos = new double[]{lateral, height, 0.0D};
      return VehicleLightOverrides.Resolved.ofPublic(b, (VehicleLights.Layout)null);
   }

   private static String sourceOf(VehicleLights.Layout layout, List<VehicleLightOverrides.Resolved> resolved) {
      for(VehicleLightOverrides.Resolved r : resolved) {
         if (r != null && r.declared) {
            return "declared";
         }
      }

      if (layout == null) {
         return "default";
      } else {
         return layout.head != null && !layout.head.isDefault() ? "model" : "default";
      }
   }

   private static TrainLamps.Lamp place(TrainPose pose, VehicleLightOverrides.Resolved r, HeadlightConfig cfg, HeadlightConfig.Effective eff, String source) {
      int cars = Math.max(1, pose.cars());
      int car = r.isHead() ? 0 : cars - 1;
      Vec3 base = r.isHead() ? pose.carStart(car) : pose.carEnd(car);
      Vec3 out = pose.carBodyForward(car);
      if (out == Vec3.ZERO) {
         out = pose.carForward(car);
      }

      if (out == Vec3.ZERO) {
         return null;
      } else {
         if (!r.isHead()) {
            out = out.scale(-1.0D);
         }

         Vec3 right = out.cross(UP);
         if (right.lengthSqr() < 1.0E-8D) {
            return null;
         } else {
            right = right.normalize();
            double lateral = clamp(r.lateral, -2.4D, 2.4D);
            double height = r.height < 0.3D ? clamp(cfg.heightOffset, 0.3D, 3.3D) : clamp(r.height, 0.3D, 3.3D);
            double outward = clamp(Math.abs(r.inset), 0.0D, 2.0D) + 0.08D;
            Vec3 pos = base.add(right.scale(lateral)).add(0.0D, height, 0.0D).add(out.scale(outward));
            Vec3 aim = out;
            if (r.rot != null && r.rot.length >= 2) {
               aim = rotate(out, right, r.rot[0], r.rot[1]);
            }

            Vec3 dir = aim.scale(-1.0D);
            int rgb = r.declared ? r.color : (r.isHead() ? eff.beamHeadColor : eff.beamTailColor);
            double alpha = clamp(r.alpha / 0.16D, 0.2D, 3.0D);
            double length = r.declared ? r.length : (r.isHead() ? eff.beamHeadLength : eff.beamTailLength);
            return new TrainLamps.Lamp(pos, dir, rgb & 16777215, alpha, Math.max(0.0D, r.width), clamp(length, 1.0D, 64.0D), r.isHead(), source);
         }
      }
   }

   private static Vec3 rotate(Vec3 dir, Vec3 right, double yawDeg, double pitchDeg) {
      Vec3 out = dir;
      if (Math.abs(yawDeg) > 1.0E-6D) {
         double a = Math.toRadians(yawDeg);
         double cos = Math.cos(a);
         double sin = Math.sin(a);
         out = new Vec3(dir.x * cos + dir.z * sin, dir.y, -dir.x * sin + dir.z * cos);
      }

      if (Math.abs(pitchDeg) > 1.0E-6D) {
         double a = Math.toRadians(pitchDeg);
         double cos = Math.cos(a);
         double sin = Math.sin(a);
         double d = out.dot(right);
         Vec3 perp = out.subtract(right.scale(d));
         Vec3 cross = right.cross(perp);
         out = perp.scale(cos).add(cross.scale(sin)).add(right.scale(d));
      }

      double len = out.length();
      return len < 1.0E-6D ? dir : out.scale(1.0D / len);
   }

   public static Vec3 centre(List<TrainLamps.Lamp> lamps, boolean head) {
      Vec3 sum = Vec3.ZERO;
      int n = 0;

      for(TrainLamps.Lamp lamp : lamps) {
         if (lamp.head == head) {
            sum = sum.add(lamp.pos);
            ++n;
         }
      }

      return n == 0 ? Vec3.ZERO : sum.scale(1.0D / (double)n);
   }

   public static Vec3 heading(List<TrainLamps.Lamp> lamps, boolean head) {
      Vec3 sum = Vec3.ZERO;
      int n = 0;

      for(TrainLamps.Lamp lamp : lamps) {
         if (lamp.head == head) {
            sum = sum.add(lamp.beam());
            ++n;
         }
      }

      if (n == 0) {
         return Vec3.ZERO;
      } else {
         double len = sum.length();
         return len < 1.0E-6D ? Vec3.ZERO : sum.scale(1.0D / len);
      }
   }

   public static double wedgeLength(List<TrainLamps.Lamp> lamps, boolean head) {
      double best = 0.0D;

      for(TrainLamps.Lamp lamp : lamps) {
         if (lamp.head == head && lamp.length > best) {
            best = lamp.length;
         }
      }

      return best;
   }

   private static double clamp(double value, double min, double max) {
      if (Double.isNaN(value)) {
         return min;
      } else {
         return value < min ? min : (value > max ? max : value);
      }
   }

   private static String fmt(double d) {
      return String.format(Locale.ROOT, "%.2f", d);
   }

   public static final class Lamp {
      public final Vec3 pos;
      public final Vec3 dir;
      public final int rgb;
      public final double alpha;
      public final double width;
      public final double length;
      public final boolean head;
      public final String source;

      Lamp(Vec3 pos, Vec3 dir, int rgb, double alpha, double width, double length, boolean head, String source) {
         this.pos = pos;
         this.dir = dir;
         this.rgb = rgb;
         this.alpha = alpha;
         this.width = width;
         this.length = length;
         this.head = head;
         this.source = source;
      }

      public Vec3 beam() {
         return this.dir.scale(-1.0D);
      }

      public String toString() {
         Vec3 b = this.beam();
         return (this.head ? "head" : "tail") + "(" + this.source + " @" + TrainLamps.fmt(this.pos.x) + "," + TrainLamps.fmt(this.pos.y) + "," + TrainLamps.fmt(this.pos.z) + " dir=" + TrainLamps.fmt(this.dir.x) + "," + TrainLamps.fmt(this.dir.y) + "," + TrainLamps.fmt(this.dir.z) + " beam=" + TrainLamps.fmt(b.x) + "," + TrainLamps.fmt(b.y) + "," + TrainLamps.fmt(b.z) + ")";
      }
   }
}

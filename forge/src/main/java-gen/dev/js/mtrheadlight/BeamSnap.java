package dev.js.mtrheadlight;

final class BeamSnap {
   static final int FRONT = 0;
   static final int BACK = 1;
   static final int UP = 2;
   static final int DOWN = 3;
   static final int LEFT = 4;
   static final int RIGHT = 5;
   static final int COUNT = 6;
   static final double MOVE_STEP = 0.0625D;
   private static final double[][] ANGLES = new double[][]{{0.0D, 0.0D}, {180.0D, 0.0D}, {0.0D, -90.0D}, {0.0D, 90.0D}, {90.0D, 0.0D}, {-90.0D, 0.0D}};
   private static final String[] KEYS = new String[]{"beam3d.snap.front", "beam3d.snap.back", "beam3d.snap.up", "beam3d.snap.down", "beam3d.snap.left", "beam3d.snap.right"};

   private BeamSnap() {
   }

   static String key(int dir) {
      return KEYS[dir >= 0 && dir < 6 ? dir : 0];
   }

   static double yaw(int dir) {
      return ANGLES[dir >= 0 && dir < 6 ? dir : 0][0];
   }

   static double pitch(int dir) {
      return ANGLES[dir >= 0 && dir < 6 ? dir : 0][1];
   }

   static double[] direction(int dir) {
      double yawRad = Math.toRadians(yaw(dir));
      double pitchRad = Math.toRadians(pitch(dir));
      double cosPitch = Math.cos(pitchRad);
      return new double[]{-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), -Math.cos(yawRad) * cosPitch};
   }

   static double[] apply(VehicleLightOverrides.Beam beam, int dir) {
      double[] target = direction(dir);
      boolean head = BeamSession.isHead(beam);
      double tx = head ? target[0] : -target[0];
      double ty = head ? target[1] : -target[1];
      double tz = head ? target[2] : -target[2];
      double outPitch = Math.toDegrees(Math.asin(clamp(-ty, -1.0D, 1.0D)));
      double cosPitch = Math.cos(Math.toRadians(outPitch));
      double outYaw = cosPitch < 1.0E-4D ? 0.0D : Math.toDegrees(Math.atan2(-tx, -tz));
      double[] rot = new double[]{wrap(outYaw), wrap(outPitch), 0.0D};
      if (beam != null) {
         beam.rot = rot;
      }

      return rot;
   }

   static double stepped(double value) {
      return BeamGizmo.quantize(0.0D, value, 0.0625D);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double wrap(double degrees) {
      double d = degrees % 360.0D;
      if (d > 180.0D) {
         d -= 360.0D;
      }

      if (d <= -180.0D) {
         d += 360.0D;
      }

      return d;
   }
}

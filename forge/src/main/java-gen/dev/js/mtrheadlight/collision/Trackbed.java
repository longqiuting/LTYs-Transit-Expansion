package dev.js.mtrheadlight.collision;

import dev.js.mtrheadlight.LteLog;
import java.util.Arrays;
import java.util.Locale;
import mtr.client.ClientData;
import net.minecraft.client.multiplayer.ClientLevel;

public final class Trackbed {
   public static final double HALF_WIDTH = 1.3D;
   public static final double SEARCH_RADIUS = 8.0D;
   public static final double DEFAULT_STEP_UP = 1.0D;
   private static final RailBed BED = new RailBed();
   private static volatile boolean snappedThisTick;
   private static volatile boolean snappedLastTick;
   private static volatile boolean watch;
   private static volatile double lastFeet = Double.NaN;
   private static volatile double lastX = Double.NaN;
   private static volatile double lastZ = Double.NaN;
   private static volatile double lastRailY = Double.NaN;
   private static volatile double lastOffset = Double.NaN;
   private static volatile double lastDy = Double.NaN;
   private static volatile double lastVy = Double.NaN;
   private static volatile boolean lastOnGround;
   private static volatile double lastMoved = Double.NaN;
   private static volatile RailBed.Verdict lastVerdict = RailBed.Verdict.NONE;
   private static int wideRescues;
   private static int railsTotal;
   private static Object railsTotalMap;
   private static long railsTotalTick = Long.MIN_VALUE;
   private static volatile String tableText = "";
   private static volatile long tableTick = 0L;
   private static volatile RailBed.Follow followState;
   private static volatile RailBed.Kind followKind = RailBed.Kind.LOST;
   private static int followOk;
   private static int followLost;
   private static int followHop;
   private static int followTable;
   private static final int[] COUNTS = new int[RailBed.Verdict.values().length];

   private Trackbed() {
   }

   public static void reset() {
      BED.reset();
      snappedThisTick = false;
      snappedLastTick = false;
      wideRescues = 0;
      railsTotal = 0;
      railsTotalMap = null;
      railsTotalTick = Long.MIN_VALUE;
      tableText = "";
      tableTick = 0L;
      followState = null;
      followKind = RailBed.Kind.LOST;
      clearFollowCounters();
      clearStep();
      clearCounters();
   }

   public static void clearWarned() {
      BED.clearWarned();
   }

   public static int cached() {
      return BED.cached();
   }

   public static boolean hit() {
      return BED.lastHit();
   }

   public static double lastY() {
      return BED.lastY();
   }

   public static double lastDistance() {
      return BED.lastDistance();
   }

   public static boolean snapped() {
      return snappedLastTick;
   }

   public static void noteSnapped() {
      snappedThisTick = true;
   }

   public static boolean watch() {
      return watch;
   }

   public static void setWatch(boolean on) {
      watch = on;
      BED.setCapture(on);
      if (!on) {
         tableText = "";
         tableTick = 0L;
      }

   }

   public static void noteStep(Trackbed.Sample sample, double x, double feet, double z, double startDy, double dy, double vy, boolean onGround, double moved, RailBed.Verdict verdict) {
      boolean wasOnBed = lastVerdict.snaps() || lastVerdict == RailBed.Verdict.BLOCK || lastVerdict == RailBed.Verdict.SPACE || lastVerdict == RailBed.Verdict.JUMP;
      double railY = sample == null ? Double.NaN : sample.y;
      double distance = sample == null ? Double.NaN : sample.distance;
      double offset = sample == null ? Double.NaN : sample.offset;
      lastX = x;
      lastZ = z;
      lastFeet = feet;
      lastRailY = railY;
      lastOffset = offset;
      lastDy = dy;
      lastVy = vy;
      lastOnGround = onGround;
      lastMoved = moved;
      lastVerdict = verdict;
      noteWideRescue(distance, verdict);
      int index = verdict.ordinal();
      if (index >= 0 && index < COUNTS.length && (wasOnBed || verdict.snaps())) {
         int var10002 = COUNTS[index]++;
      }

      if (verdict.snaps()) {
         noteSnapped();
      }

      if (watch && LteLog.LOGGER.isInfoEnabled()) {
         String candidates = BED.detailText();
         String table = tableNearest();
         LteLog.LOGGER.info("[lte] trackbed tick: pos=({}, {}, {}) rails={} cand={} best=({}, {}, {}) dy={} sdy={} vy={} verdict={} onGround={} moved={}{}{}", new Object[]{number(x), number(feet), number(z), railsTotal(), cached(), number(distance), signed(offset), number(railY), signed(dy), signed(startDy), signed(vy), verdict.shortName(), onGround, number(moved), candidates.isEmpty() ? "" : " " + candidates, table.isEmpty() ? "" : " " + table});
      }

   }

   private static String tableNearest() {
      Object rails = rails();
      long now = System.currentTimeMillis();
      if (!tableText.isEmpty() && now - tableTick < 1000L) {
         return tableText;
      } else {
         tableTick = now;
         if (rails != null && !Double.isNaN(lastX) && !Double.isNaN(lastZ)) {
            tableText = BED.tableNearestText(rails, lastX, lastZ);
            return tableText;
         } else {
            return tableText;
         }
      }
   }

   private static void noteWideRescue(double distance, RailBed.Verdict verdict) {
      if (verdict == RailBed.Verdict.RESCUE) {
         wideRescues = distance > 1.3D ? wideRescues + 1 : 0;
      } else {
         if (verdict == RailBed.Verdict.SNAP || verdict == RailBed.Verdict.NO_HIT || Double.isNaN(distance) || distance > 2.5D) {
            wideRescues = 0;
         }

      }
   }

   public static boolean rescue(double distance, double startDy, double dy, double vy) {
      if (distance <= 2.5D && RailBed.rescues(startDy, dy, vy)) {
         return distance <= 1.3D || wideRescues < 20;
      } else {
         return false;
      }
   }

   private static int railsTotal() {
      Object map = rails();
      long tick = System.currentTimeMillis();
      if (map == railsTotalMap && tick - railsTotalTick < 1000L) {
         return railsTotal;
      } else {
         railsTotalMap = map;
         railsTotalTick = tick;
         railsTotal = map == null ? 0 : RailBed.tableSize(map);
         return railsTotal;
      }
   }

   public static boolean endTick() {
      boolean value = snappedThisTick;
      snappedLastTick = value;
      snappedThisTick = false;
      return value;
   }

   public static void clearCounters() {
      synchronized(COUNTS) {
         Arrays.fill(COUNTS, 0);
      }
   }

   public static void clearStep() {
      lastX = Double.NaN;
      lastZ = Double.NaN;
      lastFeet = Double.NaN;
      lastRailY = Double.NaN;
      lastOffset = Double.NaN;
      lastDy = Double.NaN;
      lastVy = Double.NaN;
      lastOnGround = false;
      lastMoved = Double.NaN;
      lastVerdict = RailBed.Verdict.NONE;
      wideRescues = 0;
   }

   public static String describe(boolean isSnapped) {
      return "[lte] trackbed: pos=(" + number(lastX) + ", " + number(lastFeet) + ", " + number(lastZ) + ") rails=" + cached() + " scanned=" + BED.scanned() + " skipped=" + BED.skipped() + " hit=" + (hit() ? "yes" : "no") + " y=" + number(BED.lastY()) + " dist=" + number(BED.lastDistance()) + " off=" + signed(BED.lastOffset()) + " halfW=" + String.format(Locale.ROOT, "%.2f", 1.3D) + " dy=" + signed(lastDy) + " vy=" + signed(lastVy) + " onGround=" + (lastOnGround ? "true" : "false") + " moved=" + number(lastMoved) + " drop=" + lastVerdict.shortName() + " follow=" + followKind.shortName() + " snapped=" + (isSnapped ? "true" : "false") + " drops=" + counts();
   }

   private static String counts() {
      StringBuilder out = new StringBuilder();
      synchronized(COUNTS) {
         for(RailBed.Verdict verdict : RailBed.Verdict.values()) {
            if (verdict != RailBed.Verdict.NONE) {
               if (out.length() > 0) {
                  out.append(' ');
               }

               out.append(verdict.shortName()).append(':').append(COUNTS[verdict.ordinal()]);
            }
         }
      }

      return out.toString();
   }

   private static String number(double value) {
      return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%.2f", value);
   }

   private static String signed(double value) {
      return Double.isNaN(value) ? "-" : String.format(Locale.ROOT, "%+.2f", value);
   }

   public static Trackbed.Sample follow(ClientLevel world, long tick, double x, double y, double z) {
      if (world == null) {
         return null;
      } else {
         Object rails = rails();
         if (rails == null) {
            followState = null;
            followKind = RailBed.Kind.LOST;
            return null;
         } else {
            boolean had = followState != null;
            RailBed.Step step = BED.follow(rails, tick, followState, x, y, z);
            switch (step.kind) {
               case CONTINUE:
                  ++followOk;
                  break;
               case TABLE:
                  ++followOk;
                  ++followTable;
                  break;
               case HOP:
                  ++followOk;
                  ++followHop;
                  break;
               default:
                  if (had) {
                     ++followLost;
                  }
            }

            followState = step.follow;
            followKind = step.kind;
            RailBed.Sample sample = step.sample;
            if (step.kind != RailBed.Kind.LOST) {
               BED.noteSample(sample);
            }

            return sample == null ? null : new Trackbed.Sample(sample.y, sample.distance, sample.offset, sample.along, sample.length);
         }
      }
   }

   public static String followLine() {
      RailBed.Follow follow = followState;
      String line = "[lte] trackbed follow: rail=" + (follow == null ? "no" : "yes") + " seg=" + (follow == null ? "-" : follow.seg()) + " s=" + (follow == null ? "-" : number(follow.s())) + " y=" + (follow == null ? "-" : number(follow.y())) + " ok=" + followOk + " lost=" + followLost + " hop=" + followHop + " table=" + followTable;
      clearFollowCounters();
      return line;
   }

   private static void clearFollowCounters() {
      followOk = 0;
      followLost = 0;
      followHop = 0;
      followTable = 0;
   }

   public static boolean continued() {
      return followKind == RailBed.Kind.CONTINUE || followKind == RailBed.Kind.HOP;
   }

   public static Trackbed.Sample nearest(ClientLevel world, long tick, double x, double y, double z) {
      Trackbed.Sample sample = probe(world, tick, x, y, z);
      return sample != null && !(sample.distance > 1.3D) ? sample : null;
   }

   public static Trackbed.Sample probe(ClientLevel world, long tick, double x, double y, double z) {
      if (world == null) {
         return null;
      } else {
         Object rails = rails();
         if (rails == null) {
            return null;
         } else {
            RailBed.Sample sample = BED.probe(rails, tick, x, y, z);
            return sample == null ? null : new Trackbed.Sample(sample.y, sample.distance, sample.offset, sample.along, sample.length);
         }
      }
   }

   public static String[] where(ClientLevel world, long tick, double x, double y, double z) {
      Object rails = world == null ? null : rails();
      String text = BED.explain(rails, tick, x, y, z);
      return text.split("\\R", -1);
   }

   private static Object rails() {
      try {
         return ClientData.RAILS;
      } catch (Throwable var1) {
         return null;
      }
   }

   public static final class Sample {
      public final double y;
      public final double distance;
      public final double offset;
      public final double along;
      public final double length;

      Sample(double y, double distance, double offset, double along, double length) {
         this.y = y;
         this.distance = distance;
         this.offset = offset;
         this.along = along;
         this.length = length;
      }
   }
}

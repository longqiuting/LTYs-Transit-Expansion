package dev.js.lte.collision;

import dev.js.lte.LteLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.RailType.RailSlopeStyle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class RailBed {
   public static final double HALF_WIDTH = 1.3D;
   public static final double SEARCH_RADIUS = 8.0D;
   public static final double CANDIDATE_REACH = 2.5D;
   private static final double SEAM_TIE = 0.02D;
   private static final double END_EPS = 0.02D;
   private static final double HEIGHT_SLACK = 4.0D;
   private static final double SAMPLE_MAX_STEP = 0.5D;
   private static final int SAMPLE_DIVISIONS = 64;
   private static final int MAX_SAMPLES = 256;
   private static final int GEOMETRY_MAX = 8192;
   private static final int REBUILD_TICKS = 20;
   private static final double CELL_MARGIN = 12.0D;
   private static final int WHERE_MAX = 8;
   private static final int DETAIL_MAX = 4;
   public static final double DEFAULT_STEP_UP = 1.0D;
   public static final double LEVEL_TOL = 0.03D;
   public static final double STICK = 0.6D;
   public static final double CEIL = 2.0D;
   public static final double RESCUE_RADIUS = 2.5D;
   public static final double RESCUE_DROP = 3.0D;
   public static final int RESCUE_GRACE = 20;
   public static final double FOLLOW_REACH = 1.6D;
   public static final double FOLLOW_BELOW = 1.5D;
   public static final double FOLLOW_ABOVE = 1.0D;
   public static final double HOP_REACH = 2.5D;
   public static final double HOP_AHEAD = -0.35D;
   private static final double ADVANCE_MAX = 2.0D;
   private static final double ADVANCE_SLACK = 0.6D;
   private static final double END_LEAVE = 1.3D;
   private List<RailBed.Cand> cache = List.of();
   private Object cacheMap;
   private double cacheX = Double.NaN;
   private double cacheZ = Double.NaN;
   private long cacheTick;
   private final AtomicBoolean warned = new AtomicBoolean();
   private volatile boolean capture;
   private final List<RailBed.Detail> details = new ArrayList(4);
   private int detailTotal;
   private int detailStraight;
   private int lastScanned;
   private int lastSkipped;
   private final Map<Rail, RailBed.Entry> geometry = new WeakHashMap();
   private boolean lastHit;
   private double lastY = Double.NaN;
   private double lastDistance = Double.NaN;
   private double lastOffset = Double.NaN;
   private double lastAlong = Double.NaN;
   private RailBed.Entry lastEntry;
   private BlockPos lastStart;
   private BlockPos lastEnd;

   public static RailBed.Verdict verdict(double dy, double vy, double startDy, double stepUp) {
      if (vy > 0.02D && dy > -0.03D) {
         return RailBed.Verdict.JUMP;
      } else if (vy < -0.001D && !Double.isNaN(startDy) && startDy >= -0.03D && dy < -0.03D) {
         return RailBed.Verdict.SNAP;
      } else if (!(dy < -stepUp) && !(dy > 2.0D)) {
         if (dy <= 0.03D) {
            return RailBed.Verdict.SNAP;
         } else {
            return vy <= 0.02D && dy <= 0.6D ? RailBed.Verdict.SNAP : RailBed.Verdict.DY;
         }
      } else {
         return RailBed.Verdict.DY;
      }
   }

   public static boolean rescues(double startDy, double dy, double vy) {
      if (!(vy < 0.0D)) {
         return false;
      } else if (startDy >= 0.0D && dy < 0.0D) {
         return startDy <= 3.0D || vy <= -3.0D;
      } else {
         return false;
      }
   }

   public void reset() {
      this.cache = List.of();
      this.cacheMap = null;
      this.cacheX = Double.NaN;
      this.cacheZ = Double.NaN;
      this.cacheTick = 0L;
      this.lastScanned = 0;
      this.lastSkipped = 0;
      this.details.clear();
      this.detailTotal = 0;
      this.detailStraight = 0;
      this.geometry.clear();
      this.clearLast();
   }

   public void clearWarned() {
      this.warned.set(false);
   }

   public int cached() {
      return this.cache.size();
   }

   public int scanned() {
      return this.lastScanned;
   }

   public int skipped() {
      return this.lastSkipped;
   }

   public boolean lastHit() {
      return this.lastHit;
   }

   public double lastY() {
      return this.lastY;
   }

   public double lastDistance() {
      return this.lastDistance;
   }

   public double lastOffset() {
      return this.lastOffset;
   }

   public double lastAlong() {
      return this.lastAlong;
   }

   public RailBed.Sample nearest(Object railsMap, long tick, double x, double y, double z) {
      return this.nearest(railsMap, tick, x, y, z, Math.floor(x / 2.0D) * 2.0D, Math.floor(z / 2.0D) * 2.0D, 10.0D);
   }

   public RailBed.Sample nearestCell(Object railsMap, long tick, int cellX, int cellZ, double x, double y, double z) {
      return this.nearest(railsMap, tick, x, y, z, (double)cellX * 16.0D + 8.0D, (double)cellZ * 16.0D + 8.0D, 12.0D);
   }

   private RailBed.Sample nearest(Object railsMap, long tick, double x, double y, double z, double anchorX, double anchorZ, double margin) {
      RailBed.Sample best = this.probe(railsMap, tick, x, y, z, anchorX, anchorZ, margin);
      return this.lastHit ? best : null;
   }

   public RailBed.Sample probe(Object railsMap, long tick, double x, double y, double z) {
      return this.probe(railsMap, tick, x, y, z, Math.floor(x / 2.0D) * 2.0D, Math.floor(z / 2.0D) * 2.0D, 10.0D);
   }

   private RailBed.Sample probe(Object railsMap, long tick, double x, double y, double z, double anchorX, double anchorZ, double margin) {
      if (railsMap == null) {
         this.clearLast();
         return null;
      } else {
         this.refresh(railsMap, tick, anchorX, anchorZ, margin);
         if (this.capture) {
            this.details.clear();
            this.detailTotal = 0;
            this.detailStraight = 0;
         }

         RailBed.Sample best = null;
         boolean bestInterior = false;
         RailBed.Cand bestCand = null;

         for(RailBed.Cand cand : this.cache) {
            RailBed.Entry entry = cand.entry;
            boolean heightOk = !(y < entry.minY - 4.0D) && !(y > entry.maxY + 4.0D);
            double bbox = entry.bboxDistance(x, z);
            boolean bboxOk = !(bbox > 2.5D);
            if (heightOk && bboxOk) {
               RailBed.Sample sample = column(entry, x, z);
               if (sample == null) {
                  if (this.capture) {
                     this.recordDetail(entry, (RailBed.Sample)null, RailBed.Why.COLUMN);
                  }
               } else if (sample.distance > 2.5D) {
                  if (this.capture) {
                     this.recordDetail(entry, sample, RailBed.Why.FAR);
                  }
               } else {
                  if (this.capture) {
                     this.recordDetail(entry, sample, RailBed.Why.OK);
                  }

                  if (best == null || sample.distance < best.distance - 0.02D || sample.distance <= best.distance + 0.02D && sample.interior() && !bestInterior) {
                     best = sample;
                     bestInterior = sample.interior();
                     bestCand = cand;
                  }
               }
            } else if (this.capture) {
               this.recordDetail(entry, column(entry, x, z), heightOk ? RailBed.Why.BBOX : RailBed.Why.HEIGHT);
            }
         }

         this.lastY = best == null ? Double.NaN : best.y;
         this.lastDistance = best == null ? Double.NaN : best.distance;
         this.lastOffset = best == null ? Double.NaN : best.offset;
         this.lastAlong = best == null ? Double.NaN : best.along;
         this.lastEntry = bestCand == null ? null : bestCand.entry;
         this.lastStart = bestCand == null ? null : bestCand.start;
         this.lastEnd = bestCand == null ? null : bestCand.end;
         this.lastHit = best != null && best.distance <= 1.3D;
         return best;
      }
   }

   private void clearLast() {
      this.lastHit = false;
      this.lastY = Double.NaN;
      this.lastDistance = Double.NaN;
      this.lastOffset = Double.NaN;
      this.lastAlong = Double.NaN;
      this.lastEntry = null;
      this.lastStart = null;
      this.lastEnd = null;
   }

   void noteSample(RailBed.Sample sample) {
      this.lastHit = sample != null && sample.distance <= 1.3D;
      this.lastY = sample == null ? Double.NaN : sample.y;
      this.lastDistance = sample == null ? Double.NaN : sample.distance;
      this.lastOffset = sample == null ? Double.NaN : sample.offset;
      this.lastAlong = sample == null ? Double.NaN : sample.along;
   }

   public RailBed.Step follow(Object railsMap, long tick, RailBed.Follow prev, double x, double feet, double z) {
      boolean verified = tick % 100L != 0L || present(railsMap, prev);
      if (verified && prev != null && prev.entry != null) {
         RailBed.Sample advanced = this.advance(prev, x, feet, z);
         if (advanced != null) {
            return new RailBed.Step(new RailBed.Follow(prev.rail, prev.entry, prev.start, prev.end, advanced.along, advanced.y, x, z), advanced, RailBed.Kind.CONTINUE);
         }
      }

      RailBed.Sample table = this.probe(railsMap, tick, x, feet, z);
      if (table != null && accept(table, feet) && this.lastEntry != null) {
         return new RailBed.Step(new RailBed.Follow(this.lastEntry.rail, this.lastEntry, this.lastStart, this.lastEnd, table.along, table.y, x, z), table, RailBed.Kind.TABLE);
      } else {
         RailBed.Step hop = this.hopOne(railsMap, prev, x, feet, z);
         return hop != null ? hop : new RailBed.Step((RailBed.Follow)null, table, RailBed.Kind.LOST);
      }
   }

   private static boolean present(Object railsMap, RailBed.Follow follow) {
      if (railsMap instanceof Map && follow != null && follow.start != null && follow.end != null) {
         try {
            Object inner = ((Map<?, ?>)railsMap).get(follow.start);
            return inner instanceof Map && ((Map)inner).get(follow.end) == follow.rail;
         } catch (Throwable var3) {
            return false;
         }
      } else {
         return false;
      }
   }

   private RailBed.Sample advance(RailBed.Follow prev, double x, double feet, double z) {
      RailBed.Entry entry = prev.entry;
      double dx = x - prev.x;
      double dz = z - prev.z;
      double moved = Math.sqrt(dx * dx + dz * dz);
      double window = Math.min(moved, 2.0D) + 0.6D;
      RailBed.Sample sample = column(entry, x, z, Math.max(0.0D, prev.s - window), Math.min(entry.length, prev.s + window));
      return sample != null && accept(sample, feet) ? sample : null;
   }

   private RailBed.Step hopOne(Object railsMap, RailBed.Follow prev, double x, double feet, double z) {
      if (railsMap instanceof Map && prev != null && prev.entry != null && prev.start != null && prev.end != null) {
         RailBed.Entry entry = prev.entry;
         int last = entry.xs.length - 1;
         double dStart = Math.sqrt(sq(x - entry.xs[0]) + sq(z - entry.zs[0]));
         double dEnd = Math.sqrt(sq(x - entry.xs[last]) + sq(z - entry.zs[last]));
         boolean atEnd = dEnd <= dStart;
         if (!(Math.min(dStart, dEnd) <= 2.5D)) {
            return null;
         } else {
            BlockPos key = atEnd ? prev.end : prev.start;
            BlockPos back = atEnd ? prev.start : prev.end;
            double[] tangent = tangentAtEnd(entry, atEnd);
            double ax = x - (atEnd ? entry.xs[last] : entry.xs[0]);
            double az = z - (atEnd ? entry.zs[last] : entry.zs[0]);
            double ad = Math.sqrt(ax * ax + az * az);
            if (ad > 1.0E-6D && (ax * tangent[0] + az * tangent[1]) / ad < -0.35D) {
               return null;
            } else {
               Object innerRaw;
               try {
                  innerRaw = ((Map<?, ?>)railsMap).get(key);
               } catch (Throwable var45) {
                  this.warnOnce(var45);
                  return null;
               }

               if (!(innerRaw instanceof Map)) {
                  return null;
               } else {
                  RailBed.Follow bestFollow = null;
                  RailBed.Sample best = null;
                  double bestTurn = Double.MAX_VALUE;

                  for(Map.Entry<?, ?> inner : ((Map<?, ?>)innerRaw).entrySet()) {
                     Object railRaw = inner.getValue();
                     Object otherRaw = inner.getKey();
                     if (railRaw instanceof Rail && otherRaw instanceof BlockPos) {
                        Rail rail = (Rail)railRaw;
                        if (rail != prev.rail) {
                           BlockPos other = (BlockPos)otherRaw;
                           if (!other.equals(back)) {
                              try {
                                 if (physical(rail)) {
                                    double length = rail.getLength();
                                    if (length > 0.001D && !Double.isNaN(length) && !Double.isInfinite(length)) {
                                       RailBed.Entry next = this.geometry(rail, length);
                                       if (next != null) {
                                          RailBed.Sample sample = column(next, x, z);
                                          if (sample != null && accept(sample, feet)) {
                                             double[] nt = tangentAt(next, sample.along);
                                             double dot = tangent[0] * nt[0] + tangent[1] * nt[1];
                                             if (dot > 0.25D) {
                                                double turn = 1.0D - dot;
                                                if (best == null || sample.distance < best.distance - 0.02D || sample.distance <= best.distance + 0.02D && turn < bestTurn) {
                                                   best = sample;
                                                   bestTurn = turn;
                                                   bestFollow = new RailBed.Follow(rail, next, key, other, sample.along, sample.y, x, z);
                                                }
                                             }
                                          }
                                       }
                                    }
                                 }
                              } catch (Throwable var46) {
                                 this.warnOnce(var46);
                              }
                           }
                        }
                     }
                  }

                  return bestFollow == null ? null : new RailBed.Step(bestFollow, best, RailBed.Kind.HOP);
               }
            }
         }
      } else {
         return null;
      }
   }

   private static boolean accept(RailBed.Sample sample, double feet) {
      if (!(sample.distance <= 1.6D)) {
         return false;
      } else if ((sample.along <= 0.02D || sample.along >= sample.length - 0.02D) && sample.distance > 1.3D) {
         return false;
      } else {
         double dy = feet - sample.y;
         return dy >= -1.5D && dy <= 1.0D;
      }
   }

   private static double[] tangentAt(RailBed.Entry entry, double s) {
      int last = entry.xs.length - 1;

      for(int i = 0; i < last; ++i) {
         if (s <= entry.ss[i + 1] || i == last - 1) {
            return unit(entry.xs[i + 1] - entry.xs[i], entry.zs[i + 1] - entry.zs[i]);
         }
      }

      return new double[]{0.0D, 0.0D};
   }

   private static double[] tangentAtEnd(RailBed.Entry entry, boolean atEnd) {
      int last = entry.xs.length - 1;
      int i = atEnd ? last - 1 : 0;
      double[] t = unit(entry.xs[i + 1] - entry.xs[i], entry.zs[i + 1] - entry.zs[i]);
      double dir = atEnd ? 1.0D : -1.0D;
      return new double[]{dir * t[0], dir * t[1]};
   }

   private static double[] unit(double dx, double dz) {
      double len = Math.sqrt(dx * dx + dz * dz);
      return len > 1.0E-9D ? new double[]{dx / len, dz / len} : new double[]{0.0D, 0.0D};
   }

   private static double sq(double v) {
      return v * v;
   }

   public void setCapture(boolean on) {
      this.capture = on;
      if (!on) {
         this.details.clear();
         this.detailTotal = 0;
         this.detailStraight = 0;
      }

   }

   public boolean capturing() {
      return this.capture;
   }

   private void recordDetail(RailBed.Entry entry, RailBed.Sample sample, RailBed.Why why) {
      ++this.detailTotal;
      if (entry.straight) {
         ++this.detailStraight;
      }

      RailBed.Detail detail = new RailBed.Detail(entry, sample, why);

      int at;
      for(at = this.details.size(); at > 0 && ((RailBed.Detail)this.details.get(at - 1)).rank() > detail.rank(); --at) {
      }

      if (at < 4) {
         this.details.add(at, detail);
         if (this.details.size() > 4) {
            this.details.remove(this.details.size() - 1);
         }

      }
   }

   public String detailText() {
      if (!this.capture) {
         return "";
      } else {
         StringBuilder out = new StringBuilder();

         for(int i = 0; i < this.details.size(); ++i) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append("cand[").append(i).append("]=").append(((RailBed.Detail)this.details.get(i)).text());
         }

         if (this.detailTotal > this.details.size()) {
            out.append(" more=").append(this.detailTotal - this.details.size());
         }

         if (this.detailTotal > 0) {
            out.append(" straightN=").append(this.detailStraight).append('/').append(this.detailTotal);
         }

         return out.toString();
      }
   }

   public String tableNearestText(Object railsMap, double x, double z) {
      if (!(railsMap instanceof Map)) {
         return "tbl=-";
      } else {
         RailBed.Entry bestEntry = null;
         RailBed.Sample best = null;

         try {
            for(Map.Entry<?, ?> outer : ((Map<?, ?>)railsMap).entrySet()) {
               Object endsRaw = outer.getValue();
               if (endsRaw instanceof Map) {
                  for(Map.Entry<?, ?> inner : ((Map<?, ?>)endsRaw).entrySet()) {
                     Object railRaw = inner.getValue();
                     if (railRaw instanceof Rail) {
                        try {
                           Rail rail = (Rail)railRaw;
                           if (physical(rail)) {
                              double length = rail.getLength();
                              if (length > 0.001D && !Double.isNaN(length) && !Double.isInfinite(length)) {
                                 RailBed.Entry entry = this.geometry(rail, length);
                                 if (entry != null) {
                                    RailBed.Sample sample = column(entry, x, z);
                                    if (sample != null && (best == null || sample.distance < best.distance)) {
                                       best = sample;
                                       bestEntry = entry;
                                    }
                                 }
                              }
                           }
                        } catch (Throwable var19) {
                           this.warnOnce(var19);
                        }
                     }
                  }
               }
            }
         } catch (Throwable var20) {
            this.warnOnce(var20);
         }

         return best != null && bestEntry != null ? "tbl=(" + fmt(best.distance) + "," + signed(best.offset) + "," + fmt(best.y) + "," + rangeText(bestEntry.minY, bestEntry.maxY) + ") straight=" + (bestEntry.straight ? "yes" : "no") + " along=" + fmt(best.along) + "/" + fmt(bestEntry.length) : "tbl=-";
      }
   }

   private static RailBed.Sample column(RailBed.Entry entry, double qx, double qz) {
      return column(entry, qx, qz, 0.0D, entry.length);
   }

   private static RailBed.Sample column(RailBed.Entry entry, double qx, double qz, double sLo, double sHi) {
      int last = entry.xs.length - 1;
      if (last < 1) {
         return null;
      } else {
         double bestSq = Double.MAX_VALUE;
         double bestOffset = 0.0D;
         double bestAlong = 0.0D;

         for(int i = 0; i < last; ++i) {
            if (!(entry.ss[i + 1] < sLo - 1.0E-9D) && !(entry.ss[i] > sHi + 1.0E-9D)) {
               double x1 = entry.xs[i];
               double z1 = entry.zs[i];
               double ex = entry.xs[i + 1] - x1;
               double ez = entry.zs[i + 1] - z1;
               double len2 = ex * ex + ez * ez;
               double dx = qx - x1;
               double dz = qz - z1;
               double f = 0.0D;
               if (len2 > 1.0E-12D) {
                  f = clamp((dx * ex + dz * ez) / len2, 0.0D, 1.0D);
               }

               double px = x1 + ex * f;
               double pz = z1 + ez * f;
               double ox = qx - px;
               double oz = qz - pz;
               double sq = ox * ox + oz * oz;
               if (!(sq >= bestSq)) {
                  bestSq = sq;
                  bestAlong = entry.ss[i] + (entry.ss[i + 1] - entry.ss[i]) * f;
                  if (len2 > 1.0E-12D) {
                     double inv = 1.0D / Math.sqrt(len2);
                     bestOffset = ox * -ez * inv + oz * ex * inv;
                  } else {
                     bestOffset = Math.sqrt(sq);
                  }
               }
            }
         }

         if (bestSq == Double.MAX_VALUE) {
            return null;
         } else {
            double y = railY(bestAlong, entry.length, entry.yStart, entry.yEnd, entry.cable);
            return new RailBed.Sample(y, Math.sqrt(bestSq), bestOffset, bestAlong, entry.length);
         }
      }
   }

   public static double railY(double s, double length, double yStart, double yEnd, boolean cable) {
      if (!(length > 1.0E-6D)) {
         return yStart;
      } else {
         double v = clamp(s, 0.0D, length);
         double rise = yEnd - yStart;
         if (cable) {
            if (v < 0.5D) {
               return yStart;
            } else if (v > length - 0.5D) {
               return yEnd;
            } else {
               double offset = v - 0.5D;
               double offsetLength = length - 1.0D;
               if (!(offsetLength > 1.0E-6D)) {
                  return yStart;
               } else {
                  double posY = yStart + rise * offset / offsetLength;
                  double dip = offsetLength * offsetLength / 4.0D / 1000.0D;
                  double shape = dip > 8.0D ? 8.0D / dip : 1.0D;
                  return posY + shape * (offset - offsetLength) * offset / 1000.0D;
               }
            }
         } else {
            double intercept = length / 2.0D;
            double yChange;
            double yInitial;
            double offset;
            if (v < intercept) {
               yChange = rise / 2.0D;
               yInitial = yStart;
               offset = v;
            } else {
               yChange = -rise / 2.0D;
               yInitial = yEnd;
               offset = length - v;
            }

            return yChange * offset * offset / (intercept * intercept) + yInitial;
         }
      }
   }

   private void refresh(Object railsMap, long tick, double anchorX, double anchorZ, double margin) {
      boolean sameMap = railsMap == this.cacheMap;
      boolean sameAnchor = sameMap && !Double.isNaN(this.cacheX) && this.cacheX == anchorX && this.cacheZ == anchorZ;
      boolean fresh = sameMap && tick - this.cacheTick < 20L;
      if (!sameAnchor || !fresh) {
         this.cacheMap = railsMap;
         this.cacheX = anchorX;
         this.cacheZ = anchorZ;
         this.cacheTick = tick;
         this.cache = this.build(railsMap, anchorX, anchorZ, margin);
      }
   }

   private List<RailBed.Cand> build(Object railsMap, double x, double z, double queryMargin) {
      List<RailBed.Cand> out = new ArrayList();
      this.lastScanned = 0;
      this.lastSkipped = 0;
      if (!(railsMap instanceof Map)) {
         return out;
      } else {
         double reach = queryMargin + 2.5D;

         try {
            for(Map.Entry<?, ?> outer : ((Map<?, ?>)railsMap).entrySet()) {
               Object endsRaw = outer.getValue();
               if (endsRaw instanceof Map) {
                  Object startRaw = outer.getKey();
                  if (startRaw instanceof BlockPos) {
                     BlockPos start = (BlockPos)startRaw;

                     for(Map.Entry<?, ?> inner : ((Map<?, ?>)endsRaw).entrySet()) {
                        Object railRaw = inner.getValue();
                        Object endRaw = inner.getKey();
                        if (railRaw instanceof Rail && endRaw instanceof BlockPos) {
                           try {
                              RailBed.Cand cand = this.candidate((Rail)railRaw, start, (BlockPos)endRaw, x, z, reach);
                              if (cand != null) {
                                 out.add(cand);
                              }
                           } catch (Throwable var21) {
                              ++this.lastSkipped;
                              this.warnOnce(var21);
                           }
                        }
                     }
                  }
               }
            }
         } catch (Throwable var22) {
            this.warnOnce(var22);
         }

         out.sort(Comparator.comparingDouble((c) -> c.anchorDistance));
         return out;
      }
   }

   private RailBed.Cand candidate(Rail rail, BlockPos start, BlockPos end, double x, double z, double reach) {
      ++this.lastScanned;
      if (!physical(rail)) {
         return null;
      } else {
         double length = rail.getLength();
         if (length > 0.001D && !Double.isNaN(length) && !Double.isInfinite(length)) {
            double slack = reach + length * 0.5D + 1.0D;
            if (!(x < (double)Math.min(start.getX(), end.getX()) - slack) && !(x > (double)Math.max(start.getX(), end.getX()) + slack + 1.0D) && !(z < (double)Math.min(start.getZ(), end.getZ()) - slack) && !(z > (double)Math.max(start.getZ(), end.getZ()) + slack + 1.0D)) {
               RailBed.Entry entry = this.geometry(rail, length);
               if (entry == null) {
                  return null;
               } else if (entry.bboxDistance(x, z) > reach) {
                  return null;
               } else {
                  RailBed.Sample sample = column(entry, x, z);
                  return sample != null && !(sample.distance > reach) ? new RailBed.Cand(entry, start, end, sample.distance) : null;
               }
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   private RailBed.Entry geometry(Rail rail, double length) {
      RailBed.Entry entry = (RailBed.Entry)this.geometry.get(rail);
      if (entry != null) {
         return entry;
      } else {
         entry = entry(rail, length);
         if (entry == null) {
            return null;
         } else {
            if (this.geometry.size() >= 8192) {
               this.geometry.clear();
            }

            this.geometry.put(rail, entry);
            return entry;
         }
      }
   }

   private static RailBed.Entry entry(Rail rail, double length) {
      double sampleStep = Math.min(0.5D, length / 64.0D);
      int steps = (int)Math.min(256.0D, Math.ceil(length / sampleStep));
      if (steps < 2) {
         return null;
      } else {
         double[] xs = new double[steps + 1];
         double[] zs = new double[steps + 1];
         double[] ss = new double[steps + 1];
         double minX = Double.MAX_VALUE;
         double maxX = -Double.MAX_VALUE;
         double minZ = Double.MAX_VALUE;
         double maxZ = -Double.MAX_VALUE;
         double minY = Double.MAX_VALUE;
         double maxY = -Double.MAX_VALUE;

         for(int i = 0; i <= steps; ++i) {
            double s = length * (double)i / (double)steps;
            Vec3 p = position(rail, s);
            if (p == null) {
               return null;
            }

            if (!Double.isFinite(p.x) || !Double.isFinite(p.y) || !Double.isFinite(p.z)) {
               return null;
            }

            xs[i] = p.x;
            zs[i] = p.z;
            ss[i] = s;
            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minZ = Math.min(minZ, p.z);
            maxZ = Math.max(maxZ, p.z);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
         }

         double yStart = sampleY(rail, 0.0D, minY, maxY);
         double yEnd = sampleY(rail, length, minY, maxY);
         return new RailBed.Entry(rail, length, xs, zs, ss, minX, minZ, maxX, maxZ, minY, maxY, yStart, yEnd, cable(rail), straight(xs, zs));
      }
   }

   private static boolean straight(double[] xs, double[] zs) {
      int n = xs.length;
      if (n < 3) {
         return true;
      } else {
         double ex = xs[n - 1] - xs[0];
         double ez = zs[n - 1] - zs[0];
         double len = Math.sqrt(ex * ex + ez * ez);
         if (!(len > 1.0E-6D)) {
            return true;
         } else {
            double nx = -ez / len;
            double nz = ex / len;
            double worst = 0.0D;

            for(int i = 1; i < n - 1; ++i) {
               worst = Math.max(worst, Math.abs((xs[i] - xs[0]) * nx + (zs[i] - zs[0]) * nz));
            }

            return worst <= 0.001D;
         }
      }
   }

   private static double sampleY(Rail rail, double s, double fallback, double fallbackHigh) {
      Vec3 p = position(rail, s);
      if (p != null && Double.isFinite(p.y)) {
         return p.y;
      } else {
         return s <= 0.0D ? fallback : fallbackHigh;
      }
   }

   private static boolean physical(Rail rail) {
      try {
         RailType type = rail.railType;
         return type != RailType.NONE && type != RailType.AIRPLANE_DUMMY;
      } catch (Throwable var2) {
         return true;
      }
   }

   private static boolean cable(Rail rail) {
      try {
         return rail.railType.railSlopeStyle == RailSlopeStyle.CABLE;
      } catch (Throwable var2) {
         return false;
      }
   }

   private static Vec3 position(Rail rail, double t) {
      try {
         return rail.getPosition(t);
      } catch (Throwable var4) {
         return null;
      }
   }

   public String explain(Object railsMap, long tick, double x, double y, double z) {
      StringBuilder out = new StringBuilder();
      double anchorX = Math.floor(x / 2.0D) * 2.0D;
      double anchorZ = Math.floor(z / 2.0D) * 2.0D;
      out.append("[lte] where at=(").append(fmt(x)).append(", ").append(fmt(y)).append(", ").append(fmt(z)).append(") anchor=(").append(fmt(anchorX)).append(", ").append(fmt(anchorZ)).append(')');
      if (railsMap != null && railsMap instanceof Map) {
         RailBed.Sample best = this.probe(railsMap, tick, x, y, z, anchorX, anchorZ, 10.0D);
         out.append(" rails=").append(total(railsMap)).append(" near=").append(this.lastScanned).append(" cand=").append(this.cache.size()).append(" skipped=").append(this.lastSkipped).append(" halfW=").append(fmt(1.3D)).append(" reach=").append(fmt(2.5D));
         out.append(" hit=").append(this.lastHit ? "yes" : "no");
         if (this.cache.isEmpty()) {
            out.append('\n').append("[lte] where: \u5019\u9009\u8868\u662f\u7a7a\u7684 \u2014\u2014 \u8fd9\u4e2a\u4f4d\u7f6e\u5468\u56f4 ").append(fmt(8.0D)).append(" \u683c\u91cc\u4e00\u6761\u8f68\u9053\u90fd\u6ca1\u8fdb\u5019\u9009");
            return out.toString();
         } else {
            int index = 0;

            for(RailBed.Cand cand : this.cache) {
               if (index >= 8) {
                  out.append('\n').append("[lte] where: \u2026 \u8fd8\u6709 ").append(this.cache.size() - 8).append(" \u6761\u5019\u9009\u6ca1\u5217");
                  break;
               }

               ++index;
               RailBed.Entry entry = cand.entry;
               RailBed.Sample sample = column(entry, x, z);
               boolean win = best != null && sample != null && Math.abs(sample.distance - best.distance) < 1.0E-9D && Math.abs(sample.along - best.along) < 1.0E-9D;
               out.append('\n').append("[lte] where #").append(index).append(" dist=").append(sample == null ? "-" : fmt(sample.distance)).append(" off=").append(sample == null ? "-" : signed(sample.offset)).append(" y=").append(sample == null ? "-" : fmt(sample.y)).append(" along=").append(sample == null ? "-" : fmt(sample.along) + "/" + fmt(entry.length)).append(" straight=").append(entry.straight ? "yes" : "no").append(" anchorDist=").append(fmt(cand.anchorDistance)).append(' ').append(type(entry)).append(win ? " WIN" : (sample == null ? " (\u8d85\u51fa reach)" : (sample.distance > 1.3D ? " (\u8d85\u51fa halfW)" : "")));
            }

            if (best == null) {
               out.append('\n').append("[lte] where: \u6ca1\u6709\u547d\u4e2d \u2014\u2014 \u4e0a\u9762\u6bcf\u4e00\u6761\u7684 dist \u90fd\u6bd4 halfW=").append(fmt(1.3D)).append(" \u5927");
            }

            return out.toString();
         }
      } else {
         return out.append(" rails=0 (\u8f68\u9053\u8868\u4e3a\u7a7a/\u672a\u52a0\u8f7d \u2014\u2014 \u6ca1\u6709 ClientData.RAILS)").toString();
      }
   }

   public static int tableSize(Object railsMap) {
      return total(railsMap);
   }

   private static int total(Object railsMap) {
      int count = 0;

      try {
         for(Map.Entry<?, ?> outer : ((Map<?, ?>)railsMap).entrySet()) {
            Object ends = outer.getValue();
            if (ends instanceof Map) {
               count += ((Map)ends).size();
            }
         }

         return count;
      } catch (Throwable var5) {
         return -1;
      }
   }

   private static String type(RailBed.Entry entry) {
      try {
         return entry.rail.railType.name();
      } catch (Throwable var2) {
         return "?";
      }
   }

   private static String fmt(double value) {
      return !Double.isNaN(value) && !Double.isInfinite(value) ? String.format(Locale.ROOT, "%.2f", value) : "-";
   }

   private static String rangeText(double minY, double maxY) {
      if (!Double.isNaN(minY) && !Double.isNaN(maxY)) {
         return maxY - minY <= 0.005D ? fmt(minY) : fmt(minY) + ".." + fmt(maxY);
      } else {
         return "-";
      }
   }

   private static String signed(double value) {
      return !Double.isNaN(value) && !Double.isInfinite(value) ? String.format(Locale.ROOT, "%+.2f", value) : "-";
   }

   private static double clamp(double v, double lo, double hi) {
      double above = v < lo ? lo : v;
      return above > hi ? hi : above;
   }

   private void warnOnce(Throwable t) {
      if (this.warned.compareAndSet(false, true)) {
         LteLog.LOGGER.warn("[lte] collision: MTR rail data unavailable, trackbed collision disabled until it recovers", t);
      }

   }

   private static final class Cand {
      final RailBed.Entry entry;
      final BlockPos start;
      final BlockPos end;
      final double anchorDistance;

      Cand(RailBed.Entry entry, BlockPos start, BlockPos end, double anchorDistance) {
         this.entry = entry;
         this.start = start;
         this.end = end;
         this.anchorDistance = anchorDistance;
      }
   }

   public static final class Detail {
      final double distance;
      final double offset;
      final double railY;
      final double minY;
      final double maxY;
      final double along;
      final double length;
      final boolean straight;
      final RailBed.Why why;

      private Detail(RailBed.Entry entry, RailBed.Sample sample, RailBed.Why why) {
         this.distance = sample == null ? Double.NaN : sample.distance;
         this.offset = sample == null ? Double.NaN : sample.offset;
         this.railY = sample == null ? Double.NaN : sample.y;
         this.along = sample == null ? Double.NaN : sample.along;
         this.length = entry.length;
         this.minY = entry.minY;
         this.maxY = entry.maxY;
         this.straight = entry.straight;
         this.why = why;
      }

      private double rank() {
         return Double.isNaN(this.distance) ? Double.MAX_VALUE : this.distance;
      }

      private String range() {
         return RailBed.rangeText(this.minY, this.maxY);
      }

      public String text() {
         return "(" + RailBed.fmt(this.distance) + "," + RailBed.signed(this.offset) + "," + RailBed.fmt(this.railY) + "," + this.range() + "," + this.why.shortName() + ")";
      }
   }

   private static final class Entry {
      final Rail rail;
      final double length;
      final double[] xs;
      final double[] zs;
      final double[] ss;
      final double minX;
      final double minZ;
      final double maxX;
      final double maxZ;
      final double minY;
      final double maxY;
      final double yStart;
      final double yEnd;
      final boolean cable;
      final boolean straight;

      Entry(Rail rail, double length, double[] xs, double[] zs, double[] ss, double minX, double minZ, double maxX, double maxZ, double minY, double maxY, double yStart, double yEnd, boolean cable, boolean straight) {
         this.rail = rail;
         this.length = length;
         this.xs = xs;
         this.zs = zs;
         this.ss = ss;
         this.minX = minX;
         this.minZ = minZ;
         this.maxX = maxX;
         this.maxZ = maxZ;
         this.minY = minY;
         this.maxY = maxY;
         this.yStart = yStart;
         this.yEnd = yEnd;
         this.cable = cable;
         this.straight = straight;
      }

      double bboxDistance(double qx, double qz) {
         double dx = Math.max(0.0D, Math.max(this.minX - qx, qx - this.maxX));
         double dz = Math.max(0.0D, Math.max(this.minZ - qz, qz - this.maxZ));
         return Math.sqrt(dx * dx + dz * dz);
      }
   }

   public static final class Follow {
      final Rail rail;
      final RailBed.Entry entry;
      final BlockPos start;
      final BlockPos end;
      final double s;
      final double y;
      final double x;
      final double z;

      Follow(Rail rail, RailBed.Entry entry, BlockPos start, BlockPos end, double s, double y, double x, double z) {
         this.rail = rail;
         this.entry = entry;
         this.start = start;
         this.end = end;
         this.s = s;
         this.y = y;
         this.x = x;
         this.z = z;
      }

      public double s() {
         return this.s;
      }

      public double y() {
         return this.y;
      }

      public double length() {
         return this.entry == null ? 0.0D : this.entry.length;
      }

      public String seg() {
         return this.entry == null ? "-" : (this.entry.straight ? "straight/" : "arc/") + RailBed.fmt(this.entry.length);
      }
   }

   public static enum Kind {
      CONTINUE,
      TABLE,
      HOP,
      LOST;

      public String shortName() {
         switch (this.ordinal()) {
            case 0:
               return "cont";
            case 1:
               return "table";
            case 2:
               return "hop";
            default:
               return "lost";
         }
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

      boolean interior() {
         return this.along > 0.02D && this.along < this.length - 0.02D;
      }
   }

   public static final class Step {
      public final RailBed.Follow follow;
      public final RailBed.Sample sample;
      public final RailBed.Kind kind;

      Step(RailBed.Follow follow, RailBed.Sample sample, RailBed.Kind kind) {
         this.follow = follow;
         this.sample = sample;
         this.kind = kind;
      }

      public boolean continued() {
         return this.kind == RailBed.Kind.CONTINUE || this.kind == RailBed.Kind.HOP;
      }
   }

   public static enum Verdict {
      NONE,
      SNAP,
      LATERAL,
      DY,
      JUMP,
      NO_HIT,
      BLOCK,
      SPACE,
      RESCUE;

      public boolean snaps() {
         return this == SNAP || this == RESCUE;
      }

      public String shortName() {
         switch (this.ordinal()) {
            case 1:
               return "snap";
            case 2:
               return "lat";
            case 3:
               return "dy";
            case 4:
               return "jump";
            case 5:
               return "nohit";
            case 6:
               return "block";
            case 7:
               return "space";
            case 8:
               return "rescue";
            default:
               return "-";
         }
      }
   }

   public static enum Why {
      OK,
      HEIGHT,
      BBOX,
      COLUMN,
      FAR;

      public String shortName() {
         switch (this.ordinal()) {
            case 0:
               return "ok";
            case 1:
               return "height";
            case 2:
               return "bbox";
            case 3:
               return "column";
            default:
               return "far";
         }
      }
   }
}

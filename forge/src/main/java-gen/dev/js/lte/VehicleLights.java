package dev.js.lte;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mtr.client.DynamicTrainModel;
import mtr.data.TrainClient;
import mtr.model.ModelTrainBase;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;

public final class VehicleLights {
   public static final double FACE_GAP = 0.05D;
   private static final double DEFAULT_LATERAL_FRACTION = 0.36D;
   private static final double DEFAULT_HEIGHT = 0.55D;
   private static final double ROOF_FRACTION = 0.95D;
   private static final double END_FRACTION = 0.35D;
   private static final double END_MIN_BLOCKS = 1.2D;
   private static final double MAX_INSET = 1.5D;
   private static final double DEFAULT_WIDTH = 2.9D;
   private static final double LATERAL_CLAMP_FRACTION = 0.45D;
   private static final double MAX_LATERAL_BLOCKS = 2.5D;
   private static final int MAX_SECTIONS = 8;
   private static final int CACHE_SIZE = 96;
   private static final double LATERAL_DEAD_ZONE = 0.06D;
   private static final double LAMP_ALONG_TOL = 1.0D;
   private static final double LAMP_CLUSTER_TOL = 0.35D;
   private static final double FACE_MATCH_BLOCKS = 0.6D;
   private static final double MIRROR_TOL = 0.35D;
   private static final double LAMP_DUPLICATE_TOL = 0.35D;
   private static final int MAX_LAMPS = 4;
   private static final double LAMP_TRACK_SLACK = 1.3D;
   private static final String[] HEAD_WORDS = new String[]{"headlight", "headlights", "head_light", "head_lights", "headlamp", "head_lamp", "lighthead", "head_led", "\u524d\u7167", "\u5934\u706f", "toudeng", "qiandeng"};
   private static final String[] TAIL_WORDS = new String[]{"taillight", "taillights", "tail_light", "tail_lights", "weideng", "\u5c3e\u706f"};
   private static final String[] REJECT_WORDS = new String[]{"always_on", "alwayson", "always", "interior", "door", "roof", "window", "side", "cabin", "\u8f66\u5185", "\u95e8", "\u9876", "\u7a97", "logo", "display", "lcd", "tv", "screen", "plate", "\u724c\u7167", "reading", "cargo", "under", "step", "handrail", "spot", "marker", "translucent", "stablization", "stabilization"};
   private static final Map<DynamicTrainModel, VehicleLights.Layout> CACHE = new LinkedHashMap<DynamicTrainModel, VehicleLights.Layout>(32, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<DynamicTrainModel, VehicleLights.Layout> eldest) {
         return this.size() > 96;
      }
   };
   private static volatile Field fOriginX;
   private static volatile Field fOriginY;
   private static volatile Field fOriginZ;
   private static volatile Field fOffsetX;
   private static volatile Field fOffsetY;
   private static volatile Field fOffsetZ;
   private static volatile Field fHeight;
   private static final Map<String, Boolean> ANNOUNCED = new LinkedHashMap<String, Boolean>(64, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
         return this.size() > 512;
      }
   };

   private VehicleLights() {
   }

   public static DynamicTrainModel modelOf(TrainClient train) {
      try {
         if (train != null && train.trainRenderer != null) {
            TrainRendererBase model = train.trainRenderer;
            if (model instanceof JonModelTrainRenderer) {
               JonModelTrainRenderer renderer = (JonModelTrainRenderer)model;
               ModelTrainBase modelLte = renderer.model;
               DynamicTrainModel var10000;
               if (modelLte instanceof DynamicTrainModel) {
                  DynamicTrainModel dynamic = (DynamicTrainModel)modelLte;
                  var10000 = dynamic;
               } else {
                  var10000 = null;
               }

               return var10000;
            } else {
               return null;
            }
         } else {
            return null;
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   public static VehicleLights.Layout layoutFor(TrainClient train) {
      DynamicTrainModel model = modelOf(train);
      if (model != null) {
         return layoutFor(model, train == null ? 0 : train.spacing);
      } else {
         return train != null && train.trainRenderer instanceof JonModelTrainRenderer ? BuiltinLights.layoutFor(train) : null;
      }
   }

   public static VehicleLights.Layout layoutFor(DynamicTrainModel model) {
      return layoutFor(model, 0);
   }

   public static VehicleLights.Layout layoutFor(DynamicTrainModel model, int spacing) {
      if (model == null) {
         return null;
      } else {
         VehicleLights.Layout layout = (VehicleLights.Layout)CACHE.get(model);
         if (layout == null) {
            layout = parse(model, spacing);
            CACHE.put(model, layout);
         }

         return layout;
      }
   }

   public static VehicleLights.Layout parse(DynamicTrainModel model) {
      return parse(model, 0);
   }

   public static VehicleLights.Layout parse(DynamicTrainModel model, int spacing) {
      VehicleLights.Layout out = new VehicleLights.Layout();

      try {
         readParts(model, out, spacing);
      } catch (Throwable var6) {
         out.error = "partsInfo: " + String.valueOf(var6);
         out.head = defaultSlot(out, true);
         out.tail = defaultSlot(out, false);
         return out;
      }

      try {
         readStages(model, out);
      } catch (Throwable var5) {
         out.error = "properties: " + String.valueOf(var5);
      }

      try {
         analyse(out);
      } catch (Throwable var4) {
         out.error = out.error + " analyse: " + String.valueOf(var4);
         out.head = defaultSlot(out, true);
         out.tail = defaultSlot(out, false);
      }

      if (out.head == null) {
         out.head = defaultSlot(out, true);
      }

      if (out.tail == null) {
         out.tail = defaultSlot(out, false);
      }

      return out;
   }

   private static void resolvePartFields(Class<?> type) throws Exception {
      if (fOriginX == null) {
         Field ox = type.getDeclaredField("originX");
         Field oy = type.getDeclaredField("originY");
         Field oz = type.getDeclaredField("originZ");
         Field dx = type.getDeclaredField("offsetX");
         Field dy = type.getDeclaredField("offsetY");
         Field dz = type.getDeclaredField("offsetZ");
         Field h = type.getDeclaredField("height");
         ox.setAccessible(true);
         oy.setAccessible(true);
         oz.setAccessible(true);
         dx.setAccessible(true);
         dy.setAccessible(true);
         dz.setAccessible(true);
         h.setAccessible(true);
         fOriginX = ox;
         fOriginY = oy;
         fOriginZ = oz;
         fOffsetX = dx;
         fOffsetY = dy;
         fOffsetZ = dz;
         fHeight = h;
      }
   }

   private static void readParts(DynamicTrainModel model, VehicleLights.Layout out, int spacing) throws Exception {
      Map<String, ?> parts = model.partsInfo;
      if (parts == null) {
         throw new IllegalStateException("partsInfo is null");
      } else {
         double minX = Double.MAX_VALUE;
         double maxX = -Double.MAX_VALUE;
         double minY = Double.MAX_VALUE;
         double maxY = -Double.MAX_VALUE;
         double minZ = Double.MAX_VALUE;
         double maxZ = -Double.MAX_VALUE;
         Map<String, List<double[]>> instances = new LinkedHashMap();

         for(Map.Entry<String, ?> entry : parts.entrySet()) {
            Object raw = entry.getValue();
            if (raw instanceof Iterable) {
               Iterable<?> infos = (Iterable)raw;
               List<double[]> list = (List)instances.get(entry.getKey());

               for(Object info : infos) {
                  if (info != null) {
                     resolvePartFields(info.getClass());
                     double x = fOriginX.getDouble(info) + fOffsetX.getDouble(info);
                     double y = fOriginY.getDouble(info) + fOffsetY.getDouble(info);
                     double z = fOriginZ.getDouble(info) + fOffsetZ.getDouble(info);
                     double h = (double)fHeight.getFloat(info);
                     if (list == null) {
                        list = new ArrayList();
                        instances.put((String)entry.getKey(), list);
                     }

                     list.add(new double[]{x, y, z});
                     minX = Math.min(minX, x);
                     maxX = Math.max(maxX, x);
                     minY = Math.min(minY, y - Math.abs(h) * 0.5D);
                     maxY = Math.max(maxY, y + Math.abs(h) * 0.5D);
                     minZ = Math.min(minZ, z);
                     maxZ = Math.max(maxZ, z);
                  }
               }
            }
         }

         if (instances.isEmpty()) {
            out.minY = 0.0D;
            out.maxY = 0.0D;
         } else {
            double spanX = maxX - minX;
            double spanZ = maxZ - minZ;
            boolean z = spanZ >= spanX;
            out.lengthAxis = z ? "Z" : "X";
            out.minY = minY;
            out.maxY = maxY;
            List<Double> groupLaterals = new ArrayList();
            double half = 0.0D;

            for(List<double[]> list : instances.values()) {
               double sum = 0.0D;

               for(double[] in : list) {
                  sum += z ? in[0] : in[2];
               }

               groupLaterals.add(list.isEmpty() ? 0.0D : sum / (double)list.size());
            }

            out.latCentre = median(groupLaterals);

            for(List<double[]> list : instances.values()) {
               for(double[] in : list) {
                  half = Math.max(half, Math.abs((z ? in[0] : in[2]) - out.latCentre));
               }
            }

            out.halfWidth = half;
            out.length = num(model.properties, "length", 0.0D);
            out.width = num(model.properties, "width", 0.0D);
            double span = Math.max(1.0E-6D, z ? spanZ : spanX);
            double uMin = z ? minZ : minX;
            double carLen = out.length;
            if (carLen <= 0.5D && spacing >= 3 && (double)spacing <= span * 1.25D) {
               carLen = (double)spacing;
            }

            int sections = carLen > 0.5D ? (int)Math.round(span / carLen) : 1;
            if (sections < 1) {
               sections = 1;
            }

            if (sections > 8) {
               sections = 8;
            }

            out.sections = sections;
            out.sectionLen = span / (double)sections;
            int[] count = new int[8];
            double[] farthest = new double[8];

            for(Map.Entry<String, List<double[]>> entry : instances.entrySet()) {
               List<double[]> list = (List)entry.getValue();
               Arrays.fill(count, 0);
               Arrays.fill(farthest, 0.0D);

               for(double[] in : list) {
                  int sec = sectionOf(z ? in[2] : in[0], uMin, out.sectionLen, sections);
                  int var10002 = count[sec]++;
                  double s = signed(alongOf(in, z), uMin, sec, out.sectionLen);
                  farthest[sec] = Math.max(farthest[sec], Math.abs(s));
               }

               int best = 0;

               for(int s = 1; s < sections; ++s) {
                  if (count[s] > count[best] || count[s] == count[best] && farthest[s] > farthest[best]) {
                     best = s;
                  }
               }

               double anchorAlong = 0.0D;
               double bestAbs = -1.0D;

               for(double[] in : list) {
                  double a = alongOf(in, z);
                  if (sectionOf(a, uMin, out.sectionLen, sections) == best) {
                     double abs = Math.abs(signed(a, uMin, best, out.sectionLen));
                     if (abs > bestAbs) {
                        bestAbs = abs;
                        anchorAlong = a;
                     }
                  }
               }

               List<Double> onFace = new ArrayList();
               List<Double> heights = new ArrayList();

               for(double[] in : list) {
                  double a = alongOf(in, z);
                  if (sectionOf(a, uMin, out.sectionLen, sections) == best && !(Math.abs(a - anchorAlong) > 1.0D)) {
                     onFace.add(z ? in[0] : in[2]);
                     heights.add(in[1]);
                  }
               }

               if (!onFace.isEmpty()) {
                  double[] lamps = clusters(onFace);
                  VehicleLights.Group g = new VehicleLights.Group((String)entry.getKey(), onFace.size(), anchorAlong, mean(onFace), mean(heights));
                  g.lamps = lamps;
                  g.section = best;
                  g.s = signed(anchorAlong, uMin, best, out.sectionLen);
                  g.faceDist = (1.0D - Math.min(1.0D, Math.abs(g.s))) * out.sectionLen * 0.5D;
                  out.groups.put((String)entry.getKey(), g);
               }
            }

         }
      }
   }

   private static double alongOf(double[] in, boolean z) {
      return z ? in[2] : in[0];
   }

   private static int sectionOf(double along, double uMin, double sectionLen, int sections) {
      int sec = (int)Math.floor((along - uMin) / sectionLen);
      return sec < 0 ? 0 : Math.min(sec, sections - 1);
   }

   private static double signed(double along, double uMin, int section, double sectionLen) {
      double local = along - uMin - (double)section * sectionLen;
      return 2.0D * local / sectionLen - 1.0D;
   }

   private static void readStages(DynamicTrainModel model, VehicleLights.Layout out) {
      JsonObject props = model.properties;
      if (props != null) {
         JsonElement partsEl = props.get("parts");
         if (partsEl != null && partsEl.isJsonArray()) {
            for(JsonElement el : partsEl.getAsJsonArray()) {
               if (el.isJsonObject()) {
                  JsonObject part = el.getAsJsonObject();
                  if (part.has("name")) {
                     String name = str(part, "name");
                     VehicleLights.Group group = (VehicleLights.Group)out.groups.get(name);
                     if (group != null) {
                        String stage = str(part, "stage");
                        if (stageRank(stage) > stageRank(group.stage)) {
                           group.stage = stage;
                           group.condition = str(part, "render_condition");
                        }
                     }
                  }
               }
            }

         }
      }
   }

   private static int stageRank(String stage) {
      if ("ALWAYS_ON_LIGHTS".equals(stage)) {
         return 4;
      } else if ("LIGHTS".equals(stage)) {
         return 3;
      } else if ("INTERIOR".equals(stage)) {
         return 1;
      } else {
         return "?".equals(stage) ? 2 : 2;
      }
   }

   private static void analyse(VehicleLights.Layout out) {
      if (out.groups.isEmpty()) {
         out.head = defaultSlot(out, true);
         out.tail = defaultSlot(out, false);
      } else {
         double votes = 0.0D;

         for(VehicleLights.Group g : out.groups.values()) {
            if (containsAny(g.lower, HEAD_WORDS) && !isRejected(g.lower) && !isRoof(out, g)) {
               votes += Math.signum(g.s);
            }
         }

         out.frontSign = votes > 0.0D ? 1 : -1;
         out.head = pick(out, true);
         out.tail = pick(out, false);
      }
   }

   private static VehicleLights.Slot pick(VehicleLights.Layout out, boolean head) {
      String[] words = head ? HEAD_WORDS : TAIL_WORDS;
      double want = head ? (double)out.frontSign : (double)(-out.frontSign);
      double endBand = Math.max(1.2D, 0.35D * out.sectionLen);
      VehicleLights.Group best = null;
      double bestScore = -Double.MAX_VALUE;
      VehicleLights.Group relaxed = null;
      double relaxedScore = -Double.MAX_VALUE;

      for(VehicleLights.Group g : out.groups.values()) {
         if (containsAny(g.lower, words) && !isRejected(g.lower) && !isRoof(out, g)) {
            double score = (double)stageRank(g.stage) * 10.0D + (Math.signum(g.s) == want ? 12.0D : 0.0D) + Math.max(0.0D, 20.0D * (1.0D - g.faceDist / endBand)) + (double)Math.min(4, g.count);
            if (g.faceDist <= endBand) {
               if (score > bestScore) {
                  bestScore = score;
                  best = g;
               }
            } else if (score > relaxedScore) {
               relaxedScore = score;
               relaxed = g;
            }
         }
      }

      if (best != null) {
         return namedSlot(out, head, best, 2, "name+geo");
      } else {
         return relaxed != null ? namedSlot(out, head, relaxed, 3, "name") : geoSlot(out, head);
      }
   }

   private static VehicleLights.Slot geoSlot(VehicleLights.Layout out, boolean head) {
      double want = head ? (double)out.frontSign : (double)(-out.frontSign);
      VehicleLights.Slot slot = geoAtEnd(out, head, want);
      if (slot == null && head) {
         slot = geoAtEnd(out, head, -want);
      }

      return slot == null ? defaultSlot(out, head) : slot;
   }

   private static VehicleLights.Slot geoAtEnd(VehicleLights.Layout out, boolean head, double want) {
      double endBand = Math.max(1.2D, 0.35D * out.sectionLen);
      VehicleLights.Group anchor = null;
      double bestFace = Double.MAX_VALUE;

      for(VehicleLights.Group g : out.groups.values()) {
         if (stageRank(g.stage) >= 3 && Math.signum(g.s) == want && !isRejected(g.lower) && !isRoof(out, g) && !(g.faceDist > endBand) && !(g.y < 0.3D) && !(g.y > 3.4D) && g.lamps.length != 0 && g.faceDist < bestFace) {
            bestFace = g.faceDist;
            anchor = g;
         }
      }

      if (anchor == null) {
         return null;
      } else {
         VehicleLights.Slot slot = namedSlot(out, head, anchor, 4, "geo");
         return slot.lateral.length >= 2 ? slot : null;
      }
   }

   private static VehicleLights.Slot namedSlot(VehicleLights.Layout out, boolean head, VehicleLights.Group g, int level, String how) {
      double width = out.width > 0.6D ? out.width : 2.9D;
      double limit = Math.max(0.45D * width, Math.min(out.halfWidth + 0.2D, 2.5D));
      double inset = Math.min(1.5D, Math.max(0.0D, g.faceDist));
      double height = clamp(g.y, 0.35D, 3.2D);
      return new VehicleLights.Slot(g.name, level, how, faceLamps(out, head, g, limit), height, inset, g.faceDist);
   }

   private static double[] faceLamps(VehicleLights.Layout out, boolean head, VehicleLights.Group anchor, double limit) {
      String[] words = head ? HEAD_WORDS : TAIL_WORDS;
      double want = Math.signum(anchor.s);
      List<Double> raw = new ArrayList();

      for(double lamp : anchor.lamps) {
         raw.add(lamp);
      }

      for(VehicleLights.Group g : out.groups.values()) {
         if (g != anchor && stageRank(g.stage) == stageRank(anchor.stage) && Math.signum(g.s) == want && !(Math.abs(g.faceDist - anchor.faceDist) > 0.6D) && !isRejected(g.lower) && !isRoof(out, g) && (containsAny(g.lower, words) || mirrors(g, raw, out.latCentre))) {
            for(double lamp : g.lamps) {
               raw.add(lamp);
            }
         }
      }

      List<Double> out2 = new ArrayList(raw.size());

      for(double lamp : raw) {
         double rel = clamp(lamp - out.latCentre, -limit, limit);
         if (Math.abs(rel) < 0.06D) {
            rel = 0.0D;
         }

         boolean duplicate = false;

         for(double other : out2) {
            if (Math.abs(other - rel) <= 0.35D) {
               duplicate = true;
               break;
            }
         }

         if (!duplicate) {
            out2.add(rel);
         }
      }

      if (out2.isEmpty()) {
         return new double[]{0.0D};
      } else {
         Collections.sort(out2);
         if (out2.size() > 2) {
            double pairTrack = 0.0D;
            double bestSum = Double.MAX_VALUE;

            for(int i = 0; i < out2.size(); ++i) {
               for(int j = i + 1; j < out2.size(); ++j) {
                  double sum = Math.abs(out2.get(i) + out2.get(j));
                  if (sum < bestSum) {
                     bestSum = sum;
                     pairTrack = Math.max(Math.abs(out2.get(i)), Math.abs(out2.get(j)));
                  }
               }
            }

            double keep = pairTrack * 1.3D + 0.1D;
            out2.removeIf((value) -> Math.abs(value) > keep);
         }

         double[] result = new double[Math.min(out2.size(), 4)];
         if (out2.size() <= 4) {
            for(int i = 0; i < result.length; ++i) {
               result[i] = out2.get(i);
            }
         } else {
            int half = 2;

            for(int i = 0; i < 2; ++i) {
               result[i] = out2.get(i);
            }

            for(int i = 0; i < 2; ++i) {
               result[2 + i] = out2.get(out2.size() - 2 + i);
            }

            Arrays.sort(result);
         }

         return result;
      }
   }

   private static boolean mirrors(VehicleLights.Group g, List<Double> taken, double centre) {
      for(double lamp : g.lamps) {
         double rel = lamp - centre;

         for(double other : taken) {
            if (Math.abs(rel + (other - centre)) <= 0.35D) {
               return true;
            }
         }
      }

      return false;
   }

   private static VehicleLights.Slot defaultSlot(VehicleLights.Layout out, boolean head) {
      double width = out.width > 0.6D ? out.width : 2.9D;
      double half = 0.36D * width;
      return new VehicleLights.Slot("(default)", 5, "default", new double[]{-half, half}, 0.55D, 0.0D, 0.0D);
   }

   private static boolean isRoof(VehicleLights.Layout out, VehicleLights.Group g) {
      double span = out.maxY - out.minY;
      if (span <= 0.5D) {
         return false;
      } else {
         return (g.y - out.minY) / span > 0.95D;
      }
   }

   private static boolean isRejected(String lower) {
      return containsAny(lower, REJECT_WORDS) || lower.endsWith("_off") || lower.endsWith("_closed");
   }

   private static boolean containsAny(String haystack, String[] needles) {
      for(String needle : needles) {
         if (haystack.contains(needle)) {
            return true;
         }
      }

      return false;
   }

   private static double clamp(double value, double min, double max) {
      return Double.isNaN(value) ? min : Math.max(min, Math.min(max, value));
   }

   private static double median(List<Double> values) {
      if (values.isEmpty()) {
         return 0.0D;
      } else {
         double[] sorted = new double[values.size()];

         for(int i = 0; i < sorted.length; ++i) {
            sorted[i] = values.get(i);
         }

         Arrays.sort(sorted);
         int mid = sorted.length / 2;
         return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) * 0.5D;
      }
   }

   private static double mean(List<Double> values) {
      if (values.isEmpty()) {
         return 0.0D;
      } else {
         double sum = 0.0D;

         for(double value : values) {
            sum += value;
         }

         return sum / (double)values.size();
      }
   }

   private static double[] clusters(List<Double> values) {
      if (values.isEmpty()) {
         return new double[0];
      } else {
         double[] sorted = new double[values.size()];

         for(int i = 0; i < sorted.length; ++i) {
            sorted[i] = values.get(i);
         }

         Arrays.sort(sorted);
         List<Double> out = new ArrayList();
         double sum = sorted[0];
         double last = sorted[0];
         int n = 1;

         for(int i = 1; i < sorted.length; ++i) {
            if (sorted[i] - last <= 0.35D) {
               sum += sorted[i];
               ++n;
            } else {
               out.add(sum / (double)n);
               sum = sorted[i];
               n = 1;
            }

            last = sorted[i];
         }

         out.add(sum / (double)n);
         double[] result = new double[out.size()];

         for(int i = 0; i < result.length; ++i) {
            result[i] = out.get(i);
         }

         return result;
      }
   }

   private static String str(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsString() : "?";
   }

   private static double num(JsonObject o, String key, double fallback) {
      try {
         JsonElement e = o == null ? null : o.get(key);
         return e != null && !e.isJsonNull() ? e.getAsDouble() : fallback;
      } catch (Throwable var5) {
         return fallback;
      }
   }

   public static void announce(TrainClient train) {
      try {
         if (train == null) {
            return;
         }

         String id = train.baseTrainType == null ? "?" : train.baseTrainType;
         if (ANNOUNCED.containsKey(id)) {
            return;
         }

         VehicleLights.Layout layout = layoutFor(train);
         if (layout == null) {
            return;
         }

         ANNOUNCED.put(id, Boolean.TRUE);
         MtrHeadlightClient.LOGGER.info("[lte] {}", layout.diagnostic(id));
         if (!layout.error.isEmpty()) {
            MtrHeadlightClient.LOGGER.warn("[lte] lights: {} parse note: {}", id, layout.error);
         }
      } catch (Throwable var3) {
         MtrHeadlightClient.LOGGER.warn("[lte] lights: announce failed", var3);
      }

   }

   public static int slotCount(TrainClient train) {
      try {
         VehicleLights.Layout layout = layoutFor(train);
         return layout == null ? 0 : (layout.head == null ? 0 : 1) + (layout.tail == null ? 0 : 1);
      } catch (Throwable var2) {
         return 0;
      }
   }

   public static String debugLine(TrainClient train) {
      if (train == null) {
         return "lights: model=? slots=0 source=no-train";
      } else {
         String id = train.baseTrainType == null ? "?" : train.baseTrainType;

         try {
            VehicleLights.Layout layout = layoutFor(train);
            return layout == null ? "lights: model=" + id + " slots=0 head=null tail=null source=no-model" : layout.diagnostic(id);
         } catch (Throwable var3) {
            return "lights: model=" + id + " slots=0 source=error(" + var3.getClass().getSimpleName() + ")";
         }
      }
   }

   public static void clearCache() {
      CACHE.clear();
      ANNOUNCED.clear();
   }

   public static final class Group {
      public final String name;
      public final String lower;
      public final int count;
      public final double along;
      public final double lateral;
      public final double y;
      public String stage = "?";
      public String condition = "?";
      public int section;
      public double s;
      public double faceDist;
      public double[] lamps = new double[0];

      Group(String name, int count, double along, double lateral, double y) {
         this.name = name;
         this.lower = name.toLowerCase(Locale.ROOT);
         this.count = count;
         this.along = along;
         this.lateral = lateral;
         this.y = y;
      }
   }

   public static final class Layout {
      public final Map<String, VehicleLights.Group> groups = new LinkedHashMap();
      public String lengthAxis = "Z";
      public int sections = 1;
      public double sectionLen = 1.0D;
      public double length = 0.0D;
      public double width = 0.0D;
      public double latCentre = 0.0D;
      public double halfWidth = 0.0D;
      public double minY = 0.0D;
      public double maxY = 0.0D;
      public int frontSign = -1;
      public VehicleLights.Slot head;
      public VehicleLights.Slot tail;
      public String error = "";

      public String diagnostic(String modelId) {
         String source = isDefault(this.head) && isDefault(this.tail) ? "default" : "name";
         return "lights: model=" + modelId + " slots=" + this.groups.size() + " head=" + String.valueOf(this.head) + " tail=" + String.valueOf(this.tail) + " source=" + source;
      }

      private static boolean isDefault(VehicleLights.Slot slot) {
         return slot == null || slot.isDefault();
      }
   }

   public static final class Slot {
      public final String group;
      public final int level;
      public final String how;
      public final double[] lateral;
      public final double height;
      public final double inset;
      public final double faceDist;

      Slot(String group, int level, String how, double[] lateral, double height, double inset, double faceDist) {
         this.group = group;
         this.level = level;
         this.how = how;
         this.lateral = lateral;
         this.height = height;
         this.inset = inset;
         this.faceDist = faceDist;
      }

      public boolean isDefault() {
         return this.level >= 5;
      }

      public String toString() {
         return this.group + "(L" + this.level + "," + this.how + (this.lateral.length > 1 ? ",x" + this.lateral.length : "") + ")";
      }
   }
}

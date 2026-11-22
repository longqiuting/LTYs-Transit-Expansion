package dev.js.mtrheadlight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mtr.data.TrainClient;

public final class BuiltinLights {
   private static final String RESOURCE = "/assets/lte/builtin_lights.json";
   private static final double MAX_INSET = 1.5D;
   private static volatile Map<String, BuiltinLights.Entry> table;
   private static final Map<String, VehicleLights.Layout> CACHE = new ConcurrentHashMap();

   private BuiltinLights() {
   }

   public static boolean has(String id) {
      return entry(id) != null;
   }

   public static int size() {
      return load().size();
   }

   public static VehicleLights.Layout layoutFor(TrainClient train) {
      if (train == null) {
         return null;
      } else {
         String id = idOf(train);
         if (id.isEmpty()) {
            return null;
         } else {
            BuiltinLights.Entry e = entry(id);
            if (e == null) {
               return null;
            } else {
               int spacing = Math.max(1, train.spacing);
               int width = Math.max(1, train.width);
               String key = id + "|" + spacing + "|" + width;
               VehicleLights.Layout cached = (VehicleLights.Layout)CACHE.get(key);
               if (cached != null) {
                  return cached;
               } else {
                  double face = (double)spacing * 0.5D;
                  VehicleLights.Slot head = slot("headlights", e.head, face);
                  VehicleLights.Slot tail = slot("tail_lights", e.tail, face);
                  if (head != null && tail != null) {
                     VehicleLights.Layout out = new VehicleLights.Layout();
                     out.lengthAxis = "Z";
                     out.sections = 1;
                     out.sectionLen = (double)spacing;
                     out.length = (double)spacing;
                     out.width = (double)width;
                     out.latCentre = 0.0D;
                     out.minY = 0.0D;
                     out.maxY = 0.0D;
                     out.frontSign = -1;
                     out.head = head;
                     out.tail = tail;
                     CACHE.put(key, out);
                     return out;
                  } else {
                     return null;
                  }
               }
            }
         }
      }
   }

   private static VehicleLights.Slot slot(String group, BuiltinLights.Lamp[] lamps, double face) {
      if (lamps != null && lamps.length != 0) {
         double[] lateral = new double[lamps.length];
         double sy = 0.0D;
         double sz = 0.0D;

         for(int i = 0; i < lamps.length; ++i) {
            lateral[i] = lamps[i].x;
            sy += lamps[i].y;
            sz += Math.abs(lamps[i].z);
         }

         double height = sy / (double)lamps.length;
         double zMean = sz / (double)lamps.length;
         double inset = clamp(face - zMean, -2.0D, 1.5D);
         double faceDist = Math.max(0.0D, face - zMean);
         return new VehicleLights.Slot(group, 1, "builtin", lateral, clamp(height, 0.2D, 3.4D), inset, faceDist);
      } else {
         return null;
      }
   }

   private static double clamp(double v, double lo, double hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   private static String idOf(TrainClient train) {
      String id = "";

      try {
         if (train.trainId != null) {
            id = train.trainId;
         } else if (train.baseTrainType != null) {
            id = train.baseTrainType;
         }
      } catch (Throwable var3) {
         return "";
      }

      id = id.trim().toLowerCase(Locale.ROOT);
      int colon = id.indexOf(58);
      if (colon >= 0) {
         id = id.substring(colon + 1);
      }

      return id;
   }

   private static BuiltinLights.Entry entry(String id) {
      if (id != null && !id.isEmpty()) {
         String key = id.trim().toLowerCase(Locale.ROOT);
         int colon = key.indexOf(58);
         if (colon >= 0) {
            key = key.substring(colon + 1);
         }

         BuiltinLights.Entry e = (BuiltinLights.Entry)load().get(key);
         return e != null && e.usable() ? e : null;
      } else {
         return null;
      }
   }

   private static Map<String, BuiltinLights.Entry> load() {
      Map<String, BuiltinLights.Entry> local = table;
      if (local != null) {
         return local;
      } else {
         synchronized(BuiltinLights.class) {
            local = table;
            if (local != null) {
               return local;
            } else {
               local = parse();
               table = local;
               return local;
            }
         }
      }
   }

   private static Map<String, BuiltinLights.Entry> parse() {
      Map<String, BuiltinLights.Entry> out = new LinkedHashMap();

      try {
         InputStream in = BuiltinLights.class.getResourceAsStream("/assets/lte/builtin_lights.json");

         Object var12;
         label81: {
            Object var11;
            label87: {
               try {
                  if (in == null) {
                     MtrHeadlightClient.LOGGER.warn("[lte] builtin lights: {} missing", "/assets/lte/builtin_lights.json");
                     var11 = out;
                     break label87;
                  }

                  JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                  if (!root.isJsonObject()) {
                     MtrHeadlightClient.LOGGER.warn("[lte] builtin lights: root is not an object");
                     var12 = out;
                     break label81;
                  }

                  for(Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                     if (((JsonElement)e.getValue()).isJsonObject()) {
                        JsonObject v = ((JsonElement)e.getValue()).getAsJsonObject();
                        BuiltinLights.Lamp[] head = lamps(v, "head");
                        BuiltinLights.Lamp[] tail = lamps(v, "tail");
                        if (head.length > 0 && tail.length > 0) {
                           out.put(((String)e.getKey()).toLowerCase(Locale.ROOT), new BuiltinLights.Entry(head, tail));
                        }
                     }
                  }

                  MtrHeadlightClient.LOGGER.info("[lte] builtin lights: {} vehicles", out.size());
               } catch (Throwable var9) {
                  if (in != null) {
                     try {
                        in.close();
                     } catch (Throwable var8) {
                        var9.addSuppressed(var8);
                     }
                  }

                  throw var9;
               }

               if (in != null) {
                  in.close();
               }

               return out;
            }

            if (in != null) {
               in.close();
            }

            return (Map<String, BuiltinLights.Entry>)var11;
         }

         if (in != null) {
            in.close();
         }

         return (Map<String, BuiltinLights.Entry>)var12;
      } catch (Throwable var10) {
         MtrHeadlightClient.LOGGER.warn("[lte] builtin lights: failed to load", var10);
         return out;
      }
   }

   private static BuiltinLights.Lamp[] lamps(JsonObject entry, String key) {
      JsonElement el = entry.get(key);
      if (el != null && el.isJsonArray()) {
         JsonArray arr = el.getAsJsonArray();
         List<BuiltinLights.Lamp> out = new ArrayList(arr.size());

         for(JsonElement item : arr) {
            if (item.isJsonObject()) {
               JsonObject o = item.getAsJsonObject();
               out.add(new BuiltinLights.Lamp(num(o, "x"), num(o, "y"), num(o, "z"), num(o, "w"), num(o, "h")));
            }
         }

         return (BuiltinLights.Lamp[])out.toArray(new BuiltinLights.Lamp[0]);
      } else {
         return new BuiltinLights.Lamp[0];
      }
   }

   private static double num(JsonObject o, String key) {
      JsonElement e = o.get(key);
      return e != null && !e.isJsonNull() ? e.getAsDouble() : 0.0D;
   }

   public static final class Entry {
      public final BuiltinLights.Lamp[] head;
      public final BuiltinLights.Lamp[] tail;

      Entry(BuiltinLights.Lamp[] head, BuiltinLights.Lamp[] tail) {
         this.head = head;
         this.tail = tail;
      }

      public boolean usable() {
         return this.head.length > 0 && this.tail.length > 0;
      }
   }

   public static final class Lamp {
      public final double x;
      public final double y;
      public final double z;
      public final double w;
      public final double h;

      Lamp(double x, double y, double z, double w, double h) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.w = w;
         this.h = h;
      }
   }
}

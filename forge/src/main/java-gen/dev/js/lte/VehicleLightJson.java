package dev.js.lte;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class VehicleLightJson {
   public static final String FILE_NAME = "lte_vehicle_lights.json";
   public static final int FORMAT_VERSION = 1;
   private static final String[] COMMENT = new String[]{"LTE vehicle light layout - generated file, editable by hand.", "Place it at assets/<your_pack>/lte_vehicle_lights.json in a resource pack (client) or in a", "datapack / config/lte_lights on the server.", "id supports a trailing '*' wildcard (prefix match); an exact id wins over a wildcard,", "and a longer wildcard prefix wins over a shorter one.", "Fields you leave out are filled in from LTE's automatic light detection.", "pos is car-local model space: [lateral, height, along] in the model's own axes,", "so on a model whose length axis is X the 'along' number is pos[0].", "rot is degrees [yaw, pitch, roll]; length/width are blocks; alpha is 0..1."};
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final Pattern NUM_ARRAY = Pattern.compile("\\[\\s*\\n((?:\\s*-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?,?\\s*\\n)+)\\s*\\]");

   private VehicleLightJson() {
   }

   public static VehicleLightJson.Result parse(String json, String label) {
      VehicleLightJson.Result out = new VehicleLightJson.Result();

      try {
         JsonElement rootEl = JsonParser.parseString(json);
         if (rootEl == null || !rootEl.isJsonObject()) {
            out.error = "root is not an object";
            return out;
         }

         JsonObject root = rootEl.getAsJsonObject();
         JsonElement versionEl = root.get("version");
         if (versionEl != null && versionEl.isJsonPrimitive()) {
            int version = 0;

            try {
               version = versionEl.getAsInt();
            } catch (NumberFormatException var12) {
               out.notes.add("version is not a number");
            }

            if (version > 1) {
               out.notes.add("version " + version + " is newer than 1 - reading it as 1");
            }
         }

         JsonElement vehiclesEl = root.get("vehicles");
         if (vehiclesEl == null || !vehiclesEl.isJsonArray()) {
            out.error = "'vehicles' missing or not an array";
            return out;
         }

         int index = 0;

         for(JsonElement ve : vehiclesEl.getAsJsonArray()) {
            ++index;
            String where = label + " vehicles[" + index + "]";
            if (!ve.isJsonObject()) {
               out.notes.add(where + ": not an object");
            } else {
               VehicleLightOverrides.Vehicle v = parseVehicle(ve.getAsJsonObject(), where, out.notes);
               if (v != null) {
                  out.vehicles.add(v);
               }
            }
         }
      } catch (Throwable var13) {
         out.error = var13.getClass().getSimpleName() + ": " + var13.getMessage();
         out.vehicles.clear();
      }

      return out;
   }

   private static VehicleLightOverrides.Vehicle parseVehicle(JsonObject o, String where, List<String> notes) {
      JsonElement idEl = o.get("id");
      if (idEl != null && idEl.isJsonPrimitive()) {
         String id = idEl.getAsString().trim();
         if (id.isEmpty()) {
            notes.add(where + ": empty 'id' - skipped");
            return null;
         } else {
            VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle(id, "");
            JsonElement beamsEl = o.get("beams");
            if (beamsEl != null && !beamsEl.isJsonNull()) {
               if (!beamsEl.isJsonArray()) {
                  notes.add(where + " (" + id + "): 'beams' is not an array - entry ignored");
                  return v;
               } else {
                  int i = 0;

                  for(JsonElement be : beamsEl.getAsJsonArray()) {
                     ++i;
                     if (!be.isJsonObject()) {
                        notes.add(where + " (" + id + ") beams[" + i + "]: not an object");
                     } else {
                        VehicleLightOverrides.Beam beam = parseBeam(be.getAsJsonObject(), where + " (" + id + ") beams[" + i + "]", notes);
                        if (beam != null) {
                           v.beams.add(beam);
                        }
                     }
                  }

                  return v;
               }
            } else {
               notes.add(where + " (" + id + "): no 'beams' - entry ignored");
               return v;
            }
         }
      } else {
         notes.add(where + ": missing 'id' - skipped");
         return null;
      }
   }

   private static VehicleLightOverrides.Beam parseBeam(JsonObject o, String where, List<String> notes) {
      VehicleLightOverrides.Beam b = new VehicleLightOverrides.Beam();
      JsonElement typeEl = o.get("type");
      if (typeEl != null && typeEl.isJsonPrimitive()) {
         String type = typeEl.getAsString().trim();
         if (!type.isEmpty()) {
            b.type = type.toLowerCase(Locale.ROOT);
         }
      }

      b.color = color(o.get("color"), where, notes);
      b.pos = vec3(o.get("pos"), "pos", where, notes);
      b.rot = vec3(o.get("rot"), "rot", where, notes);
      b.length = positive(o.get("length"), "length", where, notes);
      b.width = positive(o.get("width"), "width", where, notes);
      b.alpha = alpha(o.get("alpha"), where, notes);
      return b;
   }

   private static Integer color(JsonElement el, String where, List<String> notes) {
      if (el != null && !el.isJsonNull()) {
         try {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
               return el.getAsInt() & 16777215;
            } else {
               String s = el.getAsString().trim();
               String hex = s.startsWith("#") ? s.substring(1) : s;
               if (hex.startsWith("0x") || hex.startsWith("0X")) {
                  hex = hex.substring(2);
               }

               if (hex.length() == 8) {
                  hex = hex.substring(2);
               }

               if (hex.length() != 6) {
                  notes.add(where + ": color \"" + s + "\" is not #RRGGBB - inherited");
                  return null;
               } else {
                  return Integer.parseInt(hex, 16) & 16777215;
               }
            }
         } catch (Throwable var5) {
            notes.add(where + ": color unreadable - inherited");
            return null;
         }
      } else {
         return null;
      }
   }

   private static double[] vec3(JsonElement el, String name, String where, List<String> notes) {
      if (el != null && !el.isJsonNull()) {
         if (el.isJsonArray() && el.getAsJsonArray().size() == 3) {
            JsonArray a = el.getAsJsonArray();
            double[] out = new double[3];

            try {
               for(int i = 0; i < 3; ++i) {
                  out[i] = a.get(i).getAsDouble();
               }

               return out;
            } catch (Throwable var7) {
               notes.add(where + ": '" + name + "' holds a non-number - inherited");
               return null;
            }
         } else {
            notes.add(where + ": '" + name + "' is not a 3-number array - inherited");
            return null;
         }
      } else {
         return null;
      }
   }

   private static Double positive(JsonElement el, String name, String where, List<String> notes) {
      if (el != null && !el.isJsonNull()) {
         try {
            double d = el.getAsDouble();
            if (d > 0.0D && !Double.isInfinite(d) && !Double.isNaN(d)) {
               return d;
            }

            notes.add(where + ": '" + name + "' must be > 0 - inherited");
         } catch (Throwable var6) {
            notes.add(where + ": '" + name + "' is not a number - inherited");
         }

         return null;
      } else {
         return null;
      }
   }

   private static Double alpha(JsonElement el, String where, List<String> notes) {
      if (el != null && !el.isJsonNull()) {
         try {
            double d = el.getAsDouble();
            return Math.max(0.0D, Math.min(1.0D, d));
         } catch (Throwable var5) {
            notes.add(where + ": 'alpha' is not a number - inherited");
            return null;
         }
      } else {
         return null;
      }
   }

   public static String write(List<VehicleLightOverrides.Vehicle> vehicles) {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      JsonArray comment = new JsonArray();

      for(String line : COMMENT) {
         comment.add(line);
      }

      root.add("_comment", comment);
      JsonArray list = new JsonArray();

      for(VehicleLightOverrides.Vehicle v : vehicles) {
         JsonObject vo = new JsonObject();
         vo.addProperty("id", v.id);
         JsonArray beams = new JsonArray();

         for(VehicleLightOverrides.Beam b : v.beams) {
            beams.add(beamJson(b));
         }

         vo.add("beams", beams);
         list.add(vo);
      }

      root.add("vehicles", list);
      return compactNumbers(GSON.toJson(root));
   }

   private static String compactNumbers(String pretty) {
      Matcher m = NUM_ARRAY.matcher(pretty);
      StringBuilder sb = new StringBuilder();

      while(m.find()) {
         String body = m.group(1).replaceAll("[\\s\\n]", "");
         body = body.replaceAll(",+$", "");
         m.appendReplacement(sb, Matcher.quoteReplacement("[" + body + "]"));
      }

      m.appendTail(sb);
      return sb.toString();
   }

   static JsonObject beamJson(VehicleLightOverrides.Beam b) {
      JsonObject o = new JsonObject();
      o.addProperty("type", b.type == null ? "head" : b.type);
      if (b.color != null) {
         o.addProperty("color", String.format(Locale.ROOT, "#%06X", b.color & 16777215));
      }

      if (b.pos != null) {
         o.add("pos", vecJson(b.pos));
      }

      if (b.rot != null) {
         o.add("rot", vecJson(b.rot));
      }

      if (b.length != null) {
         o.addProperty("length", round(b.length));
      }

      if (b.width != null) {
         o.addProperty("width", round(b.width));
      }

      if (b.alpha != null) {
         o.addProperty("alpha", round(b.alpha));
      }

      return o;
   }

   private static JsonArray vecJson(double[] v) {
      JsonArray a = new JsonArray();

      for(double d : v) {
         a.add(round(d));
      }

      return a;
   }

   private static double round(double d) {
      return (double)Math.round(d * 1000.0D) / 1000.0D;
   }

   public static List<VehicleLightJson.Source> scan(Path root, String label, int maxDepth) {
      List<VehicleLightJson.Source> out = new ArrayList();
      if (root != null && Files.isDirectory(root, new LinkOption[0])) {
         List<Path> files = new ArrayList();

         try {
            Stream<Path> walk = Files.walk(root, Math.max(1, maxDepth), new FileVisitOption[0]);

            try {
               walk.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).filter((px) -> "lte_vehicle_lights.json".equalsIgnoreCase(String.valueOf(px.getFileName()))).forEach(files::add);
            } catch (Throwable var10) {
               if (walk != null) {
                  try {
                     walk.close();
                  } catch (Throwable var8) {
                     var10.addSuppressed(var8);
                  }
               }

               throw var10;
            }

            if (walk != null) {
               walk.close();
            }
         } catch (Throwable var11) {
            return out;
         }

         files.sort(Comparator.comparing((px) -> root.relativize(px).toString().replace('\\', '/')));

         for(Path p : files) {
            try {
               String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
               out.add(new VehicleLightJson.Source(label, root.relativize(p).toString().replace('\\', '/'), text));
            } catch (IOException var9) {
            }
         }

         return out;
      } else {
         return out;
      }
   }

   public static final class Result {
      public final List<VehicleLightOverrides.Vehicle> vehicles = new ArrayList();
      public final List<String> notes = new ArrayList();
      public String error = "";

      public boolean ok() {
         return this.error.isEmpty();
      }
   }

   public static final class Source {
      public final String pack;
      public final String path;
      public final String json;

      public Source(String pack, String path, String json) {
         this.pack = pack == null ? "?" : pack;
         this.path = path == null ? "lte_vehicle_lights.json" : path;
         this.json = json == null ? "" : json;
      }

      public String toString() {
         return this.pack + ":" + this.path;
      }
   }
}

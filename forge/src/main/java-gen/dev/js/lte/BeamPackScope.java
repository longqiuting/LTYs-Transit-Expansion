package dev.js.lte;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;

final class BeamPackScope {
   static final String SHARED_FILE = "_shared.json";
   static final String RESOURCES = "assets/mtr/mtr_custom_resources.json";
   private static final String[] DIRS = new String[]{"beam3D", "beam3D".toLowerCase(Locale.ROOT), "lte_beams"};
   private static final Object LOCK = new Object();
   private static List<BeamPackScope> INDEX;
   final PackResources pack;
   final String label;
   private final List<BeamPackScope.Entry> entries = new ArrayList();
   private final Map<String, String> modelIds = new HashMap();
   private BeamPackScope.Shared shared;
   private boolean sharedTried;

   private BeamPackScope(PackResources pack) {
      this.pack = pack;

      String name;
      try {
         name = pack.packId();
      } catch (Throwable var4) {
         name = "pack@" + Integer.toHexString(System.identityHashCode(pack));
      }

      this.label = name != null && !name.isBlank() ? name : "?";
   }

   static List<BeamPackScope> index() {
      synchronized(LOCK) {
         if (INDEX != null) {
            return INDEX;
         }
      }

      List<BeamPackScope> out = of(BeamPackWriter.enabledPacks());
      synchronized(LOCK) {
         INDEX = out;
      }

      return INDEX;
   }

   static List<BeamPackScope> of(List<PackResources> packs) {
      List<BeamPackScope> out = new ArrayList();
      if (packs == null) {
         return Collections.unmodifiableList(out);
      } else {
         for(PackResources pack : packs) {
            BeamPackScope scope = new BeamPackScope(pack);
            scope.load();
            if (!scope.entries.isEmpty()) {
               out.add(scope);
            }
         }

         return Collections.unmodifiableList(out);
      }
   }

   static void invalidate() {
      synchronized(LOCK) {
         INDEX = null;
      }
   }

   void load() {
      String json = readAsset(this.pack, "assets/mtr/mtr_custom_resources.json");
      if (json != null && !json.isBlank()) {
         try {
            JsonElement rootEl = JsonParser.parseString(json);
            if (rootEl == null || !rootEl.isJsonObject()) {
               return;
            }

            JsonElement trainsEl = rootEl.getAsJsonObject().get("custom_trains");
            if (trainsEl == null || !trainsEl.isJsonObject()) {
               return;
            }

            for(Map.Entry<String, JsonElement> e : trainsEl.getAsJsonObject().entrySet()) {
               String key = (String)e.getKey();
               if (key != null && !key.isBlank() && e.getValue() != null && ((JsonElement)e.getValue()).isJsonObject()) {
                  JsonObject train = ((JsonElement)e.getValue()).getAsJsonObject();
                  this.entries.add(new BeamPackScope.Entry(key, str(train, "base_train_type"), str(train, "model_properties")));
               }
            }
         } catch (Throwable var8) {
            LteLog.LOGGER.warn("[lte] beam pack: cannot read custom_trains of {}", this.label, var8);
         }

      }
   }

   int size() {
      return this.entries.size();
   }

   synchronized List<String> keysFor(String id) {
      if (id != null && !id.isBlank() && !this.entries.isEmpty()) {
         String lower = id.trim().toLowerCase(Locale.ROOT);
         List<String> fast = new ArrayList();

         for(BeamPackScope.Entry entry : this.entries) {
            if (matches(entry, lower)) {
               fast.add(entry.key);
            }
         }

         if (!fast.isEmpty()) {
            return fast;
         } else {
            List<String> slow = new ArrayList();

            for(BeamPackScope.Entry entry : this.entries) {
               String generated = this.modelIdOf(entry.key);
               if (generated != null && generated.equals(lower)) {
                  slow.add(entry.key);
               }
            }

            return slow;
         }
      } else {
         return Collections.emptyList();
      }
   }

   private static boolean matches(BeamPackScope.Entry entry, String lower) {
      if (entry.key.toLowerCase(Locale.ROOT).equals(lower)) {
         return true;
      } else if (("mtr_custom_train_" + entry.key).toLowerCase(Locale.ROOT).equals(lower)) {
         return true;
      } else {
         return entry.base != null && entry.base.toLowerCase(Locale.ROOT).equals(lower);
      }
   }

   synchronized String runtimeIdOf(String key) {
      for(BeamPackScope.Entry entry : this.entries) {
         if (entry.key.equals(key)) {
            if (entry.base != null && !entry.base.isBlank()) {
               return entry.base;
            }
            break;
         }
      }

      return this.modelIdOf(key);
   }

   synchronized String modelIdOf(String key) {
      if (this.modelIds.containsKey(key)) {
         return (String)this.modelIds.get(key);
      } else {
         String id = null;

         for(BeamPackScope.Entry entry : this.entries) {
            if (entry.key.equals(key)) {
               if (entry.properties != null && !entry.properties.isBlank()) {
                  id = generatedId(readAsset(this.pack, assetPath(entry.properties)));
               }
               break;
            }
         }

         this.modelIds.put(key, id);
         return id;
      }
   }

   static String generatedId(String propertiesJson) {
      if (propertiesJson != null && !propertiesJson.isBlank()) {
         try {
            JsonElement rootEl = JsonParser.parseString(propertiesJson);
            if (rootEl != null && rootEl.isJsonObject()) {
               JsonObject props = rootEl.getAsJsonObject();
               String mode = str(props, "transport_mode");
               JsonElement length = props.get("length");
               JsonElement width = props.get("width");
               return mode != null && !mode.isBlank() && length != null && width != null ? (mode.toLowerCase(Locale.ROOT) + "_" + length.getAsInt() + "_" + width.getAsInt()).toLowerCase(Locale.ROOT) : null;
            } else {
               return null;
            }
         } catch (Throwable var6) {
            return null;
         }
      } else {
         return null;
      }
   }

   synchronized BeamPackScope.Shared shared() {
      if (this.sharedTried) {
         return this.shared;
      } else {
         this.sharedTried = true;
         Set<String> namespaces = new LinkedHashSet();
         namespaces.add("lte");

         try {
            for(String namespace : this.pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
               if (namespace != null && !namespace.isBlank() && !"lte".equalsIgnoreCase(namespace)) {
                  namespaces.add(namespace);
               }
            }
         } catch (Throwable var11) {
         }

         for(String dir : DIRS) {
            for(String namespace : namespaces) {
               String path = "assets/" + namespace + "/" + dir + "/_shared.json";
               String json = readAsset(this.pack, path);
               if (json != null && !json.isBlank()) {
                  List<VehicleLightOverrides.Beam> beams = BeamPackWriter.parse(json, "_shared.json", this.label + "!" + path);
                  if (beams.isEmpty()) {
                     LteLog.LOGGER.info("[lte] beam pack: {} declares 0 beams", this.label + "!" + path);
                  }

                  this.shared = new BeamPackScope.Shared(this.label + "!" + path, beams);
                  return this.shared;
               }
            }
         }

         return null;
      }
   }

   static String assetPath(String resource) {
      if (resource != null && !resource.isBlank()) {
         int colon = resource.indexOf(58);
         String namespace = colon < 0 ? "mtr" : resource.substring(0, colon);
         String path = colon < 0 ? resource : resource.substring(colon + 1);
         if (!namespace.isBlank() && !path.isBlank()) {
            int slash = path.lastIndexOf(47);
            if (path.lastIndexOf(46) <= slash) {
               path = path + ".json";
            }

            return "assets/" + namespace + "/" + path;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   static String readAsset(PackResources pack, String relative) {
      if (pack != null && relative != null && !relative.isBlank()) {
         String[] segments = relative.replace('\\', '/').replaceFirst("^/+", "").split("/");

         try {
            InputStream in = (InputStream)pack.getRootResource(segments).get();

            String var4;
            try {
               var4 = in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Throwable var7) {
               if (in != null) {
                  try {
                     in.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (in != null) {
               in.close();
            }

            return var4;
         } catch (Throwable var8) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static String str(JsonObject json, String key) {
      JsonElement element = json == null ? null : json.get(key);
      return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
   }

   public String toString() {
      return this.label + " (" + this.entries.size() + " train(s))";
   }

   private static final class Entry {
      final String key;
      final String base;
      final String properties;

      Entry(String key, String base, String properties) {
         this.key = key;
         this.base = base;
         this.properties = properties;
      }
   }

   static final class Shared {
      final String path;
      final List<VehicleLightOverrides.Beam> beams;

      Shared(String path, List<VehicleLightOverrides.Beam> beams) {
         this.path = path;
         this.beams = beams;
      }
   }
}

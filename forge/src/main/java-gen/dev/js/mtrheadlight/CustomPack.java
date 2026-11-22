package dev.js.mtrheadlight;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import mtr.client.DoorAnimationType;
import mtr.client.DynamicTrainModel;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.model.ModelTrainBase;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import dev.js.mtrheadlight.forge.platform.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;

final class CustomPack {
   static final String CANONICAL = "assets/mtr/mtr_custom_resources.json";
   private static final String PACKS_DIR = "lte_packs";
   private static final String BEAM_DIR = "beam3D";
   private static final String LEGACY_BEAM_DIR = "lte_beams";
   private static int ids;
   private final Path root;
   private final boolean zip;
   private final String resources;
   final String label;
   private final Map<String, String> lang = new LinkedHashMap();
   private final List<CustomPack.Train> trains = new ArrayList();
   private String problem = "";

   private CustomPack(Path root, boolean zip, String resources) {
      this.root = root;
      this.zip = zip;
      this.resources = resources;
      this.label = root.getFileName() == null ? root.toString() : root.getFileName().toString();
   }

   static CustomPack open(Path path) throws Exception {
      if (path == null) {
         throw new IllegalArgumentException("path");
      } else {
         Path absolute = path.toAbsolutePath().normalize();
         boolean isZip = Files.isRegularFile(absolute, new LinkOption[0]) && absolute.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
         if (!isZip && !Files.isDirectory(absolute, new LinkOption[0])) {
            throw new IOException("\u627e\u4e0d\u5230\u8d44\u6e90\u5305\uff1a" + String.valueOf(absolute));
         } else {
            String resources = findResources(absolute, isZip);
            if (resources == null) {
               throw new IOException("\u8fd9\u4e2a\u5305\u91cc\u6ca1\u6709 mtr_custom_resources.json\uff08\u5df2\u627e assets/mtr/mtr_custom_resources.json \u4e0e\u4efb\u610f\u5c42\u7684\u540c\u540d\u6587\u4ef6\uff09");
            } else {
               CustomPack pack = new CustomPack(absolute, isZip, resources);
               pack.load();
               if (pack.trains.isEmpty()) {
                  throw new IOException("mtr_custom_resources.json \u91cc\u6ca1\u6709 custom_trains \u6761\u76ee");
               } else {
                  return pack;
               }
            }
         }
      }
   }

   private static String findResources(Path root, boolean zip) {
      if (!zip && Files.isRegularFile(root.resolve("assets/mtr/mtr_custom_resources.json"), new LinkOption[0])) {
         return "assets/mtr/mtr_custom_resources.json";
      } else if (zip) {
         try {
            ZipFile file = new ZipFile(root.toFile());

            String var15;
            label91: {
               String var16;
               try {
                  if (file.getEntry("assets/mtr/mtr_custom_resources.json") != null) {
                     var15 = "assets/mtr/mtr_custom_resources.json";
                     break label91;
                  }

                  ZipEntry best = null;

                  for(ZipEntry entry : Collections.list(file.entries())) {
                     String name = entry.getName();
                     if (!entry.isDirectory() && name.endsWith("/mtr_custom_resources.json") && (best == null || name.length() < best.getName().length())) {
                        best = entry;
                     }
                  }

                  var16 = best == null ? null : best.getName();
               } catch (Throwable var11) {
                  try {
                     file.close();
                  } catch (Throwable var8) {
                     var11.addSuppressed(var8);
                  }

                  throw var11;
               }

               file.close();
               return var16;
            }

            file.close();
            return var15;
         } catch (Throwable var12) {
            return null;
         }
      } else {
         try {
            Stream<Path> walk = Files.walk(root, 4, new FileVisitOption[0]);

            String best;
            try {
               best = (String)walk.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).filter((p) -> p.getFileName() != null && "mtr_custom_resources.json".equals(p.getFileName().toString())).map((p) -> root.relativize(p).toString().replace('\\', '/')).sorted((a, b) -> Integer.compare(a.length(), b.length())).findFirst().orElse((String)null);
            } catch (Throwable var9) {
               if (walk != null) {
                  try {
                     walk.close();
                  } catch (Throwable var7) {
                     var9.addSuppressed(var7);
                  }
               }

               throw var9;
            }

            if (walk != null) {
               walk.close();
            }

            return best;
         } catch (Throwable var10) {
            return null;
         }
      }
   }

   private void load() throws Exception {
      this.loadLang();
      String json = this.readText(this.resources);
      if (json == null) {
         throw new IOException("\u8bfb\u4e0d\u5230 " + this.resources);
      } else {
         JsonElement rootEl = JsonParser.parseString(json);
         if (rootEl != null && rootEl.isJsonObject()) {
            JsonElement trainsEl = rootEl.getAsJsonObject().get("custom_trains");
            if (trainsEl != null && trainsEl.isJsonObject()) {
               for(Map.Entry<String, JsonElement> entry : trainsEl.getAsJsonObject().entrySet()) {
                  String key = (String)entry.getKey();
                  if (key != null && !key.isBlank() && entry.getValue() != null && ((JsonElement)entry.getValue()).isJsonObject()) {
                     JsonObject train = ((JsonElement)entry.getValue()).getAsJsonObject();
                     this.trains.add(new CustomPack.Train(this, key, train));
                  }
               }

               this.trains.sort((a, b) -> a.id.compareToIgnoreCase(b.id));
            }
         } else {
            throw new IOException(this.resources + " \u4e0d\u662f JSON \u5bf9\u8c61");
         }
      }
   }

   byte[] readBytes(String relative) {
      if (relative != null && !relative.isBlank()) {
         String clean = relative.replace('\\', '/').replaceFirst("^/+", "");
         if (this.zip) {
            try {
               ZipFile file = new ZipFile(this.root.toFile());

               Object var15;
               label77: {
                  byte[] var6;
                  try {
                     ZipEntry entry = file.getEntry(clean);
                     if (entry == null) {
                        entry = file.getEntry("/" + clean);
                     }

                     if (entry == null) {
                        var15 = null;
                        break label77;
                     }

                     InputStream in = file.getInputStream(entry);

                     try {
                        var6 = in.readAllBytes();
                     } catch (Throwable var11) {
                        if (in != null) {
                           try {
                              in.close();
                           } catch (Throwable var9) {
                              var11.addSuppressed(var9);
                           }
                        }

                        throw var11;
                     }

                     if (in != null) {
                        in.close();
                     }
                  } catch (Throwable var12) {
                     try {
                        file.close();
                     } catch (Throwable var8) {
                        var12.addSuppressed(var8);
                     }

                     throw var12;
                  }

                  file.close();
                  return var6;
               }

               file.close();
               return (byte[])var15;
            } catch (Throwable var13) {
               LteLog.LOGGER.warn("[lte] import: cannot read {} from {}", new Object[]{clean, this.root, var13});
               return null;
            }
         } else {
            try {
               Path path = this.root.resolve(clean).normalize();
               return path.startsWith(this.root) && Files.isRegularFile(path, new LinkOption[0]) ? Files.readAllBytes(path) : null;
            } catch (Throwable var10) {
               LteLog.LOGGER.warn("[lte] import: cannot read {} from {}", new Object[]{clean, this.root, var10});
               return null;
            }
         }
      } else {
         return null;
      }
   }

   String readText(String relative) {
      byte[] bytes = this.readBytes(relative);
      return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
   }

   static String toAssetPath(String resource) {
      if (resource != null && !resource.isBlank()) {
         int colon = resource.indexOf(58);
         String namespace = colon < 0 ? "mtr" : resource.substring(0, colon);
         String path = colon < 0 ? resource : resource.substring(colon + 1);
         return !namespace.isBlank() && !path.isBlank() ? "assets/" + namespace + "/" + path : null;
      } else {
         return null;
      }
   }

   static String namespaceOf(String resource) {
      if (resource != null && !resource.isBlank()) {
         int colon = resource.indexOf(58);
         return colon <= 0 ? null : resource.substring(0, colon);
      } else {
         return null;
      }
   }

   List<CustomPack.Train> trains() {
      return this.trains;
   }

   CustomPack.Train train(String id) {
      if (id == null) {
         return null;
      } else {
         for(CustomPack.Train train : this.trains) {
            if (train.id.equalsIgnoreCase(id)) {
               return train;
            }
         }

         return null;
      }
   }

   String problem() {
      return this.problem;
   }

   private void loadLang() {
      LinkedHashSet<String> namespaces = new LinkedHashSet();
      namespaces.add("mtr");

      try {
         if (this.zip) {
            ZipFile file = new ZipFile(this.root.toFile());

            try {
               for(ZipEntry entry : Collections.list(file.entries())) {
                  String name = entry.getName();
                  if (!entry.isDirectory() && name.startsWith("assets/") && name.endsWith(".json")) {
                     int at = name.indexOf("/lang/");
                     if (at > 7) {
                        namespaces.add(name.substring(7, at));
                     }
                  }
               }
            } catch (Throwable var10) {
               try {
                  file.close();
               } catch (Throwable var8) {
                  var10.addSuppressed(var8);
               }

               throw var10;
            }

            file.close();
         } else {
            Stream<Path> walk = Files.walk(this.root, 5, new FileVisitOption[0]);

            try {
               walk.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).forEach((path) -> {
                  String name = this.root.relativize(path).toString().replace('\\', '/');
                  if (name.startsWith("assets/") && name.endsWith(".json")) {
                     int at = name.indexOf("/lang/");
                     if (at > 7) {
                        namespaces.add(name.substring(7, at));
                     }

                  }
               });
            } catch (Throwable var9) {
               if (walk != null) {
                  try {
                     walk.close();
                  } catch (Throwable var7) {
                     var9.addSuppressed(var7);
                  }
               }

               throw var9;
            }

            if (walk != null) {
               walk.close();
            }
         }
      } catch (Throwable var11) {
         LteLog.LOGGER.warn("[lte] import: cannot scan lang files in {}", this.root, var11);
      }

      for(String namespace : namespaces) {
         this.readLang(namespace, "zh_cn");
         this.readLang(namespace, "en_us");
      }

   }

   private void readLang(String namespace, String locale) {
      String json = this.readText("assets/" + namespace + "/lang/" + locale + ".json");
      if (json != null) {
         try {
            JsonElement rootEl = JsonParser.parseString(json);
            if (rootEl == null || !rootEl.isJsonObject()) {
               return;
            }

            for(Map.Entry<String, JsonElement> entry : rootEl.getAsJsonObject().entrySet()) {
               JsonElement value = (JsonElement)entry.getValue();
               if (value != null && value.isJsonPrimitive()) {
                  this.lang.putIfAbsent((String)entry.getKey(), value.getAsString());
               }
            }
         } catch (Throwable var8) {
            LteLog.LOGGER.warn("[lte] import: cannot parse lang {}/{}", namespace, locale);
         }

      }
   }

   String resolveName(String raw) {
      if (raw != null && !raw.isBlank()) {
         String fromPack = (String)this.lang.get(raw);
         return fromPack != null && !fromPack.isBlank() ? fromPack : TrainNames.resolveRaw(raw);
      } else {
         return raw;
      }
   }

   void release() {
      for(CustomPack.Train train : this.trains) {
         train.release();
      }

   }

   CustomPack.Export writeBeams(CustomPack.Train train, List<VehicleLightOverrides.Beam> beams) throws Exception {
      String json = BeamPackWriter.toJson(train.jsonKey, beams);
      if (!this.zip) {
         Path path = this.root.resolve(train.beamPath()).normalize();
         Files.createDirectories(path.getParent());
         Files.writeString(path, json, StandardCharsets.UTF_8);
         return new CustomPack.Export(path, true, train.beamPath(), beams.size());
      } else {
         Path path = BeamPackWriter.write(train.id, beams);
         return new CustomPack.Export(path, false, train.beamPath(), beams.size());
      }
   }

   List<VehicleLightOverrides.Beam> readBeams(CustomPack.Train train) {
      String json = this.readText(train.beamPath());
      String source = train.beamPath();
      if (json == null || json.isBlank()) {
         json = this.readText(train.legacyBeamPath());
         source = train.legacyBeamPath();
      }

      if (json == null || json.isBlank()) {
         for(String candidate : this.sharedCandidates()) {
            String shared = this.readText(candidate);
            if (shared != null && !shared.isBlank()) {
               json = shared;
               source = candidate;
               break;
            }
         }
      }

      if (json != null && !json.isBlank()) {
         train.loadedFrom = source;
         return BeamPackWriter.parse(json, train.jsonKey, String.valueOf(this.root) + "!" + source);
      } else {
         return List.of();
      }
   }

   List<String> sharedCandidates() {
      LinkedHashSet<String> namespaces = new LinkedHashSet();
      namespaces.add("lte");

      for(CustomPack.Train train : this.trains) {
         namespaces.add(train.namespace);
      }

      namespaces.add("mtr");
      List<String> out = new ArrayList();

      for(String namespace : namespaces) {
         for(String dir : new String[]{"beam3D", "lte_beams"}) {
            out.add("assets/" + namespace + "/" + dir + "/_shared.json");
         }
      }

      return out;
   }

   static List<Path> candidates() {
      List<Path> out = new ArrayList();
      Map<String, Path> seen = new LinkedHashMap();

      try {
         FabricLoader loader = FabricLoader.getInstance();
         scan(loader.getGameDir().resolve("resourcepacks"), out, seen, 2);
         scan(loader.getConfigDir().resolve("lte_packs"), out, seen, 2);
      } catch (Throwable var3) {
         LteLog.LOGGER.warn("[lte] import: cannot scan for packs", var3);
      }

      return out;
   }

   private static void scan(Path dir, List<Path> out, Map<String, Path> seen, int depth) {
      if (dir != null && depth >= 0 && Files.isDirectory(dir, new LinkOption[0])) {
         try {
            Stream<Path> list = Files.list(dir);

            try {
               list.sorted().forEach((path) -> {
                  String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                  if (Files.isDirectory(path, new LinkOption[0])) {
                     if (!Files.isRegularFile(path.resolve("assets/mtr/mtr_custom_resources.json"), new LinkOption[0]) && !hasNested(path)) {
                        scan(path, out, seen, depth - 1);
                     } else {
                        add(out, seen, path);
                     }
                  } else if (name.endsWith(".zip") && zipHasResources(path)) {
                     add(out, seen, path);
                  }

               });
            } catch (Throwable var8) {
               if (list != null) {
                  try {
                     list.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (list != null) {
               list.close();
            }
         } catch (Throwable var9) {
         }

      }
   }

   private static void add(List<Path> out, Map<String, Path> seen, Path path) {
      String key = path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
      if (seen.put(key, path) == null) {
         out.add(path);
      }

   }

   private static boolean hasNested(Path dir) {
      try {
         Stream<Path> walk = Files.walk(dir, 4, new FileVisitOption[0]);

         boolean var2;
         try {
            var2 = walk.anyMatch((p) -> Files.isRegularFile(p, new LinkOption[0]) && p.getFileName() != null && "mtr_custom_resources.json".equals(p.getFileName().toString()));
         } catch (Throwable var5) {
            if (walk != null) {
               try {
                  walk.close();
               } catch (Throwable var4) {
                  var5.addSuppressed(var4);
               }
            }

            throw var5;
         }

         if (walk != null) {
            walk.close();
         }

         return var2;
      } catch (Throwable var6) {
         return false;
      }
   }

   private static boolean zipHasResources(Path zip) {
      try {
         ZipFile file = new ZipFile(zip.toFile());

         label40: {
            boolean var4;
            try {
               Iterator var2 = Collections.list(file.entries()).iterator();

               ZipEntry entry;
               do {
                  if (!var2.hasNext()) {
                     break label40;
                  }

                  entry = (ZipEntry)var2.next();
               } while(entry.isDirectory() || !entry.getName().endsWith("mtr_custom_resources.json"));

               var4 = true;
            } catch (Throwable var6) {
               try {
                  file.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }

               throw var6;
            }

            file.close();
            return var4;
         }

         file.close();
      } catch (Throwable var7) {
      }

      return false;
   }

   public String toString() {
      return "CustomPack{" + String.valueOf(this.root) + (this.zip ? " (zip)" : " (dir)") + ", trains=" + this.trains.size() + "}";
   }

   static final class Export {
      final Path path;
      final boolean insidePack;
      final String packRelative;
      final int beams;

      Export(Path path, boolean insidePack, String packRelative, int beams) {
         this.path = path;
         this.insidePack = insidePack;
         this.packRelative = packRelative;
         this.beams = beams;
      }
   }

   static final class Train {
      final CustomPack pack;
      final String jsonKey;
      final String id;
      final String name;
      final DoorAnimationType doorAnimationType;
      final String baseTrainType;
      final String textureId;
      final String model;
      final String modelProperties;
      final String namespace;
      String loadedFrom;
      private ModelTrainBase model_;
      private DynamicTrainModel dynamic;
      private boolean modelTried;
      private ResourceLocation texture;
      private boolean textureTried;
      private String error = "";

      Train(CustomPack pack, String jsonKey, JsonObject json) {
         this.pack = pack;
         this.jsonKey = jsonKey;
         this.id = "mtr_custom_train_" + jsonKey;
         this.name = pack.resolveName(str(json, "name", jsonKey));
         this.doorAnimationType = doorAnimation(str(json, "door_animation_type", ""));
         this.baseTrainType = str(json, "base_train_type", "");
         this.textureId = str(json, "texture_id", "");
         this.model = str(json, "model", "");
         this.modelProperties = str(json, "model_properties", "");
         this.namespace = preferNamespace(CustomPack.namespaceOf(this.textureId), CustomPack.namespaceOf(this.model), CustomPack.namespaceOf(this.modelProperties));
      }

      private static String preferNamespace(String... candidates) {
         for(String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && !"minecraft".equalsIgnoreCase(candidate)) {
               return candidate;
            }
         }

         return "mtr";
      }

      private static DoorAnimationType doorAnimation(String raw) {
         if (raw != null && !raw.isBlank()) {
            try {
               return DoorAnimationType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Throwable var2) {
            }
         }

         return DoorAnimationType.STANDARD;
      }

      private static String str(JsonObject json, String key, String fallback) {
         JsonElement element = json.get(key);
         return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
      }

      private static String withExtension(String path, String defaultExtension) {
         if (path == null) {
            return null;
         } else {
            int slash = path.lastIndexOf(47);
            int dot = path.lastIndexOf(46);
            return dot > slash ? path : path + defaultExtension;
         }
      }

      String modelPath() {
         return withExtension(CustomPack.toAssetPath(this.model), ".json");
      }

      String propertiesPath() {
         return withExtension(CustomPack.toAssetPath(this.modelProperties), ".json");
      }

      String texturePath() {
         return withExtension(CustomPack.toAssetPath(this.textureId), ".png");
      }

      ModelTrainBase model() {
         this.resolve();
         return this.model_;
      }

      DynamicTrainModel dynamic() {
         this.resolve();
         return this.dynamic;
      }

      private void resolve() {
         if (!this.modelTried) {
            this.modelTried = true;
            if (!this.model.isBlank() && !this.modelProperties.isBlank()) {
               try {
                  String modelJson = this.pack.readText(this.modelPath());
                  String propsJson = this.pack.readText(this.propertiesPath());
                  if (modelJson == null || propsJson == null) {
                     this.error = "model json missing (" + this.modelPath() + ")";
                     this.resolveBaseModel();
                     return;
                  }

                  DynamicTrainModel built = new DynamicTrainModel(JsonParser.parseString(modelJson).getAsJsonObject(), JsonParser.parseString(propsJson).getAsJsonObject(), this.doorAnimationType);
                  this.model_ = built;
                  this.dynamic = built;
               } catch (Throwable var4) {
                  this.error = var4.getClass().getSimpleName() + ": " + var4.getMessage();
                  LteLog.LOGGER.warn("[lte] import: cannot build model for {}", this.id, var4);
                  this.resolveBaseModel();
               }

            } else {
               this.resolveBaseModel();
            }
         }
      }

      private void resolveBaseModel() {
         if (this.baseTrainType != null && !this.baseTrainType.isBlank()) {
            try {
               TrainProperties base = TrainClientRegistry.getTrainProperties(this.baseTrainType);
               if (base != null) {
                  TrainRendererBase model = base.renderer;
                  if (model instanceof JonModelTrainRenderer) {
                     JonModelTrainRenderer jon = (JonModelTrainRenderer)model;
                     if (jon.model != null) {
                        this.model_ = jon.model;
                        ModelTrainBase var4 = jon.model;
                        DynamicTrainModel var10001;
                        if (var4 instanceof DynamicTrainModel) {
                           DynamicTrainModel modelLte = (DynamicTrainModel)var4;
                           var10001 = modelLte;
                        } else {
                           var10001 = null;
                        }

                        this.dynamic = var10001;
                        LteLog.LOGGER.info("[lte] import: {} uses the base model of {}", this.id, this.baseTrainType);
                        return;
                     }
                  }
               }

               this.error = "base_train_type " + this.baseTrainType + " \u6ca1\u6709\u53ef\u7528\u6a21\u578b";
            } catch (Throwable var5) {
               this.error = "base model: " + var5.getClass().getSimpleName();
            }

         } else {
            if (this.error.isEmpty()) {
               this.error = "no model/model_properties and no base_train_type";
            }

         }
      }

      ResourceLocation texture() {
         if (this.textureTried) {
            return this.texture;
         } else {
            this.textureTried = true;
            String path = this.texturePath();
            if (path == null) {
               return null;
            } else {
               byte[] png = this.pack.readBytes(path);
               if (png != null && png.length != 0) {
                  try {
                     Minecraft client = Minecraft.getInstance();
                     TextureManager manager = client == null ? null : client.getTextureManager();
                     if (manager == null) {
                        return null;
                     }

                     ResourceLocation id = new ResourceLocation("lte", "import/" + CustomPack.ids++ + "_" + Integer.toHexString(path.hashCode()));
                     NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
                     manager.register(id, new DynamicTexture(image));
                     this.texture = id;
                  } catch (Throwable var7) {
                     this.error = this.error.isEmpty() ? "texture: " + String.valueOf(var7) : this.error;
                     LteLog.LOGGER.warn("[lte] import: cannot load texture {} for {}", new Object[]{path, this.id, var7});
                  }

                  return this.texture;
               } else {
                  this.error = this.error.isEmpty() ? "texture missing (" + path + ")" : this.error;
                  return null;
               }
            }
         }
      }

      void release() {
         if (this.texture != null) {
            try {
               Minecraft client = Minecraft.getInstance();
               if (client != null) {
                  client.getTextureManager().release(this.texture);
               }
            } catch (Throwable var2) {
            }

            this.texture = null;
            this.textureTried = false;
         }
      }

      String error() {
         return this.error;
      }

      String beamPath() {
         return "assets/" + this.namespace + "/beam3D/" + BeamPackWriter.fileName(this.jsonKey);
      }

      String legacyBeamPath() {
         return "assets/" + this.namespace + "/lte_beams/" + BeamPackWriter.fileName(this.jsonKey);
      }

      String label() {
         return this.name.equalsIgnoreCase(this.id) ? this.name : this.name + " \u00b7 " + this.id;
      }
   }
}

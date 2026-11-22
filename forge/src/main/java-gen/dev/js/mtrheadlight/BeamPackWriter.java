package dev.js.mtrheadlight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import dev.js.mtrheadlight.forge.platform.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.IoSupplier;

final class BeamPackWriter {
   static final String CONFIG_DIR = "lte_beams";
   static final String PACK_DIR = "beam3D";
   static final String PACK_NAMESPACE = "lte";
   static final String LEGACY_PACK_DIR = "lte_beams";
   private static final String[] BEAM_DIRS = new String[]{"beam3D", "lte_beams"};
   static final String ID_PREFIX = "mtr_custom_train_";
   private static final int FORMAT_VERSION = 1;
   private static final AtomicBoolean DISABLED_PACK_CHECK = new AtomicBoolean();
   private static final Object PACK_LOCK = new Object();
   private static List<PackResources> PACK_CACHE;
   private static final String[] COMMENT = new String[]{"LTE 3D beam editor export - one vehicle per file.", "The file name is the custom_trains key (no mtr_custom_train_ prefix).", "Place it at assets/lte/beam3D/<key>.json inside a resource pack", "(the older assets/<namespace>/lte_beams/<key>.json is still read).", "Beam fields map 1:1 to the runtime BeamGeom inputs:", "  color -> rgb, pos -> origin (car-local model space), rot -> axis,", "  length/width in blocks, alpha 0..1.", "pos is [lateral, up, along] in the model's own axes (a model whose length axis is X", "uses pos[0] for the along component). rot is degrees [yaw, pitch, roll]."};
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();
   private static final Pattern NUM_ARRAY = Pattern.compile("\\[\\s*\\n((?:\\s*-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?,?\\s*\\n)+)\\s*\\]");
   private static final Object LOG_LOCK = new Object();
   private static final Map<String, String> HITS = new LinkedHashMap(64, 0.75F, true);
   private static final Set<String> MISSES = new LinkedHashSet();

   private BeamPackWriter() {
   }

   static String keyOf(String vehicleId) {
      String raw = vehicleId == null ? "" : vehicleId.trim();
      if (raw.regionMatches(true, 0, "mtr_custom_train_", 0, "mtr_custom_train_".length())) {
         raw = raw.substring("mtr_custom_train_".length());
      }

      return raw;
   }

   static String fileName(String vehicleId) {
      String key = keyOf(vehicleId);
      String safe = key.replaceAll("[^A-Za-z0-9._-]", "_");
      return (safe.isEmpty() ? "unknown" : safe) + ".json";
   }

   static List<String> fileNames(String vehicleId) {
      Set<String> out = new LinkedHashSet();
      String key = keyOf(vehicleId);
      String rawId = vehicleId == null ? "" : vehicleId.trim();

      for(String base : new String[]{key, rawId, "mtr_custom_train_" + key}) {
         if (base != null && !base.isEmpty()) {
            for(String variant : new String[]{base, base.toLowerCase(Locale.ROOT)}) {
               String safe = variant.replaceAll("[^A-Za-z0-9._-]", "_");
               if (!safe.isEmpty()) {
                  out.add(safe + ".json");
               }
            }
         }
      }

      if (out.isEmpty()) {
         out.add("unknown.json");
      }

      return new ArrayList(out);
   }

   static Path configDir() {
      return FabricLoader.getInstance().getConfigDir().resolve("lte_beams");
   }

   static Path exportDir() {
      try {
         String home = System.getProperty("user.home");
         if (home != null && !home.isBlank()) {
            Path desktop = Paths.get(home + "\\Desktop");
            if (Files.isDirectory(desktop, new LinkOption[0]) && Files.isWritable(desktop)) {
               return desktop;
            }
         }
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] beam editor: desktop unavailable, falling back to config", var2);
      }

      return configDir();
   }

   static Path dir() {
      return exportDir();
   }

   static Path pathFor(String vehicleId) {
      return exportDir().resolve(fileName(vehicleId));
   }

   static Path write(String vehicleId, List<VehicleLightOverrides.Beam> beams) throws Exception {
      Path path = pathFor(vehicleId);
      Files.createDirectories(path.getParent());
      Files.writeString(path, toJson(keyOf(vehicleId), beams), StandardCharsets.UTF_8);
      return path;
   }

   static String toJson(String vehicleId, List<VehicleLightOverrides.Beam> beams) {
      JsonObject root = new JsonObject();
      root.addProperty("version", 1);
      JsonArray comment = new JsonArray();

      for(String line : COMMENT) {
         comment.add(line);
      }

      root.add("_comment", comment);
      root.addProperty("id", vehicleId == null ? "" : vehicleId);
      JsonArray array = new JsonArray();

      for(VehicleLightOverrides.Beam beam : beams) {
         if (beam != null) {
            array.add(VehicleLightJson.beamJson(beam));
         }
      }

      root.add("beams", array);
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

   static List<VehicleLightOverrides.Beam> read(String vehicleId) {
      return read(vehicleId);
   }

   static List<VehicleLightOverrides.Beam> read(String vehicleId, String... aliases) {
      BeamPackWriter.Plan plan = BeamPackWriter.Plan.of(vehicleId, aliases);
      if (plan.isEmpty()) {
         if (!plan.id.isEmpty()) {
            logMiss(plan);
         }

         return List.of();
      } else {
         Map<String, Path> desk = dirIndex(exportDir());

         for(BeamPackWriter.Candidate candidate : plan.candidates) {
            Path path = findIn(desk, candidate.names);
            if (path != null) {
               List<VehicleLightOverrides.Beam> beams = readFile(path, plan.id);
               if (beams != null) {
                  logHit(plan.id, path.toString(), beams.size(), candidate.source);
                  return beams;
               }
            }
         }

         for(BeamPackWriter.Candidate candidate : plan.candidates) {
            BeamPackWriter.Hit hit = scanPacks(plan.id, candidate);
            if (hit != null) {
               logHit(plan.id, hit.path, hit.beams.size(), candidate.source);
               return hit.beams;
            }
         }

         BeamPackScope.Shared shared = sharedFor(plan);
         if (shared != null && !shared.beams.isEmpty()) {
            List<VehicleLightOverrides.Beam> copy = new ArrayList(shared.beams.size());

            for(VehicleLightOverrides.Beam beam : shared.beams) {
               copy.add(beam.copy());
            }

            logHit(plan.id, shared.path, copy.size(), "shared");
            return copy;
         } else {
            if (!sameDir(configDir(), exportDir())) {
               Map<String, Path> legacy = dirIndex(configDir());

               for(BeamPackWriter.Candidate candidate : plan.candidates) {
                  Path path = findIn(legacy, candidate.names);
                  if (path != null) {
                     List<VehicleLightOverrides.Beam> beams = readFile(path, plan.id);
                     if (beams != null) {
                        logHit(plan.id, path.toString(), beams.size(), "config");
                        return beams;
                     }
                  }
               }
            }

            logMiss(plan);
            return List.of();
         }
      }
   }

   private static BeamPackScope.Shared sharedFor(BeamPackWriter.Plan plan) {
      if (plan.owners.isEmpty()) {
         return null;
      } else {
         BeamPackScope.Shared best = null;

         for(BeamPackScope scope : BeamPackScope.index()) {
            if (plan.owners.contains(scope.pack)) {
               BeamPackScope.Shared shared = scope.shared();
               if (shared != null) {
                  best = shared;
               }
            }
         }

         return best;
      }
   }

   private static void logMiss(BeamPackWriter.Plan plan) {
      String key = plan.id == null ? "" : plan.id.toLowerCase(Locale.ROOT);
      synchronized(LOG_LOCK) {
         if (!MISSES.add(key)) {
            return;
         }

         if (MISSES.size() > 512) {
            MISSES.clear();
         }
      }

      LteLog.LOGGER.info("[lte] beam pack: miss id={} tried={}", plan.id, plan.tried);
      warnIfPackPresentButDisabled();
   }

   private static void logHit(String id, String path, int beams, String source) {
      String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
      String signature = path + "|" + beams + "|" + source;
      synchronized(LOG_LOCK) {
         if (signature.equals(HITS.get(key))) {
            return;
         }

         if (HITS.size() > 512) {
            HITS.clear();
         }

         HITS.put(key, signature);
      }

      LteLog.LOGGER.info("[lte] beam pack: {} id={} beams={} source={}", new Object[]{path, id, beams, source});
   }

   private static int enabledProfileCount() {
      try {
         Minecraft client = Minecraft.getInstance();
         return client != null && client.getResourcePackRepository() != null ? client.getResourcePackRepository().getSelectedPacks().size() : 0;
      } catch (Throwable var1) {
         return 0;
      }
   }

   static List<String> candidateIds(String vehicleId, String... aliases) {
      Set<String> lowered = new LinkedHashSet();
      List<String> out = new ArrayList();
      List<String> raw = new ArrayList();
      if (vehicleId != null && !vehicleId.isBlank()) {
         raw.add(vehicleId.trim());
      }

      if (aliases != null) {
         for(String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
               raw.add(alias.trim());
            }
         }
      }

      for(String id : raw) {
         for(String form : new String[]{keyOf(id), id, "mtr_custom_train_" + keyOf(id)}) {
            if (form != null && !form.isBlank() && lowered.add(form.toLowerCase(Locale.ROOT))) {
               out.add(form);
            }
         }
      }

      return out;
   }

   static List<String> namesFor(List<String> ids) {
      Set<String> lowered = new LinkedHashSet();
      List<String> out = new ArrayList();

      for(String id : ids) {
         for(String name : fileNames(id)) {
            if (lowered.add(name.toLowerCase(Locale.ROOT))) {
               out.add(name);
            }
         }
      }

      return out;
   }

   private static boolean sameDir(Path a, Path b) {
      try {
         return a != null && b != null && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
      } catch (Throwable var3) {
         return false;
      }
   }

   static Path locate(Path dir, List<String> names) {
      if (dir != null && names != null && !names.isEmpty() && Files.isDirectory(dir, new LinkOption[0])) {
         for(String name : names) {
            Path direct = dir.resolve(name);
            if (Files.isRegularFile(direct, new LinkOption[0])) {
               return direct;
            }
         }

         Set<String> lowered = new LinkedHashSet();

         for(String name : names) {
            lowered.add(name.toLowerCase(Locale.ROOT));
         }

         try {
            Stream<Path> list = Files.list(dir);

            Path var13;
            try {
               var13 = (Path)list.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).filter((p) -> p.getFileName() != null && lowered.contains(p.getFileName().toString().toLowerCase(Locale.ROOT))).findFirst().orElse((Path)null);
            } catch (Throwable var7) {
               if (list != null) {
                  try {
                     list.close();
                  } catch (Throwable var6) {
                     var7.addSuppressed(var6);
                  }
               }

               throw var7;
            }

            if (list != null) {
               list.close();
            }

            return var13;
         } catch (Throwable var8) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static List<VehicleLightOverrides.Beam> readFile(Path path, String vehicleId) {
      if (path != null && Files.isRegularFile(path, new LinkOption[0])) {
         try {
            return parse(Files.readString(path, StandardCharsets.UTF_8), keyOf(vehicleId), path.toString());
         } catch (Throwable var3) {
            LteLog.LOGGER.warn("[lte] beam editor: cannot read {}", path, var3);
            return null;
         }
      } else {
         return null;
      }
   }

   private static BeamPackWriter.Hit scanPacks(String vehicleId, BeamPackWriter.Candidate candidate) {
      List<PackResources> packs = candidate.packs.isEmpty() ? enabledPacks() : candidate.packs;
      if (!packs.isEmpty() && !candidate.names.isEmpty()) {
         for(String dir : BEAM_DIRS) {
            for(boolean onlyDefault : new boolean[]{true, false}) {
               for(int i = packs.size() - 1; i >= 0; --i) {
                  PackResources pack = (PackResources)packs.get(i);

                  for(String namespace : namespacesOf(pack, onlyDefault)) {
                     for(String dirName : new String[]{dir, dir.toLowerCase(Locale.ROOT)}) {
                        for(String name : candidate.names) {
                           String relative = "assets/" + namespace + "/" + dirName + "/" + name;
                           String json = BeamPackScope.readAsset(pack, relative);
                           if (json != null && !json.isBlank()) {
                              String path = pack.packId() + "!" + relative;
                              return new BeamPackWriter.Hit(path, parse(json, keyOf(vehicleId), path));
                           }
                        }
                     }
                  }
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static Map<String, Path> dirIndex(Path dir) {
      Map<String, Path> out = new LinkedHashMap();
      if (dir != null && Files.isDirectory(dir, new LinkOption[0])) {
         try {
            Stream<Path> list = Files.list(dir);

            try {
               list.filter((x$0) -> Files.isRegularFile(x$0, new LinkOption[0])).forEach((path) -> {
                  if (path.getFileName() != null) {
                     out.put(path.getFileName().toString().toLowerCase(Locale.ROOT), path);
                  }

               });
            } catch (Throwable var6) {
               if (list != null) {
                  try {
                     list.close();
                  } catch (Throwable var5) {
                     var6.addSuppressed(var5);
                  }
               }

               throw var6;
            }

            if (list != null) {
               list.close();
            }
         } catch (Throwable var7) {
         }

         return out;
      } else {
         return out;
      }
   }

   private static Path findIn(Map<String, Path> index, List<String> names) {
      if (!index.isEmpty() && names != null) {
         for(String name : names) {
            Path path = (Path)index.get(name.toLowerCase(Locale.ROOT));
            if (path != null) {
               return path;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private static void warnIfPackPresentButDisabled() {
      if (DISABLED_PACK_CHECK.compareAndSet(false, true)) {
         Set<String> enabled = new LinkedHashSet();

         try {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getResourcePackRepository() != null) {
               for(Pack profile : client.getResourcePackRepository().getSelectedPacks()) {
                  enabled.add(profile.getId());
               }
            }
         } catch (Throwable var5) {
         }

         Thread thread = new Thread(() -> scanDisabledPacks(enabled), "lte-beam-pack-scan");
         thread.setDaemon(true);

         try {
            thread.start();
         } catch (Throwable var4) {
            scanDisabledPacks(enabled);
         }

      }
   }

   private static void scanDisabledPacks(Set<String> enabled) {
      try {
         Path root = FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
         if (!Files.isDirectory(root, new LinkOption[0])) {
            return;
         }

         List<Path> packs = new ArrayList();
         Stream<Path> list = Files.list(root);

         try {
            list.forEach((p) -> {
               if (Files.isDirectory(p, new LinkOption[0]) || isZip(p)) {
                  packs.add(p);
               }

            });
         } catch (Throwable var7) {
            if (list != null) {
               try {
                  list.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (list != null) {
            list.close();
         }

         for(Path pack : packs) {
            String label = "file/" + String.valueOf(pack.getFileName());
            if (!enabled.contains(label) && containsBeamFile(pack)) {
               LteLog.LOGGER.warn("[lte] beam pack: {} contains beam3D files but is NOT enabled in the resource pack list (\u542f\u7528\u5b83\uff0c\u6216\u628a\u6587\u4ef6\u653e\u8fdb\u5df2\u542f\u7528\u7684\u5305)", label);
            }
         }
      } catch (Throwable var8) {
         LteLog.LOGGER.debug("[lte] beam pack: cannot check disabled packs", var8);
      }

   }

   private static boolean isZip(Path path) {
      String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
      return name.endsWith(".zip");
   }

   private static boolean containsBeamFile(Path pack) {
      try {
         if (!Files.isDirectory(pack, new LinkOption[0])) {
            ZipFile zip = new ZipFile(pack.toFile());

            boolean var11;
            try {
               var11 = zip.stream().anyMatch((e) -> {
                  String name = e.getName().toLowerCase(Locale.ROOT);
                  return name.startsWith("assets/") && (name.contains("/beam3d/") || name.contains("/lte_beams/")) && name.endsWith(".json");
               });
            } catch (Throwable var7) {
               try {
                  zip.close();
               } catch (Throwable var5) {
                  var7.addSuppressed(var5);
               }

               throw var7;
            }

            zip.close();
            return var11;
         } else {
            Path assets = pack.resolve("assets");
            if (!Files.isDirectory(assets, new LinkOption[0])) {
               return false;
            } else {
               Stream<Path> namespaces = Files.list(assets);

               boolean var3;
               try {
                  var3 = namespaces.anyMatch((ns) -> hasBeamDir(ns.resolve("beam3D")) || hasBeamDir(ns.resolve("beam3D".toLowerCase(Locale.ROOT))) || hasBeamDir(ns.resolve("lte_beams")));
               } catch (Throwable var8) {
                  if (namespaces != null) {
                     try {
                        namespaces.close();
                     } catch (Throwable var6) {
                        var8.addSuppressed(var6);
                     }
                  }

                  throw var8;
               }

               if (namespaces != null) {
                  namespaces.close();
               }

               return var3;
            }
         }
      } catch (Throwable var9) {
         return false;
      }
   }

   private static boolean hasBeamDir(Path dir) {
      try {
         if (!Files.isDirectory(dir, new LinkOption[0])) {
            return false;
         } else {
            Stream<Path> list = Files.list(dir);

            boolean var2;
            try {
               var2 = list.anyMatch((p) -> Files.isRegularFile(p, new LinkOption[0]) && p.getFileName() != null && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
            } catch (Throwable var5) {
               if (list != null) {
                  try {
                     list.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (list != null) {
               list.close();
            }

            return var2;
         }
      } catch (Throwable var6) {
         return false;
      }
   }

   private static Set<String> namespacesOf(PackResources pack, boolean onlyDefault) {
      Set<String> out = new LinkedHashSet();
      if (onlyDefault) {
         out.add("lte");
         return out;
      } else {
         try {
            List<String> rest = new ArrayList();

            for(String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
               if (namespace != null && !namespace.isBlank() && !"lte".equalsIgnoreCase(namespace)) {
                  rest.add(namespace.toLowerCase(Locale.ROOT));
               }
            }

            rest.sort(String.CASE_INSENSITIVE_ORDER);
            out.addAll(rest);
         } catch (Throwable var6) {
         }

         return out;
      }
   }

   static List<PackResources> enabledPacks() {
      synchronized(PACK_LOCK) {
         if (PACK_CACHE != null) {
            return PACK_CACHE;
         } else {
            List<PackResources> out = new ArrayList();

            try {
               Minecraft client = Minecraft.getInstance();
               if (client == null) {
                  return out;
               }

               PackRepository manager = client.getResourcePackRepository();
               if (manager == null) {
                  return out;
               }

               for(Pack profile : manager.getSelectedPacks()) {
                  try {
                     out.add(profile.open());
                  } catch (Throwable var8) {
                     LteLog.LOGGER.warn("[lte] beam editor: cannot open pack {}", profile.getId());
                  }
               }
            } catch (Throwable var9) {
               LteLog.LOGGER.warn("[lte] beam editor: pack scan unavailable", var9);
            }

            PACK_CACHE = out;
            return out;
         }
      }
   }

   static void invalidatePacks() {
      List<PackResources> old;
      synchronized(PACK_LOCK) {
         old = PACK_CACHE;
         PACK_CACHE = null;
      }

      BeamPackScope.invalidate();
      synchronized(LOG_LOCK) {
         HITS.clear();
         MISSES.clear();
      }

      if (old != null) {
         for(PackResources pack : old) {
            try {
               pack.close();
            } catch (Throwable var4) {
            }
         }

      }
   }

   private static String readAll(IoSupplier<InputStream> supplier) {
      if (supplier == null) {
         return null;
      } else {
         try {
            InputStream in = (InputStream)supplier.get();

            String var2;
            try {
               var2 = in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Throwable var5) {
               if (in != null) {
                  try {
                     in.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (in != null) {
               in.close();
            }

            return var2;
         } catch (Throwable var6) {
            return null;
         }
      }
   }

   static List<VehicleLightOverrides.Beam> parse(String json, String vehicleId, String source) {
      if (json != null && !json.isBlank()) {
         try {
            JsonElement rootEl = JsonParser.parseString(json);
            if (rootEl != null && rootEl.isJsonObject()) {
               JsonObject root = rootEl.getAsJsonObject();
               JsonElement beamsEl = root.get("beams");
               if (beamsEl != null && beamsEl.isJsonArray()) {
                  JsonObject wrapper = new JsonObject();
                  wrapper.addProperty("version", 1);
                  JsonArray vehicles = new JsonArray();
                  JsonObject vehicle = new JsonObject();
                  vehicle.addProperty("id", vehicleId == null ? "" : vehicleId);
                  vehicle.add("beams", beamsEl.deepCopy());
                  vehicles.add(vehicle);
                  wrapper.add("vehicles", vehicles);
                  VehicleLightJson.Result result = VehicleLightJson.parse(GSON.toJson(wrapper), source);
                  return (List<VehicleLightOverrides.Beam>)(result.ok() && !result.vehicles.isEmpty() ? new ArrayList<VehicleLightOverrides.Beam>(((VehicleLightOverrides.Vehicle)result.vehicles.get(0)).beams) : List.of());
               } else {
                  return List.of();
               }
            } else {
               return List.of();
            }
         } catch (Throwable var10) {
            LteLog.LOGGER.warn("[lte] beam editor: cannot parse {}", source, var10);
            return List.of();
         }
      } else {
         return List.of();
      }
   }

   static final class Candidate {
      final String source;
      final List<String> names;
      final List<PackResources> packs;

      Candidate(String source, List<String> names, List<PackResources> packs) {
         this.source = source;
         this.names = names == null ? List.of() : names;
         this.packs = packs == null ? List.of() : packs;
      }
   }

   static final class Hit {
      final String path;
      final List<VehicleLightOverrides.Beam> beams;

      Hit(String path, List<VehicleLightOverrides.Beam> beams) {
         this.path = path;
         this.beams = beams;
      }
   }

   static final class Plan {
      final String id;
      final List<String> keys;
      final Set<PackResources> owners = new LinkedHashSet();
      final List<BeamPackWriter.Candidate> candidates;
      final List<String> tried;

      private Plan(String id, List<String> keys, List<BeamPackWriter.Candidate> candidates, List<String> tried) {
         this.id = id;
         this.keys = keys;
         this.candidates = candidates;
         this.tried = tried;
      }

      static BeamPackWriter.Plan of(String vehicleId, String... aliases) {
         String id = vehicleId == null ? "" : vehicleId.trim();
         return of(id.isEmpty() ? List.of() : BeamPackScope.index(), id, aliases);
      }

      static BeamPackWriter.Plan of(List<BeamPackScope> scopes, String vehicleId, String... aliases) {
         String id = vehicleId == null ? "" : vehicleId.trim();
         LinkedHashSet<String> forms = new LinkedHashSet();
         if (!id.isEmpty()) {
            forms.add(BeamPackWriter.keyOf(id));
            forms.add(id);
            forms.add("mtr_custom_train_" + BeamPackWriter.keyOf(id));
         }

         if (aliases != null) {
            for(String alias : aliases) {
               if (alias != null && !alias.isBlank()) {
                  forms.add(BeamPackWriter.keyOf(alias));
                  forms.add(alias.trim());
               }
            }
         }

         Map<String, List<PackResources>> declared = new LinkedHashMap();

         for(String form : forms) {
            for(BeamPackScope scope : scopes) {
               for(String key : scope.keysFor(form)) {
                  List<PackResources> packs = (List)declared.computeIfAbsent(key, (k) -> new ArrayList());
                  if (!packs.contains(scope.pack)) {
                     packs.add(scope.pack);
                  }
               }
            }
         }

         BeamPackWriter.Plan plan = new BeamPackWriter.Plan(id, new ArrayList(declared.keySet()), new ArrayList(), new ArrayList());

         for(List<PackResources> packs : declared.values()) {
            plan.owners.addAll(packs);
         }

         LinkedHashSet<String> seen = new LinkedHashSet();

         for(Map.Entry<String, List<PackResources>> entry : declared.entrySet()) {
            plan.add(seen, "key", BeamPackWriter.namesFor(List.of((String)entry.getKey())), (List)entry.getValue());
         }

         boolean idIsKey = plan.keys.contains(id) || plan.keys.contains(BeamPackWriter.keyOf(id)) || plan.keys.contains("mtr_custom_train_" + BeamPackWriter.keyOf(id));
         List<String> raw = new ArrayList();

         for(String form : forms) {
            if (!plan.keys.contains(form) && !plan.keys.contains(BeamPackWriter.keyOf(form))) {
               raw.add(form);
            }
         }

         plan.add(seen, idIsKey ? "key" : "base", BeamPackWriter.namesFor(raw), List.of());

         for(String key : plan.keys) {
            for(BeamPackScope scope : scopes) {
               String runtime = scope.runtimeIdOf(key);
               if (runtime != null && !forms.contains(runtime)) {
                  plan.add(seen, "base", BeamPackWriter.namesFor(List.of(runtime)), List.of());
               }
            }
         }

         if (plan.tried.size() > 12) {
            plan.tried.subList(12, plan.tried.size()).clear();
            plan.tried.add("...");
         }

         return plan;
      }

      private void add(Set<String> seen, String source, List<String> names, List<PackResources> packs) {
         if (names != null && !names.isEmpty()) {
            String signature = String.join(",", names).toLowerCase(Locale.ROOT);
            if (seen.add(signature)) {
               this.candidates.add(new BeamPackWriter.Candidate(source, names, packs));

               for(String name : names) {
                  if (!this.tried.contains(name)) {
                     this.tried.add(name);
                  }
               }

            }
         }
      }

      boolean isEmpty() {
         return this.candidates.isEmpty();
      }
   }
}

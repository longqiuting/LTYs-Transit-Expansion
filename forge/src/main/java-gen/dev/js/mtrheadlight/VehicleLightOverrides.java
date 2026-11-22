package dev.js.mtrheadlight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VehicleLightOverrides {
   private static final double DEFAULT_HEAD_LENGTH = 16.0D;
   private static final double DEFAULT_TAIL_LENGTH = 8.0D;
   private static final double DEFAULT_WIDTH = 3.0D;
   private static final double DEFAULT_ALPHA = 0.16D;
   private static final int AUTO_CACHE = 512;
   private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();
   private static final Map<String, VehicleLightOverrides.Vehicle> PACK = new LinkedHashMap();
   private static final Map<String, VehicleLightOverrides.Vehicle> RUNTIME = new LinkedHashMap();
   private static final Map<String, List<VehicleLightOverrides.Beam>> AUTO = new LinkedHashMap<String, List<VehicleLightOverrides.Beam>>(64, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<String, List<VehicleLightOverrides.Beam>> eldest) {
         return this.size() > 512;
      }
   };
   private static final Map<String, VehicleLightOverrides.Vehicle> MATCH = new LinkedHashMap<String, VehicleLightOverrides.Vehicle>(64, 0.75F, true) {
      protected boolean removeEldestEntry(Map.Entry<String, VehicleLightOverrides.Vehicle> eldest) {
         return this.size() > 1024;
      }
   };
   private static final Set<String> WARNED = new LinkedHashSet();
   private static final VehicleLightOverrides.Vehicle MISS = new VehicleLightOverrides.Vehicle("", "");
   private static final AtomicLong VERSION = new AtomicLong(1L);
   private static volatile VehicleLightOverrides.PackBeamReader packBeamReader;
   private static final Set<String> LAZY_TRIED = new LinkedHashSet();
   private static volatile int lastFailed;
   private static Set<String> packLabels = new LinkedHashSet();
   private static volatile boolean loaded;

   private VehicleLightOverrides() {
   }

   public static void setPackBeamReader(VehicleLightOverrides.PackBeamReader reader) {
      LOCK.writeLock().lock();

      try {
         packBeamReader = reader;
         LAZY_TRIED.clear();
         MATCH.clear();
         VERSION.incrementAndGet();
      } finally {
         LOCK.writeLock().unlock();
      }

   }

   public static void load(List<VehicleLightJson.Source> sources) {
      Map<String, VehicleLightOverrides.Vehicle> parsed = new LinkedHashMap();
      Set<String> labels = new LinkedHashSet();
      int failed = 0;
      List<String> notes = new ArrayList();
      if (sources != null) {
         for(VehicleLightJson.Source src : sources) {
            VehicleLightJson.Result r = VehicleLightJson.parse(src.json, src.toString());
            if (!r.ok()) {
               ++failed;
               warnOnce(src, r.error);
            } else {
               for(String note : r.notes) {
                  warnOnce(src, note);
               }

               for(VehicleLightOverrides.Vehicle snapshot : r.vehicles) {
                  if (!snapshot.beams.isEmpty()) {
                     VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle(snapshot.id, "pack:" + src.pack);
                     v.beams.addAll(snapshot.beams);
                     VehicleLightOverrides.Vehicle replaced = (VehicleLightOverrides.Vehicle)parsed.put(v.id, v);
                     labels.add(src.pack);
                     if (replaced != null) {
                        notes.add("id '" + v.id + "' from " + String.valueOf(src) + " overrides an earlier pack");
                     }
                  }
               }
            }
         }
      }

      LOCK.writeLock().lock();

      try {
         PACK.clear();
         PACK.putAll(parsed);
         MATCH.clear();
         synchronized(LAZY_TRIED) {
            LAZY_TRIED.clear();
         }

         packLabels = labels;
         VERSION.incrementAndGet();
         lastFailed = failed;
         loaded = true;
      } finally {
         LOCK.writeLock().unlock();
      }

      if (!notes.isEmpty()) {
         LteLog.LOGGER.info("[lte] lights json: {} note(s), first: {}", notes.size(), notes.get(0));
      }

   }

   public static void reload() {
      LOCK.writeLock().lock();

      try {
         PACK.clear();
         AUTO.clear();
         MATCH.clear();
         synchronized(LAZY_TRIED) {
            LAZY_TRIED.clear();
         }

         synchronized(WARNED) {
            WARNED.clear();
         }

         packLabels = new LinkedHashSet();
         lastFailed = 0;
         VERSION.incrementAndGet();
      } finally {
         LOCK.writeLock().unlock();
      }

      try {
         VehicleLights.clearCache();
      } catch (Throwable var8) {
      }

   }

   private static void warnOnce(VehicleLightJson.Source src, String message) {
      String key = src.pack + "|" + src.path;
      synchronized(WARNED) {
         if (!WARNED.add(key)) {
            return;
         }
      }

      LteLog.LOGGER.warn("[lte] lights json: {} unreadable: {}", src, message);
   }

   public static List<VehicleLightOverrides.Vehicle> list() {
      LOCK.readLock().lock();

      Object var7;
      try {
         List<VehicleLightOverrides.Vehicle> out = new ArrayList(PACK.size() + RUNTIME.size());

         for(VehicleLightOverrides.Vehicle v : PACK.values()) {
            out.add(v.copy());
         }

         for(VehicleLightOverrides.Vehicle v : RUNTIME.values()) {
            out.add(v.copy());
         }

         var7 = out;
      } finally {
         LOCK.readLock().unlock();
      }

      return (List<VehicleLightOverrides.Vehicle>)var7;
   }

   public static List<String> ids() {
      LOCK.readLock().lock();

      Object var7;
      try {
         List<String> out = new ArrayList(PACK.size() + RUNTIME.size());

         for(String id : PACK.keySet()) {
            out.add(id);
         }

         for(String id : RUNTIME.keySet()) {
            if (!PACK.containsKey(id)) {
               out.add(id);
            }
         }

         var7 = out;
      } finally {
         LOCK.readLock().unlock();
      }

      return (List<String>)var7;
   }

   public static VehicleLightOverrides.Vehicle get(String vehicleId) {
      VehicleLightOverrides.Vehicle v = lookup(vehicleId);
      return v == null ? null : v.copy();
   }

   public static List<VehicleLightOverrides.Beam> beams(String vehicleId) {
      VehicleLightOverrides.Vehicle v = lookupOrLoad(vehicleId);
      if (v == null) {
         return Collections.emptyList();
      } else {
         List<VehicleLightOverrides.Beam> out = new ArrayList(v.beams.size());

         for(VehicleLightOverrides.Beam b : v.beams) {
            out.add(b.copy());
         }

         return out;
      }
   }

   private static VehicleLightOverrides.Vehicle lookupOrLoad(String vehicleId) {
      VehicleLightOverrides.Vehicle found = lookup(vehicleId);
      if (found == null && vehicleId != null && !vehicleId.isEmpty()) {
         VehicleLightOverrides.PackBeamReader reader = packBeamReader;
         if (reader == null) {
            return null;
         } else {
            String key = vehicleId.toLowerCase(Locale.ROOT);
            synchronized(LAZY_TRIED) {
               if (!LAZY_TRIED.add(key)) {
                  return null;
               }
            }

            List<VehicleLightOverrides.Beam> beams = null;

            try {
               beams = reader.read(vehicleId);
            } catch (Throwable var13) {
               LteLog.LOGGER.warn("[lte] beam pack: reader failed for {}", vehicleId, var13);
               return null;
            }

            if (beams != null && !beams.isEmpty()) {
               VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle(vehicleId, "pack:beam3D");

               for(VehicleLightOverrides.Beam b : beams) {
                  if (b != null) {
                     v.beams.add(b.copy());
                  }
               }

               LOCK.writeLock().lock();

               try {
                  PACK.put(key, v);
                  MATCH.clear();
                  VERSION.incrementAndGet();
               } finally {
                  LOCK.writeLock().unlock();
               }

               return lookup(vehicleId);
            } else {
               return null;
            }
         }
      } else {
         return found;
      }
   }

   private static VehicleLightOverrides.Vehicle lookup(String vehicleId) {
      if (vehicleId != null && !vehicleId.isEmpty()) {
         String key = vehicleId.toLowerCase(Locale.ROOT);
         LOCK.readLock().lock();

         try {
            VehicleLightOverrides.Vehicle cached = (VehicleLightOverrides.Vehicle)MATCH.get(key);
            if (cached != null) {
               return cached == MISS ? null : cached;
            }
         } finally {
            LOCK.readLock().unlock();
         }

         VehicleLightOverrides.Vehicle var12 = matchUncached(vehicleId);
         LOCK.writeLock().lock();

         try {
            MATCH.put(key, var12 == null ? MISS : var12);
         } finally {
            LOCK.writeLock().unlock();
         }

         return var12;
      } else {
         return null;
      }
   }

   private static VehicleLightOverrides.Vehicle matchUncached(String vehicleId) {
      VehicleLightOverrides.Vehicle best = null;
      int bestLen = -1;
      LOCK.readLock().lock();

      try {
         for(Map<String, VehicleLightOverrides.Vehicle> layer : List.of(RUNTIME, PACK)) {
            for(VehicleLightOverrides.Vehicle v : layer.values()) {
               if (v.matches(vehicleId)) {
                  if (!v.isPattern()) {
                     return v;
                  }

                  int len = v.prefix().length();
                  if (len > bestLen) {
                     bestLen = len;
                     best = v;
                  }
               }
            }
         }

         return best;
      } finally {
         LOCK.readLock().unlock();
      }
   }

   public static boolean loaded() {
      return loaded;
   }

   public static long version() {
      return VERSION.get();
   }

   public static void put(String vehicleId, List<VehicleLightOverrides.Beam> beams) {
      if (vehicleId != null && !vehicleId.trim().isEmpty()) {
         String id = vehicleId.trim();
         VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle(id, "runtime");
         if (beams != null) {
            for(VehicleLightOverrides.Beam b : beams) {
               if (b != null) {
                  v.beams.add(b.copy());
               }
            }
         }

         LOCK.writeLock().lock();

         try {
            if (v.beams.isEmpty()) {
               RUNTIME.remove(id);
            } else {
               RUNTIME.put(id, v);
            }

            MATCH.clear();
            VERSION.incrementAndGet();
         } finally {
            LOCK.writeLock().unlock();
         }

      }
   }

   public static void put(String vehicleId, VehicleLightOverrides.Beam beam) {
      if (beam != null) {
         LOCK.writeLock().lock();

         try {
            VehicleLightOverrides.Vehicle v = (VehicleLightOverrides.Vehicle)RUNTIME.get(vehicleId);
            if (v == null) {
               v = new VehicleLightOverrides.Vehicle(vehicleId == null ? "" : vehicleId.trim(), "runtime");
               RUNTIME.put(v.id, v);
            }

            for(int i = 0; i < v.beams.size(); ++i) {
               if (((VehicleLightOverrides.Beam)v.beams.get(i)).type.equalsIgnoreCase(beam.type)) {
                  v.beams.set(i, beam.copy());
                  MATCH.clear();
                  VERSION.incrementAndGet();
                  return;
               }
            }

            v.beams.add(beam.copy());
            MATCH.clear();
            VERSION.incrementAndGet();
         } finally {
            LOCK.writeLock().unlock();
         }
      }
   }

   public static boolean remove(String vehicleId) {
      if (vehicleId == null) {
         return false;
      } else {
         LOCK.writeLock().lock();

         boolean var2;
         try {
            boolean removed = RUNTIME.remove(vehicleId) != null;
            if (removed) {
               MATCH.clear();
               VERSION.incrementAndGet();
            }

            var2 = removed;
         } finally {
            LOCK.writeLock().unlock();
         }

         return var2;
      }
   }

   public static void clearRuntime() {
      LOCK.writeLock().lock();

      try {
         RUNTIME.clear();
         MATCH.clear();
         VERSION.incrementAndGet();
      } finally {
         LOCK.writeLock().unlock();
      }

   }

   public static boolean hasRuntimeEdits() {
      LOCK.readLock().lock();

      boolean var0;
      try {
         var0 = !RUNTIME.isEmpty();
      } finally {
         LOCK.readLock().unlock();
      }

      return var0;
   }

   public static List<VehicleLightOverrides.Beam> resolve(String vehicleId, VehicleLights.Layout layout) {
      List<VehicleLightOverrides.Beam> auto = autoBeams(layout);
      if (!auto.isEmpty()) {
         rememberAuto(vehicleId, auto);
      }

      VehicleLightOverrides.Vehicle declared = lookupOrLoad(vehicleId);
      return declared != null && !declared.beams.isEmpty() ? merge(auto, declared.beams) : auto;
   }

   public static List<VehicleLightOverrides.Resolved> resolvedDeclared(String vehicleId, VehicleLights.Layout layout) {
      VehicleLightOverrides.Vehicle declared = lookupOrLoad(vehicleId);
      if (declared != null && !declared.beams.isEmpty()) {
         List<VehicleLightOverrides.Resolved> out = new ArrayList(declared.beams.size());

         for(VehicleLightOverrides.Beam b : declared.beams) {
            if (b != null && b.pos != null) {
               out.add(VehicleLightOverrides.Resolved.of(b, layout));
            }
         }

         return out;
      } else {
         return Collections.emptyList();
      }
   }

   public static List<VehicleLightOverrides.Resolved> resolved(String vehicleId, VehicleLights.Layout layout) {
      List<VehicleLightOverrides.Beam> beams = resolve(vehicleId, layout);
      List<VehicleLightOverrides.Resolved> out = new ArrayList(beams.size());

      for(VehicleLightOverrides.Beam b : beams) {
         out.add(VehicleLightOverrides.Resolved.of(b, layout));
      }

      return out;
   }

   public static List<VehicleLightOverrides.Beam> autoBeams(VehicleLights.Layout layout) {
      List<VehicleLightOverrides.Beam> out = new ArrayList(2);
      if (layout == null) {
         return out;
      } else {
         boolean z = !"X".equalsIgnoreCase(layout.lengthAxis);
         double half = layout.sectionLen * 0.5D;
         double front = layout.frontSign >= 0 ? 1.0D : -1.0D;
         addSlot(out, layout.head, "head", true, front, half, layout.latCentre, z);
         addSlot(out, layout.tail, "tail", false, front, half, layout.latCentre, z);
         return out;
      }
   }

   private static void addSlot(List<VehicleLightOverrides.Beam> out, VehicleLights.Slot slot, String type, boolean head, double front, double half, double latCentre, boolean z) {
      if (slot != null) {
         double along = (head ? front : -front) * (half - slot.inset);
         double[] laterals = slot.lateral != null && slot.lateral.length != 0 ? slot.lateral : new double[]{0.0D};

         for(double lateral : laterals) {
            VehicleLightOverrides.Beam b = new VehicleLightOverrides.Beam(type);
            b.pos = z ? new double[]{latCentre + lateral, slot.height, along} : new double[]{along, slot.height, latCentre + lateral};
            b.length = head ? 16.0D : 8.0D;
            b.width = 3.0D;
            b.alpha = 0.16D;
            out.add(b);
         }

      }
   }

   private static List<VehicleLightOverrides.Beam> merge(List<VehicleLightOverrides.Beam> auto, List<VehicleLightOverrides.Beam> declared) {
      List<VehicleLightOverrides.Beam> base = auto == null ? Collections.emptyList() : auto;
      List<VehicleLightOverrides.Beam> over = declared == null ? Collections.emptyList() : declared;
      List<VehicleLightOverrides.Beam> out = new ArrayList(Math.max(base.size(), over.size()));
      if (base.isEmpty()) {
         for(VehicleLightOverrides.Beam d : over) {
            out.add(declared(d));
         }

         return out;
      } else {
         Set<String> declaredTypes = new LinkedHashSet();
         Set<String> explicitGeometry = new LinkedHashSet();

         for(VehicleLightOverrides.Beam d : over) {
            String type = d.type == null ? "head" : d.type;
            declaredTypes.add(type);
            if (d.pos != null) {
               explicitGeometry.add(type);
            }
         }

         List<Boolean> used = new ArrayList(Collections.nCopies(over.size(), Boolean.FALSE));
         Map<String, Integer> next = new LinkedHashMap();

         for(VehicleLightOverrides.Beam a : base) {
            String type = a.type;
            int index = next.getOrDefault(type, 0);
            int declaredIndex = nthIndexOfType(over, type, index);
            if (declaredIndex >= 0) {
               out.add(declared(((VehicleLightOverrides.Beam)over.get(declaredIndex)).filled(a)));
               used.set(declaredIndex, Boolean.TRUE);
               next.put(type, index + 1);
            } else if (!declaredTypes.contains(type) || !explicitGeometry.contains(type)) {
               out.add(a.copy());
            }
         }

         for(int i = 0; i < over.size(); ++i) {
            if (!used.get(i)) {
               out.add(declared(((VehicleLightOverrides.Beam)over.get(i)).copy()));
            }
         }

         return out;
      }
   }

   private static VehicleLightOverrides.Beam declared(VehicleLightOverrides.Beam beam) {
      VehicleLightOverrides.Beam out = beam.copy();
      out.declared = true;
      return out;
   }

   private static int nthIndexOfType(List<VehicleLightOverrides.Beam> list, String type, int index) {
      int seen = 0;

      for(int i = 0; i < list.size(); ++i) {
         VehicleLightOverrides.Beam b = (VehicleLightOverrides.Beam)list.get(i);
         if (b.type != null && b.type.equalsIgnoreCase(type)) {
            if (seen == index) {
               return i;
            }

            ++seen;
         }
      }

      return -1;
   }

   private static void rememberAuto(String vehicleId, List<VehicleLightOverrides.Beam> beams) {
      if (vehicleId != null && !vehicleId.isEmpty()) {
         LOCK.writeLock().lock();

         try {
            List<VehicleLightOverrides.Beam> cached = (List)AUTO.get(vehicleId);
            if (cached == null || !sameShape(cached, beams)) {
               List<VehicleLightOverrides.Beam> copy = new ArrayList(beams.size());

               for(VehicleLightOverrides.Beam b : beams) {
                  copy.add(b.copy());
               }

               AUTO.put(vehicleId, copy);
               return;
            }
         } finally {
            LOCK.writeLock().unlock();
         }

      }
   }

   private static boolean sameShape(List<VehicleLightOverrides.Beam> a, List<VehicleLightOverrides.Beam> b) {
      if (a.size() != b.size()) {
         return false;
      } else {
         for(int i = 0; i < a.size(); ++i) {
            VehicleLightOverrides.Beam x = (VehicleLightOverrides.Beam)a.get(i);
            VehicleLightOverrides.Beam y = (VehicleLightOverrides.Beam)b.get(i);
            if (!x.type.equals(y.type) || !Objects.equals(x.length, y.length)) {
               return false;
            }

            if (x.pos != null && y.pos != null) {
               for(int k = 0; k < 3; ++k) {
                  if (Math.abs(x.pos[k] - y.pos[k]) > 1.0E-9D) {
                     return false;
                  }
               }
            } else if (x.pos != y.pos) {
               return false;
            }
         }

         return true;
      }
   }

   public static Path export(Path path) throws IOException {
      String json = exportJson();
      Path parent = path.toAbsolutePath().getParent();
      if (parent != null) {
         Files.createDirectories(parent);
      }

      Files.write(path, json.getBytes(StandardCharsets.UTF_8), new OpenOption[0]);
      return path;
   }

   public static String exportJson() {
      LOCK.readLock().lock();

      List<VehicleLightOverrides.Vehicle> declared;
      Map<String, List<VehicleLightOverrides.Beam>> auto;
      try {
         declared = list();
         auto = new LinkedHashMap<String, List<VehicleLightOverrides.Beam>>();

         for(Map.Entry<String, List<VehicleLightOverrides.Beam>> e : AUTO.entrySet()) {
            auto.put((String)e.getKey(), (List)e.getValue());
         }
      } finally {
         LOCK.readLock().unlock();
      }

      TreeMap<String, List<VehicleLightOverrides.Beam>> var9 = new TreeMap<>();

      for(VehicleLightOverrides.Vehicle v : declared) {
         if (v.isPattern()) {
            for(Map.Entry<String, List<VehicleLightOverrides.Beam>> e : auto.entrySet()) {
               if (v.matches((String)e.getKey())) {
                  var9.put((String)e.getKey(), merge((List)e.getValue(), v.beams));
               }
            }
         }
      }

      for(VehicleLightOverrides.Vehicle v : declared) {
         if (!v.isPattern()) {
            var9.put(v.id, merge((List)auto.get(v.id), v.beams));
         } else if (!var9.keySet().stream().anyMatch(v::matches)) {
            var9.put(v.id, merge(Collections.emptyList(), v.beams));
         }
      }

      for(Map.Entry<String, List<VehicleLightOverrides.Beam>> e : auto.entrySet()) {
         var9.putIfAbsent((String)e.getKey(), merge((List)e.getValue(), Collections.emptyList()));
      }

      List<VehicleLightOverrides.Vehicle> out = new ArrayList(var9.size());

      for(Map.Entry<String, List<VehicleLightOverrides.Beam>> e : var9.entrySet()) {
         VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle((String)e.getKey(), "");
         v.beams.addAll((Collection)e.getValue());
         out.add(v);
      }

      return VehicleLightJson.write(out);
   }

   public static String diagnosticLine() {
      LOCK.readLock().lock();

      String var8;
      try {
         int vehicles = 0;
         int entries = 0;

         for(VehicleLightOverrides.Vehicle v : PACK.values()) {
            ++vehicles;
            entries += v.beams.size();
         }

         for(VehicleLightOverrides.Vehicle v : RUNTIME.values()) {
            ++vehicles;
            entries += v.beams.size();
         }

         var8 = "lights json: packs=" + packLabels.size() + " vehicles=" + vehicles + " entries=" + entries + " failed=" + lastFailed;
      } finally {
         LOCK.readLock().unlock();
      }

      return var8;
   }

   public static void logDiagnostics() {
      LteLog.LOGGER.info("[lte] {}", diagnosticLine());
   }

   private static String vec(double[] v) {
      return v == null ? "-" : "[" + fmt(v[0]) + "," + fmt(v[1]) + "," + fmt(v[2]) + "]";
   }

   private static String fmt(double d) {
      return String.format(Locale.ROOT, "%.2f", d);
   }

   public static final class Beam {
      public String type = "head";
      public Integer color;
      public double[] pos;
      public double[] rot;
      public Double length;
      public Double width;
      public Double alpha;
      public boolean declared;

      public Beam() {
      }

      public Beam(String type) {
         if (type != null && !type.isEmpty()) {
            this.type = type;
         }

      }

      public VehicleLightOverrides.Beam copy() {
         VehicleLightOverrides.Beam b = new VehicleLightOverrides.Beam(this.type);
         b.color = this.color;
         b.pos = this.pos == null ? null : (double[])this.pos.clone();
         b.rot = this.rot == null ? null : (double[])this.rot.clone();
         b.length = this.length;
         b.width = this.width;
         b.alpha = this.alpha;
         b.declared = this.declared;
         return b;
      }

      public VehicleLightOverrides.Beam filled(VehicleLightOverrides.Beam base) {
         VehicleLightOverrides.Beam b = this.copy();
         if (base != null) {
            if (b.color == null) {
               b.color = base.color;
            }

            if (b.pos == null) {
               b.pos = base.pos == null ? null : (double[])base.pos.clone();
            }

            if (b.rot == null) {
               b.rot = base.rot == null ? null : (double[])base.rot.clone();
            }

            if (b.length == null) {
               b.length = base.length;
            }

            if (b.width == null) {
               b.width = base.width;
            }

            if (b.alpha == null) {
               b.alpha = base.alpha;
            }
         }

         return b;
      }

      public String toString() {
         return this.type + "(color=" + (this.color == null ? "-" : String.format(Locale.ROOT, "#%06X", this.color)) + " pos=" + VehicleLightOverrides.vec(this.pos) + " len=" + this.length + " w=" + this.width + " a=" + this.alpha + ")";
      }
   }

   public interface PackBeamReader {
      List<VehicleLightOverrides.Beam> read(String var1);
   }

   public static final class Resolved {
      public final String type;
      public final int color;
      public final double lateral;
      public final double height;
      public final double inset;
      public final double length;
      public final double width;
      public final double alpha;
      public final double[] rot;
      public final boolean declared;

      private Resolved(String type, int color, double lateral, double height, double inset, double length, double width, double alpha, double[] rot, boolean declared) {
         this.type = type;
         this.color = color;
         this.lateral = lateral;
         this.height = height;
         this.inset = inset;
         this.length = length;
         this.width = width;
         this.alpha = alpha;
         this.rot = rot;
         this.declared = declared;
      }

      static VehicleLightOverrides.Resolved of(VehicleLightOverrides.Beam b, VehicleLights.Layout layout) {
         String type = b.type == null ? "head" : b.type;
         boolean head = !"tail".equalsIgnoreCase(type);
         int color = b.color != null ? b.color : (head ? 16774102 : 16738906);
         double lateral = 0.0D;
         double height = 0.0D;
         double inset = 0.0D;
         if (b.pos != null && layout != null) {
            boolean z = !"X".equalsIgnoreCase(layout.lengthAxis);
            double along = z ? b.pos[2] : b.pos[0];
            lateral = (z ? b.pos[0] : b.pos[2]) - layout.latCentre;
            height = b.pos[1];
            double half = layout.sectionLen * 0.5D;
            double front = layout.frontSign >= 0 ? 1.0D : -1.0D;
            inset = head ? half - along * front : half + along * front;
         } else if (b.pos != null) {
            lateral = b.pos[0];
            height = b.pos[1];
         }

         double length = b.length != null ? b.length : (head ? 16.0D : 8.0D);
         double width = b.width != null ? b.width : 3.0D;
         double alpha = b.alpha != null ? b.alpha : 0.16D;
         return new VehicleLightOverrides.Resolved(type, color & 16777215, lateral, height, inset, length, width, alpha, b.rot == null ? null : (double[])b.rot.clone(), b.declared);
      }

      public static VehicleLightOverrides.Resolved ofPublic(VehicleLightOverrides.Beam b, VehicleLights.Layout layout) {
         return of(b, layout);
      }

      public boolean isHead() {
         return !"tail".equalsIgnoreCase(this.type);
      }

      public String toString() {
         return this.type + "(lat=" + VehicleLightOverrides.fmt(this.lateral) + " y=" + VehicleLightOverrides.fmt(this.height) + " inset=" + VehicleLightOverrides.fmt(this.inset) + " len=" + VehicleLightOverrides.fmt(this.length) + " w=" + VehicleLightOverrides.fmt(this.width) + " a=" + VehicleLightOverrides.fmt(this.alpha) + ")";
      }
   }

   public static final class Vehicle {
      public final String id;
      public final String source;
      public final List<VehicleLightOverrides.Beam> beams = new ArrayList();

      public Vehicle(String id, String source) {
         this.id = id;
         this.source = source == null ? "" : source;
      }

      public boolean isPattern() {
         return this.id.endsWith("*");
      }

      public String prefix() {
         return (this.isPattern() ? this.id.substring(0, this.id.length() - 1) : this.id).toLowerCase(Locale.ROOT);
      }

      public boolean matches(String vehicleId) {
         if (vehicleId != null && !vehicleId.isEmpty()) {
            String lower = vehicleId.toLowerCase(Locale.ROOT);
            return this.isPattern() ? lower.startsWith(this.prefix()) : lower.equals(this.prefix());
         } else {
            return false;
         }
      }

      public VehicleLightOverrides.Vehicle copy() {
         VehicleLightOverrides.Vehicle v = new VehicleLightOverrides.Vehicle(this.id, this.source);

         for(VehicleLightOverrides.Beam b : this.beams) {
            v.beams.add(b.copy());
         }

         return v;
      }

      public String toString() {
         return this.id + " -> " + this.beams.size() + " beam(s) [" + this.source + "]";
      }
   }
}

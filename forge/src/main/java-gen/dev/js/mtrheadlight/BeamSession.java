package dev.js.mtrheadlight;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import mtr.client.ClientData;
import mtr.client.DynamicTrainModel;
import mtr.client.TrainClientRegistry;
import mtr.client.TrainProperties;
import mtr.data.Siding;
import mtr.data.TrainClient;
import mtr.model.ModelTrainBase;
import mtr.render.JonModelTrainRenderer;
import mtr.render.TrainRendererBase;
import dev.js.mtrheadlight.forge.platform.FabricLoader;

final class BeamSession {
   private static final double HEAD_LENGTH = 16.0D;
   private static final double TAIL_LENGTH = 8.0D;
   private static final double WIDTH = 3.0D;
   private static final double ALPHA = 0.16D;
   private static final double HEIGHT = 0.55D;
   private static final Set<String> EDITED = new LinkedHashSet();
   final String vehicleId;
   final List<VehicleLightOverrides.Beam> work = new ArrayList();
   final List<VehicleLightOverrides.Beam> base = new ArrayList();
   final Set<Integer> selected = new LinkedHashSet();

   private BeamSession(String vehicleId) {
      this.vehicleId = vehicleId;
   }

   static BeamSession open(String vehicleId) {
      return open(vehicleId, (VehicleLights.Layout)null);
   }

   static BeamSession open(String vehicleId, VehicleLights.Layout layout) {
      BeamSession session = new BeamSession(vehicleId == null ? "" : vehicleId.trim());
      VehicleLights.Layout resolved = layout != null ? layout : layoutOf(session.vehicleId);
      List<VehicleLightOverrides.Beam> auto = VehicleLightOverrides.autoBeams(resolved);
      List<VehicleLightOverrides.Beam> effective = VehicleLightOverrides.resolve(session.vehicleId, resolved);
      Map<String, Integer> seen = new LinkedHashMap();

      for(VehicleLightOverrides.Beam beam : effective) {
         if (beam != null) {
            VehicleLightOverrides.Beam matched = nthOfType(auto, typeOf(beam), seen.merge(typeOf(beam), 1, Integer::sum) - 1);
            VehicleLightOverrides.Beam detected = matched == null ? null : materialise(matched, (VehicleLightOverrides.Beam)null);
            VehicleLightOverrides.Beam fallback = matched == null ? snapshot(beam) : detected;
            session.base.add(fallback);
            session.work.add(materialise(beam, fallback));
         }
      }

      return session;
   }

   static int editedCount() {
      return EDITED.size();
   }

   static int abandon() {
      List<String> ids = new ArrayList(EDITED);
      EDITED.clear();
      int removed = 0;

      for(String id : ids) {
         if (VehicleLightOverrides.remove(id)) {
            ++removed;
         }
      }

      return removed;
   }

   static List<BeamSession.VehicleEntry> vehicles() {
      TreeSet<String> ids = new TreeSet(String.CASE_INSENSITIVE_ORDER);

      try {
         for(TrainClient train : ClientData.TRAINS) {
            String id = idOf(train);
            if (id != null && !id.isBlank()) {
               ids.add(id);
            }
         }
      } catch (Throwable var4) {
         LteLog.LOGGER.warn("[lte] beam editor: cannot read TRAINS", var4);
      }

      List<BeamSession.VehicleEntry> out = new ArrayList(ids.size());

      for(String id : ids) {
         out.add(new BeamSession.VehicleEntry(id, summary(layoutOf(id))));
      }

      return out;
   }

   static String summary(VehicleLights.Layout layout) {
      List<VehicleLightOverrides.Beam> auto = VehicleLightOverrides.autoBeams(layout);
      if (auto.isEmpty()) {
         return null;
      } else {
         int head = 0;
         int tail = 0;

         for(VehicleLightOverrides.Beam beam : auto) {
            if ("tail".equalsIgnoreCase(typeOf(beam))) {
               ++tail;
            } else {
               ++head;
            }
         }

         return "head " + head + " \u00b7 tail " + tail;
      }
   }

   static String presetOf(Siding siding) {
      if (siding == null) {
         return null;
      } else {
         try {
            String id = siding.getTrainId();
            return id != null && !id.isBlank() ? id : null;
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   static VehicleLights.Layout layoutOf(String vehicleId) {
      if (vehicleId != null && !vehicleId.isEmpty()) {
         try {
            for(TrainClient train : ClientData.TRAINS) {
               String id = idOf(train);
               if (id != null && id.equalsIgnoreCase(vehicleId)) {
                  VehicleLights.Layout layout = VehicleLights.layoutFor(train);
                  if (layout != null) {
                     return layout;
                  }
               }
            }
         } catch (Throwable var6) {
            LteLog.LOGGER.warn("[lte] beam editor: no layout for {}", vehicleId, var6);
         }

         try {
            TrainProperties properties = TrainClientRegistry.getTrainProperties(vehicleId);
            if (properties != null) {
               TrainRendererBase var10 = properties.renderer;
               if (var10 instanceof JonModelTrainRenderer) {
                  JonModelTrainRenderer jon = (JonModelTrainRenderer)var10;
                  ModelTrainBase var11 = jon.model;
                  if (var11 instanceof DynamicTrainModel) {
                     DynamicTrainModel dynamic = (DynamicTrainModel)var11;
                     return VehicleLights.layoutFor(dynamic, 0);
                  }
               }
            }
         } catch (Throwable var5) {
            LteLog.LOGGER.warn("[lte] beam editor: no pack layout for {}", vehicleId, var5);
         }

         return null;
      } else {
         return null;
      }
   }

   static Set<String> inUseIds() {
      Set<String> out = new LinkedHashSet();

      try {
         for(TrainClient train : ClientData.TRAINS) {
            String id = idOf(train);
            if (id != null && !id.isBlank()) {
               out.add(id);
            }
         }
      } catch (Throwable var4) {
      }

      return out;
   }

   private static String idOf(TrainClient train) {
      if (train == null) {
         return null;
      } else {
         try {
            if (train.baseTrainType != null && !train.baseTrainType.isBlank()) {
               return train.baseTrainType;
            }
         } catch (Throwable var3) {
         }

         try {
            return train.trainId;
         } catch (Throwable var2) {
            return null;
         }
      }
   }

   int size() {
      return this.work.size();
   }

   VehicleLightOverrides.Beam beam(int index) {
      return index >= 0 && index < this.work.size() ? (VehicleLightOverrides.Beam)this.work.get(index) : null;
   }

   VehicleLightOverrides.Beam base(int index) {
      return index >= 0 && index < this.base.size() ? (VehicleLightOverrides.Beam)this.base.get(index) : null;
   }

   void save() {
      VehicleLightOverrides.put(this.vehicleId, this.work);
      EDITED.add(this.vehicleId);
   }

   int add() {
      VehicleLightOverrides.Beam previous = this.work.isEmpty() ? null : (VehicleLightOverrides.Beam)this.work.get(this.work.size() - 1);
      String type = previous == null ? "head" : typeOf(previous);
      VehicleLightOverrides.Beam created = new VehicleLightOverrides.Beam(type);
      created.color = defaultColor(type);
      created.pos = previous != null && previous.pos != null ? (double[])previous.pos.clone() : new double[]{0.0D, 0.55D, 0.0D};
      created.rot = new double[]{0.0D, 0.0D, 0.0D};
      created.length = previous != null && previous.length != null ? previous.length : defaultLength(type);
      created.width = previous != null && previous.width != null ? previous.width : 3.0D;
      created.alpha = previous != null && previous.alpha != null ? previous.alpha : 0.16D;
      this.work.add(created);
      this.base.add(created.copy());
      this.selected.add(this.work.size() - 1);
      this.save();
      return this.work.size() - 1;
   }

   int duplicate(int index) {
      VehicleLightOverrides.Beam source = this.beam(index);
      if (source == null) {
         return -1;
      } else {
         VehicleLightOverrides.Beam copy = materialise(source, (VehicleLightOverrides.Beam)null);
         if (copy.pos != null) {
            copy.pos[1] += 0.25D;
         }

         int at = Math.min(this.work.size(), index + 1);
         this.work.add(at, copy);
         this.base.add(at, copy.copy());
         this.selected.clear();
         this.selected.add(at);
         this.save();
         return at;
      }
   }

   boolean deleteSelected() {
      if (this.selected.isEmpty()) {
         return false;
      } else {
         List<Integer> indexes = new ArrayList(this.selected);
         indexes.sort((a, b) -> Integer.compare(b, a));
         int removable = 0;

         for(Integer index : indexes) {
            if (index != null && index >= 0 && index < this.work.size()) {
               ++removable;
            }
         }

         if (this.work.size() - removable < 1) {
            return false;
         } else {
            for(Integer index : indexes) {
               if (index != null && index >= 0 && index < this.work.size()) {
                  this.work.remove(index);
                  this.base.remove(index);
               }
            }

            this.selected.clear();
            this.save();
            return true;
         }
      }
   }

   void resetField(int index, BeamSession.Field field) {
      if (this.applyReset(index, field)) {
         this.save();
      }

   }

   void resetAll(int index) {
      boolean touched = false;

      for(BeamSession.Field field : BeamSession.Field.values()) {
         touched |= this.applyReset(index, field);
      }

      if (touched) {
         this.save();
      }

   }

   private boolean applyReset(int index, BeamSession.Field field) {
      VehicleLightOverrides.Beam beam = this.beam(index);
      VehicleLightOverrides.Beam source = this.base(index);
      if (beam == null) {
         return false;
      } else {
         switch (field.ordinal()) {
            case 0:
               String type = source == null ? "head" : typeOf(source);
               beam.type = type;
               beam.color = source != null && source.color != null ? source.color : defaultColor(type);
               break;
            case 1:
               beam.color = source != null && source.color != null ? source.color : defaultColor(typeOf(beam));
               break;
            case 2:
            case 3:
            case 4:
               beam.pos = vector(source == null ? null : source.pos, beam.pos, 0.55D);
               break;
            case 5:
            case 6:
            case 7:
               beam.rot = vector(source == null ? null : source.rot, beam.rot, 0.0D);
               break;
            case 8:
               beam.length = source != null && source.length != null ? source.length : defaultLength(typeOf(beam));
               break;
            case 9:
               beam.width = source != null && source.width != null ? source.width : 3.0D;
               break;
            case 10:
               beam.alpha = source != null && source.alpha != null ? source.alpha : 0.16D;
               break;
            default:
               return false;
         }

         return true;
      }
   }

   static Path exportPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("lte_lights").resolve("lte_vehicle_lights.json");
   }

   static String exportFile() {
      Path path = exportPath();

      try {
         VehicleLightOverrides.export(path);
      } catch (Throwable var6) {
         LteLog.LOGGER.warn("[lte] beam editor: export failed", var6);
         return "!" + var6.getClass().getSimpleName() + ": " + var6.getMessage();
      }

      try {
         String json = Files.readString(path, StandardCharsets.UTF_8);
         VehicleLightJson.Result readBack = VehicleLightJson.parse(json, path.toString());
         if (!readBack.ok()) {
            return "!" + readBack.error;
         } else {
            int beams = 0;

            for(VehicleLightOverrides.Vehicle v : readBack.vehicles) {
               beams += v.beams.size();
            }

            return readBack.vehicles.size() + "|" + beams;
         }
      } catch (Throwable var7) {
         return "!" + var7.getClass().getSimpleName() + ": " + var7.getMessage();
      }
   }

   static String typeOf(VehicleLightOverrides.Beam beam) {
      return beam != null && beam.type != null && !beam.type.isEmpty() ? beam.type : "head";
   }

   static boolean isHead(VehicleLightOverrides.Beam beam) {
      return !"tail".equalsIgnoreCase(typeOf(beam));
   }

   static int defaultColor(String type) {
      return "tail".equalsIgnoreCase(type) ? 16738906 : 16774102;
   }

   static double defaultLength(String type) {
      return "tail".equalsIgnoreCase(type) ? 8.0D : 16.0D;
   }

   static double defaultWidth() {
      return 3.0D;
   }

   static double defaultAlpha() {
      return 0.16D;
   }

   static VehicleLightOverrides.Beam materialise(VehicleLightOverrides.Beam beam, VehicleLightOverrides.Beam base) {
      VehicleLightOverrides.Beam out = beam.copy();
      String type = typeOf(out);
      if (out.color == null) {
         out.color = base != null && base.color != null ? base.color : defaultColor(type);
      }

      if (out.pos == null) {
         out.pos = base != null && base.pos != null ? (double[])base.pos.clone() : new double[]{0.0D, 0.55D, 0.0D};
      }

      if (out.rot == null) {
         out.rot = base != null && base.rot != null ? (double[])base.rot.clone() : new double[]{0.0D, 0.0D, 0.0D};
      }

      if (out.length == null) {
         out.length = base != null && base.length != null ? base.length : defaultLength(type);
      }

      if (out.width == null) {
         out.width = base != null && base.width != null ? base.width : 3.0D;
      }

      if (out.alpha == null) {
         out.alpha = base != null && base.alpha != null ? base.alpha : 0.16D;
      }

      return out;
   }

   private static VehicleLightOverrides.Beam snapshot(VehicleLightOverrides.Beam beam) {
      return materialise(beam, (VehicleLightOverrides.Beam)null);
   }

   static String hex(Integer rgb) {
      return String.format(Locale.ROOT, "#%06X", (rgb == null ? 0 : rgb) & 16777215);
   }

   static String typeName(String type) {
      return BeamListScreen.tr("tail".equalsIgnoreCase(type) ? "beam.type.tail" : "beam.type.head").getString();
   }

   private static double[] vector(double[] preferred, double[] current, double heightDefault) {
      if (preferred != null && preferred.length >= 3) {
         return (double[])preferred.clone();
      } else {
         return current != null && current.length >= 3 ? (double[])current.clone() : new double[]{0.0D, heightDefault, 0.0D};
      }
   }

   private static VehicleLightOverrides.Beam nthOfType(List<VehicleLightOverrides.Beam> list, String type, int index) {
      int seen = 0;

      for(VehicleLightOverrides.Beam beam : list) {
         if (type.equalsIgnoreCase(typeOf(beam))) {
            if (seen == index) {
               return beam;
            }

            ++seen;
         }
      }

      return null;
   }

   static enum Field {
      TYPE,
      COLOR,
      POS_X,
      POS_Y,
      POS_Z,
      ROT_X,
      ROT_Y,
      ROT_Z,
      LENGTH,
      WIDTH,
      ALPHA;
   }

   static final class VehicleEntry {
      final String id;
      final String auto;

      VehicleEntry(String id, String auto) {
         this.id = id;
         this.auto = auto;
      }
   }
}

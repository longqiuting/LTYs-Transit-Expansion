package dev.js.lte;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import mtr.client.DynamicTrainModel;
import mtr.client.TrainClientRegistry;
import mtr.data.TransportMode;
import mtr.model.ModelTrainBase;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class BeamEditor3DScreen extends Screen {
   private static final int TOP_H = 28;
   private static final int LEFT_W = 150;
   private static final int RIGHT_W = 130;
   private static final int MIN_LEFT_W = 104;
   private static final int MIN_RIGHT_W = 112;
   private static final int MIN_VIEW_W = 180;
   private static final int PAD = 8;
   private static final int ROW_H = 18;
   private static final int PITCH = 22;
   private static final int SCROLL_W = 6;
   private static final int SEARCH_H = 16;
   private static final int SEARCH_Y = 46;
   private static final int LIST_TOP = 68;
   private static final int FOOTER_H = 20;
   private static final int SNAP_W = 40;
   private static final int SNAP_H = 14;
   private static final int SNAP_GAP = 3;
   private static final int MODE_SELECT = 0;
   private static final int MODE_MOVE = 1;
   private static final int MODE_ROTATE = 2;
   private static final int MODE_COLOR = 3;
   private static final int MODE_COUNT = 4;
   private static final String[] MODE_KEYS = new String[]{"beam3d.mode.select", "beam3d.mode.move", "beam3d.mode.rotate", "beam3d.mode.color"};
   private static final int DRAG_NONE = 0;
   private static final int DRAG_ORBIT = 1;
   private static final int DRAG_PAN = 2;
   private static final int DRAG_BEAM_MOVE = 3;
   private static final int DRAG_BEAM_ROTATE = 4;
   private static final int DRAG_SCROLL = 5;
   private static final int DRAG_BEAM_AXIS = 6;
   private static final int DRAG_BEAM_RING = 7;
   private static final int SLIDER_R = 0;
   private static final int SLIDER_G = 1;
   private static final int SLIDER_B = 2;
   private static final int SLIDER_LENGTH = 3;
   private static final int SLIDER_WIDTH = 4;
   private static final int SLIDER_ALPHA = 5;
   private static final double POS_MIN = -16.0D;
   private static final double POS_MAX = 16.0D;
   private static final double LEN_MIN = 1.0D;
   private static final double LEN_MAX = 64.0D;
   private static final double WID_MIN = 0.5D;
   private static final double WID_MAX = 8.0D;
   private static final double ALP_MIN = 0.02D;
   private static final double ALP_MAX = 0.5D;
   private static final int[] PALETTE = new int[]{16777215, 16771524, 16765514, 16726832, 3900150, 3462041, 11032055, 2282478};
   private static final int PALETTE_COLS = 4;
   private static final int SWATCH_H = 14;
   private static final int SWATCH_GAP = 2;
   private final Screen parent;
   private final TrainPreview.Camera camera = new TrainPreview.Camera();
   private static boolean gizmoChecked;
   private String vehicleId;
   private String loadedId;
   private TrainPreview.Subject subject;
   private BeamSession session;
   private int mode = 1;
   private int selected = -1;
   private String status = "";
   private boolean empty;
   private List<BeamEditor3DScreen.VehicleRow> vehicles = List.of();
   private List<BeamEditor3DScreen.VehicleRow> shown = List.of();
   private String search = "";
   private EditBox searchBox;
   private int scrollRow;
   private double scrollAccum;
   private double scrollGrab;
   private CustomPack imported;
   private int carCount = 1;
   private Button importButton;
   private int viewX0;
   private int viewY0;
   private int viewX1;
   private int viewY1;
   private final List<Button> modeButtons = new ArrayList();
   private Button deleteButton;
   private Button duplicateButton;
   private int colorSectionY = -1;
   private int paletteY = -1;
   private int swatchW = 0;
   private int geometrySectionY = -1;
   private int propertiesY = -1;
   private int hintY = -1;
   private int transformY = -1;
   private final List<Button> transformButtons = new ArrayList();
   private int drag = 0;
   private int dragButton = -1;
   private double pressX;
   private double pressY;
   private double lastX;
   private double lastY;
   private double[] moveAnchor;
   private double moveAccumX;
   private double moveAccumY;
   private int dragAxis = -1;
   private double[] axisAnchor;
   private double[] axisBase;
   private double axisGrab = Double.NaN;
   private int hoverAxis = -1;
   private double[] ringAnchorRot;
   private double[] ringBase;
   private double[] ringNormal;
   private double ringAngle0 = Double.NaN;
   private boolean ringPlanar;
   private double[] rotAnchor;
   private double rotAccumX;
   private double rotAccumY;
   private final double[] deltaPos = new double[3];
   private final double[] deltaRot = new double[3];
   private boolean deltaShown;
   private double deltaMoveStep = 0.0625D;

   BeamEditor3DScreen(Screen parent, String vehicleId) {
      super(tr("beam3d.title"));
      this.parent = parent;
      this.vehicleId = vehicleId;
      TrainNames.refresh();
      LteLog.LOGGER.info("[lte] beam3d: vehicles={} vehicleId={} loaded={}", new Object[]{this.vehicles.size(), describe(this.vehicleId), describe(this.loadedId)});
      if (!gizmoChecked) {
         gizmoChecked = true;
         String bad = BeamGizmo.selfCheck();
         if (bad.isEmpty()) {
            LteLog.LOGGER.info("[lte] beam3d: gizmo self-check OK (move step 1/16, rot step 2.5deg; each drag changes exactly one axis)");
         } else {
            LteLog.LOGGER.warn("[lte] beam3d: gizmo self-check FAILED: {}", bad);
         }
      }

   }

   private static String describe(String value) {
      return value == null ? "null" : (value.isBlank() ? "\"\"" : value);
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private static List<BeamEditor3DScreen.VehicleRow> catalog(CustomPack imported) {
      Set<String> inUse = BeamSession.inUseIds();
      TreeSet<String> ids = new TreeSet(String.CASE_INSENSITIVE_ORDER);
      ids.addAll(inUse);
      int failedModes = 0;

      for(TransportMode transportMode : TransportMode.values()) {
         try {
            TrainClientRegistry.forEach(transportMode, (id, properties) -> {
               if (id != null && !id.isBlank()) {
                  ids.add(id);
               }

            });
         } catch (Throwable var9) {
            ++failedModes;
         }
      }

      if (failedModes > 0 && ids.isEmpty()) {
         LteLog.LOGGER.warn("[lte] beam3d: train registry not readable ({} modes failed)", failedModes);
      }

      List<BeamEditor3DScreen.VehicleRow> rows = new ArrayList(ids.size());

      for(String id : ids) {
         CustomPack.Train train = imported == null ? null : imported.train(id);
         rows.add(new BeamEditor3DScreen.VehicleRow(id, containsIgnoreCase(inUse, id), train));
      }

      if (imported != null) {
         for(CustomPack.Train train : imported.trains()) {
            if (!ids.contains(train.id)) {
               rows.add(new BeamEditor3DScreen.VehicleRow(train.id, containsIgnoreCase(inUse, train.id), train));
            }
         }
      }

      rows.sort(Comparator.comparing((BeamEditor3DScreen.VehicleRow row) -> !row.inUse).thenComparing((BeamEditor3DScreen.VehicleRow row) -> row.id, String.CASE_INSENSITIVE_ORDER));
      return rows;
   }

   private static boolean containsIgnoreCase(Set<String> set, String value) {
      for(String candidate : set) {
         if (candidate.equalsIgnoreCase(value)) {
            return true;
         }
      }

      return false;
   }

   private static int worldTrainCount() {
      try {
         return BeamSession.inUseIds().size();
      } catch (Throwable var1) {
         return 0;
      }
   }

   private static boolean builtinsPruned() {
      try {
         HeadlightConfig cfg = MtrHeadlightClient.config();
         return cfg != null && cfg.disableBuiltInTrains;
      } catch (Throwable var1) {
         return false;
      }
   }

   private String defaultVehicle() {
      for(BeamEditor3DScreen.VehicleRow row : this.vehicles) {
         if (row.inUse) {
            return row.id;
         }
      }

      return this.vehicles.isEmpty() ? null : ((BeamEditor3DScreen.VehicleRow)this.vehicles.get(0)).id;
   }

   protected void init() {
      if (this.vehicles.isEmpty()) {
         this.vehicles = catalog(this.imported);
      }

      LteLog.LOGGER.info("[lte] beam3d: vehicles={} vehicleId={} loaded={}", new Object[]{this.vehicles.size(), describe(this.vehicleId), describe(this.loadedId)});
      if (!this.vehicles.isEmpty() || this.vehicleId != null && !this.vehicleId.isBlank()) {
         this.empty = false;
         if (this.vehicleId == null || this.vehicleId.isBlank()) {
            this.vehicleId = this.defaultVehicle();
         }

         this.viewX0 = this.leftW() + 8;
         this.viewY0 = 36;
         this.viewX1 = Math.max(this.viewX0 + 40, this.width - this.rightW() - 8);
         this.viewY1 = Math.max(this.viewY0 + 40, this.height - 20 - 8);
         if (this.loadedId == null || !this.loadedId.equals(this.vehicleId)) {
            this.openVehicle(this.vehicleId);
         }

         this.modeButtons.clear();
         if (this.session != null && this.selected >= this.session.size()) {
            this.selected = this.session.size() - 1;
            this.session.selected.clear();
            if (this.selected >= 0) {
               this.session.selected.add(this.selected);
            }
         }

         this.buildToolbar();
         this.buildVehicleSearch();
         this.buildImportBar();
         this.applyFilter();
         this.buildRightColumn();
      } else {
         LteLog.LOGGER.warn("[lte] beam3d: no vehicles (pack=none builtinPruned={} trainsInWorld={})", builtinsPruned(), worldTrainCount());
         this.empty = true;
         this.addRenderableWidget(Button.builder(tr("beam3d.close"), (b) -> this.onClose()).bounds(this.width / 2 - 60, this.height / 2 + 30, 120, 18).build());
         this.addRenderableWidget(Button.builder(tr("beam3d.import"), (b) -> this.openImport()).bounds(this.width / 2 - 90, this.height / 2 + 54, 180, 18).build());
      }
   }

   private void buildImportBar() {
      int width = Math.max(40, this.leftW() - 16);
      int y = this.height - 20 - 8 - 18;
      String label = this.imported == null ? tr("beam3d.import").getString() : tr("beam3d.imported", this.imported.label).getString();
      this.importButton = (Button)this.addRenderableWidget(Button.builder(Component.literal(this.font.plainSubstrByWidth(label, width - 6)), (b) -> this.openImport()).bounds(8, y, width, 18).build());
   }

   private void openImport() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new PackImportScreen(this, this::acceptImport));
      }
   }

   private void acceptImport(CustomPack pack) {
      if (pack != null) {
         if (this.imported != null && this.imported != pack) {
            this.imported.release();
         }

         this.imported = pack;
         this.vehicles = catalog(pack);
         this.shown = this.vehicles;
         this.search = "";
         this.scrollRow = 0;
         this.loadedId = null;
         CustomPack.Train first = pack.trains().isEmpty() ? null : (CustomPack.Train)pack.trains().get(0);
         if (first != null) {
            this.vehicleId = first.id;
         }

         this.status = tr("beam3d.import.done", pack.label, pack.trains().size()).getString();
         LteLog.LOGGER.info("[lte] beam3d: imported {} ({} trains)", pack, pack.trains().size());
         this.rebuild();
      }
   }

   private int leftW() {
      int overflow = Math.max(0, 460 - this.width);
      return Math.max(104, 150 - (overflow + 1) / 2);
   }

   private int rightW() {
      int overflow = Math.max(0, 460 - this.width);
      return Math.max(112, 130 - overflow / 2);
   }

   private void openVehicle(String id) {
      this.loadedId = id;
      CustomPack.Train train = this.imported == null ? null : this.imported.train(id);
      if (train != null) {
         ModelTrainBase model = train.model();
         DynamicTrainModel dynamic = train.dynamic();
         VehicleLights.Layout layout = dynamic == null ? null : VehicleLights.layoutFor(dynamic, 0);
         this.subject = model == null ? null : TrainPreview.imported(train.id, train.pack.label, train.texture(), model, layout);
         this.session = BeamSession.open(id, layout);
         if (model == null) {
            LteLog.LOGGER.warn("[lte] beam3d: imported model unavailable for {} ({})", id, train.error());
         }
      } else {
         this.subject = id == null ? null : TrainPreview.find(id);
         this.session = id == null ? null : BeamSession.open(id);
      }

      this.selected = -1;
      if (this.session != null) {
         this.session.selected.clear();
      }

      if (this.subject != null) {
         this.camera.targetY = (float)this.subject.centreY();
         this.camera.scale = this.subject.fitScale(this.viewY1 - this.viewY0, this.carCount);
         this.camera.panX = 0.0F;
         this.camera.panY = 0.0F;
      }

      if (id != null && !id.isBlank()) {
         List<VehicleLightOverrides.Beam> saved = train != null ? this.imported.readBeams(train) : BeamPackWriter.read(id);
         if (!saved.isEmpty() && this.session != null) {
            this.session.work.clear();
            this.session.base.clear();

            for(VehicleLightOverrides.Beam beam : saved) {
               VehicleLightOverrides.Beam materialised = BeamSession.materialise(beam, (VehicleLightOverrides.Beam)null);
               this.session.work.add(materialised);
               this.session.base.add(materialised.copy());
            }

            this.status = tr("beam3d.loadedFile", train != null ? (train.loadedFrom == null ? train.beamPath() : train.loadedFrom) : BeamPackWriter.fileName(id), this.session.size()).getString();
         }
      }

   }

   private void buildToolbar() {
      int gap = 4;
      int available = Math.max(192, this.width - this.leftW() - 16);
      int width = Math.max(48, Math.min(92, (available - 12) / 4));

      for(int i = 0; i < 4; ++i) {
         int index = i;
         Button button = (Button)this.addRenderableWidget(Button.builder(tr(MODE_KEYS[i]), (b) -> {
            this.mode = index;
            this.status = tr(MODE_KEYS[index]).getString();
         }).bounds(8 + i * (width + 4), 5, width, 18).build());
         this.modeButtons.add(button);
      }

   }

   private void buildVehicleSearch() {
      int w = Math.max(40, this.leftW() - 16);
      EditBox box = new EditBox(this.font, 8, 46, w, 16, tr("beam3d.search"));
      box.setMaxLength(64);
      box.setValue(this.search == null ? "" : this.search);
      box.setResponder((value) -> {
         this.search = value == null ? "" : value;
         this.scrollRow = 0;
         this.applyFilter();
      });
      this.searchBox = box;
      this.addRenderableWidget(box);
   }

   private void applyFilter() {
      String query = this.search == null ? "" : this.search.trim().toLowerCase(Locale.ROOT);
      if (query.isEmpty()) {
         this.shown = this.vehicles;
      } else {
         List<BeamEditor3DScreen.VehicleRow> hits = new ArrayList();

         for(BeamEditor3DScreen.VehicleRow row : this.vehicles) {
            if (row.id.toLowerCase(Locale.ROOT).contains(query) || row.name().toLowerCase(Locale.ROOT).contains(query) || row.pack().toLowerCase(Locale.ROOT).contains(query)) {
               hits.add(row);
            }
         }

         this.shown = hits;
      }

      this.clampScroll();
   }

   private void clampScroll() {
      int max = Math.max(0, this.shown.size() - this.visibleRows());
      if (this.scrollRow > max) {
         this.scrollRow = max;
      }

      if (this.scrollRow < 0) {
         this.scrollRow = 0;
      }

   }

   private int rowW() {
      return Math.max(24, this.leftW() - 16 - 6 - 2);
   }

   private int listBottom() {
      return 68 + this.visibleRows() * 22 - 4;
   }

   private int rowAt(double mouseX, double mouseY) {
      if (!(mouseX < 8.0D) && !(mouseX > (double)(8 + this.rowW()))) {
         if (!(mouseY < 68.0D) && !(mouseY >= (double)(68 + this.visibleRows() * 22))) {
            int index = this.scrollRow + (int)((mouseY - 68.0D) / 22.0D);
            return index >= 0 && index < this.shown.size() ? index : -1;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private int[] scrollbar() {
      int total = this.shown.size();
      int visible = this.visibleRows();
      if (total <= visible) {
         return null;
      } else {
         int trackTop = 68;
         int trackLen = Math.max(1, this.listBottom() - 68);
         int thumbLen = Math.max(14, (int)((long)trackLen * (long)visible / (long)total));
         int max = Math.max(1, total - visible);
         int thumbTop = 68 + (int)Math.round((double)(trackLen - thumbLen) * (double)this.scrollRow / (double)max);
         return new int[]{68, trackLen, thumbTop, thumbLen};
      }
   }

   private int scrollbarX() {
      return this.leftW() - 8 - 6;
   }

   private void selectVehicle(int index) {
      if (index >= 0 && index < this.shown.size()) {
         BeamEditor3DScreen.VehicleRow row = (BeamEditor3DScreen.VehicleRow)this.shown.get(index);
         if (this.vehicleId == null || !this.vehicleId.equalsIgnoreCase(row.id)) {
            this.vehicleId = row.id;
            if (this.searchBox != null) {
               this.searchBox.setFocused(false);
            }

            this.rebuild();
         }
      }
   }

   private int visibleRows() {
      return Math.max(1, (this.height - 20 - 8 - 18 - 6 - 68) / 22);
   }

   private void buildRightColumn() {
      int x = this.width - this.rightW() + 8;
      int width = Math.max(40, this.rightW() - 16);
      int buttonPitch = 20;
      int bottom = this.height - 20 - 8;
      int y = 36;
      this.addRenderableWidget(Button.builder(tr("beam3d.add"), (b) -> this.addBeam()).bounds(x, y, width, 18).build());
      y += 20;
      this.duplicateButton = (Button)this.addRenderableWidget(Button.builder(tr("beam3d.duplicate"), (b) -> this.duplicateBeam()).bounds(x, y, width, 18).build());
      y += 20;
      this.deleteButton = (Button)this.addRenderableWidget(Button.builder(tr("beam3d.delete"), (b) -> this.deleteBeam()).bounds(x, y, width, 18).build());
      y += 20;
      Button typeButton = (Button)this.addRenderableWidget(Button.builder(this.typeLabel(), (b) -> this.cycleType()).bounds(x, y, width, 18).build());
      typeButton.active = this.selected >= 0;
      y += 20;
      this.addRenderableWidget(Button.builder(this.carsLabel(), (b) -> this.cycleCars()).bounds(x, y, width, 18).build());
      y += 20;
      this.addRenderableWidget(Button.builder(tr("beam3d.export"), (b) -> this.export()).bounds(x, y, width, 18).build());
      y += 20;
      this.addRenderableWidget(Button.builder(tr("beam3d.openDir"), (b) -> this.openExportDir()).bounds(x, y, width, 18).build());
      y += 20;
      this.addRenderableWidget(Button.builder(tr("beam3d.close"), (b) -> this.onClose()).bounds(x, y, width, 18).build());
      y += 24;
      this.transformY = -1;
      this.transformButtons.clear();
      int trRowH = 14;
      int trGap = 2;
      int trBlock = 69;
      if (y + 69 + 40 <= bottom) {
         int half = Math.max(30, (width - 2) / 2);
         this.transformY = y;
         y += 11;

         for(int axis = 0; axis < 3; ++axis) {
            int axisIdx = axis;
            this.transformButtons.add((Button)this.addRenderableWidget(Button.builder(tr("beam3d.transform.flip", BeamGizmo.nameOf(axis)), (b) -> this.flipAxis(axisIdx)).bounds(x, y, half, 14).build()));
            this.transformButtons.add((Button)this.addRenderableWidget(Button.builder(tr("beam3d.transform.rot90", BeamGizmo.nameOf(axis)), (b) -> this.rotateAxis90(axisIdx)).bounds(x + half + 2, y, half, 14).build()));
            y += 16;
         }

         y += 3;
      }

      this.colorSectionY = y;
      this.geometrySectionY = -1;
      this.propertiesY = -1;
      this.hintY = -1;
      this.paletteY = -1;
      this.swatchW = 0;
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         this.hintY = y + 4;
      } else {
         int sliderH = 16;
         int paletteH = 30;
         if (y + 12 + 30 > bottom) {
            this.hintY = y + 4;
         } else {
            y += 12;
            this.paletteY = y;
            this.swatchW = Math.max(8, (width - 6) / 4);
            y += 34;
            boolean roomForSliders = y + 18 + 4 + 54 <= bottom;
            if (roomForSliders) {
               this.addRenderableWidget(Button.builder(tr("beam3d.color.custom"), (b) -> this.openColorPicker()).bounds(x, y, width, 18).build());
               y += 22;
               this.addBeamSlider(x, y, width, 16, 0);
               y += 18;
               this.addBeamSlider(x, y, width, 16, 1);
               y += 18;
               this.addBeamSlider(x, y, width, 16, 2);
               y += 22;
            }

            int geometryBlock = 66;
            if (roomForSliders && this.height >= 320 && y + 66 <= bottom) {
               this.geometrySectionY = y;
               y += 12;
               this.addBeamSlider(x, y, width, 16, 3);
               y += 18;
               this.addBeamSlider(x, y, width, 16, 4);
               y += 18;
               this.addBeamSlider(x, y, width, 16, 5);
               y += 22;
               this.propertiesY = y;
            }

         }
      }
   }

   private void addBeamSlider(int x, int y, int w, int h, int kind) {
      this.addRenderableWidget(new BeamEditor3DScreen.BeamSlider(x, y, w, h, kind));
   }

   private int swatchAt(double mouseX, double mouseY) {
      if (this.paletteY >= 0 && this.swatchW > 0 && this.selectedBeam() != null) {
         int x0 = this.width - this.rightW() + 8;

         for(int i = 0; i < PALETTE.length; ++i) {
            int x = x0 + i % 4 * (this.swatchW + 2);
            int y = this.paletteY + i / 4 * 16;
            if (mouseX >= (double)x && mouseX < (double)(x + this.swatchW) && mouseY >= (double)y && mouseY < (double)(y + 14)) {
               return i;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   private void drawPalette(GuiGraphics context, int mouseX, int mouseY) {
      if (this.paletteY >= 0 && this.swatchW > 0) {
         VehicleLightOverrides.Beam beam = this.selectedBeam();
         if (beam != null) {
            int current = color(beam) & 16777215;
            int x0 = this.width - this.rightW() + 8;

            for(int i = 0; i < PALETTE.length; ++i) {
               int rgb = PALETTE[i] & 16777215;
               int x = x0 + i % 4 * (this.swatchW + 2);
               int y = this.paletteY + i / 4 * 16;
               boolean hover = mouseX >= x && mouseX < x + this.swatchW && mouseY >= y && mouseY < y + 14;
               context.fill(x, y, x + this.swatchW, y + 14, -16777216 | rgb);
               if (rgb == current) {
                  context.fill(x - 1, y - 1, x + this.swatchW + 1, y, -1);
                  context.fill(x - 1, y + 14, x + this.swatchW + 1, y + 14 + 1, -1);
                  context.fill(x - 1, y, x, y + 14, -1);
                  context.fill(x + this.swatchW, y, x + this.swatchW + 1, y + 14, -1);
               } else {
                  context.fill(x, y, x + this.swatchW, y + 1, hover ? -3092272 : -11513776);
                  context.fill(x, y + 14 - 1, x + this.swatchW, y + 14, -14013910);
               }

               if (hover) {
                  context.fill(x, y, x + this.swatchW, y + 14, 822083583);
               }
            }

         }
      }
   }

   private void applyPalette(int index) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null && index >= 0 && index < PALETTE.length) {
         beam.color = PALETTE[index] & 16777215;
         if (this.session != null) {
            this.session.save();
         }

         this.status = tr("beam3d.color.applied", BeamSession.hex(beam.color)).getString();
         this.rebuild();
      }
   }

   private void openColorPicker() {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null && this.minecraft != null) {
         int index = this.selected;
         this.minecraft.setScreen(new ColorPickerScreen(this, tr("beam3d.section.color").getString(), color(beam), (rgb) -> {
            VehicleLightOverrides.Beam target = this.session == null ? null : this.session.beam(index);
            if (target != null) {
               target.color = rgb & 16777215;
               this.session.save();
               this.status = tr("beam3d.color.applied", BeamSession.hex(target.color)).getString();
            }
         }));
      }
   }

   private void rebuild() {
      this.rebuildWidgets();
   }

   private VehicleLightOverrides.Beam selectedBeam() {
      return this.session == null ? null : this.session.beam(this.selected);
   }

   private void select(int index) {
      this.selected = index;
      if (this.session != null) {
         this.session.selected.clear();
         if (index >= 0) {
            this.session.selected.add(index);
         }
      }

   }

   private void addBeam() {
      if (this.session == null) {
         this.status = tr("beam3d.noModel").getString();
      } else {
         int index = this.session.add();
         this.select(index);
         this.status = tr("beam3d.added", index + 1).getString();
         this.rebuild();
      }
   }

   private void duplicateBeam() {
      if (this.session != null && this.selected >= 0) {
         int index = this.session.duplicate(this.selected);
         if (index >= 0) {
            this.select(index);
            this.status = tr("beam3d.copied", index + 1).getString();
            this.rebuild();
         }
      } else {
         this.status = tr("beam3d.needSelection").getString();
      }
   }

   private void deleteBeam() {
      if (this.session != null && this.selected >= 0) {
         this.select(this.selected);
         if (this.session.deleteSelected()) {
            this.selected = Math.min(this.selected, this.session.size() - 1);
            this.select(this.selected);
            this.status = tr("beam3d.deleted").getString();
            this.rebuild();
         } else {
            this.status = tr("beam3d.cannotDeleteLast").getString();
         }

      } else {
         this.status = tr("beam3d.needSelection").getString();
      }
   }

   private void cycleType() {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         beam.type = BeamSession.isHead(beam) ? "tail" : "head";
         this.session.save();
         this.status = tr("beam3d.type", BeamSession.typeName(beam.type)).getString();
         this.rebuild();
      }
   }

   private MutableComponent typeLabel() {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      return tr("beam3d.type", BeamSession.typeName(beam == null ? "head" : BeamSession.typeOf(beam)));
   }

   private void flipAxis(int axis) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         this.status = tr("beam3d.needSelection").getString();
      } else {
         double[] rot = beam.rot == null ? new double[]{0.0D, 0.0D, 0.0D} : beam.rot;
         double[] head = BeamGizmo.headDirection(rot);
         double[] turned = BeamGizmo.mirror(head, axis);
         beam.rot = BeamGizmo.rotFromHead(turned, rot[2]);
         if (beam.pos != null) {
            double[] pos = BeamGizmo.mirrorPoint(beam.pos, beam.pos, axis);
            beam.pos[0] = pos[0];
            beam.pos[1] = pos[1];
            beam.pos[2] = pos[2];
            round(beam.pos);
         }

         round(beam.rot);
         this.session.save();
         this.clearDeltas();
         this.status = tr("beam3d.transform.flipped", BeamGizmo.nameOf(axis), String.format(Locale.ROOT, "%.1f", beam.rot[0]), String.format(Locale.ROOT, "%.1f", beam.rot[1])).getString();
         LteLog.LOGGER.info("[lte] beam3d: flipped {} axis (beam {}) -> rot {} / {}", new Object[]{BeamGizmo.nameOf(axis), this.selected + 1, beam.rot[0], beam.rot[1]});
         this.rebuild();
      }
   }

   private void rotateAxis90(int axis) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         this.status = tr("beam3d.needSelection").getString();
      } else {
         double[] rot = beam.rot == null ? new double[]{0.0D, 0.0D, 0.0D} : beam.rot;
         double[] head = BeamGizmo.headDirection(rot);
         double[] turned = BeamGizmo.rotate(head, BeamGizmo.unit(axis), 90.0D);
         beam.rot = BeamGizmo.rotFromHead(turned, rot[2]);
         round(beam.rot);
         this.session.save();
         this.clearDeltas();
         this.status = tr("beam3d.transform.rotated", BeamGizmo.nameOf(axis), String.format(Locale.ROOT, "%+.1f", 90.0D)).getString();
         LteLog.LOGGER.info("[lte] beam3d: rotated {} 90deg (beam {}) -> rot {} / {}", new Object[]{BeamGizmo.nameOf(axis), this.selected + 1, beam.rot[0], beam.rot[1]});
         this.rebuild();
      }
   }

   private MutableComponent carsLabel() {
      return tr("beam3d.cars", this.carCount);
   }

   private void cycleCars() {
      this.carCount = this.carCount >= 8 ? 1 : this.carCount + 1;
      if (this.subject != null) {
         this.camera.scale = this.subject.fitScale(this.viewY1 - this.viewY0, this.carCount);
      }

      this.status = tr("beam3d.carsHint", this.carCount, String.format(Locale.ROOT, "%.1f", TrainPreview.carPitch(this.subject))).getString();
      this.rebuild();
   }

   private void export() {
      if (this.session != null) {
         CustomPack.Train importedTrain = this.imported == null ? null : this.imported.train(this.vehicleId);
         if (importedTrain != null) {
            this.exportToPack(importedTrain);
         } else {
            try {
               Path path = BeamPackWriter.write(this.vehicleId, this.session.work);
               List<VehicleLightOverrides.Beam> readBack = BeamPackWriter.read(this.vehicleId);
               int beams = this.session.size();
               if (readBack.size() != beams) {
                  this.status = tr("beam3d.exportFail", "read-back " + readBack.size() + " != " + beams).getString();
                  return;
               }

               this.status = tr("beam3d.exported", beams, path.toAbsolutePath().toString()).getString() + "  " + tr("beam3d.exportHint").getString();
               LteLog.LOGGER.info("[lte] beam3d: exported {} beams -> {}", beams, path);
            } catch (Throwable var5) {
               LteLog.LOGGER.warn("[lte] beam3d: export failed", var5);
               this.status = tr("beam3d.exportFail", var5.getClass().getSimpleName() + ": " + var5.getMessage()).getString();
            }

         }
      }
   }

   private void exportToPack(CustomPack.Train train) {
      try {
         int beams = this.session.size();
         CustomPack.Export result = this.imported.writeBeams(train, this.session.work);
         if (result.insidePack) {
            int readBack = this.imported.readBeams(train).size();
            if (readBack != beams) {
               this.status = tr("beam3d.exportFail", "read-back " + readBack + " != " + beams).getString();
               return;
            }

            this.status = tr("beam3d.exportedPack", beams, result.packRelative, result.path.toAbsolutePath().toString()).getString();
         } else {
            this.status = tr("beam3d.exportedZip", beams, result.path.toAbsolutePath().toString(), result.packRelative).getString();
         }

         LteLog.LOGGER.info("[lte] beam3d: exported {} beams -> {} (inPack={})", new Object[]{beams, result.path, result.insidePack});
      } catch (Throwable var5) {
         LteLog.LOGGER.warn("[lte] beam3d: export to pack failed", var5);
         this.status = tr("beam3d.exportFail", var5.getClass().getSimpleName() + ": " + var5.getMessage()).getString();
      }

   }

   private void openExportDir() {
      try {
         Path dir = BeamPackWriter.dir();
         Files.createDirectories(dir);
         Util.getPlatform().openFile(dir.toFile());
         this.status = tr("beam3d.openDirDone", dir.toAbsolutePath().toString()).getString();
         LteLog.LOGGER.info("[lte] beam3d: opened export dir {}", dir.toAbsolutePath());
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] beam3d: open export dir failed", var2);
         this.status = tr("beam3d.openDirFail", var2.getClass().getSimpleName() + ": " + var2.getMessage()).getString();
      }

   }

   private boolean inViewport(double x, double y) {
      return x >= (double)this.viewX0 && x <= (double)this.viewX1 && y >= (double)this.viewY0 && y <= (double)this.viewY1;
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.empty) {
         return super.mouseClicked(mouseX, mouseY, button);
      } else {
         if (button == 0 && mouseX < (double)this.leftW() && mouseY >= 28.0D) {
            int[] bar = this.scrollbar();
            if (bar != null && mouseX >= (double)this.scrollbarX() && mouseX <= (double)(this.scrollbarX() + 6) && mouseY >= (double)bar[0] && mouseY <= (double)(bar[0] + bar[1])) {
               this.scrollGrab = mouseY >= (double)bar[2] && mouseY <= (double)(bar[2] + bar[3]) ? mouseY - (double)bar[2] : (double)bar[3] / 2.0D;
               this.drag = 5;
               this.dragButton = button;
               this.applyScrollDrag(mouseY);
               return true;
            }

            int row = this.rowAt(mouseX, mouseY);
            if (row >= 0) {
               this.selectVehicle(row);
               return true;
            }
         }

         int swatch = this.swatchAt(mouseX, mouseY);
         if (button == 0 && swatch >= 0) {
            this.applyPalette(swatch);
            return true;
         } else if (this.inViewport(mouseX, mouseY)) {
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.lastX = mouseX;
            this.lastY = mouseY;
            this.dragButton = button;
            this.clearDeltas();
            if (button == 0) {
               int snap = this.snapButtonAt(mouseX, mouseY);
               if (snap >= 0) {
                  this.snapSelected(snap);
                  this.drag = 0;
                  return true;
               }

               if (this.mode == 1 && this.beginAxisDrag(mouseX, mouseY)) {
                  return true;
               }

               if (this.mode == 2 && this.beginRingDrag(mouseX, mouseY)) {
                  return true;
               }

               TrainPreview.Hit hit = this.session == null ? null : TrainPreview.pick(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, this.session.work);
               if (hit != null) {
                  this.select(hit.index);
                  if (this.mode == 1) {
                     this.drag = 3;
                     this.moveAnchor = null;
                     this.status = tr("beam3d.dragMove", hit.index + 1).getString();
                  } else if (this.mode == 2) {
                     this.drag = 4;
                     this.rotAnchor = null;
                     this.status = tr("beam3d.dragRotate", hit.index + 1).getString();
                  } else {
                     if (this.mode == 3) {
                        this.drag = 0;
                        this.openColorPicker();
                        return true;
                     }

                     this.drag = 1;
                  }

                  this.rebuild();
               } else {
                  if (this.selected >= 0) {
                     this.select(-1);
                     this.rebuild();
                  }

                  this.drag = 1;
               }
            } else if (button == 1) {
               this.drag = 2;
            } else {
               this.drag = 1;
            }

            return true;
         } else {
            return super.mouseClicked(mouseX, mouseY, button);
         }
      }
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
      if (this.drag != 0 && button == this.dragButton) {
         double dx = mouseX - this.lastX;
         double dy = mouseY - this.lastY;
         switch (this.drag) {
            case 1:
               this.camera.orbit(dx, dy);
               break;
            case 2:
               this.camera.pan(dx, dy);
               break;
            case 3:
               this.moveSelected(dx, dy);
               break;
            case 4:
               this.rotateSelected(dx, dy);
               break;
            case 5:
               this.applyScrollDrag(mouseY);
               break;
            case 6:
               this.moveAlongAxis(mouseX, mouseY);
               break;
            case 7:
               this.rotateAlongRing(mouseX, mouseY);
         }

         this.lastX = mouseX;
         this.lastY = mouseY;
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.drag != 0 && button == this.dragButton) {
         this.reportDragResult();
         this.drag = 0;
         this.dragButton = -1;
         this.moveAnchor = null;
         this.dragAxis = -1;
         this.axisAnchor = null;
         this.axisBase = null;
         this.axisGrab = Double.NaN;
         this.ringAnchorRot = null;
         this.ringBase = null;
         this.ringNormal = null;
         this.ringAngle0 = Double.NaN;
         this.rotAnchor = null;
         return true;
      } else {
         return super.mouseReleased(mouseX, mouseY, button);
      }
   }

   private void reportDragResult() {
      if (this.deltaShown) {
         if (this.drag == 6 && this.dragAxis >= 0) {
            this.status = this.movedStatus(this.dragAxis);
         } else if (this.drag == 7 && this.dragAxis >= 0) {
            this.status = this.rotatedStatus(BeamGizmo.rotIndex(this.dragAxis));
         } else if (this.drag == 3) {
            this.status = this.movedStatus(freeAxis(this.deltaPos));
         } else if (this.drag == 4) {
            this.status = this.rotatedStatus(freeAxis(this.deltaRot));
         }

      }
   }

   private static int freeAxis(double[] delta) {
      int best = 0;

      for(int axis = 1; axis < 3; ++axis) {
         if (Math.abs(delta[axis]) > Math.abs(delta[best])) {
            best = axis;
         }
      }

      return best;
   }

   private String movedStatus(int axis) {
      double value = this.deltaPos[axis];
      int count = BeamGizmo.steps(0.0D, value, this.deltaMoveStep);
      return count == 0 ? tr("beam3d.transform.unchanged").getString() : tr("beam3d.transform.moved", BeamGizmo.nameOf(axis), String.format(Locale.ROOT, "%+.2f", value), Math.abs(count), BeamGizmo.stepLabel(this.deltaMoveStep)).getString();
   }

   private String rotatedStatus(int component) {
      double value = this.deltaRot[component];
      return Math.abs(value) < 1.0E-9D ? tr("beam3d.transform.unchanged").getString() : tr("beam3d.transform.rotated", rotNameOf(component), String.format(Locale.ROOT, "%+.1f", value)).getString();
   }

   private static String rotNameOf(int component) {
      return component == 0 ? "Y" : (component == 1 ? "P" : "R");
   }

   private void clearDeltas() {
      for(int i = 0; i < 3; ++i) {
         this.deltaPos[i] = 0.0D;
         this.deltaRot[i] = 0.0D;
      }

      this.deltaShown = false;
   }

   private void moveSelected(double dx, double dy) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         if (beam.pos == null) {
            beam.pos = new double[]{0.0D, 0.0D, 0.0D};
         }

         if (this.moveAnchor == null) {
            this.moveAnchor = (double[])beam.pos.clone();
            this.moveAccumX = 0.0D;
            this.moveAccumY = 0.0D;
         }

         this.moveAccumX += dx;
         this.moveAccumY += dy;
         Matrix4f inverse = (new Matrix4f(TrainPreview.beamRotate(this.camera))).invert();
         Vector3f delta = inverse.transformDirection(new Vector3f((float)(this.moveAccumX / (double)this.camera.scale), (float)(-this.moveAccumY / (double)this.camera.scale), 0.0F));
         double step = BeamGizmo.moveStep(hasShiftDown());
         this.deltaMoveStep = step;

         for(int axis = 0; axis < 3; ++axis) {
            double raw = this.moveAnchor[axis] + (double)(axis == 0 ? delta.x : (axis == 1 ? delta.y : delta.z));
            beam.pos[axis] = clamp(BeamGizmo.quantize(this.moveAnchor[axis], raw, step), -16.0D, 16.0D);
            this.deltaPos[axis] = beam.pos[axis] - this.moveAnchor[axis];
         }

         this.deltaShown = true;
         round(beam.pos);
         this.session.save();
      }
   }

   private boolean beginAxisDrag(double mouseX, double mouseY) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         return false;
      } else {
         if (beam.pos == null) {
            beam.pos = new double[]{0.0D, 0.0D, 0.0D};
         }

         TrainPreview.AxisHit hit = TrainPreview.pickAxis(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, beam.pos, TrainPreview.carOffsets(this.subject, this.carCount), BeamGizmo.shaftSize(this.camera.scale));
         if (hit == null) {
            return false;
         } else {
            this.axisBase = new double[]{beam.pos[0] + hit.ox, beam.pos[1], beam.pos[2] + hit.oz};
            this.axisGrab = TrainPreview.axisCoordinate(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, this.axisBase, TrainPreview.axisUnit(hit.axis));
            if (Double.isNaN(this.axisGrab)) {
               this.axisBase = null;
               return false;
            } else {
               this.drag = 6;
               this.dragAxis = hit.axis;
               this.axisAnchor = (double[])beam.pos.clone();
               this.status = tr("beam3d.dragAxis", TrainPreview.axisName(hit.axis), this.selected + 1).getString();
               return true;
            }
         }
      }
   }

   private void moveAlongAxis(double mouseX, double mouseY) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null && beam.pos != null && this.dragAxis >= 0 && this.axisAnchor != null && this.axisBase != null) {
         double t = TrainPreview.axisCoordinate(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, this.axisBase, TrainPreview.axisUnit(this.dragAxis));
         if (!Double.isNaN(t)) {
            int axis = this.dragAxis;
            double step = BeamGizmo.moveStep(hasShiftDown());
            this.deltaMoveStep = step;
            double raw = this.axisAnchor[axis] + (t - this.axisGrab);
            beam.pos[axis] = clamp(BeamGizmo.quantize(this.axisAnchor[axis], raw, step), -16.0D, 16.0D);
            beam.pos[(axis + 1) % 3] = this.axisAnchor[(axis + 1) % 3];
            beam.pos[(axis + 2) % 3] = this.axisAnchor[(axis + 2) % 3];
            this.deltaPos[axis] = beam.pos[axis] - this.axisAnchor[axis];
            this.deltaRot[0] = 0.0D;
            this.deltaRot[1] = 0.0D;
            this.deltaRot[2] = 0.0D;
            this.deltaShown = true;
            round(beam.pos);
            this.session.save();
         }
      }
   }

   private boolean beginRingDrag(double mouseX, double mouseY) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         return false;
      } else {
         if (beam.pos == null) {
            beam.pos = new double[]{0.0D, 0.0D, 0.0D};
         }

         if (beam.rot == null) {
            beam.rot = new double[]{0.0D, 0.0D, 0.0D};
         }

         TrainPreview.RingHit hit = TrainPreview.pickRing(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, beam.pos, beam.rot, TrainPreview.carOffsets(this.subject, this.carCount));
         if (hit != null && !Double.isNaN(hit.angle) && hit.planar) {
            this.ringBase = new double[]{beam.pos[0] + hit.ox, beam.pos[1], beam.pos[2] + hit.oz};
            this.ringNormal = BeamGizmo.ringNormal(hit.axis, beam.rot);
            this.ringAngle0 = hit.angle;
            this.ringPlanar = hit.planar;
            this.ringAnchorRot = (double[])beam.rot.clone();
            this.dragAxis = hit.axis;
            this.drag = 7;
            this.status = tr("beam3d.dragRing", BeamGizmo.nameOf(hit.axis), this.selected + 1, BeamGizmo.stepLabel(2.5D)).getString();
            return true;
         } else {
            return false;
         }
      }
   }

   private void rotateAlongRing(double mouseX, double mouseY) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null && this.dragAxis >= 0 && this.ringAnchorRot != null && this.ringBase != null && this.ringNormal != null && !Double.isNaN(this.ringAngle0)) {
         if (beam.rot == null) {
            beam.rot = new double[]{0.0D, 0.0D, 0.0D};
         }

         int axis = this.dragAxis;
         double angle = TrainPreview.ringAngle(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, this.ringBase, this.ringNormal, this.ringPlanar);
         if (!Double.isNaN(angle)) {
            double swept = wrapRadians(angle - this.ringAngle0) * 180.0D / Math.PI * (double)BeamGizmo.ringSign(axis);
            double step = BeamGizmo.rotStep(hasShiftDown());
            int component = BeamGizmo.rotIndex(axis);
            double value = BeamGizmo.quantize(this.ringAnchorRot[component], this.ringAnchorRot[component] + swept, step);
            if (component == 1) {
               value = clamp(value, -90.0D, 90.0D);
            } else {
               value = BeamGizmo.wrap(value);
            }

            beam.rot[component] = value;
            beam.rot[(component + 1) % 3] = this.ringAnchorRot[(component + 1) % 3];
            beam.rot[(component + 2) % 3] = this.ringAnchorRot[(component + 2) % 3];
            this.deltaRot[component] = value - this.ringAnchorRot[component];
            this.deltaPos[0] = 0.0D;
            this.deltaPos[1] = 0.0D;
            this.deltaPos[2] = 0.0D;
            this.deltaShown = true;
            round(beam.rot);
            this.session.save();
         }
      }
   }

   private static double wrapRadians(double radians) {
      double r = radians % (Math.PI * 2D);
      if (r > Math.PI) {
         r -= (Math.PI * 2D);
      }

      if (r <= -Math.PI) {
         r += (Math.PI * 2D);
      }

      return r;
   }

   private int gizmoUnder(double mouseX, double mouseY) {
      if (this.drag != 6 && this.drag != 7) {
         if (this.drag == 0 && (this.mode == 1 || this.mode == 2)) {
            VehicleLightOverrides.Beam beam = this.selectedBeam();
            if (beam != null && beam.pos != null) {
               double[][] offsets = TrainPreview.carOffsets(this.subject, this.carCount);
               if (this.mode == 1) {
                  TrainPreview.AxisHit hit = TrainPreview.pickAxis(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, beam.pos, offsets, BeamGizmo.shaftSize(this.camera.scale));
                  return hit == null ? -1 : hit.axis;
               } else {
                  TrainPreview.RingHit hit = TrainPreview.pickRing(this.camera, mouseX, mouseY, this.viewX0, this.viewY0, this.viewX1, this.viewY1, beam.pos, beam.rot, offsets);
                  return hit != null && hit.planar ? hit.axis : -1;
               }
            } else {
               return -1;
            }
         } else {
            return -1;
         }
      } else {
         return this.dragAxis;
      }
   }

   private int snapX(int dir) {
      return this.viewX0 + 4 + dir % 3 * 43;
   }

   private int snapY(int dir) {
      return this.snapTop() + dir / 3 * 17;
   }

   private int snapTop() {
      return Math.max(this.viewY0 + 20, this.viewY1 - 6 - 28 - 3);
   }

   private int snapButtonAt(double mouseX, double mouseY) {
      if (this.selectedBeam() == null) {
         return -1;
      } else {
         for(int dir = 0; dir < 6; ++dir) {
            int x = this.snapX(dir);
            int y = this.snapY(dir);
            if (mouseX >= (double)x && mouseX <= (double)(x + 40) && mouseY >= (double)y && mouseY <= (double)(y + 14)) {
               return dir;
            }
         }

         return -1;
      }
   }

   private void snapSelected(int dir) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         this.status = tr("beam3d.needSelection").getString();
      } else {
         double[] rot = BeamSnap.apply(beam, dir);
         this.session.save();
         this.status = tr("beam3d.snapDone", tr(BeamSnap.key(dir)), String.format(Locale.ROOT, "%.0f", rot[0]), String.format(Locale.ROOT, "%.0f", rot[1])).getString();
      }
   }

   private void drawSnapButtons(GuiGraphics context, int mouseX, int mouseY) {
      if (this.selectedBeam() != null) {
         context.drawString(this.font, tr("beam3d.snap.title"), this.viewX0 + 4, this.snapTop() - 11, 9474192);

         for(int dir = 0; dir < 6; ++dir) {
            int x = this.snapX(dir);
            int y = this.snapY(dir);
            boolean hover = mouseX >= x && mouseX <= x + 40 && mouseY >= y && mouseY <= y + 14;
            context.fill(x, y, x + 40, y + 14, hover ? -13020102 : -1071636448);
            context.fill(x, y, x + 40, y + 1, hover ? -6422627 : -10855846);
            context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(tr(BeamSnap.key(dir)).getString(), 36)), x + 20, y + 3, hover ? -1 : 13158600);
         }

      }
   }

   private static int flipKeyOf(int keyCode) {
      switch (keyCode) {
         case 88:
            return 0;
         case 89:
            return 1;
         case 90:
            return 2;
         default:
            return -1;
      }
   }

   private static int snapKeyOf(int keyCode) {
      switch (keyCode) {
         case 66:
            return 1;
         case 67:
         case 69:
         case 71:
         case 72:
         case 73:
         case 74:
         case 75:
         case 77:
         case 78:
         case 79:
         case 80:
         case 81:
         case 83:
         case 84:
         default:
            return -1;
         case 68:
            return 3;
         case 70:
            return 0;
         case 76:
            return 4;
         case 82:
            return 5;
         case 85:
            return 2;
      }
   }

   private void rotateSelected(double dx, double dy) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         if (beam.rot == null) {
            beam.rot = new double[]{0.0D, 0.0D, 0.0D};
         }

         if (this.rotAnchor == null) {
            this.rotAnchor = (double[])beam.rot.clone();
            this.rotAccumX = 0.0D;
            this.rotAccumY = 0.0D;
         }

         this.rotAccumX += dx;
         this.rotAccumY += dy;
         double step = BeamGizmo.rotStep(hasShiftDown());
         double yaw = BeamGizmo.quantize(this.rotAnchor[0], this.rotAnchor[0] + this.rotAccumX * 0.9D, step);
         double pitch = clamp(BeamGizmo.quantize(this.rotAnchor[1], this.rotAnchor[1] + this.rotAccumY * 0.9D, step), -90.0D, 90.0D);
         beam.rot[0] = BeamGizmo.wrap(yaw);
         beam.rot[1] = pitch;
         beam.rot[2] = this.rotAnchor[2];
         this.deltaRot[0] = beam.rot[0] - this.rotAnchor[0];
         this.deltaRot[1] = beam.rot[1] - this.rotAnchor[1];
         this.deltaRot[2] = 0.0D;
         this.deltaPos[0] = 0.0D;
         this.deltaPos[1] = 0.0D;
         this.deltaPos[2] = 0.0D;
         this.deltaShown = true;
         round(beam.rot);
         this.session.save();
      }
   }

   private void applyScrollDrag(double mouseY) {
      int[] bar = this.scrollbar();
      if (bar != null) {
         double travel = (double)(bar[1] - bar[3]);
         int max = Math.max(1, this.shown.size() - this.visibleRows());
         if (travel <= 0.0D) {
            this.scrollRow = 0;
         } else {
            double t = (mouseY - this.scrollGrab - (double)bar[0]) / travel;
            this.scrollRow = (int)Math.round(clamp(t, 0.0D, 1.0D) * (double)max);
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.empty) {
         return super.mouseScrolled(mouseX, mouseY, amount);
      } else if (this.inViewport(mouseX, mouseY) && amount != 0.0D) {
         this.camera.zoom(amount);
         return true;
      } else if (mouseX < (double)this.leftW() && mouseY >= 68.0D && this.shown.size() > this.visibleRows() && amount != 0.0D) {
         this.scrollAccum += amount;
         int steps = (int)this.scrollAccum;
         if (steps != 0) {
            this.scrollAccum -= (double)steps;
            this.scrollRow -= steps;
            this.clampScroll();
         }

         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.empty) {
         return super.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.searchBox != null && this.searchBox.isFocused()) {
         return super.keyPressed(keyCode, scanCode, modifiers);
      } else if (keyCode == 261) {
         this.deleteBeam();
         return true;
      } else if (keyCode >= 49 && keyCode <= 52) {
         this.mode = keyCode - 49;
         return true;
      } else if (keyCode == 68 && (modifiers & 2) != 0) {
         this.duplicateBeam();
         return true;
      } else {
         if ((modifiers & 4) != 0) {
            int axis = flipKeyOf(keyCode);
            if (axis >= 0) {
               if ((modifiers & 1) != 0) {
                  this.rotateAxis90(axis);
               } else {
                  this.flipAxis(axis);
               }

               return true;
            }
         }

         int snapKey = snapKeyOf(keyCode);
         if (snapKey >= 0) {
            this.snapSelected(snapKey);
            return true;
         } else {
            return super.keyPressed(keyCode, scanCode, modifiers);
         }
      }
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, -267382768);
      if (this.empty) {
         this.drawEmptyState(context);
         super.render(context, mouseX, mouseY, delta);
      } else {
         this.hoverAxis = this.gizmoUnder((double)mouseX, (double)mouseY);
         int gizmo = this.selected < 0 ? 0 : (this.mode == 1 ? 1 : (this.mode == 2 ? 2 : 0));
         TrainPreview.draw(this.subject, context, this.camera, this.viewX0, this.viewY0, this.viewX1, this.viewY1, this.session == null ? List.of() : this.session.work, this.selected, this.carCount, gizmo, this.hoverAxis);
         context.fill(0, 0, this.width, 28, -535291880);
         context.fill(0, 28, this.leftW(), this.height, -535291880);
         context.fill(this.width - this.rightW(), 28, this.width, this.height, -535291880);
         context.fill(0, this.height - 20, this.width, this.height, -535291880);
         super.render(context, mouseX, mouseY, delta);
         this.drawToolbarState(context);
         String count = this.search != null && !this.search.isBlank() ? this.shown.size() + "/" + this.vehicles.size() : String.valueOf(this.vehicles.size());
         String listTitle = tr("beam3d.vehicles", count).getString();
         context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(listTitle, this.leftW() - 16)), 8, 34, 10526880);
         this.drawVehicleList(context, mouseX, mouseY);
         this.drawScrollbar(context);
         if (this.colorSectionY >= 0) {
            context.drawString(this.font, tr("beam3d.section.color"), this.width - this.rightW() + 8, this.colorSectionY, 12632256);
         }

         this.drawPalette(context, mouseX, mouseY);
         if (this.transformY >= 0) {
            int x = this.width - this.rightW() + 8;
            int w = Math.max(20, this.rightW() - 16);
            context.drawString(this.font, tr("beam3d.section.transform"), x, this.transformY, 12632256);
            String keyHint = this.font.plainSubstrByWidth(tr("beam3d.transform.hint").getString(), w);
            context.drawString(this.font, Component.literal(keyHint), x, this.transformY + 11 + 48 + 1, 8421504);

            for(int i = 0; i < this.transformButtons.size(); ++i) {
               ((Button)this.transformButtons.get(i)).active = this.selected >= 0;
            }
         }

         if (this.geometrySectionY >= 0) {
            context.drawString(this.font, tr("beam3d.section.geometry"), this.width - this.rightW() + 8, this.geometrySectionY, 12632256);
         }

         if (this.session != null && this.session.size() != 0) {
            if (this.hintY >= 0) {
               context.drawString(this.font, tr("beam3d.pickHint"), this.width - this.rightW() + 8, this.hintY, 9474192);
            }
         } else if (this.hintY >= 0) {
            context.drawString(this.font, tr("beam3d.emptyList"), this.width - this.rightW() + 8, this.hintY, 9474192);
         }

         this.drawViewportTexts(context);
         this.drawSnapButtons(context, mouseX, mouseY);
         this.drawFooter(context);
         if (this.deleteButton != null) {
            this.deleteButton.active = this.selected >= 0 && this.session != null && this.session.size() > 1;
         }

         if (this.duplicateButton != null) {
            this.duplicateButton.active = this.selected >= 0 && this.session != null;
         }

      }
   }

   private void drawVehicleList(GuiGraphics context, int mouseX, int mouseY) {
      if (this.shown.isEmpty()) {
         context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(tr("beam3d.noMatch").getString(), this.rowW() - 4)), 11, 72, 9474192);
      } else {
         if (this.searchBox != null && (this.search == null || this.search.isEmpty()) && !this.searchBox.isFocused()) {
            context.drawString(this.font, tr("beam3d.search"), 12, 50, 7368816);
         }

         int listW = this.rowW();
         int visible = this.visibleRows();

         for(int i = 0; i < visible; ++i) {
            int index = this.scrollRow + i;
            if (index >= this.shown.size()) {
               break;
            }

            BeamEditor3DScreen.VehicleRow row = (BeamEditor3DScreen.VehicleRow)this.shown.get(index);
            int y = 68 + i * 22;
            boolean active = this.vehicleId != null && this.vehicleId.equalsIgnoreCase(row.id);
            boolean hover = !active && mouseX >= 8 && mouseX <= 8 + listW && mouseY >= y && mouseY < y + 18;
            if (active) {
               context.fill(8, y, 8 + listW, y + 18, -13874644);
               context.fill(8, y, 10, y + 18, -8396673);
            } else if (hover) {
               context.fill(8, y, 8 + listW, y + 18, 822083583);
            }

            String label = (active ? "\u25b8 " : "") + row.label();
            int colour = active ? -1 : (row.imported != null ? (hover ? 12642559 : 10342655) : (hover ? 15263976 : 12632256));
            context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(label, listW - 4)), 12, y + 5, colour);
         }

      }
   }

   private void drawScrollbar(GuiGraphics context) {
      int[] bar = this.scrollbar();
      if (bar != null) {
         int x = this.scrollbarX();
         int bottom = bar[0] + bar[1];
         context.fill(x, bar[0], x + 6, bottom, -16777216);
         context.fill(x + 1, bar[0] + 1, x + 6 - 1, bottom - 1, -13619152);
         context.fill(x, bar[2], x + 6, bar[2] + bar[3], -6381922);
         context.fill(x, bar[2], x + 6, bar[2] + 1, -2565928);
         context.fill(x, bar[2] + bar[3] - 1, x + 6, bar[2] + bar[3], -10855846);
      }
   }

   private String currentName(String id) {
      CustomPack.Train train = this.imported == null ? null : this.imported.train(id);
      return train != null ? train.name : TrainNames.of(id);
   }

   private void drawEmptyState(GuiGraphics context) {
      int cx = this.width / 2;
      int cy = this.height / 2;
      context.drawCenteredString(this.font, tr("beam3d.noVehicles"), cx, cy - 34, 16765823);
      context.drawCenteredString(this.font, tr("beam3d.noVehiclesHint"), cx, cy - 18, 10526880);
      if (builtinsPruned()) {
         context.drawCenteredString(this.font, tr("beam3d.prunedHint"), cx, cy - 2, 16744576);
      }

      context.drawCenteredString(this.font, Component.literal("/mtr_lte beam3d <\u8f66\u578bid>"), cx, cy + 14, 7368816);
   }

   private void drawToolbarState(GuiGraphics context) {
      for(int i = 0; i < this.modeButtons.size(); ++i) {
         if (i == this.mode) {
            Button button = (Button)this.modeButtons.get(i);
            context.fill(button.getX() - 1, button.getY() - 1, button.getX() + button.getWidth() + 1, button.getY(), -1);
            context.fill(button.getX() - 1, button.getY() + button.getHeight(), button.getX() + button.getWidth() + 1, button.getY() + button.getHeight() + 1, -1);
         }
      }

   }

   private void drawViewportTexts(GuiGraphics context) {
      int left = this.viewX0 + 4;
      int top = this.viewY0 + 4;
      if (this.subject != null) {
         String name = this.currentName(this.subject.id);
         String pack = this.subject.pack + " \u00b7 " + name + (name.equalsIgnoreCase(this.subject.id) ? "" : "  (" + this.subject.id + ")");
         context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(pack, this.viewX1 - this.viewX0 - 8)), left, top, 10354589);
         if (this.carCount > 1) {
            Component badge = tr("beam3d.carsBadge", this.carCount, String.format(Locale.ROOT, "%.1f", TrainPreview.carPitch(this.subject)));
            context.drawString(this.font, badge, this.viewX1 - 6 - this.font.width(badge), top, 10342655);
         }
      } else if (this.vehicleId != null) {
         context.drawCenteredString(this.font, tr("beam3d.noModel"), (this.viewX0 + this.viewX1) / 2, (this.viewY0 + this.viewY1) / 2 - 8, 16765823);
         context.drawCenteredString(this.font, tr("beam3d.noModelHint"), (this.viewX0 + this.viewX1) / 2, (this.viewY0 + this.viewY1) / 2 + 8, 9474192);
      }

      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         String head = tr("beam3d.selected", this.selected + 1, BeamSession.typeName(BeamSession.typeOf(beam)), BeamSession.hex(beam.color)).getString();
         context.drawString(this.font, Component.literal(head), left, top + 12, 16769162);
         if (this.mode == 1) {
            context.drawString(this.font, tr("beam3d.axisHint"), left, top + 24, 10342655);
         } else if (this.mode == 2) {
            context.drawString(this.font, tr("beam3d.ringHint"), left, top + 24, 10342655);
         }

         this.drawDelta(context);
         if (this.propertiesY >= 0) {
            this.drawValues(context, this.width - this.rightW() + 8, this.propertiesY, 14737632);
         } else {
            int line = top + (this.mode != 1 && this.mode != 2 ? 24 : 36);
            this.drawValues(context, left, line, 13158600);
         }
      } else if (this.session != null && this.session.size() > 0) {
         context.drawString(this.font, tr("beam3d.pickHint"), left, top + 12, 9474192);
      }

   }

   private void drawValues(GuiGraphics context, int x, int y, int colour) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         context.drawString(this.font, Component.literal("pos X " + fmtValue(axis(beam.pos, 0)) + "  Y " + fmtValue(axis(beam.pos, 1)) + "  Z " + fmtValue(axis(beam.pos, 2))), x, y, colour);
         context.drawString(this.font, Component.literal("rot Y " + fmtValue(axis(beam.rot, 0)) + "  P " + fmtValue(axis(beam.rot, 1)) + "  R " + fmtValue(axis(beam.rot, 2))), x, y + 11, colour);
      }
   }

   private void drawDelta(GuiGraphics context) {
      if (this.deltaShown) {
         String posLine = this.deltaLine(tr("beam3d.delta.pos").getString(), this.deltaPos, false);
         String rotLine = this.deltaLine(tr("beam3d.delta.rot").getString(), this.deltaRot, true);
         int y = this.viewY0 + 24;
         if (posLine != null) {
            context.drawString(this.font, Component.literal(posLine), Math.max(this.viewX0 + 4, this.viewX1 - 6 - this.font.width(posLine)), y, 15790272);
            y += 11;
         }

         if (rotLine != null) {
            context.drawString(this.font, Component.literal(rotLine), Math.max(this.viewX0 + 4, this.viewX1 - 6 - this.font.width(rotLine)), y, 12642544);
         }

      }
   }

   private String deltaLine(String prefix, double[] delta, boolean degrees) {
      StringBuilder out = new StringBuilder(prefix);

      for(int axis = 0; axis < 3; ++axis) {
         if (!(Math.abs(delta[axis]) < 1.0E-9D)) {
            out.append("  ").append(degrees ? rotNameOf(axis) : BeamGizmo.nameOf(axis)).append(' ');
            if (degrees) {
               out.append(String.format(Locale.ROOT, "%+.1f\u00b0", delta[axis]));
            } else {
               out.append(String.format(Locale.ROOT, "%+.4f", delta[axis]));
               int count = BeamGizmo.steps(0.0D, delta[axis], this.deltaMoveStep);
               if (count != 0) {
                  out.append(" (").append(Math.abs(count)).append('/').append(Math.abs(this.deltaMoveStep - 0.015625D) < 1.0E-9D ? 64 : 16).append(')');
               }
            }
         }
      }

      return out.length() == prefix.length() ? null : out.toString();
   }

   private static String fmtValue(double value) {
      String text = String.format(Locale.ROOT, "%.4f", value);
      if (text.indexOf(46) >= 0) {
         while(text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
         }

         if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
         }
      }

      return text.equals("-0") ? "0" : text;
   }

   private void drawFooter(GuiGraphics context) {
      String hint = this.status != null && !this.status.isEmpty() ? this.status : tr("beam3d.hint").getString();
      context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(hint, this.width - 16)), this.width / 2, this.height - 14, 12632256);
   }

   private static double axis(double[] vector, int index) {
      return vector != null && index < vector.length ? vector[index] : 0.0D;
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static double round(double[] vector) {
      for(int i = 0; i < vector.length; ++i) {
         vector[i] = (double)Math.round(vector[i] * 1000000.0D) / 1000000.0D;
      }

      return 0.0D;
   }

   private static double min(int kind) {
      switch (kind) {
         case 3:
            return 1.0D;
         case 4:
            return 0.5D;
         case 5:
            return 0.02D;
         default:
            return 0.0D;
      }
   }

   private static double max(int kind) {
      switch (kind) {
         case 3:
            return 64.0D;
         case 4:
            return 8.0D;
         case 5:
            return 0.5D;
         default:
            return 255.0D;
      }
   }

   private static double step(int kind) {
      switch (kind) {
         case 3:
         case 4:
         case 5:
            return 0.01D;
         default:
            return 1.0D;
      }
   }

   private static String labelKey(int kind) {
      switch (kind) {
         case 0:
            return "beam3d.slider.r";
         case 1:
            return "beam3d.slider.g";
         case 2:
            return "beam3d.slider.b";
         case 3:
            return "beam3d.slider.length";
         case 4:
            return "beam3d.slider.width";
         default:
            return "beam3d.slider.alpha";
      }
   }

   private double current(int kind) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam == null) {
         return min(kind);
      } else {
         switch (kind) {
            case 0:
               return (double)(color(beam) >> 16 & 255);
            case 1:
               return (double)(color(beam) >> 8 & 255);
            case 2:
               return (double)(color(beam) & 255);
            case 3:
               return beam.length == null ? BeamSession.defaultLength(BeamSession.typeOf(beam)) : beam.length;
            case 4:
               return beam.width == null ? BeamSession.defaultWidth() : beam.width;
            default:
               return beam.alpha == null ? BeamSession.defaultAlpha() : beam.alpha;
         }
      }
   }

   private static int color(VehicleLightOverrides.Beam beam) {
      return beam.color == null ? BeamSession.defaultColor(BeamSession.typeOf(beam)) : beam.color & 16777215;
   }

   private void apply(int kind, double raw) {
      VehicleLightOverrides.Beam beam = this.selectedBeam();
      if (beam != null) {
         switch (kind) {
            case 0:
               beam.color = color(beam) & '\uffff' | (int)Math.round(raw) << 16;
               break;
            case 1:
               beam.color = color(beam) & 16711935 | (int)Math.round(raw) << 8;
               break;
            case 2:
               beam.color = color(beam) & 16776960 | (int)Math.round(raw);
               break;
            case 3:
               beam.length = Math.max(1.0D, Math.min(64.0D, raw));
               break;
            case 4:
               beam.width = Math.max(0.5D, Math.min(8.0D, raw));
               break;
            default:
               beam.alpha = Math.max(0.02D, Math.min(0.5D, raw));
         }

         if (this.session != null) {
            this.session.save();
         }

      }
   }

   private static double normalize(double value, double min, double max) {
      double span = max - min;
      return span <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (value - min) / span));
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      if (this.imported != null) {
         this.imported.release();
         this.imported = null;
      }

      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }

   }

   private final class BeamSlider extends AbstractSliderButton {
      private final int kind;
      private final boolean ready;

      BeamSlider(final int x, final int y, final int w, final int h, final int kind) {
         super(x, y, w, h, Component.empty(), BeamEditor3DScreen.normalize(BeamEditor3DScreen.this.current(kind), BeamEditor3DScreen.min(kind), BeamEditor3DScreen.max(kind)));
         this.kind = kind;
         this.ready = true;
         this.updateMessage();
      }

      private double actual() {
         double raw = BeamEditor3DScreen.min(this.kind) + (BeamEditor3DScreen.max(this.kind) - BeamEditor3DScreen.min(this.kind)) * this.value;
         if (this.kind <= 2) {
            return (double)Math.round(raw);
         } else {
            double step = BeamEditor3DScreen.step(this.kind);
            double snapped = BeamEditor3DScreen.min(this.kind) + (double)Math.round((raw - BeamEditor3DScreen.min(this.kind)) / step) * step;
            return Math.max(BeamEditor3DScreen.min(this.kind), Math.min(BeamEditor3DScreen.max(this.kind), snapped));
         }
      }

      protected void updateMessage() {
         if (this.ready) {
            String label = BeamEditor3DScreen.tr(BeamEditor3DScreen.labelKey(this.kind)).getString();
            String value = this.kind <= 2 ? String.valueOf((int)this.actual()) : String.format(Locale.ROOT, "%.2f", this.actual());
            this.setMessage(Component.literal(label + " " + value));
         }
      }

      protected void applyValue() {
         if (this.ready) {
            BeamEditor3DScreen.this.apply(this.kind, this.actual());
         }
      }
   }

   private static final class VehicleRow {
      final String id;
      final boolean inUse;
      final CustomPack.Train imported;
      private String pack;
      private String name;

      VehicleRow(String id, boolean inUse) {
         this(id, inUse, (CustomPack.Train)null);
      }

      VehicleRow(String id, boolean inUse, CustomPack.Train imported) {
         this.id = id;
         this.inUse = inUse;
         this.imported = imported;
      }

      String pack() {
         if (this.pack == null) {
            this.pack = this.imported != null ? this.imported.pack.label : TrainPreview.packOf(this.id);
         }

         return this.pack;
      }

      String name() {
         if (this.name == null) {
            this.name = this.imported != null ? this.imported.name : TrainNames.of(this.id);
         }

         return this.name;
      }

      String label() {
         return (this.inUse ? "\u25cf " : "") + this.name();
      }
   }
}

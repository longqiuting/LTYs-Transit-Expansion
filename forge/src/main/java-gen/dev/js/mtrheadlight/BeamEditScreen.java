package dev.js.mtrheadlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

final class BeamEditScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 176;
   private static final int MIN_CELL = 84;
   private static final int SWATCH_SIDE = 12;
   private static final int RESET_W = 20;
   private static final int TITLE_Y = 22;
   private static final int HINT_Y = 36;
   private static final int CONTENT_TOP = 54;
   private static final int FOOTER_H = 70;
   private static final int FOOTER_ROW_GAP = 6;
   private static final int LIST_BOTTOM_PAD = 20;
   private static final double POS_MIN = -16.0D;
   private static final double POS_MAX = 16.0D;
   private static final double POS_STEP = 0.05D;
   private static final double ROT_MIN = -180.0D;
   private static final double ROT_MAX = 180.0D;
   private static final double ROT_STEP = 1.0D;
   private static final double LEN_MIN = 1.0D;
   private static final double LEN_MAX = 64.0D;
   private static final double LEN_STEP = 0.5D;
   private static final double WID_MIN = 0.5D;
   private static final double WID_MAX = 8.0D;
   private static final double WID_STEP = 0.1D;
   private static final double ALP_MIN = 0.02D;
   private static final double ALP_MAX = 0.5D;
   private static final double ALP_STEP = 0.02D;
   private static final int KIND_SLIDER = 0;
   private static final int KIND_COLOR = 1;
   private static final int KIND_CYCLE = 2;
   private final Screen parent;
   private final Screen root;
   private final BeamSession session;
   private final int index;
   private final List<BeamEditScreen.Row> rows = new ArrayList();
   private final List<BeamEditScreen.Label> labels = new ArrayList();
   private final List<BeamEditScreen.Swatch> swatches = new ArrayList();
   private int colX;
   private int colW;
   private int leftW;
   private int rightW;
   private int listTop;
   private int visibleRows = 1;
   private int scrollRow;

   BeamEditScreen(Screen parent, Screen root, BeamSession session, int index) {
      super(tr("beam.title.edit", index + 1, session == null ? "?" : session.vehicleId));
      this.parent = parent;
      this.root = root == null ? parent : root;
      this.session = session;
      this.index = index;
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private void buildRows() {
      this.rows.clear();
      this.rows.add(BeamEditScreen.Row.cycle(BeamSession.Field.TYPE));
      this.rows.add(BeamEditScreen.Row.color());
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.POS_X, "beam.axis.posX", -16.0D, 16.0D, 0.05D, "%.2f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.POS_Y, "beam.axis.posY", -16.0D, 16.0D, 0.05D, "%.2f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.POS_Z, "beam.axis.posZ", -16.0D, 16.0D, 0.05D, "%.2f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.ROT_X, "beam.axis.rotX", -180.0D, 180.0D, 1.0D, "%.0f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.ROT_Y, "beam.axis.rotY", -180.0D, 180.0D, 1.0D, "%.0f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.ROT_Z, "beam.axis.rotZ", -180.0D, 180.0D, 1.0D, "%.0f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.LENGTH, "beam.axis.length", 1.0D, 64.0D, 0.5D, "%.1f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.WIDTH, "beam.axis.width", 0.5D, 8.0D, 0.1D, "%.2f"));
      this.rows.add(BeamEditScreen.Row.slider(BeamSession.Field.ALPHA, "beam.axis.alpha", 0.02D, 0.5D, 0.02D, "%.2f"));
   }

   private int rightX() {
      return this.colX + this.leftW + 8;
   }

   private int resetX() {
      return this.colX + this.colW - 20;
   }

   private int viewBottom() {
      return this.height - 70 - 20;
   }

   private int textWidth(Component text) {
      return this.font.width(text.getString());
   }

   protected void init() {
      this.labels.clear();
      this.swatches.clear();
      this.buildRows();
      int cellNeed = Math.max(84, this.font.width("#FFFFFF") + 12 + 16);

      for(BeamEditScreen.Row row : this.rows) {
         if (row.kind == 2) {
            cellNeed = Math.max(cellNeed, this.textWidth(this.typeLabel()) + 14);
         } else if (row.kind == 1) {
            cellNeed = Math.max(cellNeed, this.textWidth(tr("beam.axis.color")) + 14);
         } else {
            cellNeed = Math.max(cellNeed, this.textWidth(tr(row.labelKey)) + 14);
            cellNeed = Math.max(cellNeed, this.textWidth(Component.literal(this.format(row, this.value(row.field)))) + 14);
         }
      }

      int maxCol = Math.max(176, this.width - 24);
      this.colW = Math.min(maxCol, Math.max(176, cellNeed * 2 + 8 + 20 + 8));
      int cellsW = this.colW - 20 - 8;
      this.leftW = (cellsW - 8) / 2;
      this.rightW = cellsW - 8 - this.leftW;
      this.colX = this.width / 2 - this.colW / 2;
      this.visibleRows = Math.max(1, (this.viewBottom() - 54 + 5) / 25);
      int maxScroll = Math.max(0, this.rows.size() - this.visibleRows);
      this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxScroll));
      int listH = Math.min(this.visibleRows, this.rows.size()) * 25 - 5;
      this.listTop = maxScroll == 0 ? 54 + Math.max(0, (this.viewBottom() - 54 - listH) / 2) : 54;
      int last = Math.min(this.rows.size(), this.scrollRow + this.visibleRows);

      for(int i = this.scrollRow; i < last; ++i) {
         this.buildRow((BeamEditScreen.Row)this.rows.get(i), this.listTop + (i - this.scrollRow) * 25);
      }

      this.buildFooter();
   }

   private void buildRow(BeamEditScreen.Row row, int y) {
      switch (row.kind) {
         case 1:
            this.labels.add(new BeamEditScreen.Label(this.colX, y, this.leftW, 20, () -> tr("beam.axis.color")));
            this.addButton(this.rightX(), y, this.rightW, 20, Component.empty(), (b) -> this.openPicker());
            this.swatches.add(new BeamEditScreen.Swatch(this.rightX(), y, this.rightW, 20, this.color()));
            this.addReset(row.field, y);
            break;
         case 2:
            this.addButton(this.colX, y, this.colW - 20 - 8, 20, this.typeLabel(), (b) -> this.cycleType());
            this.addReset(row.field, y);
            break;
         default:
            this.labels.add(new BeamEditScreen.Label(this.colX, y, this.leftW, 20, () -> tr(row.labelKey)));
            this.addRenderableWidget(new BeamEditScreen.FieldSlider(this.rightX(), y, this.rightW, 20, row));
            this.addReset(row.field, y);
      }

   }

   private void addReset(BeamSession.Field field, int y) {
      this.addButton(this.resetX(), y, 20, 20, tr("btn.resetOne"), (b) -> {
         this.session.resetField(this.index, field);
         this.reinit();
      });
   }

   private void buildFooter() {
      int top = this.height - 70;
      int cell2 = (this.colW - 8) / 2;
      int x2 = this.width / 2 - (cell2 * 2 + 8) / 2;
      this.addButton(x2, top, cell2, 20, tr("btn.beamResetBeam"), (b) -> {
         this.session.resetAll(this.index);
         this.reinit();
      });
      this.addButton(x2 + cell2 + 8, top, cell2, 20, tr("btn.backBeams"), (b) -> this.onClose());
   }

   private VehicleLightOverrides.Beam beam() {
      return this.session == null ? null : this.session.beam(this.index);
   }

   private int color() {
      VehicleLightOverrides.Beam beam = this.beam();
      return beam != null && beam.color != null ? beam.color & 16777215 : BeamSession.defaultColor("head");
   }

   private double value(BeamSession.Field field) {
      VehicleLightOverrides.Beam beam = this.beam();
      if (beam == null) {
         return 0.0D;
      } else {
         switch (field) {
            case POS_X:
               return beam.pos == null ? 0.0D : beam.pos[0];
            case POS_Y:
               return beam.pos == null ? 0.0D : beam.pos[1];
            case POS_Z:
               return beam.pos == null ? 0.0D : beam.pos[2];
            case ROT_X:
               return beam.rot == null ? 0.0D : beam.rot[0];
            case ROT_Y:
               return beam.rot == null ? 0.0D : beam.rot[1];
            case ROT_Z:
               return beam.rot == null ? 0.0D : beam.rot[2];
            case LENGTH:
               return beam.length == null ? BeamSession.defaultLength(BeamSession.typeOf(beam)) : beam.length;
            case WIDTH:
               return beam.width == null ? BeamSession.defaultWidth() : beam.width;
            case ALPHA:
               return beam.alpha == null ? BeamSession.defaultAlpha() : beam.alpha;
            default:
               return 0.0D;
         }
      }
   }

   private void write(BeamSession.Field field, double raw) {
      VehicleLightOverrides.Beam beam = this.beam();
      if (beam != null) {
         switch (field) {
            case POS_X:
            case POS_Y:
            case POS_Z:
               if (beam.pos == null) {
                  beam.pos = new double[]{0.0D, 0.0D, 0.0D};
               }

               beam.pos[axis(field, BeamSession.Field.POS_X)] = raw;
               break;
            case ROT_X:
            case ROT_Y:
            case ROT_Z:
               if (beam.rot == null) {
                  beam.rot = new double[]{0.0D, 0.0D, 0.0D};
               }

               beam.rot[axis(field, BeamSession.Field.ROT_X)] = raw;
               break;
            case LENGTH:
               beam.length = raw;
               break;
            case WIDTH:
               beam.width = raw;
               break;
            case ALPHA:
               beam.alpha = raw;
               break;
            default:
               return;
         }

         this.session.save();
      }
   }

   private static int axis(BeamSession.Field field, BeamSession.Field first) {
      return field.ordinal() - first.ordinal();
   }

   private String format(BeamEditScreen.Row row, double value) {
      return String.format(Locale.ROOT, row.pattern, value);
   }

   private MutableComponent typeLabel() {
      VehicleLightOverrides.Beam beam = this.beam();
      return tr("beam.field.type", BeamSession.typeName(BeamSession.typeOf(beam)));
   }

   private void cycleType() {
      VehicleLightOverrides.Beam beam = this.beam();
      if (beam != null) {
         beam.type = BeamSession.isHead(beam) ? "tail" : "head";
         this.session.save();
         this.reinit();
      }
   }

   private void openPicker() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ColorPickerScreen(this, tr("beam.axis.color").getString(), this.color(), (rgb) -> {
            VehicleLightOverrides.Beam beam = this.beam();
            if (beam != null) {
               beam.color = rgb & 16777215;
               this.session.save();
            }

         }));
      }
   }

   private void reinit() {
      this.rebuildWidgets();
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, 22, 16777215);
      VehicleLightOverrides.Beam beam = this.beam();
      String status = beam == null ? "?" : this.font.plainSubstrByWidth(BeamSession.typeName(BeamSession.typeOf(beam)) + " \u00b7 " + BeamSession.hex(beam.color) + String.format(Locale.ROOT, " \u00b7 \u957f %.1f \u5bbd %.2f \u900f\u660e %.2f", beam.length == null ? 0.0D : beam.length, beam.width == null ? 0.0D : beam.width, beam.alpha == null ? 0.0D : beam.alpha), Math.max(80, this.width - 32));
      context.drawCenteredString(this.font, Component.literal(status), this.width / 2, 36, 11579568);

      for(BeamEditScreen.Label label : this.labels) {
         context.drawCenteredString(this.font, (Component)label.text.get(), label.x + label.w / 2, label.y + (label.h - 8) / 2, 16777215);
      }

      for(BeamEditScreen.Swatch swatch : this.swatches) {
         int side = Math.max(8, Math.min(12, swatch.h - 6));
         int x = swatch.x + 6;
         int y = swatch.y + (swatch.h - side) / 2;
         context.fill(x - 1, y - 1, x + side + 1, y + side + 1, -16777216);
         context.fill(x, y, x + side, y + side, -16777216 | swatch.rgb & 16777215);
         int textRight = swatch.x + swatch.w - 6;
         context.drawCenteredString(this.font, Component.literal(HeadlightConfig.formatColor(swatch.rgb)), (x + side + textRight) / 2, swatch.y + (swatch.h - 8) / 2, -2039584);
      }

      if (this.rows.size() > this.visibleRows) {
         this.drawScrollBar(context);
         context.drawCenteredString(this.font, tr("config.scrollHint", this.scrollRow + 1, this.rows.size()), this.width / 2, this.height - 70 - 14, 9474192);
      }

      context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(tr("beam.hint.vector").getString(), Math.max(80, this.width - 32))), this.width / 2, this.height - 14, 9474192);
   }

   private void drawScrollBar(GuiGraphics context) {
      int top = 54;
      int bottom = this.listTop + this.visibleRows * 25 - 5;
      int x = this.colX + this.colW + 4;
      context.fill(x, 54, x + 1, Math.max(54, bottom), 1627389951);
      int trackH = Math.max(1, bottom - 54);
      int thumbH = Math.max(12, trackH * this.visibleRows / this.rows.size());
      int span = Math.max(1, this.rows.size() - this.visibleRows);
      int thumbY = 54 + (trackH - thumbH) * this.scrollRow / span;
      context.fill(x - 1, thumbY, x + 2, thumbY + thumbH, -1);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }

   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.rows.size() > this.visibleRows && amount != 0.0D) {
         this.scrollRow -= (int)Math.signum(amount);
         this.reinit();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      int page = Math.max(1, this.visibleRows - 1);
      if (keyCode == 267) {
         return this.scrollBy(page);
      } else {
         return keyCode == 266 ? this.scrollBy(-page) : super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   private boolean scrollBy(int delta) {
      if (this.rows.size() <= this.visibleRows) {
         return false;
      } else {
         this.scrollRow += delta;
         this.reinit();
         return true;
      }
   }

   private Button addButton(int x, int y, int w, int h, Component label, Button.OnPress action) {
      return (Button)this.addRenderableWidget(Button.builder(label, action).bounds(x, y, w, h).build());
   }

   private static double normalize(double value, double min, double max) {
      double span = max - min;
      return span <= 0.0D ? 0.0D : Math.max(0.0D, Math.min(1.0D, (value - min) / span));
   }

   private static double snap(double value, double min, double max, double step) {
      double clamped = Math.max(min, Math.min(max, value));
      if (step <= 0.0D) {
         return clamped;
      } else {
         double stepped = min + (double)Math.round((clamped - min) / step) * step;
         return Math.max(min, Math.min(max, stepped));
      }
   }

   private final class FieldSlider extends AbstractSliderButton {
      private final BeamEditScreen.Row row;
      private boolean ready;

      FieldSlider(final int x, final int y, final int w, final int h, final BeamEditScreen.Row row) {
         super(x, y, w, h, Component.empty(), BeamEditScreen.normalize(BeamEditScreen.this.value(row.field), row.min, row.max));
         this.row = row;
         this.ready = true;
         this.updateMessage();
      }

      protected void updateMessage() {
         if (this.ready) {
            this.setMessage(Component.literal(String.format(Locale.ROOT, this.row.pattern, this.actual())));
         }
      }

      protected void applyValue() {
         if (this.ready) {
            BeamEditScreen.this.write(this.row.field, this.actual());
         }
      }

      private double actual() {
         return BeamEditScreen.snap(this.row.min + (this.row.max - this.row.min) * this.value, this.row.min, this.row.max, this.row.step);
      }
   }

   private static final class Label {
      final int x;
      final int y;
      final int w;
      final int h;
      final Supplier<Component> text;

      Label(int x, int y, int w, int h, Supplier<Component> text) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.text = text;
      }
   }

   private static final class Row {
      final int kind;
      final BeamSession.Field field;
      final String labelKey;
      final double min;
      final double max;
      final double step;
      final String pattern;

      private Row(int kind, BeamSession.Field field, String labelKey, double min, double max, double step, String pattern) {
         this.kind = kind;
         this.field = field;
         this.labelKey = labelKey;
         this.min = min;
         this.max = max;
         this.step = step;
         this.pattern = pattern;
      }

      static BeamEditScreen.Row slider(BeamSession.Field field, String labelKey, double min, double max, double step, String pattern) {
         return new BeamEditScreen.Row(0, field, labelKey, min, max, step, pattern);
      }

      static BeamEditScreen.Row color() {
         return new BeamEditScreen.Row(1, BeamSession.Field.COLOR, "beam.axis.color", 0.0D, 0.0D, 0.0D, "%.0f");
      }

      static BeamEditScreen.Row cycle(BeamSession.Field field) {
         return new BeamEditScreen.Row(2, field, "beam.field.type", 0.0D, 0.0D, 0.0D, "%.0f");
      }
   }

   private static final class Swatch {
      final int x;
      final int y;
      final int w;
      final int h;
      final int rgb;

      Swatch(int x, int y, int w, int h, int rgb) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
         this.rgb = rgb;
      }
   }
}

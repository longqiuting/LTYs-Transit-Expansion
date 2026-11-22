package dev.js.lte;

import dev.js.lte.mtr.BuiltInTrains;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import mtr.data.Siding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class ParameterScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 176;
   private static final int MIN_CELL = 84;
   private static final int SWATCH_SIDE = 12;
   private static final int RESET_W = 20;
   private static final int CONTENT_TOP = 54;
   private static final int FOOTER_H = 70;
   private static final int FOOTER_ROW_GAP = 6;
   private static final int LIST_BOTTOM_PAD = 20;
   private static final int ROW_TOGGLE = 0;
   private static final int ROW_VALUE = 1;
   private static final int ROW_COLOR = 2;
   private static final int ROW_ACTION = 3;
   private static final int ROW_TOGGLE_WIDE = 4;
   private static final int ROW_CYCLE = 5;
   private static final int ROW_CYCLE_STATES = 3;
   private static boolean layoutLogged;
   private static final double DIST_MIN = 16.0D;
   private static final double DIST_MAX = 512.0D;
   private static final double DIST_STEP = 8.0D;
   private static final double LUM_MIN = 0.0D;
   private static final double LUM_MAX = 15.0D;
   private static final double LENGTH_MIN = 0.0D;
   private static final double LENGTH_MAX = 32.0D;
   private static final double LENGTH_STEP = 0.5D;
   private static final double BEAM_DIST_MIN = 16.0D;
   private static final double BEAM_DIST_MAX = 384.0D;
   private static final double BEAM_DIST_STEP = 8.0D;
   private static final double BEAM_LEN_MIN = 0.0D;
   private static final double BEAM_LEN_MAX = 48.0D;
   private static final double BEAM_TAIL_MAX = 24.0D;
   private static final double BEAM_LEN_STEP = 1.0D;
   private static final double BEAM_WIDTH_MIN = 1.0D;
   private static final double BEAM_WIDTH_MAX = 8.0D;
   private static final double BEAM_WIDTH_STEP = 0.5D;
   private static final double BEAM_HEIGHT_MIN = 0.0D;
   private static final double BEAM_HEIGHT_MAX = 4.0D;
   private static final double BEAM_HEIGHT_STEP = 0.05D;
   private static final double END_EXTRA_MIN = 0.0D;
   private static final double END_EXTRA_MAX = 4.0D;
   private static final double END_EXTRA_STEP = 0.1D;
   private static final double COL_HEIGHT_MIN = 1.0D;
   private static final double COL_HEIGHT_MAX = 8.0D;
   private static final double COL_HEIGHT_STEP = 0.1D;
   private static final double ROOF_SPEED_MIN = 0.0D;
   private static final double ROOF_SPEED_MAX = 300.0D;
   private static final double ROOF_SPEED_STEP = 5.0D;
   private static final double ROOF_RADIUS_MIN = 10.0D;
   private static final double ROOF_RADIUS_MAX = 1000.0D;
   private static final double ROOF_RADIUS_STEP = 10.0D;
   private static final double SPRAY_SPEED_MIN = 0.0D;
   private static final double SPRAY_SPEED_MAX = 200.0D;
   private static final double SPRAY_SPEED_STEP = 5.0D;
   private static final double TIER_MIN = 0.0D;
   private static final double TIER_MAX = 400.0D;
   private static final double TIER_STEP = 1.0D;
   private static final double RATE_MIN = 0.0D;
   private static final double RATE_MAX = 10.0D;
   private static final double RATE_STEP = 0.05D;
   private static final double EXP_K_MIN = 0.0D;
   private static final double EXP_K_MAX = 8.0D;
   private static final double EXP_K_STEP = 0.1D;
   private static final double RATE_SCALE_MIN = 10.0D;
   private static final double RATE_SCALE_MAX = 400.0D;
   private static final double RATE_SCALE_STEP = 5.0D;
   private static final double FLY_SCALE_MIN = 1.0D;
   private static final double FLY_SCALE_MAX = 10.0D;
   private static final double FLY_SCALE_STEP = 0.5D;
   private static final double IMPULSE_MIN = 0.0D;
   private static final double IMPULSE_MAX = 12.0D;
   private static final double IMPULSE_V_MAX = 6.0D;
   private static final double IMPULSE_STEP = 0.05D;
   private static final double COOLDOWN_MIN = 1.0D;
   private static final double COOLDOWN_MAX = 200.0D;
   private static final double COOLDOWN_STEP = 1.0D;
   private static final double KNOCKBACK_MIN = 1.0D;
   private static final double KNOCKBACK_MAX = 80.0D;
   private static final double KNOCKBACK_STEP = 0.5D;
   private static final double KNOCKBACK_K_MIN = 0.0D;
   private static final double KNOCKBACK_K_MAX = 3.0D;
   private static final double KNOCKBACK_K_STEP = 0.1D;
   private static final double KNOCKBACK_CAP_MIN = 1.0D;
   private static final double KNOCKBACK_CAP_MAX = 200.0D;
   private static final double KNOCKBACK_CAP_STEP = 1.0D;
   private static final double DMG_CAP_MIN = 0.0D;
   private static final double DMG_CAP_MAX = 100.0D;
   private static final double DMG_CAP_STEP = 0.5D;
   private static final double SCATTER_STR_MIN = 0.1D;
   private static final double SCATTER_STR_MAX = 3.0D;
   private static final double SCATTER_STR_STEP = 0.05D;
   private static final double SCATTER_R_MIN = 1.0D;
   private static final double SCATTER_R_MAX = 24.0D;
   private static final double SCATTER_R_STEP = 0.5D;
   private final Screen parent;
   private final Screen root;
   private final Siding siding;
   private final ConfigCategory category;
   private final ConfigWriteTarget target;
   private int colX;
   private int colW;
   private int leftW;
   private int rightW;
   private int listTop;
   private int scrollRow;
   private int visibleRows = 1;
   private final List<ParameterScreen.Row> rows = new ArrayList();
   private final List<ParameterScreen.Label> labels = new ArrayList();
   private final List<ParameterScreen.Swatch> swatches = new ArrayList();

   public ParameterScreen(Screen parent, Screen root, Siding siding, ConfigCategory category) {
      super(isGlobalTitle(siding, category));
      this.parent = parent;
      this.root = root == null ? parent : root;
      this.siding = siding;
      this.category = category == null ? ConfigCategory.LIGHT : category;
      this.target = siding == null ? ConfigWriteTarget.global(MtrHeadlightClient::config) : ConfigWriteTarget.siding(MtrHeadlightClient::config, siding.id);
   }

   private static MutableComponent isGlobalTitle(Siding siding, ConfigCategory category) {
      MutableComponent name = (category == null ? ConfigCategory.LIGHT : category).title();
      return siding == null ? tr("screen.lte.section", name) : tr("detail.section", Long.toString(siding.id), nameOf(siding), name);
   }

   private boolean isGlobal() {
      return this.siding == null;
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
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

   private int rowsPerPage() {
      return Math.max(1, (this.viewBottom() - 54 + 5) / 25);
   }

   protected void init() {
      this.labels.clear();
      this.swatches.clear();
      this.buildRows();
      int cellNeed = Math.max(84, this.measureCellNeed());
      int maxCol = Math.max(176, this.width - 24);
      this.colW = Math.min(maxCol, Math.max(176, cellNeed * 2 + 8));
      int cellsW = this.colW - 20 - 8;
      this.leftW = (cellsW - 8) / 2;
      this.rightW = cellsW - 8 - this.leftW;
      this.colX = this.width / 2 - this.colW / 2;
      this.visibleRows = this.rowsPerPage();
      int maxScroll = Math.max(0, this.rows.size() - this.visibleRows);
      if (this.scrollRow > maxScroll) {
         this.scrollRow = maxScroll;
      }

      if (this.scrollRow < 0) {
         this.scrollRow = 0;
      }

      int listH = Math.min(this.visibleRows, this.rows.size()) * 25 - 5;
      this.listTop = maxScroll == 0 ? 54 + Math.max(0, (this.viewBottom() - 54 - listH) / 2) : 54;
      int last = Math.min(this.rows.size(), this.scrollRow + this.visibleRows);

      for(int i = this.scrollRow; i < last; ++i) {
         this.buildRow((ParameterScreen.Row)this.rows.get(i), i - this.scrollRow);
      }

      this.buildFooter();
      this.logLayoutOnce();
   }

   private void logLayoutOnce() {
      if (!layoutLogged) {
         layoutLogged = true;
         MtrHeadlightClient.LOGGER.info("[lte] config layout {}x{} ({} / {}): column x={} w={}, left cell x={} w={}, right cell x={} w={}, reset x={} w={}, rows={} visible={}", new Object[]{this.width, this.height, this.isGlobal() ? "global" : "siding", this.category.name(), this.colX, this.colW, this.colX, this.leftW, this.rightX(), this.rightW, this.resetX(), 20, this.rows.size(), this.visibleRows});
      }
   }

   private int rowY(int visibleIndex) {
      return this.listTop + visibleIndex * 25;
   }

   private int measureCellNeed() {
      int need = this.swatchNeed();

      for(ParameterScreen.Row row : this.rows) {
         switch (row.kind) {
            case 0:
               need = Math.max(need, this.textWidth(this.toggleLabel(row.toggle)) + 14);
               need = Math.max(need, this.textWidth(this.sliderLabel(row.value, maxOf(row.value))) + 14);
               break;
            case 1:
               need = Math.max(need, this.textWidth(this.valueLabel(row.value)) + 14);
               need = Math.max(need, this.textWidth(Component.literal(formatValue(row.value, maxOf(row.value)))) + 14);
               break;
            case 2:
               need = Math.max(need, this.textWidth(this.colorLabel(row.head)) + 14);
               break;
            case 3:
            default:
               need = Math.max(need, this.textWidth(row.label) + 14);
               break;
            case 4:
            case 5:
               need = Math.max(need, this.textWidth(row.kind == 5 ? this.cycleLabel(row.value) : this.toggleLabel(row.toggle)) + 14);
         }
      }

      return need;
   }

   private int swatchNeed() {
      return 18 + this.textWidth(Component.literal("#FFFFFF")) + 10;
   }

   private int textWidth(Component text) {
      return this.font.width(text.getString());
   }

   private void buildRows() {
      this.rows.clear();
      switch (this.category) {
         case LIGHT:
            this.rows.add(ParameterScreen.Row.toggleWide(0));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.mode());
            }

            this.rows.add(ParameterScreen.Row.toggleWide(2));
            this.rows.add(ParameterScreen.Row.toggleWide(41));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.toggleWide(36));
               this.rows.add(ParameterScreen.Row.toggleWide(38));
               this.rows.add(ParameterScreen.Row.toggleWide(42));
            }

            this.rows.add(ParameterScreen.Row.toggleWide(1));
            if (this.isGlobal()) {
            }

            this.rows.add(ParameterScreen.Row.value(0));
            this.rows.add(ParameterScreen.Row.value(1));
            this.rows.add(ParameterScreen.Row.value(2));
            this.rows.add(ParameterScreen.Row.value(3));
            this.rows.add(ParameterScreen.Row.value(4));
            this.rows.add(ParameterScreen.Row.value(5));
            this.rows.add(ParameterScreen.Row.value(6));
            this.rows.add(ParameterScreen.Row.value(7));
            this.rows.add(ParameterScreen.Row.value(8));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.value(34));
               this.rows.add(ParameterScreen.Row.value(35));
            }

            this.rows.add(ParameterScreen.Row.color(true));
            this.rows.add(ParameterScreen.Row.color(false));
            this.rows.add(ParameterScreen.Row.worldAction(tr("btn.beamEditor"), this::openBeamEditor));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.action(tr("btn.reload"), LightController::reload));
            }
            break;
         case COLLISION:
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.toggleWide(6));
               this.rows.add(ParameterScreen.Row.value(25));
               this.rows.add(ParameterScreen.Row.toggleWide(7));
            }

            this.rows.add(ParameterScreen.Row.value(9));
            this.rows.add(ParameterScreen.Row.value(10));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.value(26));
               this.rows.add(ParameterScreen.Row.value(27));
            }
            break;
         case PARTICLE:
            this.rows.add(ParameterScreen.Row.toggle(4, 11));
            this.rows.add(ParameterScreen.Row.toggleWide(10));
            break;
         case DAMAGE:
            this.rows.add(ParameterScreen.Row.toggleWide(8));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.toggleWide(11));
            }

            this.rows.add(ParameterScreen.Row.value(this.isGlobal() ? 12 : 29));
            this.rows.add(ParameterScreen.Row.value(30));
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.value(13));
               this.rows.add(ParameterScreen.Row.value(14));
               this.rows.add(ParameterScreen.Row.value(15));
               this.rows.add(ParameterScreen.Row.value(16));
               this.rows.add(ParameterScreen.Row.value(17));
               this.rows.add(ParameterScreen.Row.value(18));
               this.rows.add(ParameterScreen.Row.value(19));
               this.rows.add(ParameterScreen.Row.value(31));
               this.rows.add(ParameterScreen.Row.value(32));
               this.rows.add(ParameterScreen.Row.value(33));
               this.rows.add(ParameterScreen.Row.value(21));
               this.rows.add(ParameterScreen.Row.value(23));
               this.rows.add(ParameterScreen.Row.value(24));
               this.rows.add(ParameterScreen.Row.toggleWide(9));
            }
            break;
         default:
            if (this.isGlobal()) {
               this.rows.add(ParameterScreen.Row.toggleWide(5));
               this.rows.add(ParameterScreen.Row.toggleWide(43));
               this.rows.add(ParameterScreen.Row.action(tr("btn.reloadTrains"), this::reloadTrains));
            }
      }

   }

   private void buildRow(ParameterScreen.Row row, int visibleIndex) {
      int y = this.rowY(visibleIndex);
      switch (row.kind) {
         case 0: {

            Button button = this.addButton(this.colX, y, this.leftW, 20, this.toggleLabel(row.toggle), (b) -> {
               this.target.writeToggle(row.toggle, !this.target.readToggle(row.toggle));
               if (row.toggle == 5) {
                  BuiltInTrains.reloadNow();
               }

               this.reinit();
            });
            this.addRenderableWidget(new ParameterScreen.NumberSlider(this.rightX(), y, this.rightW, 20, row.value, true, this.target.readValue(row.value)));
            this.addResetButton(row, y);
            break;
         }
         case 1: {

            this.labels.add(new ParameterScreen.Label(this.colX, y, this.leftW, 20, () -> this.valueLabel(row.value)));
            this.addRenderableWidget(new ParameterScreen.NumberSlider(this.rightX(), y, this.rightW, 20, row.value, false, this.target.readValue(row.value)));
            this.addResetButton(row, y);
            break;
         }
         case 2: {

            this.labels.add(new ParameterScreen.Label(this.colX, y, this.leftW, 20, () -> this.colorLabel(row.head)));
            this.addButton(this.rightX(), y, this.rightW, 20, Component.empty(), (buttonx) -> this.openPicker(row.head));
            this.swatches.add(new ParameterScreen.Swatch(this.rightX(), y, this.rightW, 20, this.target.readColor(row.head)));
            this.addResetButton(row, y);
            break;
         }
         case 3: {

         }
         default: {

            boolean worldOk = Minecraft.getInstance().level != null;
            Component label = (Component)(row.needsWorld && !worldOk ? row.label.copy().append(tr("hint.needWorldShort")) : row.label);
            Button button = this.addButton(this.colX, y, this.colW, 20, label, (b) -> row.action.run());
            if (row.needsWorld) {
               button.active = worldOk;
            }
            break;
         }
         case 4: {

            Component wideLabel = this.toggleLabel(row.toggle);
            Button button = this.addButton(this.colX, y, this.colW - 20 - 8, 20, wideLabel, (b) -> {
               this.target.writeToggle(row.toggle, !this.target.readToggle(row.toggle));
               if (row.toggle == 5) {
                  BuiltInTrains.reloadNow();
               }

               this.reinit();
            });
            if (row.toggle == 11) {
               button.setTooltip(Tooltip.create(tr("hint.damageInCreative")));
            }

            this.addResetButton(row, y);
            break;
         }
         case 5: {

            this.addButton(this.colX, y, this.colW - 20 - 8, 20, this.cycleLabel(row.value), (b) -> {
               int next = ((int)Math.round(this.target.readValue(row.value)) + 1) % 3;
               this.target.writeValue(row.value, (double)next);
               this.reinit();
            });
            this.addResetButton(row, y);
         }
      }

   }

   private void addResetButton(ParameterScreen.Row row, int y) {
      this.addButton(this.resetX(), y, 20, 20, tr("btn.resetOne"), (button) -> {
         switch (row.kind) {
            case 0:
            case 4:
               this.target.resetToggle(row.toggle);
               if (row.value >= 0) {
                  this.target.resetValue(row.value);
               }

               if (row.toggle == 5) {
                  BuiltInTrains.reloadNow();
               }
               break;
            case 1:
               this.target.resetValue(row.value);
               break;
            case 2:
               this.target.resetColor(row.head);
               break;
            case 3:
            default:
               return;
            case 5:
               this.target.resetValue(28);
         }

         this.reinit();
      });
   }

   private void buildFooter() {
      int resetY = this.height - 70;
      this.addButton(this.colX, resetY, this.colW, 20, tr("btn.resetAll"), (button) -> this.openResetAllConfirm());
      int y = resetY + 20 + 6;
      int cell = (this.colW - 8) / 2;
      int x = this.width / 2 - (cell * 2 + 8) / 2;
      this.addButton(x, y, cell, 20, tr("btn.backSection"), (button) -> this.closeToParent());
      this.addButton(x + cell + 8, y, cell, 20, tr("btn.backMain"), (button) -> this.closeToRoot());
   }

   private void openResetAllConfirm() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ResetConfirmScreen(this, tr("reset.confirm.title"), tr(this.isGlobal() ? "reset.confirm.global" : "reset.confirm.siding"), () -> {
            this.target.resetAll();
            this.reinit();
         }));
      }
   }

   private void closeToParent() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }

   }

   private void closeToRoot() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.root);
      }

   }

   private void openBeamEditor() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new BeamVehicleScreen(this, this.root, BeamSession.presetOf(this.siding)));
      }
   }

   private void reloadTrains() {
      String result = BuiltInTrains.reloadNow();
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         client.player.displayClientMessage(Component.literal(result), false);
      }

   }

   private void openPicker(boolean head) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ColorPickerScreen(this, this.colorLabel(head).getString(), this.target.readColor(head), (rgb) -> this.target.writeColor(head, rgb)));
      }
   }

   private void reinit() {
      this.rebuildWidgets();
   }

   private MutableComponent toggleLabel(int which) {
      switch (which) {
         case 0:
            return this.follow(this.target.readToggle(which) ? tr("opt.master.on") : tr("opt.master.off"), which);
         case 1:
            return this.follow(this.target.readToggle(which) ? tr("opt.dynamicLight.on") : tr("opt.dynamicLight.off"), which);
         case 2:
            return this.follow(this.target.readToggle(which) ? tr("opt.band.on") : tr("opt.band.off"), which);
         case 3:
         case 12:
         case 13:
         case 14:
         case 15:
         case 16:
         case 17:
         case 18:
         case 19:
         case 20:
         case 21:
         case 22:
         case 23:
         case 24:
         case 25:
         case 26:
         case 27:
         case 28:
         case 29:
         case 30:
         case 31:
         case 32:
         case 33:
         case 34:
         case 35:
         case 37:
         case 39:
         case 40:
         default:
            return this.follow(this.target.readToggle(which) ? tr("opt.band.on") : tr("opt.band.off"), which);
         case 4:
            return this.follow(this.target.readToggle(which) ? tr("opt.rainSpray.on") : tr("opt.rainSpray.off"), which);
         case 5:
            return this.follow(this.target.readToggle(which) ? tr("opt.builtin.on") : tr("opt.builtin.off"), which);
         case 6:
            return this.follow(this.target.readToggle(which) ? tr("opt.trainCollision.on") : tr("opt.trainCollision.off"), which);
         case 7:
            return this.follow(this.target.readToggle(which) ? tr("opt.trackbed.on") : tr("opt.trackbed.off"), which);
         case 8:
            return this.follow(this.target.readToggle(which) ? tr("opt.trainDamage.on") : tr("opt.trainDamage.off"), which);
         case 9:
            return this.follow(this.target.readToggle(which) ? tr("opt.trainMobs.on") : tr("opt.trainMobs.off"), which);
         case 10:
            return this.follow(this.target.readToggle(which) ? tr("opt.rainHush.on") : tr("opt.rainHush.off"), which);
         case 11:
            return tr(this.target.readToggle(which) ? "opt.damageInCreative.on" : "opt.damageInCreative.off");
         case 36:
            return tr(this.target.readToggle(which) ? "opt.scatter.on" : "opt.scatter.off");
         case 38:
            return tr(this.target.readToggle(which) ? "opt.scatterSurface.on" : "opt.scatterSurface.off");
         case 41:
            return this.follow(this.target.readToggle(which) ? tr("opt.flare.on") : tr("opt.flare.off"), which);
         case 42:
            return tr(this.target.readToggle(which) ? "opt.scatterShaders.on" : "opt.scatterShaders.off");
         case 43:
            return tr(this.target.readToggle(which) ? "opt.doorSound.on" : "opt.doorSound.off");
      }
   }

   private MutableComponent follow(MutableComponent label, int which) {
      return this.target.followsToggle(which) ? label.append(tr("hint.followGlobal")) : label;
   }

   private MutableComponent valueLabel(int which) {
      MutableComponent label = tr(valueKey(which));
      return this.target.followsValue(which) ? label.append(tr("hint.followGlobal")) : label;
   }

   private MutableComponent colorLabel(boolean head) {
      MutableComponent label = tr(head ? "opt.beamHeadColor" : "opt.beamTailColor");
      return this.target.followsColor(head) ? label.append(tr("hint.followGlobal")) : label;
   }

   private MutableComponent sliderLabel(int which, double value) {
      return tr(valueKey(which), formatValue(which, value));
   }

   private MutableComponent modeLabel() {
      int index = (int)Math.round(this.target.readValue(28));
      return tr("opt.mode", tr(modeKey(index)));
   }

   private MutableComponent cycleLabel(int which) {
      return this.modeLabel();
   }

   private static String modeKey(int index) {
      switch (index) {
         case 1:
            return "opt.mode.always";
         case 2:
            return "opt.mode.off";
         default:
            return "opt.mode.auto";
      }
   }

   private static String valueKey(int which) {
      switch (which) {
         case 0:
            return "opt.distance";
         case 1:
            return "opt.headLuminance";
         case 2:
            return "opt.headLength";
         case 3:
            return "opt.beamDistance";
         case 4:
            return "opt.beamHeadLength";
         case 5:
            return "opt.beamTailLength";
         case 6:
            return "opt.beamWidth";
         case 7:
            return "opt.beamHeadHeight";
         case 8:
         case 28:
         default:
            return "opt.beamTailHeight";
         case 9:
            return "opt.collisionHeadExtra";
         case 10:
            return "opt.collisionTailExtra";
         case 11:
            return "opt.rainSprayMinSpeed";
         case 12:
            return "opt.collisionPushMax";
         case 13:
            return "opt.collisionLowMax";
         case 14:
            return "opt.collisionHighMax";
         case 15:
            return "opt.collisionLaunchMax";
         case 16:
            return "opt.collisionBaseRate";
         case 17:
            return "opt.collisionExpK";
         case 18:
            return "opt.collisionRateScale";
         case 19:
            return "opt.collisionFlyScale";
         case 20:
            return "opt.collisionLaunchH";
         case 21:
            return "opt.collisionLaunchV";
         case 22:
            return "opt.collisionFlyH";
         case 23:
            return "opt.collisionFlyV";
         case 24:
            return "opt.collisionCooldown";
         case 25:
            return "opt.collisionHeight";
         case 26:
            return "opt.roofDropSpeed";
         case 27:
            return "opt.roofDropRadius";
         case 29:
            return "opt.collisionPushMax";
         case 30:
            return "opt.damageMax";
         case 31:
            return "opt.knockbackDistance";
         case 32:
            return "opt.knockbackExpK";
         case 33:
            return "opt.knockbackMax";
         case 34:
            return "opt.scatterStrength";
         case 35:
            return "opt.scatterRadius";
      }
   }

   private static double maxOf(int which) {
      switch (which) {
         case 0:
            return 512.0D;
         case 1:
            return 15.0D;
         case 2:
            return 32.0D;
         case 3:
            return 384.0D;
         case 4:
            return 48.0D;
         case 5:
            return 24.0D;
         case 6:
            return 8.0D;
         case 7:
         case 8:
         case 28:
         default:
            return 4.0D;
         case 9:
         case 10:
            return 4.0D;
         case 11:
            return 200.0D;
         case 12:
         case 13:
         case 14:
         case 15:
            return 400.0D;
         case 16:
            return 10.0D;
         case 17:
            return 8.0D;
         case 18:
            return 400.0D;
         case 19:
            return 10.0D;
         case 20:
         case 22:
            return 12.0D;
         case 21:
         case 23:
            return 6.0D;
         case 24:
            return 200.0D;
         case 25:
            return 8.0D;
         case 26:
            return 300.0D;
         case 27:
            return 1000.0D;
         case 29:
            return 400.0D;
         case 30:
            return 100.0D;
         case 31:
            return 80.0D;
         case 32:
            return 3.0D;
         case 33:
            return 200.0D;
         case 34:
            return 3.0D;
         case 35:
            return 24.0D;
      }
   }

   private static String formatValue(int which, double value) {
      switch (which) {
         case 0:
         case 1:
         case 3:
         case 11:
         case 26:
         case 29:
            return String.format(Locale.ROOT, "%.0f", value);
         case 2:
         case 4:
         case 5:
         case 6:
         case 9:
         case 10:
         case 16:
         case 20:
         case 21:
         case 22:
         case 23:
         case 28:
         case 30:
         case 31:
         default:
            return String.format(Locale.ROOT, "%.1f", value);
         case 7:
         case 8:
            return String.format(Locale.ROOT, "%.2f", value);
         case 12:
         case 13:
         case 14:
         case 15:
         case 18:
         case 24:
            return String.format(Locale.ROOT, "%.0f", value);
         case 17:
         case 19:
         case 32:
            return String.format(Locale.ROOT, "%.1f", value);
         case 25:
         case 27:
            return String.format(Locale.ROOT, "%.1f", value);
         case 33:
            return String.format(Locale.ROOT, "%.0f", value);
         case 34:
            return String.format(Locale.ROOT, "%.2f", value);
         case 35:
            return String.format(Locale.ROOT, "%.1f", value);
      }
   }

   private static double minOf(int which) {
      switch (which) {
         case 0:
            return 16.0D;
         case 1:
            return 0.0D;
         case 2:
            return 0.0D;
         case 3:
            return 16.0D;
         case 4:
         case 5:
            return 0.0D;
         case 6:
            return 1.0D;
         case 7:
         case 8:
         case 28:
         default:
            return 0.0D;
         case 9:
         case 10:
            return 0.0D;
         case 11:
            return 0.0D;
         case 12:
         case 13:
         case 14:
         case 15:
            return 0.0D;
         case 16:
            return 0.0D;
         case 17:
            return 0.0D;
         case 18:
            return 10.0D;
         case 19:
            return 1.0D;
         case 20:
         case 21:
         case 22:
         case 23:
            return 0.0D;
         case 24:
            return 1.0D;
         case 25:
            return 1.0D;
         case 26:
            return 0.0D;
         case 27:
            return 10.0D;
         case 29:
            return 0.0D;
         case 30:
            return 0.0D;
         case 31:
            return 1.0D;
         case 32:
            return 0.0D;
         case 33:
            return 1.0D;
         case 34:
            return 0.1D;
         case 35:
            return 1.0D;
      }
   }

   private static double stepOf(int which) {
      switch (which) {
         case 0:
            return 8.0D;
         case 1:
            return 1.0D;
         case 2:
            return 0.5D;
         case 3:
            return 8.0D;
         case 4:
         case 5:
            return 1.0D;
         case 6:
            return 0.5D;
         case 7:
         case 8:
         case 28:
         default:
            return 0.05D;
         case 9:
         case 10:
            return 0.1D;
         case 11:
            return 5.0D;
         case 12:
         case 13:
         case 14:
         case 15:
            return 1.0D;
         case 16:
            return 0.05D;
         case 17:
            return 0.1D;
         case 18:
            return 5.0D;
         case 19:
            return 0.5D;
         case 20:
         case 21:
         case 22:
         case 23:
            return 0.05D;
         case 24:
            return 1.0D;
         case 25:
            return 0.1D;
         case 26:
            return 5.0D;
         case 27:
            return 10.0D;
         case 29:
            return 1.0D;
         case 30:
            return 0.5D;
         case 31:
            return 0.5D;
         case 32:
            return 0.1D;
         case 33:
            return 1.0D;
         case 34:
            return 0.05D;
         case 35:
            return 0.5D;
      }
   }

   private Button addButton(int x, int y, int w, int h, Component label, Button.OnPress action) {
      return (Button)this.addRenderableWidget(Button.builder(label, action).bounds(x, y, w, h).build());
   }

   static String nameOf(Siding siding) {
      try {
         String name = siding.name;
         return name != null && !name.isBlank() ? name : tr("sidings.unnamed").getString();
      } catch (RuntimeException var2) {
         return tr("sidings.unnamed").getString();
      }
   }

   private void drawVanillaBackground(GuiGraphics context) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
   }

   private void drawCentered(GuiGraphics context, Component text, int cx, int y, int color) {
      context.drawCenteredString(this.font, text, cx, y, color);
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.drawVanillaBackground(context);
      super.render(context, mouseX, mouseY, delta);
      this.drawCentered(context, this.title, this.width / 2, 22, 16777215);
      boolean anyOn = this.target.readToggle(0);
      MutableComponent subtitle = tr("config.status", this.onOff(0), this.onOff(1), this.onOff(2)).append(this.scrollHint());
      this.drawCentered(context, subtitle, this.width / 2, 36, anyOn ? 10354589 : 16752029);
      this.drawLabels(context);
      this.drawSwatches(context);
      this.drawScrollBar(context);
      this.drawCentered(context, tr("hint.json"), this.width / 2, this.height - 70 - 14, 9474192);
      this.drawCentered(context, tr("hint.author"), this.width / 2, this.height - 14, 11579568);
   }

   private Component onOff(int which) {
      return tr(this.target.readToggle(which) ? "config.on" : "config.off");
   }

   private Component scrollHint() {
      return this.rows.size() <= this.visibleRows ? Component.empty() : tr("config.scrollHint", this.scrollRow + 1, this.rows.size());
   }

   private void drawLabels(GuiGraphics context) {
      for(ParameterScreen.Label label : this.labels) {
         this.drawCentered(context, (Component)label.text.get(), label.x + label.w / 2, label.y + (label.h - 8) / 2, 16777215);
      }

   }

   private void drawSwatches(GuiGraphics context) {
      for(ParameterScreen.Swatch swatch : this.swatches) {
         int side = Math.max(8, Math.min(12, swatch.h - 6));
         int x = swatch.x + 6;
         int y = swatch.y + (swatch.h - side) / 2;
         context.fill(x - 1, y - 1, x + side + 1, y + side + 1, -16777216);
         context.fill(x, y, x + side, y + side, -16777216 | swatch.rgb & 16777215);
         int textRight = swatch.x + swatch.w - 6;
         this.drawCentered(context, Component.literal(HeadlightConfig.formatColor(swatch.rgb)), (x + side + textRight) / 2, swatch.y + (swatch.h - 8) / 2, -2039584);
      }

   }

   private void drawScrollBar(GuiGraphics context) {
      if (this.rows.size() > this.visibleRows) {
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
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.closeToParent();
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.rows.size() > this.visibleRows && amount != 0.0D) {
         int before = this.scrollRow;
         this.scrollRow -= (int)Math.signum(amount);
         this.reinit();
         if (this.scrollRow != before) {
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, amount);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      int page = Math.max(1, this.visibleRows - 1);
      switch (keyCode) {
         case 266:
            return this.scrollBy(-page);
         case 267:
            return this.scrollBy(page);
         default:
            return super.keyPressed(keyCode, scanCode, modifiers);
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

   private final class NumberSlider extends AbstractSliderButton {
      private final int which;
      private final boolean withName;
      private final double min;
      private final double max;
      private final double step;
      private boolean ready;

      NumberSlider(final int x, final int y, final int w, final int h, final int which, final boolean withName, final double current) {
         super(x, y, w, h, Component.empty(), ParameterScreen.normalize(current, ParameterScreen.minOf(which), ParameterScreen.maxOf(which)));
         this.which = which;
         this.withName = withName;
         this.min = ParameterScreen.minOf(which);
         this.max = ParameterScreen.maxOf(which);
         this.step = ParameterScreen.stepOf(which);
         this.ready = true;
         this.updateMessage();
      }

      protected void updateMessage() {
         if (this.ready) {
            this.setMessage(this.withName ? ParameterScreen.this.sliderLabel(this.which, this.actual()) : Component.literal(ParameterScreen.formatValue(this.which, this.actual())));
         }
      }

      protected void applyValue() {
         if (this.ready) {
            ParameterScreen.this.target.writeValue(this.which, this.actual());
         }
      }

      private double actual() {
         return ParameterScreen.snap(this.min + (this.max - this.min) * this.value, this.min, this.max, this.step);
      }
   }

   private static final class Row {
      final int kind;
      final int toggle;
      final int value;
      final boolean head;
      final boolean needsWorld;
      final boolean needsLdl;
      final Component label;
      final Runnable action;

      private Row(int kind, int toggle, int value, boolean head, boolean needsWorld, boolean needsLdl, Component label, Runnable action) {
         this.kind = kind;
         this.toggle = toggle;
         this.value = value;
         this.head = head;
         this.needsWorld = needsWorld;
         this.needsLdl = needsLdl;
         this.label = label;
         this.action = action;
      }

      static ParameterScreen.Row toggle(int toggle, int value) {
         return new ParameterScreen.Row(0, toggle, value, false, false, false, Component.empty(), (Runnable)null);
      }

      static ParameterScreen.Row toggleWide(int toggle) {
         return new ParameterScreen.Row(4, toggle, -1, false, false, false, Component.empty(), (Runnable)null);
      }

      static ParameterScreen.Row value(int value) {
         return new ParameterScreen.Row(1, -1, value, false, false, false, Component.empty(), (Runnable)null);
      }

      static ParameterScreen.Row color(boolean head) {
         return new ParameterScreen.Row(2, -1, -1, head, false, false, Component.empty(), (Runnable)null);
      }

      static ParameterScreen.Row action(Component label, Runnable action) {
         return new ParameterScreen.Row(3, -1, -1, false, false, false, label, action);
      }

      static ParameterScreen.Row worldAction(Component label, Runnable action) {
         return new ParameterScreen.Row(3, -1, -1, false, true, false, label, action);
      }

      static ParameterScreen.Row mode() {
         return new ParameterScreen.Row(5, -1, 28, false, false, false, Component.empty(), (Runnable)null);
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

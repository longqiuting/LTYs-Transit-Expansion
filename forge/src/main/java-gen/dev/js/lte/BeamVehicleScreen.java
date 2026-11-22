package dev.js.lte;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

final class BeamVehicleScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 200;
   private static final int STATUS_W = 128;
   private static final int CLEAR_W = 18;
   private static final int TITLE_Y = 22;
   private static final int HINT_Y = 36;
   private static final int SEARCH_Y = 46;
   private static final int SEARCH_H = 18;
   private static final int LIST_TOP = 72;
   private static final int FOOTER_H = 44;
   private final Screen parent;
   private final Screen root;
   private final String preset;
   private List<BeamSession.VehicleEntry> all = List.of();
   private List<BeamSession.VehicleEntry> shown = List.of();
   private EditBox searchField;
   private Button clearButton;
   private String query = "";
   private int left;
   private int colW;
   private int rowW;
   private int statusRight;
   private int listTop = 72;
   private int visibleRows = 1;
   private int scrollRow;
   private final List<AbstractWidget> dynamicRows = new ArrayList();

   BeamVehicleScreen(Screen parent, Screen root, String preset) {
      super(tr("beam.title.vehicles"));
      this.parent = parent;
      this.root = root == null ? parent : root;
      this.preset = preset != null && !preset.isBlank() ? preset : null;
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private int viewBottom() {
      return this.height - 44 - 8;
   }

   protected void init() {
      this.all = this.order(BeamSession.vehicles());
      int need = 0;

      for(BeamSession.VehicleEntry entry : this.all) {
         need = Math.max(need, this.textWidth(this.label(entry)));
      }

      int charW = this.font.width("\u6d4b");
      int maxCol = Math.max(200, this.width - 32);
      this.colW = Math.min(maxCol, Math.max(200, need + 128 + 8 + charW * 2));
      this.left = this.width / 2 - this.colW / 2;
      this.statusRight = this.left + this.colW;
      this.rowW = this.colW - 128 - 8;
      this.visibleRows = Math.max(1, (this.viewBottom() - 72 + 5) / 25);
      this.listTop = this.listTop();
      this.buildTopBar();
      this.rebuildRows();
   }

   private List<BeamSession.VehicleEntry> order(List<BeamSession.VehicleEntry> entries) {
      if (this.preset == null) {
         return entries;
      } else {
         List<BeamSession.VehicleEntry> out = new ArrayList(entries.size());

         for(BeamSession.VehicleEntry entry : entries) {
            if (entry.id.equalsIgnoreCase(this.preset)) {
               out.add(entry);
            }
         }

         for(BeamSession.VehicleEntry entry : entries) {
            if (!entry.id.equalsIgnoreCase(this.preset)) {
               out.add(entry);
            }
         }

         return out;
      }
   }

   private int listTop() {
      int shownRows = Math.min(this.visibleRows, this.shown.size());
      int listH = shownRows * 25 - 5;
      boolean scrolls = this.shown.size() > this.visibleRows;
      return scrolls ? 72 : 72 + Math.max(0, (this.viewBottom() - 72 - listH) / 2);
   }

   private int textWidth(Component text) {
      return this.font.width(text.getString());
   }

   private Component label(BeamSession.VehicleEntry entry) {
      MutableComponent text = Component.literal(entry.id);
      if (this.preset != null && entry.id.equalsIgnoreCase(this.preset)) {
         text.append(tr("beam.preset"));
      }

      return text;
   }

   private void buildTopBar() {
      int fieldW = this.colW - 18 - 8;
      this.searchField = new EditBox(this.font, this.left, 46, Math.max(40, fieldW), 18, tr("beam.search"));
      this.searchField.setMaxLength(64);
      this.searchField.setHint(tr("beam.search"));
      this.searchField.setCanLoseFocus(true);
      this.searchField.setValue(this.query);
      this.searchField.setResponder(this::onQueryChanged);
      this.addRenderableWidget(this.searchField);
      this.clearButton = (Button)this.addRenderableWidget(Button.builder(tr("btn.clearSearch"), (b) -> {
         if (this.searchField != null) {
            this.searchField.setValue("");
            this.searchField.setFocused(true);
         }

      }).bounds(this.left + fieldW + 8, 46, 18, 18).build());
   }

   private void onQueryChanged(String text) {
      String q = text == null ? "" : text.trim();
      if (!q.equals(this.query)) {
         this.query = q;
         this.scrollRow = 0;
         this.rebuildRows();
      }
   }

   private void rebuildRows() {
      for(AbstractWidget widget : this.dynamicRows) {
         this.removeWidget(widget);
      }

      this.dynamicRows.clear();
      List<BeamSession.VehicleEntry> filtered = new ArrayList();

      for(BeamSession.VehicleEntry entry : this.all) {
         if (this.query.isEmpty() || this.matches(entry, this.query)) {
            filtered.add(entry);
         }
      }

      this.shown = filtered;
      this.visibleRows = Math.max(1, (this.viewBottom() - 72 + 5) / 25);
      int maxScroll = Math.max(0, this.shown.size() - this.visibleRows);
      this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxScroll));
      this.listTop = this.listTop();
      int last = Math.min(this.shown.size(), this.scrollRow + this.visibleRows);

      for(int i = this.scrollRow; i < last; ++i) {
         BeamSession.VehicleEntry entry = (BeamSession.VehicleEntry)this.shown.get(i);
         int y = this.listTop + (i - this.scrollRow) * 25;
         String trimmed = this.font.plainSubstrByWidth(this.label(entry).getString(), this.rowW - 12);
         Button button = Button.builder(Component.literal(trimmed), (b) -> this.open(entry)).bounds(this.left, y, this.rowW, 20).build();
         this.attach(button);
      }

      if (this.clearButton != null) {
         this.clearButton.active = !this.query.isEmpty();
      }

      this.buildFooter();
   }

   private void attach(AbstractWidget widget) {
      this.addRenderableWidget(widget);
      this.dynamicRows.add(widget);
   }

   private boolean matches(BeamSession.VehicleEntry entry, String rawQuery) {
      String q = rawQuery.toLowerCase(Locale.ROOT);
      return entry.id.toLowerCase(Locale.ROOT).contains(q);
   }

   private void buildFooter() {
      int bottom = this.height - 44;
      int backW = (this.colW - 8) / 2;
      this.attach(Button.builder(tr("btn.back"), (b) -> this.onClose()).bounds(this.width / 2 - backW / 2, bottom, backW, 20).build());
   }

   private void open(BeamSession.VehicleEntry entry) {
      if (this.minecraft != null && entry != null) {
         this.minecraft.setScreen(new BeamListScreen(this, this.root, BeamSession.open(entry.id)));
      }
   }

   private void drawRight(GuiGraphics context, Component text, int right, int y, int color) {
      context.drawString(this.font, text, right - this.textWidth(text), y, color);
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      this.drawPresetFrame(context);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, 22, 16777215);
      String hint = this.font.plainSubstrByWidth(tr("beam.hint.vehicles").getString(), Math.max(80, this.width - 32));
      context.drawCenteredString(this.font, Component.literal(hint), this.width / 2, 36, 11579568);
      if (this.all.isEmpty()) {
         context.drawCenteredString(this.font, tr("beam.noVehicles"), this.width / 2, 88, 16765823);
         context.drawCenteredString(this.font, tr("beam.noVehiclesHint"), this.width / 2, 102, 9474192);
      } else if (this.shown.isEmpty()) {
         context.drawCenteredString(this.font, tr("sidings.noMatch"), this.width / 2, this.listTop + 16, 16765823);
      } else {
         int last = Math.min(this.shown.size(), this.scrollRow + this.visibleRows);

         for(int i = this.scrollRow; i < last; ++i) {
            BeamSession.VehicleEntry entry = (BeamSession.VehicleEntry)this.shown.get(i);
            int y = this.listTop + (i - this.scrollRow) * 25;
            Component auto = entry.auto == null ? tr("beam.auto.none") : tr("beam.auto.summary", entry.auto);
            this.drawRight(context, auto, this.statusRight, y + 6, entry.auto == null ? 10132122 : 10354589);
         }

         this.drawScrollBar(context);
         if (this.shown.size() > this.visibleRows) {
            context.drawCenteredString(this.font, tr("config.scrollHint", this.scrollRow + 1, this.shown.size()), this.width / 2, this.height - 44 - 14, 9474192);
         }

      }
   }

   private void drawPresetFrame(GuiGraphics context) {
      if (this.preset != null && !this.shown.isEmpty()) {
         int last = Math.min(this.shown.size(), this.scrollRow + this.visibleRows);

         for(int i = this.scrollRow; i < last; ++i) {
            BeamSession.VehicleEntry entry = (BeamSession.VehicleEntry)this.shown.get(i);
            if (entry.id.equalsIgnoreCase(this.preset)) {
               int y = this.listTop + (i - this.scrollRow) * 25;
               context.fill(this.left - 2, y - 2, this.left + this.rowW + 2, y - 1, -1);
               context.fill(this.left - 2, y + 20 + 1, this.left + this.rowW + 2, y + 20 + 2, -1);
               context.fill(this.left - 2, y - 2, this.left - 1, y + 20 + 2, -1);
               context.fill(this.left + this.rowW + 1, y - 2, this.left + this.rowW + 2, y + 20 + 2, -1);
            }
         }

      }
   }

   private void drawScrollBar(GuiGraphics context) {
      if (this.shown.size() > this.visibleRows) {
         int top = 72;
         int bottom = this.listTop + this.visibleRows * 25 - 5;
         int x = this.left + this.colW + 4;
         context.fill(x, 72, x + 1, Math.max(72, bottom), 1627389951);
         int trackH = Math.max(1, bottom - 72);
         int thumbH = Math.max(12, trackH * this.visibleRows / this.shown.size());
         int span = Math.max(1, this.shown.size() - this.visibleRows);
         int thumbY = 72 + (trackH - thumbH) * this.scrollRow / span;
         context.fill(x - 1, thumbY, x + 2, thumbY + thumbH, -1);
      }
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
      if (this.shown.size() > this.visibleRows && amount != 0.0D) {
         this.scrollRow -= (int)Math.signum(amount);
         this.rebuildRows();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.searchField != null && this.searchField.isFocused() && !this.query.isEmpty() && keyCode == 256) {
         this.searchField.setValue("");
         return true;
      } else if (keyCode == 267) {
         return this.scrollBy(Math.max(1, this.visibleRows - 1));
      } else {
         return keyCode == 266 ? this.scrollBy(-Math.max(1, this.visibleRows - 1)) : super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   private boolean scrollBy(int delta) {
      if (this.shown.size() <= this.visibleRows) {
         return false;
      } else {
         this.scrollRow += delta;
         this.rebuildRows();
         return true;
      }
   }
}

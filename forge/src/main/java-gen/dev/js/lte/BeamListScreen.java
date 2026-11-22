package dev.js.lte;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

final class BeamListScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 200;
   private static final int MIN_CELL = 84;
   private static final int SEL_W = 20;
   private static final int TITLE_Y = 22;
   private static final int HINT_Y = 36;
   private static final int CONTENT_TOP = 58;
   private static final int FOOTER_H = 70;
   private static final int FOOTER_ROW_GAP = 6;
   private static final int LIST_BOTTOM_PAD = 20;
   private final Screen parent;
   private final Screen root;
   private BeamSession session;
   private boolean discarded;
   private final List<AbstractWidget> dynamicRows = new ArrayList();
   private String notice = "";
   private int colX;
   private int colW;
   private int rowX;
   private int rowW;
   private int listTop;
   private int visibleRows = 1;
   private int scrollRow;

   BeamListScreen(Screen parent, Screen root, BeamSession session) {
      super(tr("beam.title.list", session.vehicleId));
      this.parent = parent;
      this.root = root == null ? parent : root;
      this.session = session;
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private int viewBottom() {
      return this.height - 70 - 20;
   }

   protected void init() {
      if (this.discarded) {
         this.discarded = false;
         this.scrollRow = 0;
         this.notice = "";
         this.session = BeamSession.open(this.session.vehicleId);
      }

      int need = 0;

      for(int i = 0; i < this.session.size(); ++i) {
         need = Math.max(need, this.textWidth(Component.literal(this.rowText(this.session.beam(i), i))));
      }

      int cellNeed = 0;

      for(Component label : List.of(tr("btn.beamAdd"), tr("btn.beamDelete"), tr("btn.beamExport"), tr("btn.beamDiscard"))) {
         cellNeed = Math.max(cellNeed, this.textWidth(label) + 12);
      }

      int charW = this.font.width("\u6d4b");
      int maxCol = Math.max(200, this.width - 24);
      this.colW = Math.min(maxCol, Math.max(200, Math.max(need + 20 + 8 + charW, cellNeed * 3 + 16)));
      this.colX = this.width / 2 - this.colW / 2;
      this.rowX = this.colX + 20 + 8;
      this.rowW = this.colW - 20 - 8;
      this.visibleRows = Math.max(1, (this.viewBottom() - 58 + 5) / 25);
      int maxScroll = Math.max(0, this.session.size() - this.visibleRows);
      this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxScroll));
      int shownRows = Math.min(this.visibleRows, this.session.size());
      int listH = shownRows * 25 - 5;
      this.listTop = maxScroll == 0 ? 58 + Math.max(0, (this.viewBottom() - 58 - listH) / 2) : 58;
      this.rebuildRows();
   }

   private int textWidth(Component text) {
      return this.font.width(text.getString());
   }

   private String rowText(VehicleLightOverrides.Beam beam, int index) {
      if (beam == null) {
         return "?";
      } else {
         String length = beam.length == null ? "?" : String.format(Locale.ROOT, "%.1f", beam.length);
         return "#" + (index + 1) + " " + BeamSession.typeName(BeamSession.typeOf(beam)) + " " + BeamSession.hex(beam.color) + " " + length;
      }
   }

   private void rebuildRows() {
      for(AbstractWidget widget : this.dynamicRows) {
         this.removeWidget(widget);
      }

      this.dynamicRows.clear();
      int last = Math.min(this.session.size(), this.scrollRow + this.visibleRows);

      for(int i = this.scrollRow; i < last; ++i) {
         int index = i;
         VehicleLightOverrides.Beam beam = this.session.beam(i);
         int y = this.listTop + (i - this.scrollRow) * 25;
         boolean picked = this.session.selected.contains(i);
         Button box = Button.builder(Component.literal(picked ? "\u2611" : "\u2610"), (b) -> this.toggle(index)).bounds(this.colX, y, 20, 20).build();
         this.attach(box);
         String label = this.font.plainSubstrByWidth(this.rowText(beam, i), this.rowW - 12);
         this.attach(Button.builder(Component.literal(label), (b) -> this.open(index)).bounds(this.rowX, y, this.rowW, 20).build());
      }

      this.buildFooter();
   }

   private void buildFooter() {
      int rowA = this.height - 70;
      int cell3 = (this.colW - 16) / 3;
      int barW = cell3 * 3 + 16;
      int barX = this.width / 2 - barW / 2;
      this.attach(Button.builder(tr("btn.beamAdd"), (b) -> this.addBeam()).bounds(barX, rowA, cell3, 20).build());
      Button delete = Button.builder(tr("btn.beamDelete"), (b) -> this.deleteSelected()).bounds(barX + cell3 + 8, rowA, cell3, 20).build();
      delete.active = !this.session.selected.isEmpty();
      this.attach(delete);
      this.attach(Button.builder(tr("btn.beamExport"), (b) -> this.export()).bounds(barX + (cell3 + 8) * 2, rowA, cell3, 20).build());
      int rowB = rowA + 20 + 6;
      int cell2 = (this.colW - 8) / 2;
      int x2 = this.width / 2 - (cell2 * 2 + 8) / 2;
      Button discard = Button.builder(tr("btn.beamDiscard"), (b) -> this.confirmDiscard()).bounds(x2, rowB, cell2, 20).build();
      discard.active = BeamSession.editedCount() > 0;
      this.attach(discard);
      this.attach(Button.builder(tr("btn.back"), (b) -> this.onClose()).bounds(x2 + cell2 + 8, rowB, cell2, 20).build());
   }

   private void attach(AbstractWidget widget) {
      this.addRenderableWidget(widget);
      this.dynamicRows.add(widget);
   }

   private void toggle(int index) {
      Integer key = index;
      if (!this.session.selected.remove(key)) {
         this.session.selected.add(key);
      }

      this.notice = "";
      this.reinit();
   }

   private void open(int index) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new BeamEditScreen(this, this.root, this.session, index));
      }
   }

   private void addBeam() {
      int index = this.session.add();
      this.notice = "";
      this.open(index);
   }

   private void deleteSelected() {
      if (!this.session.deleteSelected()) {
         this.notice = tr("beam.keepOne").getString();
         this.reinit();
      } else {
         this.notice = "";
         this.reinit();
      }
   }

   private void export() {
      String result = BeamSession.exportFile();
      Minecraft client = Minecraft.getInstance();
      Component message;
      if (result.startsWith("!")) {
         message = tr("beam.export.fail", result.substring(1)).withStyle(ChatFormatting.RED);
         LteLog.LOGGER.warn("[lte] beam editor: export failed: {}", result.substring(1));
      } else {
         String[] parts = result.split("\\|");
         message = tr("beam.export.ok", parts.length > 0 ? parts[0] : "?", parts.length > 1 ? parts[1] : "?", BeamSession.exportPath().toString()).withStyle(ChatFormatting.GREEN);
         LteLog.LOGGER.info("[lte] beam editor: exported {} vehicle(s) to {}", parts.length > 0 ? parts[0] : "?", BeamSession.exportPath());
      }

      if (client.player != null) {
         client.player.displayClientMessage(message, false);
      }

      this.notice = message.getString();
      this.reinit();
   }

   private void confirmDiscard() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ResetConfirmScreen(this, tr("beam.discard.title"), tr("beam.discard.confirm", BeamSession.editedCount()), () -> {
            int removed = BeamSession.abandon();
            this.discarded = true;
            LteLog.LOGGER.info("[lte] beam editor: abandoned {} vehicle override(s)", removed);
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
      context.drawCenteredString(this.font, tr("beam.count", this.session.size(), this.session.selected.size()), this.width / 2, 36, 11579568);
      if (this.session.size() == 0) {
         context.drawCenteredString(this.font, tr("beam.empty"), this.width / 2, 74, 16765823);
      }

      if (this.session.size() > this.visibleRows) {
         context.drawCenteredString(this.font, tr("config.scrollHint", this.scrollRow + 1, this.session.size()), this.width / 2, this.height - 70 - 14, 9474192);
         this.drawScrollBar(context);
      }

      String hint = this.notice.isEmpty() ? tr("beam.hint.list").getString() : this.notice;
      context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(hint, Math.max(80, this.width - 32))), this.width / 2, this.height - 14, this.notice.isEmpty() ? 9474192 : 16765823);
   }

   private void drawScrollBar(GuiGraphics context) {
      int top = 58;
      int bottom = this.listTop + this.visibleRows * 25 - 5;
      int x = this.colX + this.colW + 4;
      context.fill(x, 58, x + 1, Math.max(58, bottom), 1627389951);
      int trackH = Math.max(1, bottom - 58);
      int thumbH = Math.max(12, trackH * this.visibleRows / Math.max(1, this.session.size()));
      int span = Math.max(1, this.session.size() - this.visibleRows);
      int thumbY = 58 + (trackH - thumbH) * this.scrollRow / span;
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
      if (this.session.size() > this.visibleRows && amount != 0.0D) {
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
      if (this.session.size() <= this.visibleRows) {
         return false;
      } else {
         this.scrollRow += delta;
         this.reinit();
         return true;
      }
   }
}

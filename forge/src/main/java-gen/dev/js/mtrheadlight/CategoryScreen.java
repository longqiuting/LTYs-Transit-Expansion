package dev.js.mtrheadlight;

import java.util.ArrayList;
import java.util.List;
import mtr.data.Siding;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class CategoryScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 176;
   private static final int CONTENT_TOP = 58;
   private static final int FOOTER_H = 44;
   private static final int BOTTOM_PAD = 16;
   private final Screen back;
   private final Screen root;
   private final Siding siding;
   private final List<ConfigCategory> shown = new ArrayList();
   private int colX;
   private int colW;
   private int listTop;
   private int visibleRows = 1;
   private int scrollRow;

   public CategoryScreen(Screen back, Screen root, Siding siding) {
      super(siding == null ? tr("cat.title.global") : tr("cat.title.siding", Long.toString(siding.id), ParameterScreen.nameOf(siding)));
      this.back = back;
      this.root = root == null ? back : root;
      this.siding = siding;
   }

   static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   private boolean isGlobal() {
      return this.siding == null;
   }

   private String sidingHint() {
      if (this.siding == null) {
         return "?";
      } else {
         try {
            StringBuilder out = new StringBuilder();
            out.append('#').append(this.siding.id).append(' ').append(ParameterScreen.nameOf(this.siding));
            out.append("  \u00b7  ").append(this.siding.transportMode == null ? "?" : this.siding.transportMode.name()).append("  \u00b7  ").append(Math.round(this.siding.railLength)).append(" \u683c");
            return out.toString();
         } catch (RuntimeException var2) {
            return "?";
         }
      }
   }

   private int viewBottom() {
      return this.height - 44 - 16;
   }

   private int rowsPerPage() {
      return Math.max(1, (this.viewBottom() - 58 + 5) / 25);
   }

   protected void init() {
      this.shown.clear();

      for(ConfigCategory category : ConfigCategory.values()) {
         if (this.siding == null || category.hasSidingRows) {
            this.shown.add(category);
         }
      }

      int need = 0;

      for(ConfigCategory category : this.shown) {
         need = Math.max(need, this.font.width(category.title().getString()));
      }

      int charW = this.font.width("\u6d4b");
      int maxCol = Math.max(176, this.width - 24);
      this.colW = Math.min(maxCol, Math.max(176, need + charW * 2 + 32));
      this.colX = this.width / 2 - this.colW / 2;
      this.visibleRows = this.rowsPerPage();
      int maxScroll = Math.max(0, this.shown.size() - this.visibleRows);
      this.scrollRow = Math.max(0, Math.min(this.scrollRow, maxScroll));
      int listH = Math.min(this.visibleRows, this.shown.size()) * 25 - 5;
      this.listTop = maxScroll == 0 ? 58 + Math.max(0, (this.viewBottom() - 58 - listH) / 2) : 58;
      int last = Math.min(this.shown.size(), this.scrollRow + this.visibleRows);

      for(int i = this.scrollRow; i < last; ++i) {
         ConfigCategory category = (ConfigCategory)this.shown.get(i);
         int y = this.listTop + (i - this.scrollRow) * 25;
         this.addButton(this.colX, y, this.colW, 20, category.title(), (b) -> this.open(category));
      }

      int y = this.height - 44;
      this.addButton(this.width / 2 - this.colW / 2, y, this.colW, 20, tr("btn.back"), (b) -> this.closeToBack());
   }

   private Button addButton(int x, int y, int w, int h, Component label, Button.OnPress action) {
      return (Button)this.addRenderableWidget(Button.builder(label, action).bounds(x, y, w, h).build());
   }

   private void open(ConfigCategory category) {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new ParameterScreen(this, this.root, this.siding, category));
      }

   }

   private void closeToBack() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.back);
      }

   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, 22, 16777215);
      Component hint = this.isGlobal() ? tr("cat.hint.global") : tr("cat.hint.siding", this.sidingHint());
      context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(hint.getString(), this.width - 24)), this.width / 2, 38, 11579568);
      if (this.shown.size() > this.visibleRows) {
         this.drawScrollBar(context);
      }

      context.drawCenteredString(this.font, tr("hint.author"), this.width / 2, this.height - 14, 11579568);
   }

   private void drawScrollBar(GuiGraphics context) {
      int top = 58;
      int bottom = this.listTop + this.visibleRows * 25 - 5;
      int x = this.colX + this.colW + 4;
      context.fill(x, 58, x + 1, Math.max(58, bottom), 1627389951);
      int trackH = Math.max(1, bottom - 58);
      int thumbH = Math.max(12, trackH * this.visibleRows / this.shown.size());
      int span = Math.max(1, this.shown.size() - this.visibleRows);
      int thumbY = 58 + (trackH - thumbH) * this.scrollRow / span;
      context.fill(x - 1, thumbY, x + 2, thumbY + thumbH, -1);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.closeToBack();
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
      if (this.shown.size() > this.visibleRows && amount != 0.0D) {
         this.scrollRow -= (int)Math.signum(amount);
         this.rebuildWidgets();
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, amount);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 267) {
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
         this.rebuildWidgets();
         return true;
      }
   }
}

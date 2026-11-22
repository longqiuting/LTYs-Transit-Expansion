package dev.js.mtrheadlight;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class PackImportScreen extends Screen {
   private static final int ROW_H = 18;
   private static final int ROW_PITCH = 20;
   private static final int PAD = 12;
   private static String lastPath = "";
   private final Screen parent;
   private final PackImportScreen.Listener listener;
   private final List<Path> candidates = new ArrayList();
   private EditBox pathBox;
   private String status = "";
   private boolean failed;
   private int listTop;
   private int listRows;

   PackImportScreen(Screen parent, PackImportScreen.Listener listener) {
      super(Component.translatable("beam3d.import.title"));
      this.parent = parent;
      this.listener = listener;
   }

   protected void init() {
      this.candidates.clear();
      this.candidates.addAll(CustomPack.candidates());
      LteLog.LOGGER.info("[lte] import: {} candidate pack(s)", this.candidates.size());
      int boxW = Math.max(80, this.width - 24);
      EditBox box = new EditBox(this.font, 12, 58, boxW, 18, Component.translatable("beam3d.import.path"));
      box.setMaxLength(512);
      box.setValue(lastPath);
      this.pathBox = (EditBox)this.addRenderableWidget(box);
      this.listTop = 96;
      this.listRows = Math.max(1, Math.min(this.candidates.size(), (this.height - 62 - this.listTop) / 20));
      int buttonY = this.height - 40;
      int half = Math.max(60, (Math.min(360, this.width - 24) - 8) / 2);
      int left = this.width / 2 - half - 4;
      this.addRenderableWidget(Button.builder(Component.translatable("beam3d.import.ok"), (b) -> this.doImport()).bounds(left, buttonY, half, 20).build());
      this.addRenderableWidget(Button.builder(Component.translatable("beam3d.import.cancel"), (b) -> this.onClose()).bounds(left + half + 8, buttonY, half, 20).build());
   }

   private void doImport() {
      String raw = this.pathBox == null ? "" : this.pathBox.getValue();
      String cleaned = raw == null ? "" : raw.trim().replaceAll("^\"|\"$", "");
      if (cleaned.isEmpty()) {
         this.failed = true;
         this.status = Component.translatable("beam3d.import.pathEmpty").getString();
      } else {
         Path path;
         try {
            path = Path.of(cleaned);
         } catch (InvalidPathException var6) {
            this.failed = true;
            this.status = Component.translatable("beam3d.import.failed", new Object[]{Component.literal(var6.getMessage())}).getString();
            return;
         }

         try {
            CustomPack pack = CustomPack.open(path);
            lastPath = cleaned;
            LteLog.LOGGER.info("[lte] import: opened {} ({} trains)", pack, pack.trains().size());
            this.listener.imported(pack);
            this.onClose();
         } catch (Throwable var5) {
            this.failed = true;
            this.status = Component.translatable("beam3d.import.failed", new Object[]{Component.literal(String.valueOf(var5.getMessage()))}).getString();
            LteLog.LOGGER.warn("[lte] import: cannot open {}", cleaned, var5);
         }

      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && mouseX >= 12.0D && mouseX <= (double)(this.width - 12)) {
         for(int i = 0; i < this.listRows; ++i) {
            int y = this.listTop + i * 20;
            if (mouseY >= (double)y && mouseY < (double)(y + 18)) {
               Path path = (Path)this.candidates.get(i);
               String text = path.toAbsolutePath().normalize().toString();
               this.pathBox.setValue(text);
               lastPath = text;
               this.failed = false;
               this.status = "";
               return true;
            }
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 257 && this.pathBox != null && this.pathBox.isFocused()) {
         this.doImport();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      this.renderBackground(context);
      int centre = this.width / 2;
      context.drawCenteredString(this.font, Component.translatable("beam3d.import.title"), centre, 16, 16777215);
      context.drawCenteredString(this.font, Component.translatable("beam3d.import.hint"), centre, 32, 10526880);
      context.drawString(this.font, Component.translatable("beam3d.import.path"), 12, 46, 12566463);
      if (this.candidates.isEmpty()) {
         context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(Component.translatable("beam3d.import.none").getString(), this.width - 24)), 12, this.listTop, 9474192);
      } else {
         context.drawString(this.font, Component.translatable("beam3d.import.found"), 12, this.listTop - 12, 9474192);

         for(int i = 0; i < this.listRows; ++i) {
            Path path = (Path)this.candidates.get(i);
            int y = this.listTop + i * 20;
            boolean hover = mouseY >= y && mouseY < y + 18 && mouseX >= 12 && mouseX <= this.width - 12;
            if (hover) {
               context.fill(12, y, this.width - 12, y + 18, 822083583);
            }

            String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
            String label = name + "   " + String.valueOf(path.toAbsolutePath().getParent());
            context.drawString(this.font, Component.literal(this.font.plainSubstrByWidth(label, this.width - 24 - 6)), 15, y + 5, hover ? 16777215 : 12632256);
         }

         if (this.candidates.size() > this.listRows) {
            context.drawString(this.font, Component.literal("\u2026 +" + (this.candidates.size() - this.listRows)), 12, this.listTop + this.listRows * 20, 8421504);
         }
      }

      if (!this.status.isEmpty()) {
         context.drawCenteredString(this.font, Component.literal(this.font.plainSubstrByWidth(this.status, this.width - 24)), centre, this.height - 62, this.failed ? 16744576 : 10354589);
      }

      super.render(context, mouseX, mouseY, delta);
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }

   }

   static String describe(List<Path> paths) {
      StringBuilder out = new StringBuilder();

      for(Path path : paths) {
         if (out.length() > 0) {
            out.append(", ");
         }

         out.append(path.getFileName() == null ? path.toString() : path.getFileName().toString().toLowerCase(Locale.ROOT));
      }

      return out.toString();
   }

   interface Listener {
      void imported(CustomPack var1);
   }
}

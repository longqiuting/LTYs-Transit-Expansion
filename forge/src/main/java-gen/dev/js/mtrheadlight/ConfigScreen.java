package dev.js.mtrheadlight;

import dev.js.mtrheadlight.mtr.BuiltInTrains;
import java.util.ArrayList;
import java.util.List;
import mtr.data.Siding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

public final class ConfigScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int ROW_H = 20;
   private static final int PITCH = 25;
   private static final int GAP = 8;
   private static final int MIN_COLUMN = 176;
   private static final int CONTENT_TOP = 58;
   private static final int FOOTER_H = 44;
   private static final int BOTTOM_PAD = 14;
   private final Screen parent;
   private String snapshot;
   private boolean internalReinit;
   private int colX;
   private int colW;
   private int listTop;

   public ConfigScreen(Screen parent) {
      super(tr("screen.lte.config"));
      this.parent = parent;
   }

   private static MutableComponent tr(String key, Object... args) {
      return Component.translatable(key, args);
   }

   protected void init() {
      if (this.snapshot == null && !this.internalReinit) {
         this.snapshot = MtrHeadlightClient.snapshotConfig();
      }

      this.internalReinit = false;
      List<Component> labels = new ArrayList();
      labels.add(tr("btn.global"));
      labels.add(this.builtInLabel());
      labels.add(tr("btn.reloadAll"));
      int need = 0;

      for(Component label : labels) {
         need = Math.max(need, this.font.width(label.getString()));
      }

      int charW = this.font.width("\u6d4b");
      int maxCol = Math.max(176, this.width - 24);
      this.colW = Math.min(maxCol, Math.max(176, need + charW * 2 + 28));
      this.colX = this.width / 2 - this.colW / 2;
      int viewBottom = this.height - 44 - 14;
      int listH = labels.size() * 25 - 5;
      this.listTop = 58 + Math.max(0, (viewBottom - 58 - listH) / 2);
      int y = this.listTop;
      this.addButton(this.colX, y, this.colW, 20, (Component)labels.get(0), (b) -> this.openGlobal());
      y += 25;
      this.addButton(this.colX, y, this.colW, 20, (Component)labels.get(1), (b) -> this.toggleBuiltIn());
      y += 25;
      this.addButton(this.colX, y, this.colW, 20, (Component)labels.get(2), (b) -> this.reloadAll());
      int cell = (this.colW - 8) / 2;
      int x = this.width / 2 - (cell * 2 + 8) / 2;
      int fy = this.height - 44;
      this.addButton(x, fy, cell, 20, tr("btn.done"), (b) -> this.commitAndClose());
      this.addButton(x + cell + 8, fy, cell, 20, tr("btn.cancel"), (b) -> this.cancelAndClose());
   }

   private Button addButton(int x, int y, int w, int h, Component label, Button.OnPress action) {
      return (Button)this.addRenderableWidget(Button.builder(label, action).bounds(x, y, w, h).build());
   }

   private MutableComponent builtInLabel() {
      boolean on = MtrHeadlightClient.config().disableBuiltInTrains;
      return tr(on ? "opt.builtin.on" : "opt.builtin.off");
   }

   private void openGlobal() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(new CategoryScreen(this, this, (Siding)null));
      }
   }

   private void toggleBuiltIn() {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      cfg.disableBuiltInTrains = !cfg.disableBuiltInTrains;
      BuiltInTrains.reloadNow();
      this.reinit();
   }

   private void reloadAll() {
      String trains = BuiltInTrains.reloadNow();
      LightController.reload();
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         client.player.displayClientMessage(Component.literal(trains), false);
      }

   }

   private void commitAndClose() {
      ConfigWriteTarget.global(MtrHeadlightClient::config).commit();
      this.closeToParent();
   }

   private void cancelAndClose() {
      MtrHeadlightClient.restoreConfig(this.snapshot);
      this.closeToParent();
   }

   private void closeToParent() {
      if (this.minecraft != null) {
         this.minecraft.setScreen(this.parent);
      }

   }

   private void reinit() {
      this.internalReinit = true;
      this.rebuildWidgets();
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, 22, 16777215);
      HeadlightConfig cfg = MtrHeadlightClient.config();
      MutableComponent status = tr("config.status", this.onOff(cfg.enabled), this.onOff(cfg.dynamicLight), this.onOff(cfg.drawFlare())).append(Component.literal(cfg.disableBuiltInTrains ? tr("main.builtinHidden").getString() : ""));
      context.drawCenteredString(this.font, status, this.width / 2, 38, cfg.enabled ? 10354589 : 16752029);
      context.drawCenteredString(this.font, tr("main.hint"), this.width / 2, this.height - 44 - 16, 9474192);
      context.drawCenteredString(this.font, tr("hint.author"), this.width / 2, this.height - 14, 11579568);
   }

   private Component onOff(boolean on) {
      return tr(on ? "config.on" : "config.off");
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void onClose() {
      this.cancelAndClose();
   }
}

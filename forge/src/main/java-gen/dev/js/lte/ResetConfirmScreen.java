package dev.js.lte;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

final class ResetConfirmScreen extends Screen {
   private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
   private static final int BTN_W = 150;
   private static final int BTN_H = 20;
   private static final int GAP = 8;
   private final Screen parent;
   private final Component message;
   private final Runnable onConfirm;

   ResetConfirmScreen(Screen parent, Component title, Component message, Runnable onConfirm) {
      super(title);
      this.parent = parent;
      this.message = message;
      this.onConfirm = onConfirm;
   }

   protected void init() {
      int y = this.height / 2 + 16;
      int left = this.width / 2 - 154;
      this.addRenderableWidget(Button.builder(ParameterScreen.tr("btn.resetConfirm"), (button) -> {
         this.onConfirm.run();
         this.onClose();
      }).bounds(left, y, 150, 20).build());
      this.addRenderableWidget(Button.builder(ParameterScreen.tr("btn.resetAbort"), (button) -> this.onClose()).bounds(left + 150 + 8, y, 150, 20).build());
   }

   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      context.blit(OPTIONS_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.height, 32, 32);
      context.fillGradient(0, 0, this.width, this.height, -1072689136, -804253680);
      super.render(context, mouseX, mouseY, delta);
      context.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, 16777215);
      List<FormattedCharSequence> lines = this.font.split(this.message, Math.max(120, Math.min(this.width - 60, 340)));
      int y = this.height / 2 - 22;

      for(FormattedCharSequence line : lines) {
         context.drawCenteredString(this.font, line, this.width / 2, y, 14737632);
         y += 12;
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
}

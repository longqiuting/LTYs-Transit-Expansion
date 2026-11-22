package dev.js.lte;

import net.minecraft.client.Minecraft;

public final class BeamEditorOpener {
   private BeamEditorOpener() {
   }

   public static void open() {
      Minecraft client = Minecraft.getInstance();
      client.tell(() -> client.setScreen(new BeamEditor3DScreen(client.screen, (String)null)));
   }
}

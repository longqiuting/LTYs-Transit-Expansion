package dev.js.mtrheadlight.collision;

import dev.js.mtrheadlight.MtrHeadlightClient;
import dev.js.mtrheadlight.net.ClientHandshake;
import dev.js.mtrheadlight.server.ServerConfig;
import dev.js.mtrheadlight.forge.platform.ClientModInitializer;
import dev.js.mtrheadlight.forge.platform.ClientTickEvents;
import dev.js.mtrheadlight.forge.platform.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public final class CollisionBootstrap implements ClientModInitializer {
   public void onInitializeClient() {
      ServerConfig.install(MtrHeadlightClient::config);
      ClientHandshake.register();
      installImpulseMirror();
      ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
         try {
            CollisionSystem.tick();
         } catch (Throwable var2) {
         }

      });
      ServerTickEvents.END_SERVER_TICK.register((ServerTickEvents.EndTick)(server) -> {
         try {
            MobCollision.serverTick(server);
         } catch (Throwable var2) {
         }

      });
   }

   private static void installImpulseMirror() {
      CollisionImpact.installImpulseMirror((id, x, y, z) -> {
         Minecraft client = Minecraft.getInstance();
         if (client != null && id != null) {
            client.execute(() -> {
               try {
                  Player self = client.player;
                  if (self == null || !id.equals(self.getUUID())) {
                     return;
                  }

                  if (DriverKeyBypass.holds(self)) {
                     return;
                  }

                  if (OnboardToolBypass.holds(self)) {
                     return;
                  }

                  self.setDeltaMovement(x, y, z);
                  self.hurtMarked = true;
                  self.fallDistance = 0.0F;
               } catch (Throwable var9) {
               }

            });
         }
      });
   }
}

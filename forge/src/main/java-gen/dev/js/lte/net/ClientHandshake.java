package dev.js.lte.net;

import dev.js.lte.HeadlightConfig;
import dev.js.lte.LteLog;
import dev.js.lte.MtrHeadlightClient;
import dev.js.lte.forge.platform.ClientTickEvents;
import dev.js.lte.forge.platform.ClientPlayConnectionEvents;
import dev.js.lte.forge.platform.ClientPlayNetworking;
import dev.js.lte.forge.platform.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public final class ClientHandshake {
   private static final int RETRY_TICKS = 5;
   private static boolean registered;
   private static int retries;
   private static boolean answered;

   private ClientHandshake() {
   }

   public static void register() {
      if (!registered) {
         registered = true;

         try {
            ClientPlayNetworking.registerGlobalReceiver(LteChannel.ID, (client, handler, buf, responseSender) -> {
               LteChannel.ServerHello hello = LteChannel.ServerHello.read(buf);
               client.execute(() -> accept(hello));
            });
         } catch (Throwable var4) {
            LteLog.LOGGER.warn("[lte] could not register the client handshake receiver", var4);
         }

         try {
            ClientPlayConnectionEvents.JOIN.register((ClientPlayConnectionEvents.Join)(handler, sender, client) -> {
               answered = false;
               retries = 5;
               sendHello();
            });
         } catch (Throwable var3) {
            LteLog.LOGGER.debug("[lte] could not hook the join event", var3);
         }

         try {
            ClientPlayConnectionEvents.DISCONNECT.register((ClientPlayConnectionEvents.Disconnect)(handler, client) -> ServerAuthority.clearRemote());
         } catch (Throwable var2) {
            LteLog.LOGGER.debug("[lte] could not hook the disconnect event", var2);
         }

         try {
            ClientTickEvents.END_CLIENT_TICK.register((ClientTickEvents.EndTick)(client) -> {
               if (!answered && retries > 0) {
                  --retries;
                  sendHello();
               }
            });
         } catch (Throwable var1) {
            LteLog.LOGGER.debug("[lte] could not hook the client tick", var1);
         }

      }
   }

   private static void sendHello() {
      try {
         if (ClientPlayNetworking.canSend(LteChannel.ID)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            LteChannel.writeHello(buf);
            ClientPlayNetworking.send(LteChannel.ID, buf);
         }
      } catch (Throwable var1) {
      }

   }

   private static void accept(LteChannel.ServerHello hello) {
      if (hello != null) {
         answered = true;
         retries = 0;
         HeadlightConfig cfg = MtrHeadlightClient.config();
         boolean before = ServerAuthority.authoritative();
         ServerAuthority.acceptRemote(hello);
         if (!before && ServerAuthority.authoritative()) {
            LteLog.LOGGER.info("[lte] server-side collision is authoritative (collision={} damage={} mobs={} high={}km/h pushOnly={}km/h) - this client will no longer apply collision damage itself", new Object[]{hello.trainCollision, hello.damage, hello.mobs, hello.highDamageMaxKmh, hello.pushOnlyMaxKmh});
         }

         ServerAuthority.warnIfMismatch(cfg.trainCollisionDamage, cfg.trainCollisionHighDamageMaxKmhClamped());
      }
   }
}

package dev.js.mtrheadlight.net;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteLog;
import dev.js.mtrheadlight.server.ServerConfig;
import dev.js.mtrheadlight.forge.platform.PacketByteBufs;
import dev.js.mtrheadlight.forge.platform.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerHandshake {
   private static boolean registered;

   private ServerHandshake() {
   }

   public static void register() {
      if (!registered) {
         registered = true;

         try {
            ServerPlayNetworking.registerGlobalReceiver(LteChannel.ID, (server, player, handler, buf, responseSender) -> {
               int version = LteChannel.readHelloVersion(buf);
               if (version == 1) {
                  server.execute(() -> {
                     try {
                        reply(server, player);
                     } catch (Throwable var3) {
                        LteLog.LOGGER.debug("[lte] could not answer the handshake", var3);
                     }

                  });
               }
            });
         } catch (Throwable var1) {
            LteLog.LOGGER.warn("[lte] could not register the server handshake", var1);
         }

      }
   }

   public static void announce(MinecraftServer server, ServerPlayer player) {
      if (server != null && player != null) {
         try {
            reply(server, player);
         } catch (Throwable var3) {
            LteLog.LOGGER.debug("[lte] could not announce server authority", var3);
         }

      }
   }

   private static void reply(MinecraftServer server, ServerPlayer player) {
      if (player != null && !player.isRemoved()) {
         if (ServerPlayNetworking.canSend(player, LteChannel.ID)) {
            HeadlightConfig cfg = ServerConfig.get();
            boolean collision = cfg.trainCollision;
            boolean damage = collision && cfg.trainCollisionDamage;
            LteChannel.ServerHello hello = new LteChannel.ServerHello(1, collision, damage, cfg.trainCollisionMobs, cfg.trainCollisionHighDamageMaxKmhClamped(), cfg.trainCollisionPushOnlyMaxKmhClamped());
            FriendlyByteBuf buf = PacketByteBufs.create();
            hello.write(buf);
            ServerPlayNetworking.send(player, LteChannel.ID, buf);
            LteLog.LOGGER.debug("[lte] told {} who is authoritative (damage={})", player.getGameProfile().getName(), damage);
         }
      }
   }
}

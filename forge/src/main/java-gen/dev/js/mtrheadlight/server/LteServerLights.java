package dev.js.mtrheadlight.server;

import dev.js.mtrheadlight.HeadlightConfig;
import dev.js.mtrheadlight.LteLog;
import dev.js.mtrheadlight.bind.BindUnbind;
import dev.js.mtrheadlight.bind.DisplayPushTicker;
import dev.js.mtrheadlight.bind.LteBindCommand;
import dev.js.mtrheadlight.net.ServerAuthority;
import dev.js.mtrheadlight.net.ServerHandshake;
import dev.js.mtrheadlight.signal.LteCoreItems;
import dev.js.mtrheadlight.signal.SignalLinkNet;
import dev.js.mtrheadlight.signal.SignalLinkStore;
import dev.js.mtrheadlight.forge.platform.EnvType;
import dev.js.mtrheadlight.forge.platform.ModInitializer;
import dev.js.mtrheadlight.forge.platform.ServerLifecycleEvents;
import dev.js.mtrheadlight.forge.platform.ServerTickEvents;
import dev.js.mtrheadlight.forge.platform.ServerPlayConnectionEvents;
import dev.js.mtrheadlight.forge.platform.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class LteServerLights implements ModInitializer {
   private static volatile HeadlightConfig config = new HeadlightConfig();

   public static HeadlightConfig config() {
      return config;
   }

   public void onInitialize() {
      try {
         config = HeadlightConfig.load();
         ServerConfig.install(LteServerLights::config);
      } catch (Throwable var5) {
         LteLog.LOGGER.error("[lte] server config unavailable - using defaults", var5);
      }

      if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
         ServerAuthority.markDedicated();
      }

      LteServer.module("handshake", ServerHandshake::register);
      LteServer.module("connector items", LteCoreItems::register);
      LteServer.module("/ltebind", LteBindCommand::register);
      LteServer.module("connector unbind hooks", BindUnbind::register);

      try {
         ServerPlayConnectionEvents.JOIN.register((ServerPlayConnectionEvents.Join)(handler, sender, server) -> {
            try {
               ServerPlayer player = handler.player;
               ServerLevel world = player.serverLevel();
               SignalLinkStore store = SignalLinkStore.get(world);
               SignalLinkNet.sendAll(player, SignalLinkNet.dimensionOf(world), store.links(), store.lampLinks());
            } catch (Throwable var6) {
            }

         });
      } catch (Throwable var4) {
         LteLog.LOGGER.debug("[lte] could not hook the signal-link sync", var4);
      }

      try {
         ServerPlayConnectionEvents.JOIN.register((ServerPlayConnectionEvents.Join)(handler, sender, server) -> {
            try {
               ServerHandshake.announce(server, handler.player);
            } catch (Throwable var4) {
            }

         });
      } catch (Throwable var3) {
         LteLog.LOGGER.debug("[lte] could not hook the join event", var3);
      }

      try {
         ServerTickEvents.END_SERVER_TICK.register((ServerTickEvents.EndTick)(server) -> {
            try {
               ServerCollision.tick(server);
            } catch (Throwable var3) {
               ServerCollision.warnOnce(var3);
            }

            try {
               DisplayPushTicker.tick(server);
            } catch (Throwable var2) {
            }

         });
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] server collision unavailable", var2);
      }

      LteLog.LOGGER.info("[lte] server initialised (collision={} damage={} mobs={})", new Object[]{config.trainCollision, config.trainCollisionDamage, config.trainCollisionMobs});
   }

   static void installRegistrySelfCheck() {
      try {
         RegistrySelfCheck.purgePending("init");
      } catch (Throwable var2) {
         LteLog.LOGGER.warn("[lte] block registry self-check unavailable", var2);
      }

      try {
         ServerLifecycleEvents.SERVER_STARTING.register((ServerLifecycleEvents.ServerStarting)(server) -> {
            try {
               RegistrySelfCheck.purgePending("server-starting");
               RegistrySelfCheck.reportBlocks("server-starting");
            } catch (Throwable var2) {
               LteLog.LOGGER.warn("[lte] block registry self-check failed", var2);
            }

         });
      } catch (Throwable var1) {
         LteLog.LOGGER.debug("[lte] could not hook the block registry self-check", var1);
      }

   }
}

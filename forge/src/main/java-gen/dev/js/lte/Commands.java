package dev.js.lte;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import dev.js.lte.collision.CollisionImpact;
import dev.js.lte.collision.CollisionSystem;
import dev.js.lte.collision.Trackbed;
import dev.js.lte.mtr.BuiltInTrains;
import dev.js.lte.server.ServerConfig;
import dev.js.lte.sss.ScatterPass;
import java.util.Locale;
import java.util.UUID;
import dev.js.lte.forge.platform.ClientCommandManager;
import dev.js.lte.forge.platform.ClientCommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class Commands {
   private static final String NAME = "mtr_lte";
   private static final String LEGACY_NAME = "externallights";

   private Commands() {
   }

   public static void register() {
      ClientCommandRegistrationCallback.EVENT.register((ClientCommandRegistrationCallback)(dispatcher, registryAccess) -> {
         dispatcher.register(root(net.minecraft.commands.Commands.literal("mtr_lte")));
         dispatcher.register(root(net.minecraft.commands.Commands.literal("externallights")));
         CommandNode<CommandSourceStack> node = dispatcher.getRoot().getChild("mtr_lte");
         LteLog.LOGGER.info("[lte] commands registered: /{} beam3d={}", "mtr_lte", node != null && node.getChild("beam3d") != null);
      });
   }

   private static LiteralArgumentBuilder<CommandSourceStack> root(LiteralArgumentBuilder<CommandSourceStack> root) {
      return (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)root.executes((context) -> {
         ((CommandSourceStack)context.getSource()).sendSystemMessage(Component.literal("LTE\uff1a\u6307\u4ee4\u6267\u884c\u6210\u529f"));
         MtrHeadlightClient.openConfigScreen();
         return 1;
      })).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("beam3d").executes((context) -> openBeamEditor((String)null))).then(net.minecraft.commands.Commands.argument("vehicle", StringArgumentType.greedyString()).executes((context) -> openBeamEditor(StringArgumentType.getString(context, "vehicle")))))).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("headlight").executes((context) -> report((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.argument("mode", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("AUTO").suggest("ALWAYS").suggest("OFF").buildFuture()).executes((context) -> set((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "mode")))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("rain").executes((context) -> rainStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setRainHush((CommandSourceStack)context.getSource(), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setRainHush((CommandSourceStack)context.getSource(), false)))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> rainStatus((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("force").executes((context) -> force((CommandSourceStack)context.getSource(), true))).then(net.minecraft.commands.Commands.argument("state", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture()).executes((context) -> force((CommandSourceStack)context.getSource(), !"off".equalsIgnoreCase(StringArgumentType.getString(context, "state")))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("trains").executes((context) -> trainsStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> trainsStatus((CommandSourceStack)context.getSource())))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setBuiltInTrains((CommandSourceStack)context.getSource(), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setBuiltInTrains((CommandSourceStack)context.getSource(), false)))).then(net.minecraft.commands.Commands.literal("reload").executes((context) -> reloadTrains((CommandSourceStack)context.getSource()))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("flare").executes((context) -> flareStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> flareStatus((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("style").executes((context) -> flareStatus((CommandSourceStack)context.getSource()))).then(((RequiredArgumentBuilder)((RequiredArgumentBuilder)net.minecraft.commands.Commands.argument("what", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("band").suggest("flare").suggest("scatter").buildFuture()).executes((context) -> flareStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setLightSwitch((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "what"), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setLightSwitch((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "what"), false))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("scatter").executes((context) -> scatterStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> scatterStatus((CommandSourceStack)context.getSource())))).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("debug").executes((context) -> scatterDebugStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.argument("what", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("off").suggest("surface").suggest("normal").suggest("cone").buildFuture()).executes((context) -> scatterDebug((CommandSourceStack)context.getSource(), StringArgumentType.getString(context, "what")))))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setScatter((CommandSourceStack)context.getSource(), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setScatter((CommandSourceStack)context.getSource(), false)))).then(net.minecraft.commands.Commands.argument("state", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture()).executes((context) -> setScatter((CommandSourceStack)context.getSource(), !"off".equalsIgnoreCase(StringArgumentType.getString(context, "state"))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("lighttest").executes((context) -> lightTestStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> lightTestStatus((CommandSourceStack)context.getSource())))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setLightTest((CommandSourceStack)context.getSource(), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setLightTest((CommandSourceStack)context.getSource(), false)))).then(net.minecraft.commands.Commands.argument("state", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture()).executes((context) -> setLightTest((CommandSourceStack)context.getSource(), "on".equalsIgnoreCase(StringArgumentType.getString(context, "state"))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("debug").executes((context) -> debugStatus((CommandSourceStack)context.getSource()))).then(((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("spray").executes((context) -> debugStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.argument("state", StringArgumentType.word()).suggests((context, builder) -> builder.suggest("on").suggest("off").buildFuture()).executes((context) -> setDebugSpray((CommandSourceStack)context.getSource(), "on".equalsIgnoreCase(StringArgumentType.getString(context, "state"))))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("killself").executes((context) -> killSelf((CommandSourceStack)context.getSource(), true))).then(net.minecraft.commands.Commands.literal("low").executes((context) -> killSelf((CommandSourceStack)context.getSource(), false)))).then(net.minecraft.commands.Commands.literal("high").executes((context) -> killSelf((CommandSourceStack)context.getSource(), true)))))).then(((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)net.minecraft.commands.Commands.literal("trackbed").executes((context) -> trackbedStatus((CommandSourceStack)context.getSource()))).then(net.minecraft.commands.Commands.literal("status").executes((context) -> trackbedStatus((CommandSourceStack)context.getSource())))).then(net.minecraft.commands.Commands.literal("where").executes((context) -> trackbedWhere((CommandSourceStack)context.getSource())))).then(net.minecraft.commands.Commands.literal("on").executes((context) -> setTrackbedWatch((CommandSourceStack)context.getSource(), true)))).then(net.minecraft.commands.Commands.literal("off").executes((context) -> setTrackbedWatch((CommandSourceStack)context.getSource(), false))));
   }

   private static int killSelf(CommandSourceStack source, boolean high) {
      Minecraft client = Minecraft.getInstance();
      MinecraftServer server = client.getSingleplayerServer();
      if (server != null && client.player != null) {
         UUID id = client.player.getUUID();
         server.execute(() -> {
            ServerPlayer target = null;

            try {
               target = server.getPlayerList().getPlayer(id);
            } catch (Throwable var8) {
               target = null;
            }

            String report = CollisionImpact.debugKill(target, high, ServerConfig.get());
            LteLog.LOGGER.info(report);
            client.execute(() -> {
               try {
                  source.sendSystemMessage(Component.literal(report));
               } catch (Throwable var3) {
               }

            });
         });
         return 1;
      } else {
         source.sendSystemMessage(Component.literal("LTE\uff1akillself \u9700\u8981\u5355\u4eba\u5b58\u6863\uff08\u670d\u52a1\u5668\u6743\u5a01\u5b9e\u4f8b\u4e0d\u5728\u672c\u8fdb\u7a0b\uff09\u2014\u2014\u591a\u4eba\u8bf7\u8ba9\u670d\u4e3b\u88c5 LTE \u540e\u7528 /mtr_lte debug killself"));
         return 0;
      }
   }

   private static int trackbedStatus(CommandSourceStack source) {
      String line = Trackbed.describe(Trackbed.snapped());
      LteLog.LOGGER.info(line);
      Trackbed.setWatch(true);
      source.sendSystemMessage(Component.literal(line));
      source.sendSystemMessage(Component.literal("LTE\uff1a\u9010 tick \u884c\u672b\u5c3e\u73b0\u5728\u662f cand[i]=(dist,off,railY,minY..maxY,why) \u6700\u591a 4 \u6761 + tbl=\uff1awhy=height \u2192 \u88ab\u9ad8\u5ea6\u7a97\u53e3\u6321\u6389\uff08\u62ff railY \u548c pos \u91cc\u7684 y \u4e00\u6bd4\u5c31\u77e5\u9053\uff09\uff1bwhy=bbox/far \u2192 \u5019\u9009\u91cc\u53ea\u6709\u9644\u8fd1\u7684\u5176\u5b83\u8f68\u9053\uff1bwhy=ok+dist>1.3 \u2192 \u5dee\u4e00\u70b9\u70b9\uff1btbl= \u662f\u6574\u5f20\u8868\u91cc\u811a\u4e0b\u6700\u8fd1\u7684\u90a3\u6761\uff08\u4e0d\u770b\u7f13\u5b58\u3001\u4e0d\u770b\u9ad8\u5ea6\uff09\uff0cdist\u22480 \u5374\u6ca1\u8fdb cand \u5c31\u662f\u7f13\u5b58\u6f0f\u4e86\u3002"));
      source.sendSystemMessage(Component.literal("LTE\uff1a\u9053\u5e8a\u53d6\u8bc1\u5df2\u6253\u5f00 \u2014\u2014 \u6bcf 5 \u79d2\u4e00\u884c\u6c47\u603b\uff0c\u5916\u52a0\u6bcf tick \u4e00\u884c trackbed tick:\uff08cand= / best=(dist, offset, y) / dy= / vy= / verdict=\uff09\u3002\u770b drop=\uff1anohit=\u7f13\u5b58\u6ca1\u8f68\u9053 / lat=\u6a2a\u5411\u6ca1\u8e29\u4e0a / dy=\u9ad8\u5ea6\u5dee\u8d85\u7a97\u53e3 / rescue=\u5b89\u5168\u7f51\u63a5\u4f4f\uff1bdrops= \u662f\u5404\u7ed3\u8bba\u8ba1\u6570"));
      return 1;
   }

   private static int setTrackbedWatch(CommandSourceStack source, boolean on) {
      Trackbed.setWatch(on);
      if (on) {
         LteLog.LOGGER.info(Trackbed.describe(Trackbed.snapped()));
      }

      source.sendSystemMessage(Component.literal("LTE\uff1a\u9053\u5e8a\u53d6\u8bc1" + (on ? "\u5df2\u6253\u5f00\uff08\u6bcf 5 \u79d2\u4e00\u53e5\u6c47\u603b + \u6bcf tick \u4e00\u884c tick:\uff09" : "\u5df2\u5173\u95ed")));
      return 1;
   }

   private static int trackbedWhere(CommandSourceStack source) {
      Minecraft client = Minecraft.getInstance();
      if (client != null && client.player != null && client.level != null) {
         LocalPlayer player = client.player;
         String[] lines = Trackbed.where(client.level, CollisionSystem.tickCount(), player.getX(), player.getY(), player.getZ());

         for(String line : lines) {
            if (!line.isEmpty()) {
               LteLog.LOGGER.info(line);
               source.sendSystemMessage(Component.literal(line));
            }
         }

         source.sendSystemMessage(Component.literal("LTE\uff1awhere \u8bfb\u6cd5 \u2014 cand=0 \u8868\u793a\u5468\u56f4\u6ca1\u6709\u8f68\u9053\u8fdb\u5019\u9009\uff1b\u8868\u91cc\u6ca1\u6709 straight=yes \u90a3\u884c\u8868\u793a\u76f4\u9053\u8fde\u5019\u9009\u90fd\u4e0d\u662f\uff1bdist>halfW \u8868\u793a\u627e\u5230\u4e86\u4f46\u6a2a\u5411\u6ca1\u8e29\u4e0a\uff1balong \u8d34 0/len \u8868\u793a\u8fd9\u6761\u5df2\u5230\u5934\uff08\u8be5\u9009\u63a5\u7f1d\u5bf9\u9762\u90a3\u6761\uff09"));
         return 1;
      } else {
         source.sendSystemMessage(Component.literal("LTE\uff1awhere \u9700\u8981\u5728\u6e38\u620f\u91cc\u3001\u6709\u73a9\u5bb6\u4f4d\u7f6e\u624d\u80fd\u67e5"));
         return 0;
      }
   }

   private static int trainsStatus(CommandSourceStack source) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      source.sendSystemMessage(Component.literal("LTE\uff1adisableBuiltInTrains = " + cfg.disableBuiltInTrains + (cfg.disableBuiltInTrains ? "\uff08\u8f66\u5382\u91cc\u53ea\u5269\u8d44\u6e90\u5305/\u9644\u5c5e\u8f66\u578b\u5728\u7528\u5230\u7684\u5185\u7f6e\u8f66\uff09" : "\uff08\u5185\u7f6e\u8f66\u578b\u5168\u90e8\u4fdd\u7559\uff09") + " \u2014 /mtr_lte trains reload \u7acb\u5373\u91cd\u5efa\u6ce8\u518c\u8868"));
      return 1;
   }

   private static int setBuiltInTrains(CommandSourceStack source, boolean on) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      cfg.disableBuiltInTrains = on;
      cfg.save();
      source.sendSystemMessage(Component.literal(BuiltInTrains.reloadNow()));
      return 1;
   }

   private static int reloadTrains(CommandSourceStack source) {
      source.sendSystemMessage(Component.literal(BuiltInTrains.reloadNow()));
      return 1;
   }

   private static int rainStatus(CommandSourceStack source) {
      source.sendSystemMessage(Component.literal("LTE\uff1arain " + RainHush.status()));
      return 1;
   }

   private static int setRainHush(CommandSourceStack source, boolean on) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      cfg.rainHush = on;
      cfg.save();
      source.sendSystemMessage(Component.literal("LTE\uff1arainHush = " + on + (on ? "\uff08\u5728\u8f66\u53a2\u5185\u4e0d\u753b\u96e8/\u96ea\uff09" : "\uff08\u56de\u5230\u539f\u7248\uff1a\u8f66\u5185\u4e5f\u753b\u96e8\uff09")));
      return 1;
   }

   private static int force(CommandSourceStack source, boolean on) {
      RainHush.setForceInside(on);
      source.sendSystemMessage(Component.literal("LTE\uff1arain force = " + on + (on ? "\uff08\u5f3a\u5236\u5728\u8f66\u5185\uff0c\u96e8\u7684\u7ed8\u5236\u88ab cancel\uff09" : "\uff08\u6062\u590d\u6309\u8f66\u53a2\u51e0\u4f55\u5224\u5b9a\uff09")));
      return 1;
   }

   private static int report(CommandSourceStack source) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      source.sendSystemMessage(Component.literal("LTE\uff1aheadlightMode = " + cfg.resolvedMode() + "\uff08AUTO \u6309\u591c\u665a/\u96a7\u9053/\u96e8\u5929\uff0cALWAYS \u5e38\u4eae\uff0cOFF \u4e0d\u81ea\u52a8\u5173\u706f\uff09"));
      return 1;
   }

   private static int flareStatus(CommandSourceStack source) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      HeadlightConfig.Effective eff = cfg.effectiveFor(0L);
      source.sendSystemMessage(Component.literal("LTE\uff1abeamBand = " + cfg.beamBand + " beamFlare = " + cfg.beamFlare + " beamScatter = " + cfg.beamScatter + " draws= band " + (cfg.drawBand() ? "on" : "off") + " / flare " + (cfg.drawFlare() ? "on" : "off") + " / scatter " + (cfg.drawScatter() ? "on" : "off") + "\uff08\u8fd9\u8f86\u8f66\u7684\u503c\uff1a\u5149\u5e26=" + (eff.beamBand ? "\u753b" : "\u4e0d\u753b") + "\uff0c\u706f\u6655=" + (eff.beamFlare ? "\u753b" : "\u4e0d\u753b") + "\uff1b\u7528 /mtr_lte flare style band|flare|scatter on|off \u5207\u6362\uff09 flareSize=" + String.format(Locale.ROOT, "%.2f", cfg.flareSizeClamped()) + " minPx=" + cfg.flareMinPxClamped() + "(\u5b9e\u9645\u7528 " + cfg.beamFlareMinPxFor(Minecraft.getInstance().getWindow().getGuiScaledHeight()) + "\uff0c\u4e0b\u9650 8 \u6216\u5c4f\u9ad8 " + String.format(Locale.ROOT, "%.1f%%", cfg.flareMinPctClamped() * 100.0D) + ") alpha=" + String.format(Locale.ROOT, "%.2f", cfg.beamAlpha) + " blockLight=" + (cfg.beamBlockLight ? "on" : "off") + " radius=" + cfg.beamBlockLightRadius + " \u2014 \u706f\u4f4d\uff1a\u6a21\u578b LIGHT \u90e8\u4ef6 / builtin_lights.json / \u5c3a\u5bf8\u515c\u5e95\uff0c\u9010\u8f66\u578b\u8bca\u65ad\u884c\u89c1 [lte] lights:\uff0c\u6bcf 5 \u79d2\u6c47\u603b\u89c1 [lte] flare:\uff08\u9700 -Dlte.debug.lights=true\uff09"));
      source.sendSystemMessage(Component.literal("LTE\uff1aauto switch " + LightFlare.skipText() + "\uff08\u6700\u8fd1 5 \u79d2\u88ab\u62e6\u4e0b\u7684\u5217\u8f66\u6570\uff0c\u53bb\u91cd\uff1boffRoute=\u4e0d\u5728\u7ebf\u8def/\u8f66\u5382\uff0cdark=AUTO \u4e14\u767d\u5929\u9732\u5929\uff0cparked=AUTO \u4e14\u505c\u4f4f\uff09"));
      if (!BeamBlockLight.alive()) {
         source.sendSystemMessage(Component.literal("LTE\uff1a\u65b9\u5757\u5149\u7167\u6302\u94a9\u6ca1\u6709\u8fd0\u884c\uff08" + (BeamRelight.available() ? "\u533a\u5757\u7f51\u683c\u91cd\u5efa\u88ab\u522b\u7684\u6a21\u7ec4\u63a5\u7ba1" : "Sodium/Embeddium \u5728\u7ebf") + "\uff09\u2014\u2014 \u706f\u6655\u7167\u5e38\u753b\uff0c\u5730\u9762\u7684\u4eae\u6591\u4e0d\u4f1a\u51fa\u73b0"));
      }

      return 1;
   }

   private static int setLightSwitch(CommandSourceStack source, String what, boolean on) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      if ("band".equalsIgnoreCase(what)) {
         cfg.beamBand = on;
      } else if ("flare".equalsIgnoreCase(what)) {
         cfg.beamFlare = on;
      } else {
         if (!"scatter".equalsIgnoreCase(what)) {
            source.sendFailure(Component.literal("LTE\uff1a\u672a\u77e5\u5f00\u5173 " + what + "\uff08band / flare / scatter\uff09"));
            return 0;
         }

         cfg.beamScatter = on;
      }

      cfg.save();
      source.sendSystemMessage(Component.literal("LTE\uff1a\u5149\u5e26=" + cfg.beamBand + " \u706f\u6655=" + cfg.beamFlare + " \u6563\u5c04=" + cfg.beamScatter + "\uff08\u6e32\u67d3\u5c42\u6bcf\u5e27\u8bfb\u8fd9\u4e09\u4e2a\u5e03\u5c14\uff1b-Dlte.debug.lights=true \u65f6\u6bcf 5 \u79d2\u6253\u4e00\u884c [lte] lights:\uff09"));
      return 1;
   }

   private static int scatterStatus(CommandSourceStack source) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      source.sendSystemMessage(Component.literal("LTE\uff1a" + ScatterPass.status(cfg)));
      ScatterPass.requestLitPixelProbe();
      source.sendSystemMessage(Component.literal("LTE\uff1a\u2014 /mtr_lte scatter on|off\uff0c\u8868\u9762\u9879/\u5f3a\u5ea6/\u534a\u5f84\u5728 \u914d\u7f6e \u2192 \u5149\u6e90 \u2192 \u7a7a\u6c14\u6563\u5c04"));
      return 1;
   }

   private static int scatterDebugStatus(CommandSourceStack source) {
      source.sendSystemMessage(Component.literal("LTE\uff1ascatter debug \u5f53\u524d = " + ScatterPass.debugName() + "\uff1b\u53ef\u9009 off / surface / normal / cone\uff08\u4e0d\u5199\u914d\u7f6e\uff0c\u9000\u51fa\u5373\u5931\u6548\uff09"));
      return 1;
   }

   private static int scatterDebug(CommandSourceStack source, String what) {
      source.sendSystemMessage(Component.literal("LTE\uff1a" + ScatterPass.setDebug(what)));
      return 1;
   }

   private static int setScatter(CommandSourceStack source, boolean on) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      cfg.beamScatter = on;
      cfg.save();
      source.sendSystemMessage(Component.literal("LTE\uff1ascatter = " + (on ? "on" : "off") + (on ? "\uff08\u8f66\u706f\u4f1a\u5728\u7a7a\u6c14\u91cc\u6563\u51fa\u5149\u96fe\uff1b\u6ca1\u6709\u706f\u65f6\u96f6\u5f00\u9500\uff09" : "\uff08\u4e0d\u518d\u8fd0\u884c\u5168\u5c4f\u6563\u5c04 pass\uff09")));
      return 1;
   }

   private static int lightTestStatus(CommandSourceStack source) {
      source.sendSystemMessage(Component.literal("LTE\uff1a" + LightTest.status() + " \u2014 /mtr_lte lighttest on|off\uff08\u6d0b\u7ea2 = \u706f\u6655\u5c42\u51e0\u4f55\uff0c\u5de6\u534a\u5c4f\u7ea2 = \u6563\u5c04 pass\uff1b\u65e5\u5fd7\u89c1 [lte] lighttest: \u884c\uff09"));
      return 1;
   }

   private static int setLightTest(CommandSourceStack source, boolean on) {
      LightTest.set(on);
      source.sendSystemMessage(Component.literal("LTE\uff1alighttest = " + (on ? "on" : "off") + (on ? "\uff08\u6bcf\u5217\u8f66\u4e24\u7aef\u5404\u4e00\u5757 3 \u683c\u6d0b\u7ea2\u56db\u8fb9\u5f62 + \u5de6\u534a\u5c4f\u7ea2\uff1b\u6d0b\u7ea2=\u706f\u6655\u5c42\u51e0\u4f55\uff0c\u7ea2=\u6563\u5c04 pass \u5728\u5199\u989c\u8272\uff09" : "\uff08\u6062\u590d\u771f\u5b9e\u6e32\u67d3\uff1a\u706f\u6655 + \u6563\u5c04\uff09")));
      return 1;
   }

   private static int debugStatus(CommandSourceStack source) {
      source.sendSystemMessage(Component.literal("LTE\uff1adebug " + DebugProbe.status() + " \u2014 spray \u63a2\u9488\uff1a\u773c\u524d 2 \u683c\u773c\u5e73\u7684\u4e00\u5708 FLAME\uff0c5 \u79d2" + culledHint()));
      return 1;
   }

   private static String culledHint() {
      String reason = ParticleCull.reason();
      return reason == null ? "" : "\uff1b\u26a0 " + reason;
   }

   private static int setDebugSpray(CommandSourceStack source, boolean on) {
      int ticks = DebugProbe.setSpray(on);
      source.sendSystemMessage(Component.literal("LTE\uff1aspray probe = " + (on ? "on" : "off") + (on ? "\uff08\u773c\u524d 2 \u683c \u00b7 \u773c\u5e73 \u00b7 \u6bcf tick 8 \u9897 FLAME\uff0c" + ticks / 20 + " \u79d2\uff1b\u8fd9 " + ticks / 20 + " \u79d2\u5185\u96e8\u5929\u6c34\u96fe\u5ffd\u7565\u9732\u5929\u9650\u5236\uff09" : "") + culledHint()));
      return 1;
   }

   private static int openBeamEditor(String vehicleId) {
      Minecraft client = Minecraft.getInstance();

      try {
         client.tell(() -> {
            try {
               client.setScreen(new BeamEditor3DScreen(client.screen, vehicleId));
            } catch (Throwable var3) {
               reportBeamEditorFailure(var3);
            }

         });
      } catch (Throwable var3) {
         reportBeamEditorFailure(var3);
      }

      return 1;
   }

   private static void reportBeamEditorFailure(Throwable t) {
      LteLog.LOGGER.error("[lte] beam3d: could not open the editor", t);

      try {
         Minecraft client = Minecraft.getInstance();
         client.gui.getChat().addMessage(Component.literal("LTE: /mtr_lte beam3d \u6253\u5f00\u5931\u8d25 \u2014 " + firstLine(t)).withStyle(ChatFormatting.RED));
      } catch (Throwable var2) {
      }

   }

   private static String firstLine(Throwable t) {
      String text = t == null ? "unknown" : t.toString();
      int newline = text.indexOf(10);
      return newline < 0 ? text : text.substring(0, newline);
   }

   private static int set(CommandSourceStack source, String raw) {
      HeadlightConfig cfg = MtrHeadlightClient.config();
      String mode = HeadlightConfig.normalizeHeadlightMode(raw);
      cfg.headlightMode = mode;
      cfg.save();
      boolean accepted = mode.equalsIgnoreCase(raw.trim());
      source.sendSystemMessage(Component.literal("LTE\uff1aheadlightMode = " + mode + (accepted ? "" : "\uff08\"" + raw + "\" \u4e0d\u662f\u5408\u6cd5\u503c\uff0c\u5df2\u56de\u843d AUTO\uff09")));
      return accepted ? 1 : 0;
   }
}

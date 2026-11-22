package dev.js.lte.bind;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Set;
import dev.js.lte.forge.platform.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class LteBindCommand {
   private LteBindCommand() {
   }

   public static void register() {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> dispatcher.register((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("ltebind").then(Commands.literal("projector").then(coord("x").then(coord("y").then(coord("z").executes(LteBindCommand::projector)))))).then(Commands.literal("pids").then(coord("x").then(coord("y").then(coord("z").executes(LteBindCommand::pids)))))));
   }

   private static RequiredArgumentBuilder<CommandSourceStack, Integer> coord(String name) {
      return Commands.argument(name, IntegerArgumentType.integer());
   }

   private static BlockPos pos(CommandContext<CommandSourceStack> context) {
      return new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"));
   }

   private static int projector(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      ServerLevel world = source.getLevel();
      BlockPos pos = pos(context);
      ProjectorLinkStore.Link link = ProjectorLinkStore.get(world).at(pos);
      if (link == null) {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.projector_none", new Object[]{posText(pos)}).withStyle(ChatFormatting.GRAY), false);
      } else {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.projector_link", new Object[]{posText(pos), link.stationName(), String.valueOf(link.stationId()), String.valueOf(link.platformCount())}).withStyle(ChatFormatting.GREEN), false);
      }

      Object tile = BindTargets.projectorAt(world, pos);
      if (tile == null) {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.not_a_projector").withStyle(ChatFormatting.RED), false);
         return 0;
      } else {
         Set<Long> ids = MtrTiles.projectorPlatformIds(tile);
         String list = MtrTiles.join(ids);
         String displayPage = String.valueOf(readDisplayPage(tile));
         source.sendSuccess(() -> Component.translatable("message.lte.bind.live_platforms", new Object[]{String.valueOf(ids.size()), list.isEmpty() ? "-" : list}).withStyle(ChatFormatting.AQUA).append(" ").append(Component.translatable("message.lte.bind.display_page", new Object[]{displayPage}).withStyle(ChatFormatting.AQUA)), false);
         return 1;
      }
   }

   private static int pids(CommandContext<CommandSourceStack> context) {
      CommandSourceStack source = (CommandSourceStack)context.getSource();
      ServerLevel world = source.getLevel();
      BlockPos pos = pos(context);
      PidsLinkStore.Link link = PidsLinkStore.get(world).at(BindTargets.dataPos(world, pos));
      if (link == null) {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.pids_none", new Object[]{posText(pos)}).withStyle(ChatFormatting.GRAY), false);
      } else {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.pids_link", new Object[]{posText(pos), link.platformName(), String.valueOf(link.platformId()), posText(link.nodePos())}).withStyle(ChatFormatting.GREEN), false);
      }

      List<MtrTiles.PidsTile> tiles = BindTargets.pidsAt(world, pos);
      if (tiles.isEmpty()) {
         source.sendSuccess(() -> Component.translatable("message.lte.bind.not_a_pids").withStyle(ChatFormatting.RED), false);
         return 0;
      } else {
         for(MtrTiles.PidsTile entry : tiles) {
            Set<Long> ids = MtrTiles.pidsPlatformIds(entry.tile());
            String list = MtrTiles.join(ids);
            String where = posText(entry.pos());
            source.sendSuccess(() -> Component.translatable("message.lte.bind.pids_live", new Object[]{where, String.valueOf(ids.size()), list.isEmpty() ? "-" : list}).withStyle(ChatFormatting.AQUA), false);
         }

         return 1;
      }
   }

   private static String readDisplayPage(Object tile) {
      try {
         return String.valueOf(tile.getClass().getMethod("getDisplayPage").invoke(tile));
      } catch (Throwable var2) {
         return "?";
      }
   }

   private static String posText(BlockPos pos) {
      return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
   }
}

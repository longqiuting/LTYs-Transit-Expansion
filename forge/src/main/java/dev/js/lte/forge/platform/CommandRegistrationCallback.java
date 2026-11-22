package dev.js.lte.forge.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * Fabric {@code CommandRegistrationCallback} 的等价物（服务端指令，由 {@code RegisterCommandsEvent} 驱动）。
 *
 * <p>形状与 Fabric 完全一致：<b>接口本身就是函数式接口</b>，lambda 会以
 * {@code (CommandRegistrationCallback)(dispatcher, registryAccess, environment) -> ...} 的形式出现。
 */
@FunctionalInterface
public interface CommandRegistrationCallback {

    FEvent<CommandRegistrationCallback> EVENT = new FEvent<>();

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess,
                  Commands.CommandSelection environment);

    static void fire(final RegisterCommandsEvent event) {
        EVENT.fire(l -> l.register(event.getDispatcher(), event.getBuildContext(),
                event.getCommandSelection()));
    }
}

package dev.js.mtrheadlight.forge.platform;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** Fabric {@code ClientCommandManager} 的等价物：就是原版的 {@link Commands} 静态工厂。 */
public final class ClientCommandManager {

    private ClientCommandManager() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> literal(final String name) {
        return Commands.literal(name);
    }

    public static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(final String name,
                                                                             final ArgumentType<T> type) {
        return Commands.argument(name, type);
    }
}

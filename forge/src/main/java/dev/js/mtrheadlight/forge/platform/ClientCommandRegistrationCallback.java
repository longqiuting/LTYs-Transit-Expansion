package dev.js.mtrheadlight.forge.platform;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;

/**
 * Fabric {@code ClientCommandRegistrationCallback} 的等价物。
 *
 * <p>Forge 1.20.1 的客户端指令走 {@code RegisterClientCommandsEvent}（客户端专用事件），
 * 指令树锚在 {@code CommandSourceStack} 上（Forge 传进来的是 {@code ClientCommandSourceStack}）。
 */
@FunctionalInterface
public interface ClientCommandRegistrationCallback {

    FEvent<ClientCommandRegistrationCallback> EVENT = new FEvent<>();

    void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess);

    static void fire(final RegisterClientCommandsEvent event) {
        EVENT.fire(l -> l.register(event.getDispatcher(), event.getBuildContext()));
    }
}

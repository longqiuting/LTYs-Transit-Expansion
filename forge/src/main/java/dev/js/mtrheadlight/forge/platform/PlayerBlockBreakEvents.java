package dev.js.mtrheadlight.forge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Fabric {@code PlayerBlockBreakEvents} 的等价物（由 {@code BlockEvent.BreakEvent} 驱动）。 */
public final class PlayerBlockBreakEvents {

    public interface After {
        void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity);
    }

    public static final FEvent<After> AFTER = new FEvent<>();

    private PlayerBlockBreakEvents() {
    }
}

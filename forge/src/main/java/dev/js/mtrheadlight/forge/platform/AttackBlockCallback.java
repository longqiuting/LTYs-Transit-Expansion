package dev.js.mtrheadlight.forge.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Fabric {@code AttackBlockCallback} 的等价物（两端都触发，由
 * {@code PlayerInteractEvent.LeftClickBlock} 驱动）。
 */
@FunctionalInterface
public interface AttackBlockCallback {

    FEvent<AttackBlockCallback> EVENT = new FEvent<>();

    InteractionResult interact(Player player, Level world, InteractionHand hand, BlockPos pos,
                               Direction direction);
}

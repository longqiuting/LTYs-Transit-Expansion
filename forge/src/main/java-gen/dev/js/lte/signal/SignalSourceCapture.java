package dev.js.lte.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class SignalSourceCapture {
   public final Direction facing;
   public final Direction sourceFacing;
   public final BlockPos node;

   public SignalSourceCapture(Direction facing, Direction sourceFacing, BlockPos node) {
      this.facing = facing;
      this.sourceFacing = sourceFacing;
      this.node = node;
   }
}

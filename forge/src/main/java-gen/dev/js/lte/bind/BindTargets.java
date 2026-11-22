package dev.js.lte.bind;

import dev.js.lte.signal.SignalLinkNet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mtr.block.BlockNode;
import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

public final class BindTargets {
   private static final int NODE_SEARCH = 3;

   private BindTargets() {
   }

   public static boolean isProjector(Level world, BlockPos pos) {
      return MtrTiles.isProjector(MtrTiles.tileAt(world, pos));
   }

   public static String kind(Level world, BlockPos pos) {
      if (isProjector(world, pos)) {
         return "projector";
      } else {
         try {
            if (BlockNode.class.isInstance(world.getBlockState(pos).getBlock())) {
               return "node";
            }
         } catch (Throwable var3) {
         }

         return "other";
      }
   }

   public static Object projectorAt(Level world, BlockPos pos) {
      Object tile = MtrTiles.tileAt(world, pos);
      return MtrTiles.isProjector(tile) ? tile : null;
   }

   public static List<MtrTiles.PidsTile> pidsAt(Level world, BlockPos pos) {
      List<MtrTiles.PidsTile> out = new ArrayList(2);
      BlockPos data = dataPos(world, pos);
      if (!addPids(world, data, out)) {
         return Collections.emptyList();
      } else {
         Direction towardsOther = halfOf(world, data) == DoubleBlockHalf.UPPER ? Direction.DOWN : Direction.UP;
         BlockPos other = data.relative(towardsOther);
         if (world.getBlockState(other).getBlock() == world.getBlockState(data).getBlock() && isOtherHalf(world, data, other)) {
            addPids(world, other, out);
         }

         return out;
      }
   }

   private static DoubleBlockHalf halfOf(Level world, BlockPos pos) {
      try {
         BlockState state = world.getBlockState(pos);
         return state.hasProperty(IBlock.HALF) ? (DoubleBlockHalf)IBlock.getStatePropertySafe(state, IBlock.HALF) : null;
      } catch (Throwable var3) {
         return null;
      }
   }

   public static BlockPos dataPos(Level world, BlockPos pos) {
      BlockPos reflected = reflectedDataPos(world, pos);
      if (reflected != null) {
         return reflected;
      } else {
         try {
            BlockState state = world.getBlockState(pos);
            if (state.hasProperty(IBlock.HALF) && IBlock.getStatePropertySafe(state, IBlock.HALF) == DoubleBlockHalf.LOWER) {
               BlockPos above = pos.above();
               BlockState upper = world.getBlockState(above);
               if (upper.getBlock() == state.getBlock() && IBlock.getStatePropertySafe(upper, IBlock.HALF) == DoubleBlockHalf.UPPER) {
                  return above;
               }
            }
         } catch (Throwable var6) {
         }

         return pos;
      }
   }

   private static BlockPos reflectedDataPos(Level world, BlockPos pos) {
      try {
         Block block = world.getBlockState(pos).getBlock();
         Method method = block.getClass().getMethod("getBlockPosWithData", BlockGetter.class, BlockPos.class);
         Object value = method.invoke(block, world, pos);
         BlockPos var10000;
         if (value instanceof BlockPos found) {
            var10000 = found.immutable();
         } else {
            var10000 = null;
         }

         return var10000;
      } catch (Throwable var6) {
         return null;
      }
   }

   private static boolean isOtherHalf(Level world, BlockPos a, BlockPos b) {
      try {
         BlockState first = world.getBlockState(a);
         BlockState second = world.getBlockState(b);
         if (first.hasProperty(IBlock.HALF) && second.hasProperty(IBlock.HALF)) {
            return IBlock.getStatePropertySafe(first, IBlock.HALF) != IBlock.getStatePropertySafe(second, IBlock.HALF);
         } else {
            return false;
         }
      } catch (Throwable var5) {
         return false;
      }
   }

   private static boolean addPids(Level world, BlockPos pos, List<MtrTiles.PidsTile> out) {
      BlockEntity entity = world.getBlockEntity(pos);
      if (entity != null && MtrTiles.isPids(entity)) {
         for(MtrTiles.PidsTile existing : out) {
            if (existing.pos().equals(pos)) {
               return true;
            }
         }

         out.add(new MtrTiles.PidsTile(entity, pos.immutable()));
         return true;
      } else {
         return false;
      }
   }

   public static BlockPos findNode(Level world, BlockPos clicked, Vec3 hitPos) {
      if (BlockNode.class.isInstance(world.getBlockState(clicked).getBlock()) && SignalLinkNet.hasRail(world, clicked)) {
         return clicked;
      } else {
         BlockPos best = null;
         double bestDist = Double.MAX_VALUE;
         BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

         for(int dx = -3; dx <= 3; ++dx) {
            for(int dy = -3; dy <= 3; ++dy) {
               for(int dz = -3; dz <= 3; ++dz) {
                  cursor.set(clicked.getX() + dx, clicked.getY() + dy, clicked.getZ() + dz);
                  if (BlockNode.class.isInstance(world.getBlockState(cursor).getBlock()) && SignalLinkNet.hasRail(world, cursor)) {
                     double distance = cursor.distToLowCornerSqr(hitPos.x, hitPos.y, hitPos.z);
                     if (distance < bestDist) {
                        bestDist = distance;
                        best = cursor.immutable();
                     }
                  }
               }
            }
         }

         return best;
      }
   }
}

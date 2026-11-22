package dev.js.lte.signal;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mtr.block.BlockNode;
import mtr.client.ClientData;
import mtr.data.Rail;
import mtr.path.PathData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class FangsuSignalSync {
   private static final String KEY_DISP_TYPE = "dispType";
   private static final String MODE_MTR = "mtr";
   private static final String KEY_RED = "red";
   private static final String KEY_YELLOW = "yellow";
   private static final String KEY_GREEN = "green";
   private static final int ASPECTS = 3;
   private static final Map<Class<?>, FangsuSignalSync.Refs> REFS = new ConcurrentHashMap();

   private FangsuSignalSync() {
   }

   public static void onRender(Object self) {
      try {
         if (!(self instanceof BlockEntity)) {
            return;
         }

         BlockEntity be = (BlockEntity)self;
         Level world = be.getLevel();
         if (world == null || !world.isClientSide) {
            return;
         }

         BlockPos lamp = be.getBlockPos();
         Direction ownFacing = facingOf(be.getBlockState(), Direction.NORTH);
         BlockPos node = SignalLinks.nodeFor(world, lamp);
         Direction facing = ownFacing;
         if (node == null) {
            BlockPos source = SignalLinks.resolveSource(world, lamp);
            if (source == null) {
               return;
            }

            facing = facingOf(world.getBlockState(source), ownFacing);
            node = SignalLinks.nodeFor(world, source);
            if (node == null) {
               node = scanNode(world, source, facing);
            }
         }

         if (node == null) {
            return;
         }

         FangsuSignalSync.Refs refs = refs(be.getClass());
         if (refs.dispType == null || refs.lightState == null) {
            return;
         }

         if (!"mtr".equals(refs.dispType.get(be))) {
            refs.dispType.set(be, "mtr");
         }

         Object var9 = refs.lightState.get(be);
         if (!(var9 instanceof Map)) {
            return;
         }

         Map<String, Object> map = (Map)var9;
         applyAspect(map, aspect(node, facing.toYRot() + 90.0F));
      } catch (Throwable var10) {
      }

   }

   private static void applyAspect(Map<String, Object> raw, int aspect) {
      boolean hasYellow = raw.containsKey("yellow");
      boolean green = aspect == 0;
      boolean red = aspect >= 1 && (aspect == 1 || !hasYellow);
      boolean yellow = aspect >= 2 && hasYellow;
      if (raw.containsKey("red")) {
         raw.put("red", red);
      }

      if (hasYellow) {
         raw.put("yellow", yellow);
      }

      if (raw.containsKey("green")) {
         raw.put("green", green);
      }

   }

   private static int aspect(BlockPos node, float angle) {
      Map<BlockPos, Float> scan = new HashMap(4);
      scan.put(node, angle);
      int occupiedAspect = -1;

      for(int j = 1; j < 3; ++j) {
         Map<BlockPos, Float> next = new HashMap(4);

         for(Map.Entry<BlockPos, Float> check : scan.entrySet()) {
            Map<BlockPos, Rail> rails = (Map)ClientData.RAILS.get(check.getKey());
            if (rails != null) {
               for(Map.Entry<BlockPos, Rail> railEntry : rails.entrySet()) {
                  Rail rail = (Rail)railEntry.getValue();
                  if (rail != null && rail.facingStart.similarFacing(check.getValue())) {
                     UUID product = PathData.getRailProduct((BlockPos)check.getKey(), (BlockPos)railEntry.getKey());
                     if (ClientData.SIGNAL_BLOCKS.isOccupied(product)) {
                        return j;
                     }

                     Boolean occupied = (Boolean)ClientData.OCCUPIED_RAILS.get(product);
                     if (occupied != null && occupied) {
                        return j;
                     }

                     next.put((BlockPos)railEntry.getKey(), rail.facingEnd.getOpposite().angleDegrees);
                     occupiedAspect = 0;
                  }
               }
            }
         }

         scan = next;
         if (next.isEmpty()) {
            break;
         }
      }

      return occupiedAspect;
   }

   private static Direction facingOf(BlockState state, Direction fallback) {
      try {
         if (state != null && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return (Direction)state.getValue(BlockStateProperties.HORIZONTAL_FACING);
         }
      } catch (Throwable var3) {
      }

      return fallback;
   }

   private static BlockPos scanNode(Level world, BlockPos pos, Direction facing) {
      int[] d = new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4};

      for(int z : d) {
         for(int x : d) {
            for(int up = -5; up <= 0; ++up) {
               BlockPos p = pos.above(up).relative(facing.getClockWise(), x).relative(facing, z);
               if (BlockNode.class.isInstance(world.getBlockState(p).getBlock())) {
                  return p;
               }
            }
         }
      }

      return null;
   }

   private static FangsuSignalSync.Refs refs(Class<?> type) {
      FangsuSignalSync.Refs cached = (FangsuSignalSync.Refs)REFS.get(type);
      if (cached != null) {
         return cached;
      } else {
         try {
            Field mode = type.getDeclaredField("dispType");
            mode.setAccessible(true);
            Field state = type.getDeclaredField("lightState");
            state.setAccessible(true);
            cached = new FangsuSignalSync.Refs(mode, state);
            REFS.put(type, cached);
            return cached;
         } catch (Throwable var4) {
            REFS.put(type, FangsuSignalSync.Refs.NONE);
            return FangsuSignalSync.Refs.NONE;
         }
      }
   }

   private static final class Refs {
      static final FangsuSignalSync.Refs NONE = new FangsuSignalSync.Refs((Field)null, (Field)null);
      final Field dispType;
      final Field lightState;

      Refs(Field dispType, Field lightState) {
         this.dispType = dispType;
         this.lightState = lightState;
      }
   }
}

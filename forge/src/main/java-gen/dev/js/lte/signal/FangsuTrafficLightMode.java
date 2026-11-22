package dev.js.lte.signal;

import dev.js.lte.LteLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FangsuTrafficLightMode {
   private static final String KEY_DISP_TYPE = "dispType";
   private static final String MODE_MTR = "mtr";
   private static final String ENUM_CONFIG = "com.fangsu.extraConfig.EnumConfig";
   private static final int MODE_MTR_INDEX = 1;

   private FangsuTrafficLightMode() {
   }

   public static FangsuTrafficLightMode.Result toMtr(Level world, BlockPos pos) {
      if (world != null && pos != null && !world.isClientSide) {
         Object be;
         try {
            be = world.getBlockEntity(pos);
         } catch (Throwable var4) {
            return FangsuTrafficLightMode.Result.FAIL;
         }

         if (be != null && modeField(be.getClass()) != null) {
            String old = readMode(be);
            if ("mtr".equals(old)) {
               return FangsuTrafficLightMode.Result.ALREADY;
            } else if ((setViaOwnConfig(be) || writeFieldsDirectly(be)) && "mtr".equals(readMode(be))) {
               pushToClients(world, pos);
               LteLog.LOGGER.info("[lte] fangsu: mode={}->mtr ok", old == null ? "?" : old);
               return FangsuTrafficLightMode.Result.OK;
            } else {
               LteLog.LOGGER.warn("[lte] fangsu: mode={}->mtr fail", old == null ? "?" : old);
               return FangsuTrafficLightMode.Result.FAIL;
            }
         } else {
            return FangsuTrafficLightMode.Result.NOT_FANGSU;
         }
      } else {
         return FangsuTrafficLightMode.Result.FAIL;
      }
   }

   public static String currentMode(Level world, BlockPos pos) {
      try {
         if (world != null && pos != null) {
            Object be = world.getBlockEntity(pos);
            return be == null ? null : readMode(be);
         } else {
            return null;
         }
      } catch (Throwable var3) {
         return null;
      }
   }

   private static boolean setViaOwnConfig(Object be) {
      try {
         Method getConfigs = be.getClass().getMethod("getConfigs");
         Object raw = getConfigs.invoke(be);
         if (!(raw instanceof List)) {
            return false;
         }

         for(Object entry : (List)raw) {
            if (entry != null && "com.fangsu.extraConfig.EnumConfig".equals(entry.getClass().getName())) {
               Field setter = field(entry.getClass(), "setter");
               if (setter == null) {
                  return false;
               }

               Object consumer = setter.get(entry);
               if (consumer instanceof Consumer) {
                  Consumer<Object> c = (Consumer)consumer;
                  acceptRaw(c, 1);
                  return true;
               }

               return false;
            }
         }
      } catch (Throwable var9) {
         LteLog.LOGGER.debug("[lte] fangsu: own config setter unavailable ({})", var9.toString());
      }

      return false;
   }

   private static void acceptRaw(Consumer<Object> consumer, int value) {
      consumer.accept(value);
   }

   private static boolean writeFieldsDirectly(Object be) {
      try {
         Field mode = modeField(be.getClass());
         if (mode == null) {
            return false;
         } else {
            mode.set(be, "mtr");
            Field extra = field(be.getClass(), "extraConfigs");
            if (extra != null) {
               Object var4 = extra.get(be);
               if (var4 instanceof Map) {
                  Map<Object, Object> map = (Map)var4;
                  map.put("dispType", "mtr");
               }
            }

            if (be instanceof BlockEntity) {
               BlockEntity blockEntity = (BlockEntity)be;
               blockEntity.setChanged();
            }

            try {
               Method rebuild = be.getClass().getDeclaredMethod("rebuildProgram");
               rebuild.setAccessible(true);
               rebuild.invoke(be);
            } catch (Throwable var5) {
            }

            return true;
         }
      } catch (Throwable var6) {
         return false;
      }
   }

   private static void pushToClients(Level world, BlockPos pos) {
      try {
         BlockState state = world.getBlockState(pos);
         world.sendBlockUpdated(pos, state, state, 3);
      } catch (Throwable var3) {
      }

   }

   private static String readMode(Object be) {
      try {
         Field mode = modeField(be.getClass());
         if (mode == null) {
            return null;
         } else {
            Object var3 = mode.get(be);
            String var10000;
            if (var3 instanceof String) {
               String s = (String)var3;
               var10000 = s;
            } else {
               var10000 = null;
            }

            return var10000;
         }
      } catch (Throwable var4) {
         return null;
      }
   }

   private static Field modeField(Class<?> type) {
      return field(type, "dispType");
   }

   private static Field field(Class<?> type, String name) {
      if (type != null && name != null) {
         Class<?> c = type;

         while(c != null && c != Object.class) {
            try {
               Field f = c.getDeclaredField(name);
               f.setAccessible(true);
               return f;
            } catch (NoSuchFieldException var4) {
               c = c.getSuperclass();
            } catch (Throwable var5) {
               return null;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static enum Result {
      OK,
      ALREADY,
      NOT_FANGSU,
      FAIL;
   }
}

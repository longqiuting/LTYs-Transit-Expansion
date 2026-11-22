package dev.js.lte;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class ParticleCull {
   public static final String[] USED = new String[]{"lte:spray", "minecraft:cloud", "minecraft:large_smoke", "minecraft:splash", "minecraft:flame"};
   private static boolean probed;
   private static String cached = "";

   private ParticleCull() {
   }

   public static boolean blocked(String id) {
      return Boolean.FALSE.equals(lookup(id));
   }

   public static String culled() {
      if (probed) {
         return cached;
      } else {
         probed = true;

         try {
            Object map = otherMap();
            if (!(map instanceof Map)) {
               cached = "";
               return "";
            } else {
               Object master = field(particleSettings(), "particles");
               if (master instanceof Boolean) {
                  Boolean b = (Boolean)master;
                  if (!b) {
                     cached = "ALL(particles=false)";
                     return "ALL(particles=false)";
                  }
               }

               StringBuilder out = new StringBuilder();

               for(String id : USED) {
                  if (Boolean.FALSE.equals(lookupIn(map, id))) {
                     if (out.length() > 0) {
                        out.append(',');
                     }

                     out.append(id.startsWith("minecraft:") ? id.substring(10) : id);
                  }
               }

               return cached = out.toString();
            }
         } catch (Throwable var7) {
            cached = "";
            return "";
         }
      }
   }

   private static Boolean lookup(String id) {
      try {
         return lookupIn(otherMap(), id);
      } catch (Throwable var2) {
         return null;
      }
   }

   private static Boolean lookupIn(Object map, String id) {
      if (map instanceof Map<?, ?> other) {
         try {
            Object value = other.get(new ResourceLocation(id));
            if (value == null) {
               value = other.get(id);
            }

            Boolean var10000;
            if (value instanceof Boolean b) {
               var10000 = b;
            } else {
               var10000 = null;
            }

            return var10000;
         } catch (Throwable var5) {
            return null;
         }
      } else {
         return null;
      }
   }

   private static Object particleSettings() {
      try {
         Class<?> mod = Class.forName("me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod");
         Method options = mod.getMethod("options");
         Object gameOptions = options.invoke((Object)null);
         return gameOptions == null ? null : field(gameOptions, "particleSettings");
      } catch (Throwable var3) {
         return null;
      }
   }

   private static Object otherMap() {
      Object particleSettings = particleSettings();
      return particleSettings == null ? null : field(particleSettings, "otherMap");
   }

   public static boolean anyCulled() {
      return !culled().isEmpty();
   }

   public static String reason() {
      String culled = culled();
      return culled.isEmpty() ? null : "Sodium Extra \u5c4f\u853d\u4e86\u7c92\u5b50\u7c7b\u578b " + culled + " \u2014\u2014 \u5728 \u89c6\u9891\u8bbe\u7f6e \u2192 Sodium Extra \u2192 \u7c92\u5b50 \u91cc\u6253\u5f00\uff08\u6216\u5173\u6389 Particle Culling\uff09\uff0c\u5426\u5219\u6c34\u96fe/\u63a2\u9488\u4e00\u9897\u90fd\u4e0d\u4f1a\u6e32\u67d3";
   }

   private static Object field(Object owner, String name) {
      try {
         Field f = owner.getClass().getField(name);
         return f.get(owner);
      } catch (Throwable var3) {
         return null;
      }
   }

   public static void reset() {
      probed = false;
      cached = "";
   }
}

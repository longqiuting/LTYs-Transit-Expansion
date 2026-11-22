package dev.js.mtrheadlight.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class FangsuMixinPlugin implements IMixinConfigPlugin {
   private static final String TURNOUT_RENDERER = "com.fangsu.blocks.client.TurnoutBlockEntityRenderer";
   private static Boolean turnoutsPresent;

   public void onLoad(String mixinPackage) {
   }

   public String getRefMapperConfig() {
      return null;
   }

   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      return loadable(targetClassName);
   }

   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   public List<String> getMixins() {
      return null;
   }

   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   public static boolean turnoutsPresent() {
      Boolean cached = turnoutsPresent;
      if (cached == null) {
         cached = loadable("com.fangsu.blocks.client.TurnoutBlockEntityRenderer");
         turnoutsPresent = cached;
      }

      return cached;
   }

   private static boolean loadable(String className) {
      if (className != null && !className.isEmpty()) {
         try {
            String path = className.replace('.', '/') + ".class";
            ClassLoader own = FangsuMixinPlugin.class.getClassLoader();
            if (own != null && own.getResource(path) != null) {
               return true;
            } else {
               ClassLoader context = Thread.currentThread().getContextClassLoader();
               return context != null && context.getResource(path) != null;
            }
         } catch (Throwable var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   private static String simpleName(String className) {
      if (className == null) {
         return "?";
      } else {
         int dot = className.lastIndexOf(46);
         return dot < 0 ? className : className.substring(dot + 1);
      }
   }
}

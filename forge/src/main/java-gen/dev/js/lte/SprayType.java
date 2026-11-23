package dev.js.lte;

import dev.js.lte.forge.platform.Reg;
import dev.js.lte.forge.platform.ParticleFactoryRegistry;
import dev.js.lte.forge.platform.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

public final class SprayType {
   public static final String ID = "lte:spray";
   private static final ResourceLocation KEY = new ResourceLocation("lte", "spray");
   private static SimpleParticleType type;
   private static boolean registered;
   private static boolean resolved;
   private static boolean self;
   private static String note = "not initialised";

   private SprayType() {
   }

   public static void init() {
      if (type == null) {
         try {
            SimpleParticleType created = FabricParticleTypes.simple(true);
            Reg.particle( KEY, created);
            ParticleFactoryRegistry.getInstance().register(created, (sprites) -> (effect, world, x, y, z, vx, vy, vz) -> new SprayParticle(world, x, y, z, vx, vy, vz, sprites));
            if (BuiltInRegistries.PARTICLE_TYPE.get(KEY) != created) {
               throw new IllegalStateException("registry holds a different object for lte:spray");
            }

            type = created;
            registered = true;
            note = "registered";
         } catch (Throwable var1) {
            type = null;
            registered = false;
            note = "registration failed: " + String.valueOf(var1);
            MtrHeadlightClient.LOGGER.warn("[lte] could not register the {} particle, falling back to minecraft:cloud", "lte:spray", var1);
         }

         resolved = false;
         if (LteDebug.spray()) {
            MtrHeadlightClient.LOGGER.info("[lte] spray particle type={} registered={} ({})", new Object[]{"lte:spray", registered, note});
         }
      }
   }

   private static void resolve() {
      resolved = true;
      if (registered && type != null) {
         if (ParticleCull.blocked("lte:spray")) {
            self = false;
            note = "Sodium Extra culls lte:spray";
            MtrHeadlightClient.LOGGER.warn("[lte] spray: {} is culled by Sodium Extra, falling back to minecraft:cloud", "lte:spray");
         } else {
            self = true;
            note = "Sodium Extra has no entry for lte:spray -> getOrDefault(id, TRUE) lets it through";
            if (LteDebug.spray()) {
               MtrHeadlightClient.LOGGER.info("[lte] spray: using {} ({})", "lte:spray", note);
            }
         }
      } else {
         self = false;
         MtrHeadlightClient.LOGGER.warn("[lte] spray: falling back to minecraft:cloud ({})", note);
      }
   }

   private static void ensureResolved() {
      if (!resolved) {
         resolve();
      }

   }

   public static ParticleOptions effect() {
      ensureResolved();
      return self && type != null ? type : ParticleTypes.CLOUD;
   }

   public static String label() {
      ensureResolved();
      return self && type != null ? "self" : "vanilla";
   }

   public static String note() {
      ensureResolved();
      return note;
   }
}

package dev.js.lte.collision;

import dev.js.lte.HeadlightConfig;
import dev.js.lte.LteLog;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public final class CollisionImpact {
   private static final double MAX_RATE = 200.0D;
   public static final int DAMAGE_INTERVAL_TICKS = 10;
   private static final long SESSION_GRACE = 10L;
   private static final double OVERKILL_SCALE = 1.5D;
   private static final int MAX_STATES = 512;
   private static final double ROOF_FALL_SCALE = 1.0D;
   private static final double ROOF_FALL_FATAL_SCALE = 8.0D;
   private static final float ROOF_FALL_FATAL_FLAT = 20.0F;
   private static final long NEVER = -2305843009213693952L;
   private static final Map<UUID, CollisionImpact.State> STATES = new HashMap();
   public static final double KNOCKBACK_EXP_SCALE_KMH = 50.0D;
   public static final double KNOCKBACK_REF_KMH = 50.0D;
   private static final double GRAVITY = 0.08D;
   private static final double VERTICAL_DRAG = 0.98D;
   private static final double HORIZONTAL_DRAG = 0.91D;
   private static final long IMPULSE_GROUND_GRACE_MS = 250L;
   private static final double GROUND_PROBE = 0.1D;
   private static final long IMPULSE_LOCKOUT_MIN = 8L;
   private static final long IMPULSE_LOCKOUT_MAX = 100L;
   private static volatile CollisionImpact.ImpulseMirror mirror;
   private static long IMPULSES;
   private static final long LANDING_GRACE_TICKS = 4L;
   private static final long TILT_SUPPRESS_MS = 1500L;
   private static long lastImpactLog = -2305843009213693952L;
   private static final long IMPACT_LOG_TICKS = 20L;

   private CollisionImpact() {
   }

   public static CollisionImpact.Tier tierOf(HeadlightConfig cfg, double speedKmh) {
      return tierOf(cfg, (double)cfg.trainCollisionPushOnlyMaxKmhClamped(), speedKmh);
   }

   public static CollisionImpact.Tier tierOf(HeadlightConfig cfg, HeadlightConfig.Effective eff, double speedKmh) {
      return tierOf(cfg, eff == null ? (double)cfg.trainCollisionPushOnlyMaxKmhClamped() : eff.trainCollisionDamageMinSpeedKmh, speedKmh);
   }

   private static CollisionImpact.Tier tierOf(HeadlightConfig cfg, double v0, double speedKmh) {
      if (speedKmh < v0) {
         return CollisionImpact.Tier.PUSH;
      } else if (speedKmh < Math.max(v0, (double)cfg.trainCollisionLowDamageMaxKmhClamped())) {
         return CollisionImpact.Tier.LOW;
      } else if (speedKmh < Math.max(v0, (double)cfg.trainCollisionHighDamageMaxKmhClamped())) {
         return CollisionImpact.Tier.HIGH;
      } else {
         return speedKmh < Math.max(v0, (double)cfg.trainCollisionLaunchMaxKmhClamped()) ? CollisionImpact.Tier.LAUNCH : CollisionImpact.Tier.FLY;
      }
   }

   public static double damagePerSecond(HeadlightConfig cfg, double speedKmh) {
      return damagePerSecond(cfg, (double)cfg.trainCollisionPushOnlyMaxKmhClamped(), speedKmh);
   }

   private static double damagePerSecond(HeadlightConfig cfg, double v0, double speedKmh) {
      if (speedKmh < v0) {
         return 0.0D;
      } else {
         double base = cfg.trainCollisionDamageBasePerSecondClamped();
         if (!(base > 0.0D)) {
            return 0.0D;
         } else {
            double k = cfg.trainCollisionDamageExpKClamped();
            double scale = cfg.trainCollisionDamageSpeedScaleKmhClamped();
            double rate = base * Math.exp(k * (speedKmh - v0) / scale);
            if (speedKmh >= (double)cfg.trainCollisionLaunchMaxKmhClamped()) {
               rate *= cfg.trainCollisionFlyDamageScaleClamped();
            }

            return !(rate > 0.0D) ? 0.0D : Math.min(200.0D, rate);
         }
      }
   }

   /** @deprecated */
   @Deprecated
   public static double launchHorizontal(HeadlightConfig cfg, CollisionImpact.Tier tier) {
      return knockbackHorizontal(cfg, tier, (double)cfg.trainCollisionPushOnlyMaxKmhClamped() + 50.0D);
   }

   public static double launchVertical(HeadlightConfig cfg, CollisionImpact.Tier tier) {
      return tier == CollisionImpact.Tier.FLY ? cfg.trainCollisionFlyVerticalClamped() : cfg.trainCollisionLaunchVerticalClamped();
   }

   public static double knockbackDistanceAt(HeadlightConfig cfg, double speedKmh) {
      double base = cfg.knockbackDistanceClamped();
      double k = cfg.knockbackExpKClamped();
      double max = cfg.knockbackMaxClamped();
      double exponent = k * (speedKmh - 50.0D) / 50.0D;
      if (!(exponent < 20.0D)) {
         return max;
      } else {
         double d = base * Math.exp(exponent);
         return !(d > 0.0D) ? 1.0D : Math.min(max, Math.max(1.0D, d));
      }
   }

   public static int flightTicks(double vy) {
      if (!(vy > 0.0D)) {
         return 1;
      } else {
         double y = 0.0D;
         double v = vy;

         for(int t = 1; t <= 200; ++t) {
            v -= 0.08D;
            y += v;
            if (y <= 0.0D && t > 1) {
               return t;
            }

            v *= 0.98D;
         }

         return 200;
      }
   }

   public static double horizontalReach(double vy) {
      int n = flightTicks(vy);
      double sum = 0.0D;
      double k = 1.0D;

      for(int i = 0; i < n; ++i) {
         sum += k;
         k *= 0.91D;
      }

      return sum;
   }

   public static double knockbackHorizontal(HeadlightConfig cfg, CollisionImpact.Tier tier, double speedKmh) {
      double vy = launchVertical(cfg, tier);
      double reach = Math.max(0.001D, horizontalReach(vy));
      double vx = knockbackDistanceAt(cfg, speedKmh) / reach;
      double cap = tier == CollisionImpact.Tier.FLY ? 12.0D : 8.0D;
      return Math.min(cap, Math.max(0.0D, vx));
   }

   public static double applied(UUID id) {
      CollisionImpact.State s = (CollisionImpact.State)STATES.get(id);
      return s == null ? 0.0D : s.applied;
   }

   public static boolean launched(UUID id) {
      CollisionImpact.State s = (CollisionImpact.State)STATES.get(id);
      return s != null && s.launched;
   }

   public static boolean impulseActive(LivingEntity entity) {
      if (entity == null) {
         return false;
      } else {
         CollisionImpact.State s = (CollisionImpact.State)STATES.get(entity.getUUID());
         if (s != null && s.impulseLock) {
            long nowMs = System.currentTimeMillis();
            if (nowMs - s.impulseSinceMs < 250L) {
               return true;
            } else if (nowMs >= s.impulseMaxMs) {
               s.impulseLock = false;
               return false;
            } else if (grounded(entity)) {
               s.impulseLock = false;
               return false;
            } else {
               return true;
            }
         } else {
            return false;
         }
      }
   }

   private static boolean grounded(LivingEntity entity) {
      if (entity.onGround()) {
         return true;
      } else {
         try {
            AABB box = entity.getBoundingBox().move(0.0D, -0.1D, 0.0D);
            return !entity.level().noCollision(entity, box);
         } catch (Throwable var2) {
            return false;
         }
      }
   }

   public static void installImpulseMirror(CollisionImpact.ImpulseMirror hook) {
      mirror = hook;
   }

   public static boolean wasFast(UUID id) {
      CollisionImpact.State s = (CollisionImpact.State)STATES.get(id);
      return s != null && s.fast;
   }

   public static long impulsesTotal() {
      return IMPULSES;
   }

   public static float tick(LivingEntity entity, HeadlightConfig cfg, HeadlightConfig.Effective eff, double speedKmh, double dirX, double dirZ, long now) {
      if (entity == null) {
         return 0.0F;
      } else if (dead(entity)) {
         forget(entity.getUUID());
         return 0.0F;
      } else {
         CollisionImpact.Tier tier = tierOf(cfg, eff, speedKmh);
         if (tier == CollisionImpact.Tier.PUSH) {
            return 0.0F;
         } else {
            CollisionImpact.State s = state(entity);
            if (!s.launched && now > s.hurtUntil && now - s.lastTouch > 10L) {
               s.applied = 0.0D;
               s.pending = 0.0D;
               s.nextFlush = now + 10L;
            }

            s.lastTouch = now;
            s.speed = speedKmh;
            s.fast = tier == CollisionImpact.Tier.FLY || speedKmh >= (double)cfg.trainCollisionHighDamageMaxKmhClamped();
            int hurtTicks = cfg.trainCollisionHurtTicksClamped();
            if (hurtTicks > 0) {
               long until = now + (long)hurtTicks;
               if (until > s.hurtUntil) {
                  s.hurtUntil = until;
                  s.hurtUntilMs = System.currentTimeMillis() + (long)hurtTicks * 50L;
               }
            }

            s.damageOn = eff == null || eff.trainCollisionDamage;
            s.damageMax = eff == null ? cfg.trainCollisionDamageMaxClamped() : eff.trainCollisionDamageMax;
            s.v0 = eff == null ? (double)cfg.trainCollisionPushOnlyMaxKmhClamped() : eff.trainCollisionDamageMinSpeedKmh;
            boolean pushed = false;
            if (tier.launches() && !impulseActive(entity) && now - s.lastImpulse >= (long)cfg.trainCollisionImpactCooldownTicksClamped()) {
               s.lastImpulse = now;
               double vx = impulse(entity, cfg, tier, speedKmh, dirX, dirZ);
               if (vx > 0.0D) {
                  pushed = true;
                  s.launchVx = vx;
                  s.launched = true;
                  s.landed = false;
                  s.launchTick = now;
                  s.impulseLock = true;
                  s.impulseSinceMs = System.currentTimeMillis();
                  int air = (int)Math.max(8L, (long)(flightTicks(launchVertical(cfg, tier)) + 4));
                  s.impulseMaxMs = s.impulseSinceMs + Math.min(100L, (long)Math.max(air, 40)) * 50L;
               }

               s.nextFlush = now + 10L;
            }

            float hp = damage(entity, cfg, s, now);
            logImpact(cfg, now, speedKmh, tier, s, pushed);
            return hp;
         }
      }
   }

   private static boolean dead(LivingEntity entity) {
      return !entity.isAlive() || entity.isDeadOrDying() || !(entity.getHealth() > 0.0F);
   }

   public static float flightDamage(LivingEntity entity, HeadlightConfig cfg, long now) {
      if (entity == null) {
         return 0.0F;
      } else {
         CollisionImpact.State s = (CollisionImpact.State)STATES.get(entity.getUUID());
         if (s != null && s.launched) {
            if (dead(entity)) {
               forget(entity.getUUID());
               return 0.0F;
            } else {
               s.lastTouch = now;
               if (!s.landed && s.launchTick != -2305843009213693952L && now - s.launchTick > 4L && grounded(entity)) {
                  s.landed = true;
                  s.launched = false;
                  return settleOnLanding(entity, cfg, s, now);
               } else if (s.hurtUntil != -2305843009213693952L && now > s.hurtUntil) {
                  s.launched = false;
                  closeWindow(s);
                  return 0.0F;
               } else {
                  return damage(entity, cfg, s, now);
               }
            }
         } else {
            return 0.0F;
         }
      }
   }

   private static float settleOnLanding(LivingEntity entity, HeadlightConfig cfg, CollisionImpact.State s, long now) {
      s.hurtUntil = now + 20L;
      s.hurtUntilMs = System.currentTimeMillis() + 1000L;
      if (s.damageOn && damageable(entity, cfg)) {
         double cap = damageCap(entity, s);
         if (!(cap > 0.0D)) {
            closeWindow(s);
            return 0.0F;
         } else {
            double remaining = cap - s.applied;
            if (!(remaining > 0.0D)) {
               closeWindow(s);
               return 0.0F;
            } else {
               s.applied = cap;
               s.pending = 0.0D;
               s.lastAccrue = now;
               s.nextFlush = now + 10L;
               s.flushNow = false;
               LteLog.LOGGER.info("[lte] landing: settle={} hp={} max={} cap={}", new Object[]{format2((float)remaining), format2(entity.getHealth()), format2(entity.getMaxHealth()), format2((float)cap)});
               return (float)remaining;
            }
         }
      } else {
         closeWindow(s);
         return 0.0F;
      }
   }

   private static double damageCap(LivingEntity entity, CollisionImpact.State s) {
      double cap = Math.max(0.0D, (double)entity.getMaxHealth()) * 1.5D;
      if (s.damageMax > 0.0D) {
         cap = Math.min(Math.max(0.0D, (double)entity.getMaxHealth()), s.damageMax);
      }

      return cap;
   }

   private static void closeWindow(CollisionImpact.State s) {
      s.hurtUntil = -2305843009213693952L;
      s.hurtUntilMs = 0L;
      s.launched = false;
      s.pending = 0.0D;
   }

   public static float hurtDamage(LivingEntity entity, HeadlightConfig cfg, long now) {
      if (entity == null) {
         return 0.0F;
      } else {
         CollisionImpact.State s = (CollisionImpact.State)STATES.get(entity.getUUID());
         if (s != null && now <= s.hurtUntil) {
            if (dead(entity)) {
               forget(entity.getUUID());
               return 0.0F;
            } else {
               return damage(entity, cfg, s, now);
            }
         } else {
            return 0.0F;
         }
      }
   }

   public static void sweep(long now) {
      if (STATES.size() > 512) {
         STATES.entrySet().removeIf((entry) -> !((CollisionImpact.State)entry.getValue()).launched);
      }
   }

   public static void forget(UUID id) {
      if (id != null) {
         STATES.remove(id);
      }

   }

   public static void clear() {
      STATES.clear();
   }

   private static CollisionImpact.State state(LivingEntity entity) {
      return (CollisionImpact.State)STATES.computeIfAbsent(entity.getUUID(), (id) -> new CollisionImpact.State());
   }

   private static float damage(LivingEntity entity, HeadlightConfig cfg, CollisionImpact.State s, long now) {
      if (!s.damageOn) {
         return 0.0F;
      } else if (!damageable(entity, cfg)) {
         if (!s.skipLogged) {
            s.skipLogged = true;
            LteLog.LOGGER.info("[lte] damage: skipped - {} (v={} tier-state launched={})", new Object[]{skipReason(entity, cfg), format2((float)s.speed), s.launched});
         }

         return 0.0F;
      } else if (s.lastAccrue == now) {
         return 0.0F;
      } else {
         double cap = damageCap(entity, s);
         if (!(cap > 0.0D)) {
            return 0.0F;
         } else if (s.applied >= (double)entity.getMaxHealth() && !s.finishing) {
            s.finishing = true;
            s.lastAccrue = now;
            double finish = Math.max(4.0D, (double)entity.getHealth()) * 8.0D + 20.0D;
            LteLog.LOGGER.info("[lte] damage: finishing blow (raw spent={} hp={} max={} cap={})", new Object[]{format2((float)s.applied), format2(entity.getHealth()), format2(entity.getMaxHealth()), format2((float)cap)});
            return (float)finish;
         } else if (s.applied >= cap) {
            closeWindow(s);
            return 0.0F;
         } else {
            double perTick = damagePerSecond(cfg, s.v0, s.speed) / 20.0D;
            if (!(perTick > 0.0D)) {
               return 0.0F;
            } else {
               double amount = Math.min(perTick, cap - s.applied);
               if (!(amount > 0.0D)) {
                  return 0.0F;
               } else {
                  s.applied += amount;
                  s.pending += amount;
                  s.lastAccrue = now;
                  if (s.nextFlush == -2305843009213693952L) {
                     s.nextFlush = now + 10L;
                     return 0.0F;
                  } else if (!s.flushNow && now < s.nextFlush) {
                     return 0.0F;
                  } else {
                     s.flushNow = false;
                     s.nextFlush = Math.max(now + 10L, s.nextFlush + 10L);
                     float out = (float)s.pending;
                     s.pending = 0.0D;
                     return out;
                  }
               }
            }
         }
      }
   }

   public static void requeue(LivingEntity entity, float amount) {
      if (entity != null && amount > 0.0F) {
         CollisionImpact.State s = (CollisionImpact.State)STATES.get(entity.getUUID());
         if (s != null) {
            s.pending += (double)amount;
            s.applied = Math.max(0.0D, s.applied - (double)amount);
            s.flushNow = true;
         }
      }
   }

   private static boolean damageable(LivingEntity entity, HeadlightConfig cfg) {
      if (!dead(entity) && !entity.isInvulnerable()) {
         if (entity instanceof Player) {
            Player player = (Player)entity;
            if (player.isSpectator()) {
               return false;
            } else {
               return cfg.damageInCreative || !player.isCreative() && !player.getAbilities().invulnerable;
            }
         } else {
            return true;
         }
      } else {
         return false;
      }
   }

   private static String skipReason(LivingEntity entity, HeadlightConfig cfg) {
      if (entity == null) {
         return "null";
      } else if (entity.isAlive() && !entity.isDeadOrDying() && entity.getHealth() > 0.0F) {
         if (entity.isInvulnerable()) {
            return "invulnerable-flag";
         } else {
            if (entity instanceof Player) {
               Player player = (Player)entity;
               if (player.isSpectator()) {
                  return "spectator";
               }

               if (!cfg.damageInCreative && player.isCreative()) {
                  return "creative";
               }

               if (!cfg.damageInCreative && player.getAbilities().invulnerable) {
                  return "abilities.invulnerable(god)";
               }
            }

            return null;
         }
      } else {
         return "dead";
      }
   }

   private static double impulse(LivingEntity entity, HeadlightConfig cfg, CollisionImpact.Tier tier, double speedKmh, double dirX, double dirZ) {
      double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
      if (!(len > 1.0E-6D)) {
         return 0.0D;
      } else {
         double x = dirX / len;
         double z = dirZ / len;
         double horizontal = knockbackHorizontal(cfg, tier, speedKmh);
         double vertical = launchVertical(cfg, tier);

         try {
            entity.setDeltaMovement(x * horizontal, vertical, z * horizontal);
            entity.hurtMarked = true;
            entity.fallDistance = 0.0F;
            ++IMPULSES;
            CollisionImpact.ImpulseMirror hook = mirror;
            if (hook != null) {
               try {
                  hook.push(entity.getUUID(), x * horizontal, vertical, z * horizontal);
               } catch (Throwable var21) {
               }
            }

            LteLog.LOGGER.info("[lte] knockback: tier={} v={}km/h base={} k={} max={} dist={} vx={} vy={} air={}t", new Object[]{tier.name(), String.format(Locale.ROOT, "%.0f", speedKmh), String.format(Locale.ROOT, "%.2f", cfg.knockbackDistanceClamped()), String.format(Locale.ROOT, "%.2f", cfg.knockbackExpKClamped()), String.format(Locale.ROOT, "%.0f", cfg.knockbackMaxClamped()), String.format(Locale.ROOT, "%.2f", knockbackDistanceAt(cfg, speedKmh)), String.format(Locale.ROOT, "%.3f", horizontal), String.format(Locale.ROOT, "%.2f", vertical), flightTicks(vertical)});
            return horizontal;
         } catch (Throwable var22) {
            return 0.0D;
         }
      }
   }

   public static boolean applyDamage(LivingEntity target, float amount, boolean high, HeadlightConfig cfg) {
      if (target != null && amount > 0.0F && !dead(target)) {
         float hpBefore = target.getHealth();
         boolean lethal = amount >= hpBefore;
         if (lethal) {
            target.invulnerableTime = 0;
         }

         DamageSource source = sourceFor(target, high);

         boolean applied;
         try {
            applied = damageIgnoringCreativeInvuln(target, source, amount, cfg != null && cfg.damageInCreative);
         } catch (Throwable var9) {
            applied = false;
            LteLog.LOGGER.warn("[lte] damage: threw " + String.valueOf(var9) + " src=" + source.getClass().getSimpleName(), var9);
         }

         logDamage(target, source, amount, hpBefore, applied, lethal);
         if (!applied) {
            requeue(target, amount);
            return false;
         } else {
            CollisionImpact.State s = (CollisionImpact.State)STATES.get(target.getUUID());
            if (s != null) {
               s.lastDamageMs = System.currentTimeMillis();
            }

            suppressHurtTilt(target);
            return true;
         }
      } else {
         return false;
      }
   }

   public static float roofFallHp(LivingEntity entity, HeadlightConfig cfg, double speedKmh) {
      if (entity != null && cfg != null && !dead(entity)) {
         double v0 = (double)cfg.trainCollisionPushOnlyMaxKmhClamped();
         if (!(speedKmh > v0)) {
            return 0.0F;
         } else {
            double max = Math.max(1.0D, Math.min(200.0D, (double)entity.getMaxHealth()));
            double launch = (double)cfg.trainCollisionLaunchMaxKmhClamped();
            if (speedKmh >= launch) {
               return (float)(max * 8.0D + 20.0D);
            } else {
               double t = launch > v0 ? (speedKmh - v0) / (launch - v0) : 1.0D;
               double frac = Math.max(0.0D, Math.min(1.0D, t));
               return (float)(max * 1.0D * frac);
            }
         }
      } else {
         return 0.0F;
      }
   }

   public static boolean applyRoofFall(LivingEntity target, float hp, HeadlightConfig cfg) {
      if (target != null && hp > 0.0F && !dead(target) && damageable(target, cfg)) {
         DamageSource source = target.level().damageSources().fall();
         float before = target.getHealth();
         boolean lethal = hp >= before;
         if (lethal) {
            target.invulnerableTime = 0;
         }

         target.fallDistance = 0.0F;

         boolean landed;
         try {
            landed = damageIgnoringCreativeInvuln(target, source, hp, cfg != null && cfg.damageInCreative);
         } catch (Throwable var8) {
            landed = false;
            LteLog.LOGGER.warn("[lte] roof-fall: threw " + String.valueOf(var8), var8);
         }

         logDamage(target, source, hp, before, landed, lethal);
         return landed;
      } else {
         return false;
      }
   }

   private static boolean damageIgnoringCreativeInvuln(LivingEntity target, DamageSource source, float amount, boolean allowCreative) {
      if (allowCreative && target instanceof Player player) {
         if (!player.isSpectator() && player.getAbilities().invulnerable) {
            player.getAbilities().invulnerable = false;

            boolean var5;
            try {
               var5 = target.hurt(source, amount);
            } finally {
               player.getAbilities().invulnerable = true;
            }

            return var5;
         }
      }

      return target.hurt(source, amount);
   }

   private static void logDamage(LivingEntity target, DamageSource source, float requested, float hpBefore, boolean applied, boolean lethal) {
      try {
         String caller;
         Logger var10000;
         Object[] var10002;
         boolean var10005;
         label32: {
            float hpAfter = target.getHealth();
            caller = (String)StackWalker.getInstance().walk((frames) -> (String)frames.skip(2L).findFirst().map(StackWalker.StackFrame::toString).orElse("?"));
            var10000 = LteLog.LOGGER;
            var10002 = new Object[]{source.getClass().getSimpleName(), format2(requested), format2(hpBefore), format2(hpAfter), applied, target.invulnerableTime, lethal, target.isInvulnerable(), null, null, null};
            if (target instanceof Player p) {
               if (p.getAbilities().invulnerable) {
                  var10005 = true;
                  break label32;
               }
            }

            var10005 = false;
         }

         label27: {
            var10002[8] = var10005;
            if (target instanceof Player p2) {
               if (p2.isCreative()) {
                  var10005 = true;
                  break label27;
               }
            }

            var10005 = false;
         }

         var10002[9] = var10005;
         var10002[10] = caller;
         var10000.info("[lte] damage: src={} want={} hp={}->{} landed={} iframes={} reset={} inv={} god={} creative={} at {}", var10002);
         if (!target.isAlive() || target.getHealth() <= 0.0F) {
            logDeath(target, source);
         }
      } catch (Throwable var9) {
      }

   }

   private static void logDeath(LivingEntity target, DamageSource source) {
      String message = "?";
      String key = "?";

      try {
         message = target.getCombatTracker().getDeathMessage().getString();
      } catch (Throwable var6) {
      }

      try {
         if (source instanceof TrainImpactDamageSource impact) {
            key = impact.high() ? "death.attack.lte.trainHigh" : "death.attack.lte.trainLow";
         } else {
            key = source.getMsgId();
         }
      } catch (Throwable var5) {
      }

      LteLog.LOGGER.info("[lte] death: target={} src={} key={} msg=\"{}\"", new Object[]{target.getName().getString(), source.getClass().getSimpleName(), key, message});
   }

   private static String format2(float value) {
      return String.format(Locale.ROOT, "%.2f", value);
   }

   public static String debugKill(LivingEntity target, boolean high, HeadlightConfig cfg) {
      if (target == null) {
         return "LTE\uff1akillself \u9700\u8981\u5355\u4eba\u5b58\u6863\uff08\u627e\u4e0d\u5230\u670d\u52a1\u7aef\u73a9\u5bb6\u5b9e\u4f8b\uff09";
      } else {
         forget(target.getUUID());
         String skip = skipReason(target, cfg);
         float before = target.getHealth();
         DamageSource source = sourceFor(target, high);
         float want = Math.max(4.0F, before) * 8.0F + 20.0F;

         boolean landed;
         try {
            target.invulnerableTime = 0;
            target.fallDistance = 0.0F;
            landed = damageIgnoringCreativeInvuln(target, source, want, cfg != null && cfg.damageInCreative);
         } catch (Throwable var12) {
            landed = false;
            LteLog.LOGGER.warn("[lte] killself: threw " + String.valueOf(var12), var12);
         }

         logDamage(target, source, want, before, landed, true);
         float after = target.getHealth();
         String message = "?";

         try {
            message = target.getCombatTracker().getDeathMessage().getString();
         } catch (Throwable var11) {
         }

         String key = high ? "death.attack.lte.trainHigh" : "death.attack.lte.trainLow";
         return "LTE\uff1akillself \u2014 src=" + source.getClass().getSimpleName() + " key=" + key + " hp=" + format2(before) + "->" + format2(after) + " landed=" + landed + " alive=" + target.isAlive() + (skip == null ? "" : " blocked=" + skip) + " msg=\"" + message + "\"";
      }
   }

   public static void suppressHurtTilt(LivingEntity entity) {
      if (entity instanceof Player player) {
         CollisionImpact.State s = (CollisionImpact.State)STATES.get(player.getUUID());
         if (s != null) {
            long nowMs = System.currentTimeMillis();
            boolean inWindow = s.launched || nowMs < s.hurtUntilMs;
            if (inWindow || s.lastDamageMs > 0L && nowMs - s.lastDamageMs <= 1500L) {
               if (player.hurtTime != 0 || player.hurtDuration != 0) {
                  player.hurtTime = 0;
                  player.hurtDuration = 0;
               }
            }
         }
      }
   }

   private static void logImpact(HeadlightConfig cfg, long now, double speedKmh, CollisionImpact.Tier tier, CollisionImpact.State s, boolean pushed) {
      if (now - lastImpactLog >= 20L) {
         lastImpactLog = now;

         try {
            double perWindow = damagePerSecond(cfg, s.v0, speedKmh) * 10.0D / 20.0D;
            int window = cfg.trainCollisionHurtTicksClamped();
            long left = s.hurtUntil == -2305843009213693952L ? 0L : Math.max(0L, s.hurtUntil - now);
            LteLog.LOGGER.info("[lte] impact: v={} tier={} dmg={} window={} left={} pushed={}", new Object[]{String.format(Locale.ROOT, "%.1f", speedKmh), tier.name(), String.format(Locale.ROOT, "%.2f", perWindow), window, left, pushed});
         } catch (Throwable var13) {
         }

      }
   }

   private static DamageSource sourceFor(LivingEntity target, boolean high) {
      Holder<DamageType> type = target.level().damageSources().generic().typeHolder();
      return new TrainImpactDamageSource(type, high);
   }

   public interface ImpulseMirror {
      void push(UUID var1, double var2, double var4, double var6);
   }

   private static final class State {
      long lastTouch = -2305843009213693952L;
      long lastImpulse = -2305843009213693952L;
      long lastAccrue = -2305843009213693952L;
      long nextFlush = -2305843009213693952L;
      double pending;
      boolean flushNow;
      boolean impulseLock;
      long impulseSinceMs;
      long impulseMaxMs;
      long hurtUntil = -2305843009213693952L;
      long hurtUntilMs;
      long launchTick = -2305843009213693952L;
      boolean landed;
      long lastDamageMs;
      double applied;
      double speed;
      boolean fast;
      boolean launched;
      boolean finishing;
      double launchVx;
      boolean skipLogged;
      boolean damageOn = true;
      double damageMax;
      double v0 = 5.0D;
   }

   public static enum Tier {
      PUSH(false),
      LOW(false),
      HIGH(false),
      LAUNCH(true),
      FLY(true);

      private final boolean launches;

      private Tier(final boolean launches) {
         this.launches = launches;
      }

      public boolean launches() {
         return this.launches;
      }
   }
}

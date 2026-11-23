package dev.js.lte;

import java.util.function.Supplier;

abstract class ConfigWriteTarget {
   static final int DISTANCE = 0;
   static final int LUMINANCE = 1;
   static final int HEAD_LENGTH = 2;
   static final int BEAM_DISTANCE = 3;
   static final int BEAM_HEAD_LENGTH = 4;
   static final int BEAM_TAIL_LENGTH = 5;
   static final int BEAM_WIDTH = 6;
   static final int BEAM_HEAD_HEIGHT = 7;
   static final int BEAM_TAIL_HEIGHT = 8;
   static final int COLLISION_HEAD_EXTRA = 9;
   static final int COLLISION_TAIL_EXTRA = 10;
   static final int RAIN_SPRAY_MIN_SPEED = 11;
   static final int COLLISION_PUSH_MAX = 12;
   static final int COLLISION_LOW_MAX = 13;
   static final int COLLISION_HIGH_MAX = 14;
   static final int COLLISION_LAUNCH_MAX = 15;
   static final int COLLISION_BASE_RATE = 16;
   static final int COLLISION_EXP_K = 17;
   static final int COLLISION_RATE_SCALE = 18;
   static final int COLLISION_FLY_SCALE = 19;
   static final int COLLISION_LAUNCH_H = 20;
   static final int COLLISION_LAUNCH_V = 21;
   static final int COLLISION_FLY_H = 22;
   static final int COLLISION_FLY_V = 23;
   static final int COLLISION_COOLDOWN = 24;
   static final int COLLISION_HEIGHT = 25;
   static final int ROOF_DROP_SPEED = 26;
   static final int ROOF_DROP_RADIUS = 27;
   static final int HEADLIGHT_MODE = 28;
   static final int DAMAGE_MIN_SPEED = 29;
   static final int DAMAGE_MAX = 30;
   static final int COLLISION_KNOCKBACK = 31;
   static final int COLLISION_KNOCKBACK_K = 32;
   static final int COLLISION_KNOCKBACK_MAX = 33;
   static final int SCATTER_STRENGTH = 34;
   static final int SCATTER_RADIUS = 35;
   static final int BEAM_FLARE = 41;
   static final int[] ALL_VALUES = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35};
   static final int MASTER = 0;
   static final int DYNAMIC_LIGHT = 1;
   static final int LIGHT_BAND = 2;
   static final int RAIN_SPRAY = 4;
   static final int BUILTIN_TRAINS = 5;
   static final int TRAIN_COLLISION = 6;
   static final int TRACKBED = 7;
   static final int TRAIN_DAMAGE = 8;
   static final int TRAIN_MOBS = 9;
   static final int RAIN_HUSH = 10;
   static final int DAMAGE_IN_CREATIVE = 11;
   static final int SCATTER = 36;
   static final int SCATTER_SURFACE = 38;
   static final int SCATTER_SHADERS = 42;
   static final int[] ALL_TOGGLES = new int[]{0, 1, 2, 41, 4, 5, 6, 7, 8, 9, 10, 11, 36, 38, 42};
   final Supplier<HeadlightConfig> cfg;

   ConfigWriteTarget(Supplier<HeadlightConfig> cfg) {
      if (cfg == null) {
         throw new IllegalArgumentException("cfg == null");
      } else {
         this.cfg = cfg;
      }
   }

   final HeadlightConfig config() {
      HeadlightConfig live = (HeadlightConfig)this.cfg.get();
      if (live == null) {
         throw new IllegalStateException("no live config");
      } else {
         return live;
      }
   }

   static ConfigWriteTarget global(Supplier<HeadlightConfig> cfg) {
      return new ConfigWriteTarget.GlobalTarget(cfg);
   }

   static ConfigWriteTarget siding(Supplier<HeadlightConfig> cfg, long sidingId) {
      return new ConfigWriteTarget.SidingTarget(cfg, sidingId);
   }

   private static void ignored(int which) {
      LteLog.LOGGER.debug("[lte] config: row {} is global-only, ignored on a siding screen", which);
   }

   abstract String describe();

   abstract HeadlightConfig.Effective effective();

   abstract double readValue(int var1);

   abstract void writeValue(int var1, double var2);

   abstract boolean readToggle(int var1);

   abstract void writeToggle(int var1, boolean var2);

   abstract int readColor(boolean var1);

   abstract void writeColor(boolean var1, int var2);

   abstract boolean followsValue(int var1);

   abstract boolean followsToggle(int var1);

   abstract boolean followsColor(boolean var1);

   abstract boolean hasOverrides();

   abstract void resetValue(int var1);

   abstract void resetToggle(int var1);

   abstract void resetColor(boolean var1);

   abstract void reset();

   abstract void resetAll();

   final void commit() {
      this.config().save();
   }

   private static final class GlobalTarget extends ConfigWriteTarget {
      GlobalTarget(Supplier<HeadlightConfig> cfg) {
         super(cfg);
      }

      String describe() {
         return "global";
      }

      HeadlightConfig.Effective effective() {
         return this.config().effectiveFor(0L);
      }

      double readValue(int which) {
         HeadlightConfig.Effective eff = this.effective();
         switch (which) {
            case 0:
               return eff.maxDistance;
            case 1:
               return (double)eff.headLuminance;
            case 2:
               return eff.headLength;
            case 3:
               return eff.beamDistance;
            case 4:
               return eff.beamHeadLength;
            case 5:
               return eff.beamTailLength;
            case 6:
               return eff.beamWidth;
            case 7:
               return eff.beamHeadHeight;
            case 8:
            default:
               return eff.beamTailHeight;
            case 9:
               return eff.collisionHeadExtra;
            case 10:
               return eff.collisionTailExtra;
            case 11:
               return (double)this.config().rainSprayMinSpeedKmh;
            case 12:
               return (double)this.config().trainCollisionPushOnlyMaxKmh;
            case 13:
               return (double)this.config().trainCollisionLowDamageMaxKmh;
            case 14:
               return (double)this.config().trainCollisionHighDamageMaxKmh;
            case 15:
               return (double)this.config().trainCollisionLaunchMaxKmh;
            case 16:
               return this.config().trainCollisionDamageBasePerSecond;
            case 17:
               return this.config().trainCollisionDamageExpK;
            case 18:
               return this.config().trainCollisionDamageSpeedScaleKmh;
            case 19:
               return this.config().trainCollisionFlyDamageScale;
            case 20:
               return this.config().trainCollisionLaunchHorizontal;
            case 21:
               return this.config().trainCollisionLaunchVertical;
            case 22:
               return this.config().trainCollisionFlyHorizontal;
            case 23:
               return this.config().trainCollisionFlyVertical;
            case 24:
               return (double)this.config().trainCollisionImpactCooldownTicks;
            case 25:
               return this.config().trainCollisionHeightClamped();
            case 26:
               return (double)this.config().roofDropMinSpeedKmh;
            case 27:
               return this.config().roofDropMaxRadiusMClamped();
            case 28:
               String mode = this.config().resolvedMode();
               if ("ALWAYS".equals(mode)) {
                  return 1.0D;
               }

               return "OFF".equals(mode) ? 2.0D : 0.0D;
            case 29:
               return (double)this.config().trainCollisionPushOnlyMaxKmh;
            case 30:
               return this.config().trainCollisionDamageMax;
            case 31:
               return this.config().knockbackDistance;
            case 32:
               return this.config().knockbackExpK;
            case 33:
               return this.config().knockbackMax;
            case 34:
               return this.config().scatterStrengthClamped();
            case 35:
               return this.config().scatterRadiusClamped();
         }
      }

      void writeValue(int which, double value) {
         switch (which) {
            case 0:
               this.config().maxDistance = value;
               break;
            case 1:
               this.config().headLuminance = (int)Math.round(value);
               break;
            case 2:
               this.config().headLength = value;
               break;
            case 3:
               this.config().beamDistance = value;
               break;
            case 4:
               this.config().beamHeadLength = value;
               break;
            case 5:
               this.config().beamTailLength = value;
               break;
            case 6:
               this.config().beamWidth = value;
               break;
            case 7:
               this.config().beamHeadHeight = value;
               break;
            case 8:
            default:
               this.config().beamTailHeight = value;
               break;
            case 9:
               this.config().collisionHeadExtra = value;
               break;
            case 10:
               this.config().collisionTailExtra = value;
               break;
            case 11:
               this.config().rainSprayMinSpeedKmh = (int)Math.round(value);
               break;
            case 12:
               this.config().trainCollisionPushOnlyMaxKmh = (int)Math.round(value);
               break;
            case 13:
               this.config().trainCollisionLowDamageMaxKmh = (int)Math.round(value);
               break;
            case 14:
               this.config().trainCollisionHighDamageMaxKmh = (int)Math.round(value);
               break;
            case 15:
               this.config().trainCollisionLaunchMaxKmh = (int)Math.round(value);
               break;
            case 16:
               this.config().trainCollisionDamageBasePerSecond = value;
               break;
            case 17:
               this.config().trainCollisionDamageExpK = value;
               break;
            case 18:
               this.config().trainCollisionDamageSpeedScaleKmh = value;
               break;
            case 19:
               this.config().trainCollisionFlyDamageScale = value;
               break;
            case 20:
               this.config().trainCollisionLaunchHorizontal = value;
               break;
            case 21:
               this.config().trainCollisionLaunchVertical = value;
               break;
            case 22:
               this.config().trainCollisionFlyHorizontal = value;
               break;
            case 23:
               this.config().trainCollisionFlyVertical = value;
               break;
            case 24:
               this.config().trainCollisionImpactCooldownTicks = (int)Math.round(value);
               break;
            case 25:
               this.config().trainCollisionHeight = value;
               break;
            case 26:
               this.config().roofDropMinSpeedKmh = (int)Math.round(value);
               break;
            case 27:
               this.config().roofDropMaxRadiusM = value;
               break;
            case 28:
               int index = (int)Math.round(value);
               this.config().headlightMode = index == 1 ? "ALWAYS" : (index == 2 ? "OFF" : "AUTO");
               break;
            case 29:
               this.config().trainCollisionPushOnlyMaxKmh = (int)Math.round(value);
               break;
            case 30:
               this.config().trainCollisionDamageMax = value;
               break;
            case 31:
               this.config().knockbackDistance = value;
               break;
            case 32:
               this.config().knockbackExpK = value;
               break;
            case 33:
               this.config().knockbackMax = value;
               break;
            case 34:
               this.config().scatterStrength = value;
               break;
            case 35:
               this.config().scatterRadius = value;
         }

      }

      boolean readToggle(int which) {
         HeadlightConfig.Effective eff = this.effective();
         switch (which) {
            case 0:
               return eff.enabled;
            case 1:
               return eff.dynamicLight;
            case 2:
               return this.config().beamBand;
            case 3:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 37:
            case 39:
            case 40:
            default:
               return false;
            case 4:
               return eff.rainSpray;
            case 5:
               return this.config().disableBuiltInTrains;
            case 6:
               return this.config().trainCollision;
            case 7:
               return this.config().trackbedCollision;
            case 8:
               return eff.trainCollisionDamage;
            case 9:
               return this.config().trainCollisionMobs;
            case 10:
               return eff.rainHush;
            case 11:
               return this.config().damageInCreative;
            case 36:
               return this.config().beamScatter;
            case 38:
               return this.config().scatterSurface;
            case 41:
               return this.config().beamFlare;
            case 42:
               return this.config().scatterUnderShaders;
         }
      }

      void writeToggle(int which, boolean on) {
         switch (which) {
            case 0:
               this.config().enabled = on;
               break;
            case 1:
               this.config().dynamicLight = on;
               break;
            case 2:
               this.config().beamBand = on;
               break;
            case 3:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 37:
            case 39:
            case 40:
            default:
               this.config().beamFlare = on;
               break;
            case 4:
               this.config().rainSpray = on;
               break;
            case 5:
               this.config().disableBuiltInTrains = on;
               break;
            case 6:
               this.config().trainCollision = on;
               break;
            case 7:
               this.config().trackbedCollision = on;
               break;
            case 8:
               this.config().trainCollisionDamage = on;
               break;
            case 9:
               this.config().trainCollisionMobs = on;
               break;
            case 10:
               this.config().rainHush = on;
               break;
            case 11:
               this.config().damageInCreative = on;
               break;
            case 36:
               this.config().beamScatter = on;
               break;
            case 38:
               this.config().scatterSurface = on;
               break;
            case 41:
               this.config().beamFlare = on;
               break;
            case 42:
               this.config().scatterUnderShaders = on;
               break;
         }

      }

      int readColor(boolean head) {
         return head ? this.effective().beamHeadColor : this.effective().beamTailColor;
      }

      void writeColor(boolean head, int rgb) {
         String text = HeadlightConfig.formatColor(rgb);
         if (head) {
            this.config().beamHeadColor = text;
         } else {
            this.config().beamTailColor = text;
         }

      }

      boolean followsValue(int which) {
         return false;
      }

      boolean followsToggle(int which) {
         return false;
      }

      boolean followsColor(boolean head) {
         return false;
      }

      boolean hasOverrides() {
         return false;
      }

      void resetValue(int which) {
         HeadlightConfig d = HeadlightConfig.defaults();
         HeadlightConfig c = this.config();
         switch (which) {
            case 0:
               c.maxDistance = d.maxDistance;
               break;
            case 1:
               c.headLuminance = d.headLuminance;
               break;
            case 2:
               c.headLength = d.headLength;
               break;
            case 3:
               c.beamDistance = d.beamDistance;
               break;
            case 4:
               c.beamHeadLength = d.beamHeadLength;
               break;
            case 5:
               c.beamTailLength = d.beamTailLength;
               break;
            case 6:
               c.beamWidth = d.beamWidth;
               break;
            case 7:
               c.beamHeadHeight = d.beamHeadHeight;
               break;
            case 8:
            default:
               c.beamTailHeight = d.beamTailHeight;
               break;
            case 9:
               c.collisionHeadExtra = d.collisionHeadExtra;
               break;
            case 10:
               c.collisionTailExtra = d.collisionTailExtra;
               break;
            case 11:
               c.rainSprayMinSpeedKmh = d.rainSprayMinSpeedKmh;
               break;
            case 12:
               c.trainCollisionPushOnlyMaxKmh = d.trainCollisionPushOnlyMaxKmh;
               break;
            case 13:
               c.trainCollisionLowDamageMaxKmh = d.trainCollisionLowDamageMaxKmh;
               break;
            case 14:
               c.trainCollisionHighDamageMaxKmh = d.trainCollisionHighDamageMaxKmh;
               break;
            case 15:
               c.trainCollisionLaunchMaxKmh = d.trainCollisionLaunchMaxKmh;
               break;
            case 16:
               c.trainCollisionDamageBasePerSecond = d.trainCollisionDamageBasePerSecond;
               break;
            case 17:
               c.trainCollisionDamageExpK = d.trainCollisionDamageExpK;
               break;
            case 18:
               c.trainCollisionDamageSpeedScaleKmh = d.trainCollisionDamageSpeedScaleKmh;
               break;
            case 19:
               c.trainCollisionFlyDamageScale = d.trainCollisionFlyDamageScale;
               break;
            case 20:
               c.trainCollisionLaunchHorizontal = d.trainCollisionLaunchHorizontal;
               break;
            case 21:
               c.trainCollisionLaunchVertical = d.trainCollisionLaunchVertical;
               break;
            case 22:
               c.trainCollisionFlyHorizontal = d.trainCollisionFlyHorizontal;
               break;
            case 23:
               c.trainCollisionFlyVertical = d.trainCollisionFlyVertical;
               break;
            case 24:
               c.trainCollisionImpactCooldownTicks = d.trainCollisionImpactCooldownTicks;
               break;
            case 25:
               c.trainCollisionHeight = d.trainCollisionHeight;
               break;
            case 26:
               c.roofDropMinSpeedKmh = d.roofDropMinSpeedKmh;
               break;
            case 27:
               c.roofDropMaxRadiusM = d.roofDropMaxRadiusM;
               break;
            case 28:
               c.headlightMode = d.headlightMode;
               break;
            case 29:
               c.trainCollisionPushOnlyMaxKmh = d.trainCollisionPushOnlyMaxKmh;
               break;
            case 30:
               c.trainCollisionDamageMax = d.trainCollisionDamageMax;
               break;
            case 31:
               c.knockbackDistance = d.knockbackDistance;
               break;
            case 32:
               c.knockbackExpK = d.knockbackExpK;
               break;
            case 33:
               c.knockbackMax = d.knockbackMax;
               break;
            case 34:
               c.scatterStrength = d.scatterStrength;
               break;
            case 35:
               c.scatterRadius = d.scatterRadius;
         }

      }

      void resetToggle(int which) {
         HeadlightConfig d = HeadlightConfig.defaults();
         HeadlightConfig c = this.config();
         switch (which) {
            case 0:
               c.enabled = d.enabled;
               break;
            case 1:
               c.dynamicLight = d.dynamicLight;
               break;
            case 2:
            case 3:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 37:
            case 39:
            case 40:
            case 41:
            default:
               c.beamBand = d.beamBand;
               c.beamFlare = d.beamFlare;
               break;
            case 4:
               c.rainSpray = d.rainSpray;
               break;
            case 5:
               c.disableBuiltInTrains = d.disableBuiltInTrains;
               break;
            case 6:
               c.trainCollision = d.trainCollision;
               break;
            case 7:
               c.trackbedCollision = d.trackbedCollision;
               break;
            case 8:
               c.trainCollisionDamage = d.trainCollisionDamage;
               break;
            case 9:
               c.trainCollisionMobs = d.trainCollisionMobs;
               break;
            case 10:
               c.rainHush = d.rainHush;
               break;
            case 11:
               c.damageInCreative = d.damageInCreative;
               break;
            case 36:
               c.beamScatter = d.beamScatter;
               break;
            case 38:
               c.scatterSurface = d.scatterSurface;
               break;
            case 42:
               c.scatterUnderShaders = d.scatterUnderShaders;
               break;
         }

      }

      void resetColor(boolean head) {
         HeadlightConfig d = HeadlightConfig.defaults();
         HeadlightConfig c = this.config();
         if (head) {
            c.beamHeadColor = d.beamHeadColor;
         } else {
            c.beamTailColor = d.beamTailColor;
         }

      }

      void reset() {
      }

      void resetAll() {
         HeadlightConfig d = HeadlightConfig.defaults();
         HeadlightConfig c = this.config();
         c.enabled = d.enabled;
         c.dynamicLight = d.dynamicLight;
         c.beamBand = d.beamBand;
         c.beamFlare = d.beamFlare;
         c.rainSpray = d.rainSpray;
         c.rainSprayMinSpeedKmh = d.rainSprayMinSpeedKmh;
         c.disableBuiltInTrains = d.disableBuiltInTrains;
         c.maxDistance = d.maxDistance;
         c.headLuminance = d.headLuminance;
         c.headLength = d.headLength;
         c.beamDistance = d.beamDistance;
         c.beamHeadLength = d.beamHeadLength;
         c.beamTailLength = d.beamTailLength;
         c.beamWidth = d.beamWidth;
         c.beamHeadColor = d.beamHeadColor;
         c.beamTailColor = d.beamTailColor;
         c.beamHeadHeight = d.beamHeadHeight;
         c.beamTailHeight = d.beamTailHeight;
         c.collisionHeadExtra = d.collisionHeadExtra;
         c.collisionTailExtra = d.collisionTailExtra;
         c.trainCollisionPushOnlyMaxKmh = d.trainCollisionPushOnlyMaxKmh;
         c.trainCollisionLowDamageMaxKmh = d.trainCollisionLowDamageMaxKmh;
         c.trainCollisionHighDamageMaxKmh = d.trainCollisionHighDamageMaxKmh;
         c.trainCollisionLaunchMaxKmh = d.trainCollisionLaunchMaxKmh;
         c.trainCollisionDamageBasePerSecond = d.trainCollisionDamageBasePerSecond;
         c.trainCollisionDamageExpK = d.trainCollisionDamageExpK;
         c.trainCollisionDamageSpeedScaleKmh = d.trainCollisionDamageSpeedScaleKmh;
         c.trainCollisionFlyDamageScale = d.trainCollisionFlyDamageScale;
         c.knockbackDistance = d.knockbackDistance;
         c.knockbackExpK = d.knockbackExpK;
         c.knockbackMax = d.knockbackMax;
         c.trainCollisionLaunchHorizontal = d.trainCollisionLaunchHorizontal;
         c.trainCollisionLaunchVertical = d.trainCollisionLaunchVertical;
         c.trainCollisionFlyHorizontal = d.trainCollisionFlyHorizontal;
         c.trainCollisionFlyVertical = d.trainCollisionFlyVertical;
         c.trainCollisionImpactCooldownTicks = d.trainCollisionImpactCooldownTicks;
         c.trainCollision = d.trainCollision;
         c.trainCollisionHeight = d.trainCollisionHeight;
         c.trainCollisionDamage = d.trainCollisionDamage;
         c.trainCollisionDamageMax = d.trainCollisionDamageMax;
         c.trainCollisionMobs = d.trainCollisionMobs;
         c.damageInCreative = d.damageInCreative;
         c.trackbedCollision = d.trackbedCollision;
         c.roofDropMinSpeedKmh = d.roofDropMinSpeedKmh;
         c.roofDropMaxRadiusM = d.roofDropMaxRadiusM;
         c.rainHush = d.rainHush;
         c.headlightMode = d.headlightMode;
         c.beamScatter = d.beamScatter;
         c.scatterSurface = d.scatterSurface;
         c.scatterUnderShaders = d.scatterUnderShaders;
         c.scatterStrength = d.scatterStrength;
         c.scatterRadius = d.scatterRadius;
         if (c.sidingSettings != null) {
            c.sidingSettings.clear();
         }

      }
   }

   private static final class SidingTarget extends ConfigWriteTarget {
      private final long sidingId;

      SidingTarget(Supplier<HeadlightConfig> cfg, long sidingId) {
         super(cfg);
         if (sidingId == 0L) {
            throw new IllegalArgumentException("sidingId 0 means the global config");
         } else {
            this.sidingId = sidingId;
         }
      }

      String describe() {
         return "siding #" + this.sidingId;
      }

      HeadlightConfig.Effective effective() {
         return this.config().effectiveFor(this.sidingId);
      }

      private HeadlightConfig.SidingSettings entry() {
         return this.config().settingsFor(this.sidingId);
      }

      double readValue(int which) {
         HeadlightConfig.Effective eff = this.effective();
         switch (which) {
            case 0:
               return eff.maxDistance;
            case 1:
               return (double)eff.headLuminance;
            case 2:
               return eff.headLength;
            case 3:
               return eff.beamDistance;
            case 4:
               return eff.beamHeadLength;
            case 5:
               return eff.beamTailLength;
            case 6:
               return eff.beamWidth;
            case 7:
               return eff.beamHeadHeight;
            case 8:
            case 37:
            case 39:
            case 40:
            case 41:
            default:
               return eff.beamTailHeight;
            case 9:
               return eff.collisionHeadExtra;
            case 10:
               return eff.collisionTailExtra;
            case 11:
               return eff.rainSprayMinSpeedKmh;
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 38:
            case 42:
               ConfigWriteTarget.ignored(which);
               return 0.0D;
            case 29:
               return eff.trainCollisionDamageMinSpeedKmh;
            case 30:
               return eff.trainCollisionDamageMax;
         }
      }

      void writeValue(int which, double value) {
         HeadlightConfig.SidingSettings o = this.entry();
         switch (which) {
            case 0:
               o.maxDistance = value;
               break;
            case 1:
               o.headLuminance = (int)Math.round(value);
               break;
            case 2:
               o.headLength = value;
               break;
            case 3:
               o.beamDistance = value;
               break;
            case 4:
               o.beamHeadLength = value;
               break;
            case 5:
               o.beamTailLength = value;
               break;
            case 6:
               o.beamWidth = value;
               break;
            case 7:
               o.beamHeadHeight = value;
               break;
            case 8:
            case 37:
            case 39:
            case 40:
            case 41:
            default:
               o.beamTailHeight = value;
               break;
            case 9:
               o.collisionHeadExtra = value;
               break;
            case 10:
               o.collisionTailExtra = value;
               break;
            case 11:
               o.rainSprayMinSpeedKmh = value;
               break;
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 36:
            case 38:
            case 42:
               ConfigWriteTarget.ignored(which);
               break;
            case 29:
               o.trainCollisionDamageMinSpeedKmh = value;
               break;
            case 30:
               o.trainCollisionDamageMax = value;
         }

      }

      boolean readToggle(int which) {
         HeadlightConfig.Effective eff = this.effective();
         switch (which) {
            case 0:
               return eff.enabled;
            case 1:
               return eff.dynamicLight;
            case 2:
               return eff.beamBand;
            case 3:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 37:
            case 39:
            case 40:
            default:
               return false;
            case 4:
               return eff.rainSpray;
            case 5:
            case 6:
            case 7:
            case 9:
            case 11:
            case 36:
            case 38:
            case 42:
               ConfigWriteTarget.ignored(which);
               return false;
            case 8:
               return eff.trainCollisionDamage;
            case 10:
               return eff.rainHush;
            case 41:
               return eff.beamFlare;
         }
      }

      void writeToggle(int which, boolean on) {
         HeadlightConfig.SidingSettings o = this.entry();
         switch (which) {
            case 0:
               o.enabled = on;
               break;
            case 1:
               o.dynamicLight = on;
               break;
            case 2:
               o.beamBand = on;
               break;
            case 3:
            case 12:
            case 13:
            case 14:
            case 15:
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 32:
            case 33:
            case 34:
            case 35:
            case 37:
            case 39:
            case 40:
            default:
               ConfigWriteTarget.ignored(which);
               break;
            case 4:
               o.rainSpray = on;
               break;
            case 5:
            case 6:
            case 7:
            case 9:
            case 11:
            case 36:
            case 38:
            case 42:
               ConfigWriteTarget.ignored(which);
               break;
            case 8:
               o.trainCollisionDamage = on;
               break;
            case 10:
               o.rainHush = on;
               break;
            case 41:
               o.beamFlare = on;
         }

      }

      int readColor(boolean head) {
         return head ? this.effective().beamHeadColor : this.effective().beamTailColor;
      }

      void writeColor(boolean head, int rgb) {
         HeadlightConfig.SidingSettings o = this.entry();
         String text = HeadlightConfig.formatColor(rgb);
         if (head) {
            o.beamHeadColor = text;
         } else {
            o.beamTailColor = text;
         }

      }

      boolean followsValue(int which) {
         HeadlightConfig.SidingSettings o = this.config().overrides(this.sidingId);
         if (o == null) {
            return true;
         } else {
            switch (which) {
               case 0:
                  return o.maxDistance == null;
               case 1:
                  return o.headLuminance == null;
               case 2:
                  return o.headLength == null;
               case 3:
                  return o.beamDistance == null;
               case 4:
                  return o.beamHeadLength == null;
               case 5:
                  return o.beamTailLength == null;
               case 6:
                  return o.beamWidth == null;
               case 7:
                  return o.beamHeadHeight == null;
               case 8:
               case 37:
               case 39:
               case 40:
               case 41:
               default:
                  return o.beamTailHeight == null;
               case 9:
                  return o.collisionHeadExtra == null;
               case 10:
                  return o.collisionTailExtra == null;
               case 11:
                  return o.rainSprayMinSpeedKmh == null;
               case 12:
               case 13:
               case 14:
               case 15:
               case 16:
               case 17:
               case 18:
               case 19:
               case 20:
               case 21:
               case 22:
               case 23:
               case 24:
               case 25:
               case 26:
               case 27:
               case 28:
               case 31:
               case 32:
               case 33:
               case 34:
               case 35:
               case 36:
               case 38:
               case 42:
                  ConfigWriteTarget.ignored(which);
                  return true;
               case 29:
                  return o.trainCollisionDamageMinSpeedKmh == null;
               case 30:
                  return o.trainCollisionDamageMax == null;
            }
         }
      }

      boolean followsToggle(int which) {
         HeadlightConfig.SidingSettings o = this.config().overrides(this.sidingId);
         if (o == null) {
            return true;
         } else {
            switch (which) {
               case 0:
                  return o.enabled == null;
               case 1:
                  return o.dynamicLight == null;
               case 2:
                  return o.beamBand == null;
               case 3:
               case 12:
               case 13:
               case 14:
               case 15:
               case 16:
               case 17:
               case 18:
               case 19:
               case 20:
               case 21:
               case 22:
               case 23:
               case 24:
               case 25:
               case 26:
               case 27:
               case 28:
               case 29:
               case 30:
               case 31:
               case 32:
               case 33:
               case 34:
               case 35:
               case 37:
               case 39:
               case 40:
               default:
                  return o.beamFlare == null;
               case 4:
                  return o.rainSpray == null;
               case 5:
               case 6:
               case 7:
               case 9:
               case 11:
               case 36:
               case 38:
               case 42:
                  ConfigWriteTarget.ignored(which);
                  return true;
               case 8:
                  return o.trainCollisionDamage == null;
               case 10:
                  return o.rainHush == null;
               case 41:
                  return o.beamFlare == null;
            }
         }
      }

      boolean followsColor(boolean head) {
         HeadlightConfig.SidingSettings o = this.config().overrides(this.sidingId);
         if (o == null) {
            return true;
         } else {
            return head ? o.beamHeadColor == null : o.beamTailColor == null;
         }
      }

      boolean hasOverrides() {
         HeadlightConfig.SidingSettings o = this.config().overrides(this.sidingId);
         return o != null && !o.isEmpty();
      }

      private HeadlightConfig.SidingSettings dropEntry() {
         return this.config().overrides(this.sidingId);
      }

      private void prune() {
         HeadlightConfig.SidingSettings o = this.config().overrides(this.sidingId);
         if (o != null && o.isEmpty()) {
            this.config().clearSiding(this.sidingId);
         }

      }

      void resetValue(int which) {
         HeadlightConfig.SidingSettings o = this.dropEntry();
         if (o != null) {
            switch (which) {
               case 0:
                  o.maxDistance = null;
                  break;
               case 1:
                  o.headLuminance = null;
                  break;
               case 2:
                  o.headLength = null;
                  break;
               case 3:
                  o.beamDistance = null;
                  break;
               case 4:
                  o.beamHeadLength = null;
                  break;
               case 5:
                  o.beamTailLength = null;
                  break;
               case 6:
                  o.beamWidth = null;
                  break;
               case 7:
                  o.beamHeadHeight = null;
                  break;
               case 8:
               case 37:
               case 39:
               case 40:
               case 41:
               default:
                  o.beamTailHeight = null;
                  break;
               case 9:
                  o.collisionHeadExtra = null;
                  break;
               case 10:
                  o.collisionTailExtra = null;
                  break;
               case 11:
                  o.rainSprayMinSpeedKmh = null;
                  break;
               case 12:
               case 13:
               case 14:
               case 15:
               case 16:
               case 17:
               case 18:
               case 19:
               case 20:
               case 21:
               case 22:
               case 23:
               case 24:
               case 25:
               case 26:
               case 27:
               case 28:
               case 31:
               case 32:
               case 33:
               case 34:
               case 35:
               case 36:
               case 38:
               case 42:
                  ConfigWriteTarget.ignored(which);
                  break;
               case 29:
                  o.trainCollisionDamageMinSpeedKmh = null;
                  break;
               case 30:
                  o.trainCollisionDamageMax = null;
            }

            this.prune();
         }
      }

      void resetToggle(int which) {
         HeadlightConfig.SidingSettings o = this.dropEntry();
         if (o != null) {
            switch (which) {
               case 0:
                  o.enabled = null;
                  break;
               case 1:
                  o.dynamicLight = null;
                  break;
               case 2:
                  o.beamBand = null;
                  break;
               case 3:
               case 12:
               case 13:
               case 14:
               case 15:
               case 16:
               case 17:
               case 18:
               case 19:
               case 20:
               case 21:
               case 22:
               case 23:
               case 24:
               case 25:
               case 26:
               case 27:
               case 28:
               case 29:
               case 30:
               case 31:
               case 32:
               case 33:
               case 34:
               case 35:
               case 37:
               case 39:
               case 40:
               default:
                  o.beamFlare = null;
                  break;
               case 4:
                  o.rainSpray = null;
                  break;
               case 5:
               case 6:
               case 7:
               case 9:
               case 11:
               case 36:
               case 38:
               case 42:
                  ConfigWriteTarget.ignored(which);
                  break;
               case 8:
                  o.trainCollisionDamage = null;
                  break;
               case 10:
                  o.rainHush = null;
                  break;
               case 41:
                  o.beamFlare = null;
            }

            this.prune();
         }
      }

      void resetColor(boolean head) {
         HeadlightConfig.SidingSettings o = this.dropEntry();
         if (o != null) {
            if (head) {
               o.beamHeadColor = null;
            } else {
               o.beamTailColor = null;
            }

            this.prune();
         }
      }

      void reset() {
         this.config().clearSiding(this.sidingId);
      }

      void resetAll() {
         this.config().clearSiding(this.sidingId);
      }
   }
}

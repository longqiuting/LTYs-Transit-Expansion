package dev.js.lte;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import dev.js.lte.forge.platform.FabricLoader;

public final class HeadlightConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final int CONFIG_VERSION = 27;
   private static final int MIN_PX_FLOOR = 8;
   public static final int DEFAULT_HEAD_BEAM = 16774102;
   public static final int DEFAULT_TAIL_BEAM = 16738906;
   public int configVersion = 0;
   public boolean enabled = true;
   public boolean dynamicLight = true;
   public boolean ldlRegisterSources = true;
   public boolean onlyTrains = true;
   public int headLuminance = 15;
   public double headLength = 11.0D;
   public double headOffset = 0.6D;
   public int tailLuminance = 9;
   public double tailLength = 4.0D;
   public double tailOffset = 0.4D;
   public double heightOffset = 0.85D;
   public static final double HEAD_TIP_DROP = 0.6D;
   public static final double TAIL_TIP_DROP = 0.35D;
   public double maxDistance = 192.0D;
   public int missingKeepTicks = 600;
   public int missingDimTicks = 1200;
   public int missingDetachTicks = 2400;
   public double massClearRatio = 0.5D;
   public boolean massClearDiagnostics = true;
   public boolean swapEnds = false;
   /** @deprecated */
   @Deprecated
   public List<Long> mutedSidings = new ArrayList();
   public Map<Long, HeadlightConfig.SidingSettings> sidingSettings = new LinkedHashMap();
   public boolean beamBand = true;
   public boolean beamFlare = false;
   public boolean beamScatter = false;
   /** @deprecated */
   @Deprecated
   public Boolean beamGlow;
   /** @deprecated */
   @Deprecated
   public String lightStyle;
   /** @deprecated */
   @Deprecated
   public Boolean scatterEnabled;
   public double beamFlareSize = 1.4D;
   public int beamFlareMinPx = 6;
   public double beamFlareMinPct = 0.012D;
   public double scatterStrength = 1.0D;
   public double scatterRadius = 6.0D;
   public boolean scatterSurface = true;
   public boolean scatterUnderShaders = true;
   /** @deprecated */
   @Deprecated
   public boolean beamCone = false;
   public static final String CONE_SOURCE_MODEL = "MODEL";
   public static final String CONE_SOURCE_GLOW = "GLOW";
   public String beamConeSource = "MODEL";
   public double beamDistance = 128.0D;
   public double beamHeadLength = 16.0D;
   public double beamTailLength = 4.0D;
   public double beamWidth = 3.0D;
   public String beamHeadColor = "#FFF3D6";
   public String beamTailColor = "#FF6A5A";
   public double beamHeadHeight = 0.15D;
   public double beamTailHeight = 0.1D;
   public double beamLift = 0.07D;
   public double beamAlpha = 0.55D;
   public double beamConeAlpha = 0.3D;
   public boolean beamConeOcclusion = true;
   public boolean beamConeVolume = false;
   public boolean coneOnTail = false;
   public int beamConeSteps = 10;
   public boolean beamBlockLight = true;
   public int beamBlockLightRadius = 12;
   public static final String MODE_AUTO = "AUTO";
   public static final String MODE_ALWAYS = "ALWAYS";
   public static final String MODE_OFF = "OFF";
   public String headlightMode = "AUTO";
   public boolean parkedLightsOff = true;
   public int parkedLightsOffTicks = 200;
   /** @deprecated */
   @Deprecated
   public boolean autoLightsInTunnel = true;
   public boolean weatherDiagnostics = true;
   public boolean disableBuiltInTrains = false;
   public boolean builtinTrainsDiagnostics = true;
   public boolean rainSpray = true;
   public int rainSprayMinSpeedKmh = 50;
   public boolean rainSprayLowSpeedBoost = true;
   public boolean rainSprayDiagnostics = true;
   public boolean rainHush = true;
   public boolean rainHushSplash = true;
   public boolean rainHushDiagnostics = true;
   public boolean trainCollision = true;
   public double trainCollisionHeight = 3.8D;
   public double trainCollisionEndPad = 1.0D;
   public double collisionHeadExtra = 1.0D;
   public double collisionTailExtra = 1.0D;
   public int roofDropMinSpeedKmh = 50;
   public double roofDropMaxRadiusM = 100.0D;
   public boolean trainCollisionDamage = true;
   public boolean damageInCreative = false;
   public int trainCollisionPushOnlyMaxKmh = 5;
   public int trainCollisionLowDamageMaxKmh = 20;
   public int trainCollisionHighDamageMaxKmh = 50;
   public int trainCollisionLaunchMaxKmh = 80;
   public double trainCollisionDamageBasePerSecond = 0.56D;
   public double trainCollisionDamageExpK = 1.35D;
   public double trainCollisionDamageSpeedScaleKmh = 100.0D;
   public double trainCollisionFlyDamageScale = 1.0D;
   public int trainCollisionHurtTicks = 120;
   public double trainCollisionDamageMax = 0.0D;
   public double knockbackDistance = 5.5D;
   public double knockbackExpK = 1.0D;
   public double knockbackMax = 80.0D;
   /** @deprecated */
   @Deprecated
   public double trainCollisionLaunchHorizontal = 1.0D;
   public double trainCollisionLaunchVertical = 0.45D;
   /** @deprecated */
   @Deprecated
   public double trainCollisionFlyHorizontal = 1.8D;
   public double trainCollisionFlyVertical = 0.6D;
   public int trainCollisionImpactCooldownTicks = 20;
   public boolean trainCollisionMobs = true;
   public boolean trackbedCollision = true;
   public boolean trackbedCollisionEntities = true;
   public boolean trackbedCollisionItems = true;
   public double trackbedThickness = 0.15D;
   public double trackbedStepUp = 1.0D;
   public boolean collisionDiagnostics = false;
   private static final String CONFIG_FILE = "lte.json";
   private static final String[] LEGACY_CONFIG_FILES = new String[]{"mtr-external-lights.json", "mtr-headlight.json"};

   public double headOffsetClamped() {
      return clampOffset(this.headOffset);
   }

   public double tailOffsetClamped() {
      return clampOffset(this.tailOffset);
   }

   private static double clampOffset(double value) {
      return Double.isNaN(value) ? 0.0D : Math.max(0.0D, Math.min(2.0D, value));
   }

   public boolean drawBand() {
      return this.enabled && this.beamAnySiding();
   }

   public boolean drawFlare() {
      return this.enabled && this.flareAnySiding();
   }

   public boolean drawScatter() {
      return this.enabled && this.beamScatter;
   }

   public boolean drawBlockLight() {
      return this.enabled && this.dynamicLight && this.beamBlockLight;
   }

   public int beamFlareMinPxFor(int screenHeight) {
      int byPct = (int)Math.round((double)Math.max(1, screenHeight) * this.flareMinPctClamped());
      int configured = this.flareMinPxClamped();
      return Math.max(8, Math.max(configured, byPct));
   }

   public double flareMinPctClamped() {
      return clamp(this.beamFlareMinPct, 0.0D, 0.05D);
   }

   public double scatterStrengthClamped() {
      return clamp(this.scatterStrength, 0.1D, 3.0D);
   }

   public double scatterRadiusClamped() {
      return clamp(this.scatterRadius, 1.0D, 24.0D);
   }

   public String resolvedConeSource() {
      return normalizeConeSource(this.beamConeSource);
   }

   public boolean coneSourceGlow() {
      return "GLOW".equals(this.resolvedConeSource());
   }

   public static String normalizeConeSource(String raw) {
      if (raw == null) {
         return "MODEL";
      } else {
         return "GLOW".equalsIgnoreCase(raw.trim()) ? "GLOW" : "MODEL";
      }
   }

   public int beamConeSteps() {
      return this.beamConeSteps < 4 ? 4 : Math.min(this.beamConeSteps, 16);
   }

   public String resolvedMode() {
      return normalizeHeadlightMode(this.headlightMode);
   }

   public boolean autoHeadlights() {
      return "AUTO".equals(this.resolvedMode());
   }

   public int parkIdleTicks() {
      return Math.max(0, this.parkedLightsOffTicks);
   }

   public static String normalizeHeadlightMode(String raw) {
      if (raw == null) {
         return "AUTO";
      } else {
         String trimmed = raw.trim();
         if ("ALWAYS".equalsIgnoreCase(trimmed)) {
            return "ALWAYS";
         } else {
            return "OFF".equalsIgnoreCase(trimmed) ? "OFF" : "AUTO";
         }
      }
   }

   public double trainCollisionHeightClamped() {
      return Double.isNaN(this.trainCollisionHeight) ? 3.8D : Math.max(1.0D, Math.min(8.0D, this.trainCollisionHeight));
   }

   public double trainCollisionEndPadClamped() {
      return Double.isNaN(this.trainCollisionEndPad) ? 1.0D : Math.max(0.0D, Math.min(3.0D, this.trainCollisionEndPad));
   }

   public double collisionHeadExtraClamped() {
      return clampEndExtra(this.collisionHeadExtra);
   }

   public double collisionTailExtraClamped() {
      return clampEndExtra(this.collisionTailExtra);
   }

   static double clampEndExtra(double value) {
      return Double.isNaN(value) ? 1.0D : Math.max(0.0D, Math.min(4.0D, value));
   }

   public double roofDropMaxRadiusMClamped() {
      return Double.isNaN(this.roofDropMaxRadiusM) ? 100.0D : Math.max(10.0D, Math.min(1000.0D, this.roofDropMaxRadiusM));
   }

   public int trainCollisionHurtTicksClamped() {
      return clamp(this.trainCollisionHurtTicks, 40, 600);
   }

   public double trainCollisionDamageMaxClamped() {
      return clamp(this.trainCollisionDamageMax, 0.0D, 200.0D);
   }

   public double knockbackDistanceClamped() {
      return clamp(this.knockbackDistance, 1.0D, 80.0D);
   }

   public double knockbackExpKClamped() {
      return clamp(this.knockbackExpK, 0.0D, 3.0D);
   }

   public double knockbackMaxClamped() {
      return clamp(this.knockbackMax, 1.0D, 200.0D);
   }

   public int trainCollisionPushOnlyMaxKmhClamped() {
      return clamp(this.trainCollisionPushOnlyMaxKmh, 0, 300);
   }

   public int trainCollisionLowDamageMaxKmhClamped() {
      return Math.max(this.trainCollisionPushOnlyMaxKmhClamped(), clamp(this.trainCollisionLowDamageMaxKmh, 0, 300));
   }

   public int trainCollisionHighDamageMaxKmhClamped() {
      return Math.max(this.trainCollisionLowDamageMaxKmhClamped(), clamp(this.trainCollisionHighDamageMaxKmh, 0, 300));
   }

   public int trainCollisionLaunchMaxKmhClamped() {
      return Math.max(this.trainCollisionHighDamageMaxKmhClamped(), clamp(this.trainCollisionLaunchMaxKmh, 0, 400));
   }

   public double trainCollisionDamageBasePerSecondClamped() {
      return clamp(this.trainCollisionDamageBasePerSecond, 0.0D, 20.0D);
   }

   public double trainCollisionDamageExpKClamped() {
      return clamp(this.trainCollisionDamageExpK, 0.0D, 8.0D);
   }

   public double trainCollisionDamageSpeedScaleKmhClamped() {
      return clamp(this.trainCollisionDamageSpeedScaleKmh, 10.0D, 1000.0D);
   }

   public double trainCollisionFlyDamageScaleClamped() {
      return clamp(this.trainCollisionFlyDamageScale, 1.0D, 10.0D);
   }

   public double trainCollisionLaunchHorizontalClamped() {
      return clamp(this.trainCollisionLaunchHorizontal, 0.0D, 8.0D);
   }

   public double trainCollisionLaunchVerticalClamped() {
      return clamp(this.trainCollisionLaunchVertical, 0.0D, 4.0D);
   }

   public double trainCollisionFlyHorizontalClamped() {
      return clamp(this.trainCollisionFlyHorizontal, 0.0D, 12.0D);
   }

   public double trainCollisionFlyVerticalClamped() {
      return clamp(this.trainCollisionFlyVertical, 0.0D, 6.0D);
   }

   public int trainCollisionImpactCooldownTicksClamped() {
      return clamp(this.trainCollisionImpactCooldownTicks, 1, 200);
   }

   public double trackbedStepUpClamped() {
      return Double.isNaN(this.trackbedStepUp) ? 1.0D : Math.max(0.0D, Math.min(3.0D, this.trackbedStepUp));
   }

   public double trackbedThicknessClamped() {
      return Double.isNaN(this.trackbedThickness) ? 0.15D : Math.max(0.0D, Math.min(1.0D, this.trackbedThickness));
   }

   public HeadlightConfig.Effective effectiveFor(long sidingId) {
      HeadlightConfig.SidingSettings o = sidingId != 0L && this.sidingSettings != null ? (HeadlightConfig.SidingSettings)this.sidingSettings.get(sidingId) : null;
      if (o == null && o == null) {
         return new HeadlightConfig.Effective(this.enabled, this.dynamicLight, this.maxDistance, this.headLuminance, this.tailLuminance, this.headLength, this.tailLength, this.beamBand, this.beamFlare, this.beamDistance, this.beamHeadLength, this.beamTailLength, this.beamWidth, parseColor(this.beamHeadColor, 16774102), parseColor(this.beamTailColor, 16738906), this.beamHeadHeight, this.beamTailHeight, clampEndExtra(this.collisionHeadExtra), clampEndExtra(this.collisionTailExtra), this.beamCone, clamp(this.beamConeAlpha, 0.02D, 0.5D), this.beamConeOcclusion, this.beamConeSteps(), this.rainSpray, (double)this.rainSprayMinSpeedKmh, this.rainHush, this.trainCollisionDamage, (double)this.trainCollisionPushOnlyMaxKmhClamped(), this.trainCollisionDamageMaxClamped(), sidingId, false);
      } else {
         HeadlightConfig.Effective var10000;
         boolean var10002;
         boolean var10003;
         double var10004;
         int var10005;
         int var10006;
         double var10007;
         double var10008;
         boolean var10009;
         label182: {
            label181: {
               var10002 = o.enabled != null ? o.enabled : this.enabled;
               var10003 = o.dynamicLight != null ? o.dynamicLight : this.dynamicLight;
               var10004 = o.maxDistance != null ? o.maxDistance : this.maxDistance;
               var10005 = o.headLuminance != null ? o.headLuminance : this.headLuminance;
               var10006 = o.tailLuminance != null ? o.tailLuminance : this.tailLuminance;
               var10007 = o.headLength != null ? o.headLength : this.headLength;
               var10008 = o.tailLength != null ? o.tailLength : this.tailLength;
               if (this.beamBand) {
                  if (o.beamBand != null) {
                     if (o.beamBand) {
                        break label181;
                     }
                  } else if (this.beamBand) {
                     break label181;
                  }
               }

               var10009 = false;
               break label182;
            }

            var10009 = true;
         }

         boolean var10010;
         label173: {
            label172: {
               if (this.beamFlare) {
                  if (o.beamFlare != null) {
                     if (o.beamFlare) {
                        break label172;
                     }
                  } else if (this.beamFlare) {
                     break label172;
                  }
               }

               var10010 = false;
               break label173;
            }

            var10010 = true;
         }

         var10000 = new HeadlightConfig.Effective(var10002, var10003, var10004, var10005, var10006, var10007, var10008, var10009, var10010, o.beamDistance != null ? o.beamDistance : this.beamDistance, o.beamHeadLength != null ? o.beamHeadLength : this.beamHeadLength, o.beamTailLength != null ? o.beamTailLength : this.beamTailLength, o.beamWidth != null ? o.beamWidth : this.beamWidth, parseColor(o.beamHeadColor != null ? o.beamHeadColor : this.beamHeadColor, 16774102), parseColor(o.beamTailColor != null ? o.beamTailColor : this.beamTailColor, 16738906), o.beamHeadHeight != null ? o.beamHeadHeight : this.beamHeadHeight, o.beamTailHeight != null ? o.beamTailHeight : this.beamTailHeight, clampEndExtra(o.collisionHeadExtra != null ? o.collisionHeadExtra : this.collisionHeadExtra), clampEndExtra(o.collisionTailExtra != null ? o.collisionTailExtra : this.collisionTailExtra), o.beamCone != null ? o.beamCone : this.beamCone, clamp(o.beamConeAlpha != null ? o.beamConeAlpha : this.beamConeAlpha, 0.02D, 0.5D), o.beamConeOcclusion != null ? o.beamConeOcclusion : this.beamConeOcclusion, clamp(o.beamConeSteps != null ? o.beamConeSteps : this.beamConeSteps, 4, 16), o.rainSpray != null ? o.rainSpray : this.rainSpray, clamp(o.rainSprayMinSpeedKmh != null ? o.rainSprayMinSpeedKmh : (double)this.rainSprayMinSpeedKmh, 0.0D, 200.0D), o.rainHush != null ? o.rainHush : this.rainHush, o.trainCollisionDamage != null ? o.trainCollisionDamage : this.trainCollisionDamage, clamp(o.trainCollisionDamageMinSpeedKmh != null ? o.trainCollisionDamageMinSpeedKmh : (double)this.trainCollisionPushOnlyMaxKmhClamped(), 0.0D, 300.0D), clamp(o.trainCollisionDamageMax != null ? o.trainCollisionDamageMax : this.trainCollisionDamageMax, 0.0D, 200.0D), sidingId, !o.isEmpty());
         return var10000;
      }
   }

   public boolean beamAnySiding() {
      return this.beamBand ? true : this.anySiding((o) -> o.beamBand);
   }

   public boolean flareAnySiding() {
      return this.beamFlare ? true : this.anySiding((o) -> o.beamFlare);
   }

   public double flareSizeClamped() {
      return clamp(this.beamFlareSize, 0.2D, 4.0D);
   }

   public int flareMinPxClamped() {
      return this.beamFlareMinPx < 1 ? 1 : Math.min(this.beamFlareMinPx, 24);
   }

   public boolean beamConeAnySiding() {
      return this.anySiding((o) -> o.beamCone);
   }

   public boolean beamConeOcclusionAnySiding() {
      return this.anySiding((o) -> o.beamConeOcclusion);
   }

   public boolean rainSprayAnySiding() {
      return this.anySiding((o) -> o.rainSpray);
   }

   public boolean rainHushAnySiding() {
      return this.anySiding((o) -> o.rainHush);
   }

   private boolean anySiding(Function<HeadlightConfig.SidingSettings, Boolean> get) {
      if (this.sidingSettings == null) {
         return false;
      } else {
         for(HeadlightConfig.SidingSettings o : this.sidingSettings.values()) {
            if (o != null && Boolean.TRUE.equals(get.apply(o))) {
               return true;
            }
         }

         return false;
      }
   }

   public static int parseColor(String text, int fallback) {
      if (text == null) {
         return fallback;
      } else {
         String s = text.trim();
         if (s.startsWith("#")) {
            s = s.substring(1);
         }

         if (s.length() != 6) {
            return fallback;
         } else {
            try {
               return Integer.parseInt(s, 16) & 16777215;
            } catch (NumberFormatException var4) {
               return fallback;
            }
         }
      }
   }

   public static String formatColor(int rgb) {
      return String.format("#%06X", rgb & 16777215);
   }

   public static HeadlightConfig defaults() {
      return new HeadlightConfig();
   }

   public HeadlightConfig.SidingSettings settingsFor(long sidingId) {
      if (this.sidingSettings == null) {
         this.sidingSettings = new LinkedHashMap();
      }

      HeadlightConfig.SidingSettings settings = (HeadlightConfig.SidingSettings)this.sidingSettings.get(sidingId);
      if (settings == null) {
         settings = new HeadlightConfig.SidingSettings();
         this.sidingSettings.put(sidingId, settings);
      }

      return settings;
   }

   public HeadlightConfig.SidingSettings overrides(long sidingId) {
      return this.sidingSettings == null ? null : (HeadlightConfig.SidingSettings)this.sidingSettings.get(sidingId);
   }

   public boolean clearSiding(long sidingId) {
      return this.sidingSettings != null && this.sidingSettings.remove(sidingId) != null;
   }

   public static HeadlightConfig load() {
      Path configDir = FabricLoader.getInstance().getConfigDir();
      Path path = configDir.resolve("lte.json");
      migrateLegacyFile(configDir, path);
      if (Files.exists(path, new LinkOption[0])) {
         try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            HeadlightConfig cfg = (HeadlightConfig)GSON.fromJson(stripDiagnosticKeys(json), HeadlightConfig.class);
            if (cfg != null) {
               HeadlightConfig ready = cfg.sanitized();
               if (ready.migrate()) {
                  ready.save(path);
               }

               return ready;
            }
         } catch (RuntimeException | IOException var5) {
            LteLog.LOGGER.warn("Could not read {} - using defaults", path, var5);
         }
      }

      HeadlightConfig cfg = new HeadlightConfig();
      cfg.configVersion = 27;
      cfg.save(path);
      return cfg;
   }

   /**
    * Drops the diagnostic keys before deserialization so they fall back to "absent = the new
    * default (off)". Rewriting the whole file would throw away everything the player tuned;
    * a version bump alone would leave "collisionDiagnostics": true sitting in the file.
    */
   private static String stripDiagnosticKeys(String json) {
      try {
         JsonElement parsed = JsonParser.parseString(json);
         if (!parsed.isJsonObject()) {
            return json;
         } else {
            JsonObject obj = parsed.getAsJsonObject();
            boolean removed = obj.remove("collisionDiagnostics") != null;
            removed |= obj.remove("debugProbe") != null;
            return removed ? GSON.toJson(obj) : json;
         }
      } catch (RuntimeException var4) {
         LteLog.LOGGER.warn("[lte] could not strip diagnostic keys from config - reading it as-is", var4);
         return json;
      }
   }

   private static void migrateLegacyFile(Path configDir, Path target) {
      if (!Files.exists(target, new LinkOption[0])) {
         for(String name : LEGACY_CONFIG_FILES) {
            Path legacy = configDir.resolve(name);
            if (Files.isRegularFile(legacy, new LinkOption[0])) {
               try {
                  Files.copy(legacy, target);
                  LteLog.LOGGER.info("[lte] config migrated from {} to {} (old file kept)", legacy.getFileName(), target.getFileName());
               } catch (IOException var8) {
                  LteLog.LOGGER.warn("[lte] could not migrate {} - using defaults", legacy, var8);
               }

               return;
            }
         }

      }
   }

   private boolean migrate() {
      if (this.configVersion >= 27) {
         return false;
      } else {
         if (this.configVersion < 3 && this.mutedSidings != null && !this.mutedSidings.isEmpty()) {
            for(Long id : this.mutedSidings) {
               if (id != null && id != 0L) {
                  this.settingsFor(id).enabled = Boolean.FALSE;
               }
            }

            this.mutedSidings.clear();
         }

         if (this.configVersion < 16 && this.beamConeAlpha == 0.16D) {
            this.beamConeAlpha = 0.3D;
         }

         if (this.configVersion < 8 && (this.headlightMode == null || normalizeHeadlightMode(this.headlightMode).equals("AUTO")) && !this.autoLightsInTunnel) {
            this.headlightMode = "OFF";
         }

         if (this.configVersion < 21 && this.beamCone) {
            this.beamCone = false;
         }

         if (this.configVersion < 25) {
            String style = this.lightStyle == null ? null : this.lightStyle.trim().toUpperCase(Locale.ROOT);
            if ("FLARE".equals(style)) {
               this.beamBand = false;
               this.beamFlare = true;
               this.beamScatter = true;
            } else if ("BOTH".equals(style)) {
               this.beamBand = true;
               this.beamFlare = true;
               this.beamScatter = true;
            } else if ("BAND".equals(style)) {
               this.beamBand = true;
               this.beamFlare = false;
               this.beamScatter = false;
            } else if (this.configVersion < 22) {
               boolean band = this.beamGlow == null || this.beamGlow;
               this.beamBand = band;
               this.beamScatter = this.beamFlare;
            }

            this.lightStyle = null;
            this.beamGlow = null;
            this.scatterEnabled = null;
            if (this.sidingSettings != null) {
               for(HeadlightConfig.SidingSettings o : this.sidingSettings.values()) {
                  if (o != null && o.beamGlow != null) {
                     o.beamBand = o.beamGlow;
                     o.beamGlow = null;
                  }
               }
            }

            LteLog.LOGGER.info("[lte] \u5149\u6837\u5f0f {} \u5df2\u62c6\u6210\u4e09\u4e2a\u72ec\u7acb\u5f00\u5173\uff1a\u5149\u5e26={} \u706f\u6655={} \u6563\u5c04={}", new Object[]{style == null ? "(\u65e7\u6587\u4ef6\u672a\u5199 lightStyle)" : style, this.beamBand, this.beamFlare, this.beamScatter});
         }

         if (this.configVersion < 24 && Math.abs(this.beamFlareSize - 0.9D) < 1.0E-9D) {
            this.beamFlareSize = 1.4D;
         }

         if (this.configVersion < 26) {
            boolean flareWasDefault = this.beamFlare;
            boolean scatterWasDefault = this.beamScatter;
            if (flareWasDefault) {
               this.beamFlare = false;
            }

            if (scatterWasDefault) {
               this.beamScatter = false;
            }

            LteLog.LOGGER.info("[lte] \u7248\u672c 26\uff1a\u9ed8\u8ba4\u53ea\u5f00\u5149\u5e26\uff08\u5149\u5e26={} \u706f\u6655={} \u6563\u5c04={}\uff09\uff1b\u65e7\u6587\u4ef6\u91cc\u7684 \u706f\u6655={} \u6563\u5c04={} \u82e5\u4e3a\u65e7\u9ed8\u8ba4\u503c true \u5df2\u6309\u65b0\u9ed8\u8ba4\u6539\u4e3a false\uff0c\u8981\u5f00\u56de\u6765\u8bf7\u5728\u300c\u5149\u6e90\u300d\u9875\u628a\u5bf9\u5e94\u7684\u90a3\u4e00\u884c\u70b9\u5f00", new Object[]{this.beamBand, this.beamFlare, this.beamScatter, flareWasDefault, scatterWasDefault});
         }

         this.configVersion = 27;
         return true;
      }
   }

   private HeadlightConfig sanitized() {
      if (this.mutedSidings == null) {
         this.mutedSidings = new ArrayList();
      }

      if (this.sidingSettings == null) {
         this.sidingSettings = new LinkedHashMap();
      } else {
         this.sidingSettings.entrySet().removeIf((entry) -> entry.getKey() == null || entry.getValue() == null);
         this.sidingSettings.values().forEach((o) -> {
            if (o.maxDistance != null) {
               o.maxDistance = clamp(o.maxDistance, 16.0D, 1024.0D);
            }

            if (o.headLuminance != null) {
               o.headLuminance = clamp(o.headLuminance, 0, 15);
            }

            if (o.tailLuminance != null) {
               o.tailLuminance = clamp(o.tailLuminance, 0, 15);
            }

            if (o.headLength != null) {
               o.headLength = clamp(o.headLength, 0.0D, 32.0D);
            }

            if (o.tailLength != null) {
               o.tailLength = clamp(o.tailLength, 0.0D, 32.0D);
            }

            if (o.beamDistance != null) {
               o.beamDistance = clamp(o.beamDistance, 16.0D, 512.0D);
            }

            if (o.beamHeadLength != null) {
               o.beamHeadLength = clamp(o.beamHeadLength, 0.0D, 64.0D);
            }

            if (o.beamTailLength != null) {
               o.beamTailLength = clamp(o.beamTailLength, 0.0D, 64.0D);
            }

            if (o.beamWidth != null) {
               o.beamWidth = clamp(o.beamWidth, 0.5D, 8.0D);
            }

            if (o.beamHeadHeight != null) {
               o.beamHeadHeight = clamp(o.beamHeadHeight, 0.0D, 4.0D);
            }

            if (o.beamTailHeight != null) {
               o.beamTailHeight = clamp(o.beamTailHeight, 0.0D, 4.0D);
            }

            if (o.beamHeadColor != null) {
               o.beamHeadColor = formatColor(parseColor(o.beamHeadColor, 16774102));
            }

            if (o.beamTailColor != null) {
               o.beamTailColor = formatColor(parseColor(o.beamTailColor, 16738906));
            }

            if (o.collisionHeadExtra != null) {
               o.collisionHeadExtra = clamp(o.collisionHeadExtra, 0.0D, 4.0D);
            }

            if (o.collisionTailExtra != null) {
               o.collisionTailExtra = clamp(o.collisionTailExtra, 0.0D, 4.0D);
            }

            if (o.beamConeAlpha != null) {
               o.beamConeAlpha = clamp(o.beamConeAlpha, 0.02D, 0.5D);
            }

            if (o.beamConeSteps != null) {
               o.beamConeSteps = clamp(o.beamConeSteps, 4, 16);
            }

            if (o.rainSprayMinSpeedKmh != null) {
               o.rainSprayMinSpeedKmh = clamp(o.rainSprayMinSpeedKmh, 0.0D, 200.0D);
            }

            if (o.trainCollisionDamageMinSpeedKmh != null) {
               o.trainCollisionDamageMinSpeedKmh = clamp(o.trainCollisionDamageMinSpeedKmh, 0.0D, 300.0D);
            }

            if (o.trainCollisionDamageMax != null) {
               o.trainCollisionDamageMax = clamp(o.trainCollisionDamageMax, 0.0D, 200.0D);
            }

         });
         this.sidingSettings.values().removeIf(HeadlightConfig.SidingSettings::isEmpty);
      }

      this.headLuminance = clamp(this.headLuminance, 0, 15);
      this.tailLuminance = clamp(this.tailLuminance, 0, 15);
      this.headLength = clamp(this.headLength, 0.0D, 32.0D);
      this.tailLength = clamp(this.tailLength, 0.0D, 32.0D);
      this.maxDistance = clamp(this.maxDistance, 16.0D, 1024.0D);
      this.massClearRatio = clamp(this.massClearRatio, 0.2D, 1.0D);
      this.beamDistance = clamp(this.beamDistance, 16.0D, 512.0D);
      this.beamHeadLength = clamp(this.beamHeadLength, 0.0D, 64.0D);
      this.beamTailLength = clamp(this.beamTailLength, 0.0D, 64.0D);
      this.beamBlockLightRadius = clamp(this.beamBlockLightRadius, 4, 32);
      this.beamWidth = clamp(this.beamWidth, 0.5D, 8.0D);
      this.beamHeadHeight = clamp(this.beamHeadHeight, 0.0D, 4.0D);
      this.beamTailHeight = clamp(this.beamTailHeight, 0.0D, 4.0D);
      this.beamLift = clamp(this.beamLift, 0.0D, 0.5D);
      this.beamAlpha = clamp(this.beamAlpha, 0.0D, 1.0D);
      this.scatterStrength = clamp(this.scatterStrength, 0.1D, 3.0D);
      this.scatterRadius = clamp(this.scatterRadius, 1.0D, 24.0D);
      this.beamConeAlpha = clamp(this.beamConeAlpha, 0.02D, 0.5D);
      this.rainSprayMinSpeedKmh = clamp(this.rainSprayMinSpeedKmh, 0, 200);
      this.trainCollisionHeight = clamp(this.trainCollisionHeight, 1.0D, 8.0D);
      this.trainCollisionEndPad = clamp(this.trainCollisionEndPad, 0.0D, 3.0D);
      this.collisionHeadExtra = clampEndExtra(this.collisionHeadExtra);
      this.collisionTailExtra = clampEndExtra(this.collisionTailExtra);
      this.roofDropMinSpeedKmh = clamp(this.roofDropMinSpeedKmh, 0, 300);
      this.roofDropMaxRadiusM = clamp(this.roofDropMaxRadiusM, 10.0D, 1000.0D);
      this.trainCollisionPushOnlyMaxKmh = clamp(this.trainCollisionPushOnlyMaxKmh, 0, 300);
      this.trainCollisionLowDamageMaxKmh = Math.max(this.trainCollisionPushOnlyMaxKmh, clamp(this.trainCollisionLowDamageMaxKmh, 0, 300));
      this.trainCollisionHighDamageMaxKmh = Math.max(this.trainCollisionLowDamageMaxKmh, clamp(this.trainCollisionHighDamageMaxKmh, 0, 300));
      this.trainCollisionLaunchMaxKmh = Math.max(this.trainCollisionHighDamageMaxKmh, clamp(this.trainCollisionLaunchMaxKmh, 0, 400));
      this.trainCollisionDamageBasePerSecond = clamp(this.trainCollisionDamageBasePerSecond, 0.0D, 20.0D);
      this.trainCollisionDamageExpK = clamp(this.trainCollisionDamageExpK, 0.0D, 8.0D);
      this.trainCollisionDamageSpeedScaleKmh = clamp(this.trainCollisionDamageSpeedScaleKmh, 10.0D, 1000.0D);
      this.trainCollisionFlyDamageScale = clamp(this.trainCollisionFlyDamageScale, 1.0D, 10.0D);
      this.trainCollisionDamageMax = clamp(this.trainCollisionDamageMax, 0.0D, 200.0D);
      this.knockbackDistance = clamp(this.knockbackDistance, 1.0D, 80.0D);
      this.knockbackExpK = clamp(this.knockbackExpK, 0.0D, 3.0D);
      this.knockbackMax = clamp(this.knockbackMax, 1.0D, 200.0D);
      this.trainCollisionLaunchHorizontal = clamp(this.trainCollisionLaunchHorizontal, 0.0D, 8.0D);
      this.trainCollisionLaunchVertical = clamp(this.trainCollisionLaunchVertical, 0.0D, 4.0D);
      this.trainCollisionFlyHorizontal = clamp(this.trainCollisionFlyHorizontal, 0.0D, 12.0D);
      this.trainCollisionFlyVertical = clamp(this.trainCollisionFlyVertical, 0.0D, 6.0D);
      this.trainCollisionImpactCooldownTicks = clamp(this.trainCollisionImpactCooldownTicks, 1, 200);
      this.trainCollisionHurtTicks = clamp(this.trainCollisionHurtTicks, 40, 600);
      this.trackbedThickness = clamp(this.trackbedThickness, 0.0D, 1.0D);
      this.parkedLightsOffTicks = clamp(this.parkedLightsOffTicks, 0, 24000);
      this.beamHeadColor = formatColor(parseColor(this.beamHeadColor, 16774102));
      this.beamTailColor = formatColor(parseColor(this.beamTailColor, 16738906));
      this.headlightMode = normalizeHeadlightMode(this.headlightMode);
      this.beamConeSource = normalizeConeSource(this.beamConeSource);
      return this;
   }

   private void save(Path path) {
      try {
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
      } catch (IOException var3) {
         LteLog.LOGGER.warn("Could not write {}", path, var3);
      }

   }

   public void save() {
      this.save(FabricLoader.getInstance().getConfigDir().resolve("lte.json"));
   }

   public String toJson() {
      return GSON.toJson(this);
   }

   public static HeadlightConfig fromJson(String json) {
      try {
         HeadlightConfig cfg = (HeadlightConfig)GSON.fromJson(json, HeadlightConfig.class);
         if (cfg != null) {
            return cfg.sanitized();
         }
      } catch (RuntimeException var2) {
         LteLog.LOGGER.warn("Could not read a config snapshot - keeping the live one", var2);
      }

      return new HeadlightConfig();
   }

   private static int clamp(int v, int lo, int hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   private static double clamp(double v, double lo, double hi) {
      return Math.max(lo, Math.min(hi, v));
   }

   public static final class Effective {
      public final boolean enabled;
      public final boolean dynamicLight;
      public final double maxDistance;
      public final int headLuminance;
      public final int tailLuminance;
      public final double headLength;
      public final double tailLength;
      public final boolean beamBand;
      public final boolean beamFlare;
      public final double beamDistance;
      public final double beamHeadLength;
      public final double beamTailLength;
      public final double beamWidth;
      public final int beamHeadColor;
      public final int beamTailColor;
      public final double beamHeadHeight;
      public final double beamTailHeight;
      public final double collisionHeadExtra;
      public final double collisionTailExtra;
      public final boolean beamCone;
      public final double beamConeAlpha;
      public final boolean beamConeOcclusion;
      public final int beamConeSteps;
      public final boolean rainSpray;
      public final double rainSprayMinSpeedKmh;
      public final boolean rainHush;
      public final boolean trainCollisionDamage;
      public final double trainCollisionDamageMinSpeedKmh;
      public final double trainCollisionDamageMax;
      public final long sidingId;
      public final boolean overridden;

      Effective(boolean enabled, boolean dynamicLight, double maxDistance, int headLuminance, int tailLuminance, double headLength, double tailLength, boolean beamBand, boolean beamFlare, double beamDistance, double beamHeadLength, double beamTailLength, double beamWidth, int beamHeadColor, int beamTailColor, double beamHeadHeight, double beamTailHeight, double collisionHeadExtra, double collisionTailExtra, boolean beamCone, double beamConeAlpha, boolean beamConeOcclusion, int beamConeSteps, boolean rainSpray, double rainSprayMinSpeedKmh, boolean rainHush, boolean trainCollisionDamage, double trainCollisionDamageMinSpeedKmh, double trainCollisionDamageMax, long sidingId, boolean overridden) {
         this.enabled = enabled;
         this.dynamicLight = dynamicLight;
         this.maxDistance = maxDistance;
         this.headLuminance = headLuminance;
         this.tailLuminance = tailLuminance;
         this.headLength = headLength;
         this.tailLength = tailLength;
         this.beamBand = beamBand;
         this.beamFlare = beamFlare;
         this.beamDistance = beamDistance;
         this.beamHeadLength = beamHeadLength;
         this.beamTailLength = beamTailLength;
         this.beamWidth = beamWidth;
         this.beamHeadColor = beamHeadColor;
         this.beamTailColor = beamTailColor;
         this.beamHeadHeight = beamHeadHeight;
         this.beamTailHeight = beamTailHeight;
         this.collisionHeadExtra = collisionHeadExtra;
         this.collisionTailExtra = collisionTailExtra;
         this.beamCone = beamCone;
         this.beamConeAlpha = beamConeAlpha;
         this.beamConeOcclusion = beamConeOcclusion;
         this.beamConeSteps = beamConeSteps;
         this.rainSpray = rainSpray;
         this.rainSprayMinSpeedKmh = rainSprayMinSpeedKmh;
         this.rainHush = rainHush;
         this.trainCollisionDamage = trainCollisionDamage;
         this.trainCollisionDamageMinSpeedKmh = trainCollisionDamageMinSpeedKmh;
         this.trainCollisionDamageMax = trainCollisionDamageMax;
         this.sidingId = sidingId;
         this.overridden = overridden;
      }
   }

   public static final class SidingSettings {
      public Boolean enabled;
      public Boolean dynamicLight;
      public Double maxDistance;
      public Integer headLuminance;
      public Integer tailLuminance;
      public Double headLength;
      public Double tailLength;
      public Boolean beamBand;
      /** @deprecated */
      @Deprecated
      public Boolean beamGlow;
      public Boolean beamFlare;
      public Double beamDistance;
      public Double beamHeadLength;
      public Double beamTailLength;
      public Double beamWidth;
      public String beamHeadColor;
      public String beamTailColor;
      public Double beamHeadHeight;
      public Double beamTailHeight;
      public Double collisionHeadExtra;
      public Double collisionTailExtra;
      public Boolean beamCone;
      public Double beamConeAlpha;
      public Boolean beamConeOcclusion;
      public Integer beamConeSteps;
      public Boolean rainSpray;
      public Double rainSprayMinSpeedKmh;
      public Boolean rainHush;
      public Boolean trainCollisionDamage;
      public Double trainCollisionDamageMinSpeedKmh;
      public Double trainCollisionDamageMax;

      public boolean isEmpty() {
         return this.enabled == null && this.dynamicLight == null && this.maxDistance == null && this.headLuminance == null && this.tailLuminance == null && this.headLength == null && this.tailLength == null && this.beamBand == null && this.beamGlow == null && this.beamFlare == null && this.beamDistance == null && this.beamHeadLength == null && this.beamTailLength == null && this.beamWidth == null && this.beamHeadColor == null && this.beamTailColor == null && this.beamHeadHeight == null && this.beamTailHeight == null && this.collisionHeadExtra == null && this.collisionTailExtra == null && this.beamCone == null && this.beamConeAlpha == null && this.beamConeOcclusion == null && this.beamConeSteps == null && this.rainSpray == null && this.rainSprayMinSpeedKmh == null && this.rainHush == null && this.trainCollisionDamage == null && this.trainCollisionDamageMinSpeedKmh == null && this.trainCollisionDamageMax == null;
      }
   }
}

#!/usr/bin/env node
/**
 * 生成 Forge 侧 mixin refmap（named/mojmap → searge）。
 *
 * 为什么不靠注解处理器：ForgeGradle 6.0.24 没有 `mixin { add ... }` 扩展（MixinGradle 是独立插件），
 * 而我们需要映射的成员只有 6 个。手写一张小表比引一套插件链更可控，而且运行期行为完全一样：
 * 生产环境 Mixin 读 refmap 的 `searge` 段，把源码里的 mojmap 选择器换成 SRG 名。
 *
 * 表里的 SRG 名来自 `forge-1.20.1-47.4.0-srg.jar` 的 javap（官方类名 + SRG 成员名），
 * 逐个核对过描述符。改选择器时同步改这张表，然后重跑本脚本。
 *
 * 用法：node tools/gen-forge-refmap.js   → forge/src/main/resources/lte.forge.refmap.json
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'forge/src/main/resources/lte.forge.refmap.json');

// mixin 类（内部名） → { 源码选择器: SRG 选择器 }
const TABLE = {
  'dev/js/lte/mixin/beamlight/BeamLightMixin': {
    // LevelRenderer#getLightColor(BlockAndTintGetter, BlockState, BlockPos)I
    'getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I':
      'Lnet/minecraft/client/renderer/LevelRenderer;m_109537_(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I',
  },
  'dev/js/lte/mixin/collision/MixinEntityMove': {
    // Entity#move(MoverType, Vec3)V（三个注入点共用同一条）
    'move': 'Lnet/minecraft/world/entity/Entity;m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V',
  },
  'dev/js/lte/mixin/rain/WorldRendererRainMixin': {
    // LevelRenderer#renderSnowAndRain(LightTexture, float, double, double, double)V
    'renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V':
      'Lnet/minecraft/client/renderer/LevelRenderer;m_109703_(Lnet/minecraft/client/renderer/LightTexture;FDDD)V',
    // LevelRenderer#tickRain(Camera)V
    'tickRain(Lnet/minecraft/client/Camera;)V':
      'Lnet/minecraft/client/renderer/LevelRenderer;m_109693_(Lnet/minecraft/client/Camera;)V',
  },
  'dev/js/lte/mixin/scatter/GameRendererScatterMixin': {
    // GameRenderer#renderLevel(float, long, PoseStack)V
    'renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V':
      'Lnet/minecraft/client/renderer/GameRenderer;m_109089_(FJLcom/mojang/blaze3d/vertex/PoseStack;)V',
  },
  'dev/js/lte/mixin/registry/SimpleRegistryFreezeMixin': {
    // MappedRegistry#freeze()Lnet/minecraft/core/Registry;
    'freeze': 'Lnet/minecraft/core/MappedRegistry;m_203521_()Lnet/minecraft/core/Registry;',
  },
};

const refmap = {
  mappings: TABLE,
  data: {
    // Forge 1.20.1 生产环境：类名 official、成员 SRG ⇒ 走 searge 段
    searge: TABLE,
  },
};

fs.writeFileSync(OUT, JSON.stringify(refmap, null, 2) + '\n', 'utf8');
const classes = Object.keys(TABLE).length;
const entries = Object.values(TABLE).reduce((n, m) => n + Object.keys(m).length, 0);
console.log(`已写出 ${path.relative(ROOT, OUT).replace(/\\/g, '/')}（${classes} 个 mixin，${entries} 条选择器）`);

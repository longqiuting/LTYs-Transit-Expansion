#!/usr/bin/env node
/**
 * Fabric → Forge 平台层批量改写（可重跑、幂等）—— LTE 版。
 *
 * 只做「调用平台 API 的那几行」的机械替换，业务逻辑一行不动：
 *   1. 导入 / 全限定名：net.fabricmc.* → dev.js.lte.forge.platform.*（shim 与 Fabric
 *      同名同形状，所以 lambda / 强转 / 方法名都不用改）。
 *   2. 注册：Registry.register(BuiltInRegistries.X, id, value) → Reg.item/tab/particle(id, value)。
 *   3. 创造页签：FabricItemGroup.builder() → CreativeModeTab.builder()；
 *      反编译残留 entries.method_45421( → entries.add(。
 *   4. 客户端指令：FabricClientCommandSource → CommandSourceStack；
 *      ClientCommandManager.* → Commands.*；sendFeedback/sendError → sendSystemMessage/sendFailure。
 *   5. 反编译丢类型：CompletableFuture.completedFuture((Object)null) → (Void)null。
 *
 * java-gen 是生成物（tools/remap-to-mojmap.sh 会覆盖），所以改写全部放在这里。
 *
 * 用法：
 *   node tools/fix-java-gen-platform.js             # 应用
 *   node tools/fix-java-gen-platform.js --dry-run   # 只报会改什么
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const GEN = path.join(ROOT, 'forge/src/main/java-gen');
const DRY = process.argv.includes('--dry-run');
const PLATFORM = 'dev.js.lte.forge.platform.';

const changed = [];

/** Fabric 全限定名 → shim 全限定名（整串替换，import 与内联 FQ 一起处理）。 */
const IMPORT_MAP = [
  ['net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource', 'net.minecraft.commands.CommandSourceStack'],
  ['net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback', PLATFORM + 'ClientCommandRegistrationCallback'],
  ['net.fabricmc.fabric.api.client.command.v2.ClientCommandManager', PLATFORM + 'ClientCommandManager'],
  ['net.fabricmc.loader.api.FabricLoader', PLATFORM + 'FabricLoader'],
  ['net.fabricmc.api.EnvType', PLATFORM + 'EnvType'],
  ['net.fabricmc.api.Environment', PLATFORM + 'Environment'],
  ['net.fabricmc.api.ClientModInitializer', PLATFORM + 'ClientModInitializer'],
  ['net.fabricmc.api.ModInitializer', PLATFORM + 'ModInitializer'],
  ['net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents', PLATFORM + 'ClientLifecycleEvents'],
  ['net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents', PLATFORM + 'ClientTickEvents'],
  ['net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents', PLATFORM + 'ServerLifecycleEvents'],
  ['net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents', PLATFORM + 'ServerTickEvents'],
  ['net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext', PLATFORM + 'WorldRenderContext'],
  ['net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents', PLATFORM + 'WorldRenderEvents'],
  ['net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents', PLATFORM + 'ClientPlayConnectionEvents'],
  ['net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking', PLATFORM + 'ClientPlayNetworking'],
  ['net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents', PLATFORM + 'ServerPlayConnectionEvents'],
  ['net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking', PLATFORM + 'ServerPlayNetworking'],
  ['net.fabricmc.fabric.api.networking.v1.PacketByteBufs', PLATFORM + 'PacketByteBufs'],
  ['net.fabricmc.fabric.api.networking.v1.PlayerLookup', PLATFORM + 'PlayerLookup'],
  ['net.fabricmc.fabric.api.resource.ResourceManagerHelper', PLATFORM + 'ResourceManagerHelper'],
  ['net.fabricmc.fabric.api.resource.SimpleResourceReloadListener', PLATFORM + 'SimpleResourceReloadListener'],
  ['net.fabricmc.fabric.api.particle.v1.FabricParticleTypes', PLATFORM + 'FabricParticleTypes'],
  ['net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry', PLATFORM + 'ParticleFactoryRegistry'],
  ['net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper', PLATFORM + 'KeyBindingHelper'],
  ['net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents', PLATFORM + 'ItemGroupEvents'],
  ['net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup', PLATFORM + 'FabricItemGroup'],
  ['net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents', PLATFORM + 'PlayerBlockBreakEvents'],
  ['net.fabricmc.fabric.api.event.player.AttackBlockCallback', PLATFORM + 'AttackBlockCallback'],
  ['net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback', PLATFORM + 'CommandRegistrationCallback'],
];

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.java')) out.push(p);
  }
}

function ensureImport(src, fqcn) {
  const line = `import ${fqcn};`;
  if (src.includes(line)) return src;
  // 插到第一条 import 之前（保持 Java 习惯；编译不关心顺序）
  const idx = src.indexOf('\nimport ');
  if (idx < 0) return src;
  return src.slice(0, idx + 1) + line + '\n' + src.slice(idx + 1);
}

function rewrite(src) {
  let out = src;
  for (const [from, to] of IMPORT_MAP) {
    out = out.split(from).join(to);
  }
  // 注册调用
  out = out.split('Registry.register(BuiltInRegistries.ITEM,').join('Reg.item(');
  out = out.split('Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,').join('Reg.tab(');
  out = out.split('Registry.register(BuiltInRegistries.PARTICLE_TYPE,').join('Reg.particle(');
  // 创建页签
  out = out.split('FabricItemGroup.builder()').join('CreativeModeTab.builder()');
  out = out.split('.method_45421(').join('.add(');
  // 客户端指令：简单名 FabricClientCommandSource → CommandSourceStack（导入已在上面整串替换）
  out = out.split('FabricClientCommandSource').join('CommandSourceStack');
  out = out.split('ClientCommandManager.literal(').join('net.minecraft.commands.Commands.literal(');
  out = out.split('ClientCommandManager.argument(').join('net.minecraft.commands.Commands.argument(');
  out = out.split('.sendFeedback(').join('.sendSystemMessage(');
  out = out.split('.sendError(').join('.sendFailure(');
  // 反编译丢类型
  out = out.split('CompletableFuture.completedFuture((Object)null)').join('CompletableFuture.completedFuture((Void)null)');

  if (out.includes('Reg.item(') || out.includes('Reg.tab(') || out.includes('Reg.particle(')) {
    out = ensureImport(out, 'dev.js.lte.forge.platform.Reg');
  }
  if (out.includes('CommandSourceStack')) {
    out = ensureImport(out, 'net.minecraft.commands.CommandSourceStack');
  }
  return out;
}

const files = [];
walk(GEN, files);

// Fabric 专属、Forge 无等价物的类：整文件删除（ModMenu 在 Forge 侧不存在；Forge 的配置入口
// 是按键 + 模组列表的 ConfigScreenHandler 扩展点，本模组的 UI 不依赖 ModMenu）。
const DEAD = ['dev/js/lte/ModMenuIntegration.java'];
for (const rel of DEAD) {
  const file = path.join(GEN, rel);
  if (fs.existsSync(file)) {
    if (!DRY) fs.unlinkSync(file);
    changed.push(rel + '  (Fabric-only → 删除)');
  }
}

let touched = 0;
for (const file of files) {
  if (!fs.existsSync(file)) continue;
  const src = fs.readFileSync(file, 'utf8');
  const out = rewrite(src);
  if (out === src) continue;
  touched++;
  const rel = path.relative(GEN, file).replace(/\\/g, '/');
  changed.push(rel);
  if (!DRY) fs.writeFileSync(file, out, 'utf8');
}

console.log(`${DRY ? '[dry-run] 将改写' : '已改写'} ${touched} 个文件（平台层 shim）`);
if (process.env.LTE_VERBOSE) {
  for (const c of changed) console.log('  - ' + c);
}

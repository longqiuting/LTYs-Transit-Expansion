#!/usr/bin/env node
/**
 * 机械修复器（可重跑、幂等）—— LTE 版。
 *
 * 只做「反编译/重映射产物的机械瑕疵」修复，不改业务逻辑、不改方法行为：
 *   1. 表格修复：丢 cast / 类型推断 / 重映射残留 / decompiler 的 label-block 构造器。
 *   2. 结构性修复：switch 的 case 体加块作用域（反编译把不同 case 的局部变量摊平到同一作用域）。
 *   3. 结构性修复：重复局部变量 —— 取 javac 报错行，找到最内层 `{}`，只在该块内改名 + 改引用。
 *
 * java-gen 是生成物（tools/remap-to-mojmap.sh 会覆盖），所以修复全部放在这里，
 * 每次重跑管线后重跑本脚本即可复原同样的修复结果。
 *
 * 用法：
 *   node tools/fix-java-gen.js             # 应用全部修复（幂等）
 *   node tools/fix-java-gen.js --dry-run   # 只报将要改什么
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const GEN = path.join(ROOT, 'forge/src/main/java-gen');
const LOG = path.join(ROOT, '.remap-work/m4b3-javac.log');
const DRY = process.argv.includes('--dry-run');

const changed = [];
function read(rel) {
  return fs.readFileSync(path.join(GEN, rel), 'utf8');
}
function write(rel, text, why) {
  if (DRY) { changed.push(`${rel}  (${why})`); return; }
  fs.writeFileSync(path.join(GEN, rel), text, 'utf8');
  changed.push(`${rel}  (${why})`);
}

/* ---------------- 1. 表格修复 ---------------- */
// decompiler 把 `new X;` 与后面的 `<init>(...)` 拆开，中间夹着 label 块。
// 语义等价改法：删掉裸 `new X;`，把 `<init>(` 改成 `new X(`（同一变量被重新赋值）。
const LABEL_CTOR = [
  { rel: 'dev/js/mtrheadlight/HeadlightConfig.java', cls: 'HeadlightConfig.Effective' },
];

const TABLE = [
  // --- 反编译丢泛型 / 类型推断（补显式 cast 或类型参数，语义不变） ---
  { rel: 'dev/js/mtrheadlight/CustomPack.java', from: '.orElse((Object)null)', to: '.orElse((String)null)', why: 'Optional<String>.orElse 参数被擦成 Object' },
  { rel: 'dev/js/mtrheadlight/BeamPackWriter.java', from: '.orElse((Object)null)', to: '.orElse((Path)null)', why: 'Optional<Path>.orElse 参数被擦成 Object' },
  { rel: 'dev/js/mtrheadlight/BeamPackWriter.java', from: 'new ArrayList(((VehicleLightOverrides.Vehicle)result.vehicles.get(0)).beams)', to: 'new ArrayList<VehicleLightOverrides.Beam>(((VehicleLightOverrides.Vehicle)result.vehicles.get(0)).beams)', why: '裸 ArrayList 使三元式退化成 List<Object>' },
  { rel: 'dev/js/mtrheadlight/bind/MtrTiles.java', re: /new LinkedHashSet\(\(Set\)value\)/g, to: 'new LinkedHashSet<Long>((Set)value)', why: 'Set<Object> → Set<Long>：补类型参数（两处）' },
  { rel: 'dev/js/mtrheadlight/VehicleLightOverrides.java', from: 'TreeMap var9 = new TreeMap();', to: 'TreeMap<String, List<VehicleLightOverrides.Beam>> var9 = new TreeMap<>();', why: '裸 TreeMap 让 keySet()/entrySet() 退化成 Object' },
  { rel: 'dev/js/mtrheadlight/VehicleLightOverrides.java', from: 'auto = new LinkedHashMap();', to: 'auto = new LinkedHashMap<String, List<VehicleLightOverrides.Beam>>();', why: '同上：auto 需要 String→List<Beam> 的类型' },
  { rel: 'dev/js/mtrheadlight/collision/RailBed.java', re: /\(\(Map\)(railsMap|endsRaw|innerRaw)\)/g, to: '((Map<?, ?>)$1)', why: '裸 Map 的 entrySet 元素是 Object，换成 Map<?,?> 后才是 Entry<?,?>' },
  { rel: 'dev/js/mtrheadlight/DebugProbe.java', from: 'new ArrayList(ClientData.TRAINS)', to: 'new ArrayList<TrainClient>(ClientData.TRAINS)', why: '裸 ArrayList 让 for 变量退化成 Object' },
  { rel: 'dev/js/mtrheadlight/ShaderPackProbe.java', from: 'optifineActiveProgram.get((Object)null) != 0', to: '((Number)optifineActiveProgram.get((Object)null)).intValue() != 0', why: 'Field#get 返回 Object，不能直接与 int 比较' },
  { rel: 'dev/js/mtrheadlight/sss/ColorCopy.java', from: '         return (boolean)target;\n', to: '         return false;\n', why: '走到这行时 target 一定是 0（tex == -1）→ 原意是 false' },
  { rel: 'dev/js/mtrheadlight/signal/FangsuSignalSync.java', from: 'private static void applyAspect(Map<?, ?> raw, int aspect)', to: 'private static void applyAspect(Map<String, Object> raw, int aspect)', why: 'Map<?,?> 不能 put' },
  { rel: 'dev/js/mtrheadlight/signal/FangsuTrafficLightMode.java', from: 'private static void acceptRaw(Consumer<?> consumer, int value)', to: 'private static void acceptRaw(Consumer<Object> consumer, int value)', why: 'Consumer<?> 不能 accept(int)' },
  { rel: 'dev/js/mtrheadlight/signal/FangsuTrafficLightMode.java', from: 'Map<?, ?> map = (Map)var4;', to: 'Map<Object, Object> map = (Map)var4;', why: 'Map<?,?> 不能 put' },
  { rel: 'dev/js/mtrheadlight/ProtoBeamCone.java', from: '      int var10002 = GATE[index]++;\n      var10002 = WC[index]++;\n', to: '      GATE[index]++;\n      WC[index]++;\n', why: '计数器自增的返回值被塞进 int（GATE 是 long[]）；返回值本来就没用' },
  { rel: 'dev/js/mtrheadlight/signal/FangsuSignalSync.java', from: 'Map<?, ?> map = (Map)var9;', to: 'Map<String, Object> map = (Map)var9;', why: 'applyAspect 的实参也要是 Map<String,Object>' },
  { rel: 'dev/js/mtrheadlight/signal/FangsuTrafficLightMode.java', from: 'Consumer<?> c = (Consumer)consumer;', to: 'Consumer<Object> c = (Consumer)consumer;', why: 'acceptRaw 现在吃 Consumer<Object>' },
  { rel: 'dev/js/mtrheadlight/BeamEditor3DScreen.java', from: 'rows.sort(Comparator.comparing((row) -> !row.inUse).thenComparing((row) -> row.id, String.CASE_INSENSITIVE_ORDER));', to: 'rows.sort(Comparator.comparing((BeamEditor3DScreen.VehicleRow row) -> !row.inUse).thenComparing((BeamEditor3DScreen.VehicleRow row) -> row.id, String.CASE_INSENSITIVE_ORDER));', why: 'lambda 形参类型被擦成 Object' },
  { rel: 'dev/js/mtrheadlight/BeamEditor3DScreen.java', from: 'for(int axis = 0; axis < 3; ++axis) {\n            this.transformButtons.add((Button)this.addRenderableWidget(Button.builder(tr("beam3d.transform.flip", BeamGizmo.nameOf(axis)), (b) -> this.flipAxis(axis))', to: 'for(int axis = 0; axis < 3; ++axis) {\n            int axisIdx = axis;\n            this.transformButtons.add((Button)this.addRenderableWidget(Button.builder(tr("beam3d.transform.flip", BeamGizmo.nameOf(axis)), (b) -> this.flipAxis(axisIdx))', why: 'lambda 捕获 for 变量（非 effectively final）→ 每次迭代取一份 final 副本' },
  { rel: 'dev/js/mtrheadlight/BeamEditor3DScreen.java', from: 'BeamGizmo.nameOf(axis)), (b) -> this.rotateAxis90(axis))', to: 'BeamGizmo.nameOf(axis)), (b) -> this.rotateAxis90(axisIdx))', why: '同上（rot90 那个 lambda）' },

  // --- 重映射残留：单字母 obf 方法名没被改名（Yarn 名在映射表里对不上） ---
  { rel: 'dev/js/mtrheadlight/bind/DisplayPushTicker.java', re: /\.F\(\)/g, to: '.getAllLevels()', why: 'MinecraftServer#F() → getAllLevels()' },
  { rel: 'dev/js/mtrheadlight/collision/CollisionSystem.java', re: /\.ac\(\)/g, to: '.getPlayerList()', why: 'MinecraftServer#ac() → getPlayerList()' },
  { rel: 'dev/js/mtrheadlight/collision/MobCollision.java', re: /\.ag\(\)/g, to: '.getTickCount()', why: 'MinecraftServer#ag() → getTickCount()' },
  { rel: 'dev/js/mtrheadlight/collision/MobCollision.java', re: /\.F\(\)/g, to: '.getAllLevels()', why: 'MinecraftServer#F() → getAllLevels()' },
  { rel: 'dev/js/mtrheadlight/Commands.java', re: /\.ac\(\)/g, to: '.getPlayerList()', why: 'MinecraftServer#ac() → getPlayerList()' },
  { rel: 'dev/js/mtrheadlight/server/ServerCollision.java', re: /\.F\(\)/g, to: '.getAllLevels()', why: 'MinecraftServer#F() → getAllLevels()' },
  { rel: 'dev/js/mtrheadlight/server/ServerCollision.java', re: /\.ag\(\)/g, to: '.getTickCount()', why: 'MinecraftServer#ag() → getTickCount()' },
  { rel: 'dev/js/mtrheadlight/server/ServerCollision.java', re: /\.l\(\)/g, to: '.isDedicatedServer()', why: 'MinecraftServer#l() → isDedicatedServer()' },
  { rel: 'dev/js/mtrheadlight/VehicleLightPacks.java', re: /\.a\(LevelResource\.DATAPACK_DIR\)/g, to: '.getWorldPath(LevelResource.DATAPACK_DIR)', why: 'MinecraftServer#a(LevelResource) → getWorldPath(LevelResource)' },

  // --- mixin 选择器：yarn 名 → mojmap 名（Forge 侧由 refmap 再映射到 SRG） ---
  { rel: 'dev/js/mtrheadlight/mixin/beamlight/BeamLightMixin.java', from: 'getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I', to: 'getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I', why: 'LevelRenderer: getLightmapCoordinates → getLightColor（mojmap）' },
  { rel: 'dev/js/mtrheadlight/mixin/rain/WorldRendererRainMixin.java', from: 'renderWeather(Lnet/minecraft/client/render/LightmapTextureManager;FDDD)V', to: 'renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V', why: 'LevelRenderer: renderWeather → renderSnowAndRain（mojmap）' },
  { rel: 'dev/js/mtrheadlight/mixin/rain/WorldRendererRainMixin.java', from: 'tickRainSplashing(Lnet/minecraft/client/render/Camera;)V', to: 'tickRain(Lnet/minecraft/client/Camera;)V', why: 'LevelRenderer: tickRainSplashing → tickRain（mojmap）' },
  { rel: 'dev/js/mtrheadlight/mixin/scatter/GameRendererScatterMixin.java', from: 'renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V', to: 'renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V', why: 'GameRenderer: renderWorld → renderLevel（mojmap）' },
  { rel: 'dev/js/mtrheadlight/mixin/fangsu/FangsuTurnoutRenderMixin.java', from: 'Lcom/fangsu/blockEntities/client/BaseBlockEntityRender;render(Lcom/fangsu/blockEntities/BaseObjBlockEntity;FLnet/minecraft/class_4587;Lnet/minecraft/class_4597;II)V', to: 'Lcom/fangsu/blockEntities/client/BaseBlockEntityRender;render(Lcom/fangsu/blockEntities/BaseObjBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V', why: '@At target 里的 intermediary 名 → mojmap（PoseStack / MultiBufferSource）' },
  { rel: 'dev/js/mtrheadlight/mixin/fangsu/FangsuTurnoutRenderMixin.java', from: '@Mixin(\n   targets = {"com.fangsu.blocks.client.TurnoutBlockEntityRenderer"}\n)', to: '@Mixin(\n   targets = {"com.fangsu.blocks.client.TurnoutBlockEntityRenderer"},\n   remap = false\n)', why: '可选宿主（方速）内部名不做 refmap 重映射' },
  // MTR 侧：ServerCommandSource#sendFeedback(Supplier, boolean) 是 mojmap 名，不用改。
];

/** Mixin 类里 `(Target)this` 的强转：javac 不认「@Mixin 目标 = 隐式父类」，
 *  Forge 侧没有 Loom 那个 javac 插件，必须绕一层 Object。 */
const MIXIN_SELF_CAST = [
  { rel: 'dev/js/mtrheadlight/mixin/collision/MixinEntityMove.java', type: 'Entity' },
  { rel: 'dev/js/mtrheadlight/mixin/mtrserver/RailwayDataCaptureMixin.java', type: 'RailwayData' },
  { rel: 'dev/js/mtrheadlight/mixin/mtrserver/TrainSimulateCaptureMixin.java', type: 'TrainServer' },
  { rel: 'dev/js/mtrheadlight/mixin/registry/SimpleRegistryFreezeMixin.java', type: 'Registry' },
];

/* ---------------- 源码遮罩（字符串/注释 → 空白），用于精确的括号匹配 ---------------- */
function mask(src) {
  const out = src.split('');
  let i = 0, n = src.length, state = 0; // 0 normal 1 line 2 block 3 str 4 char
  while (i < n) {
    const c = src[i], d = src[i + 1];
    if (state === 0) {
      if (c === '/' && d === '/') { state = 1; out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
      if (c === '/' && d === '*') { state = 2; out[i] = ' '; out[i + 1] = ' '; i += 2; continue; }
      if (c === '"') { state = 3; out[i] = ' '; i++; continue; }
      if (c === "'") { state = 4; out[i] = ' '; i++; continue; }
      i++; continue;
    }
    if (state === 1) { if (c === '\n') { state = 0; } else out[i] = ' '; i++; continue; }
    if (state === 2) { if (c === '*' && d === '/') { out[i] = ' '; out[i + 1] = ' '; i += 2; state = 0; continue; } if (c !== '\n') out[i] = ' '; i++; continue; }
    if (state === 3) { if (c === '\\') { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; } if (c === '"') { out[i] = ' '; state = 0; } else if (c !== '\n') out[i] = ' '; i++; continue; }
    if (state === 4) { if (c === '\\') { out[i] = ' '; out[i + 1] = ' '; i += 2; continue; } if (c === "'") { out[i] = ' '; state = 0; } else if (c !== '\n') out[i] = ' '; i++; continue; }
  }
  return out.join('');
}

const lineStarts = (s) => { const a = [0]; for (let i = 0; i < s.length; i++) if (s[i] === '\n') a.push(i + 1); return a; };

/* ---------------- 2. switch case 体加块作用域 ---------------- */
function wrapSwitchCase(src, m, switchOpen) {
  let depth = 0, switchClose = -1;
  for (let i = switchOpen; i < m.length; i++) {
    if (m[i] === '{') depth++;
    else if (m[i] === '}') { depth--; if (depth === 0) { switchClose = i; break; } }
  }
  if (switchClose < 0) return src;
  const labels = [];
  let d = 0;
  for (let i = switchOpen + 1; i < switchClose; i++) {
    const ch = m[i];
    if (ch === '{') d++;
    else if (ch === '}') d--;
    else if (d === 0) {
      const ls = src.lastIndexOf('\n', i) + 1;
      if (!/^[ \t]*$/.test(m.slice(ls, i))) continue;
      if (!/^(case\b|default\b)/.test(m.slice(i, i + 8))) continue;
      let j = i;
      while (j < switchClose && m[j] !== ':' && m[j] !== '{') j++;
      if (j >= switchClose || m[j] !== ':') continue;
      labels.push({ lineStart: ls, colon: j });
      i = j;
    }
  }
  if (labels.length === 0) return src;
  if (labels.every(l => /^[ \t\r\n]*\{/.test(src.slice(l.colon + 1, l.colon + 40)))) return src;

  const eol = src.includes('\r\n') ? '\r\n' : '\n';
  const closeLineStart = (() => {
    const ls = src.lastIndexOf('\n', switchClose) + 1;
    return /^[ \t]*$/.test(src.slice(ls, switchClose)) ? ls : switchClose;
  })();
  let out = '', cursor = switchOpen + 1;
  for (let k = 0; k < labels.length; k++) {
    const end = k + 1 < labels.length ? labels[k + 1].lineStart : closeLineStart;
    const indent = (m.slice(labels[k].lineStart).match(/^[ \t]*/) || [''])[0];
    const body = src.slice(labels[k].colon + 1, end).replace(/\s+$/, '');
    out += src.slice(cursor, labels[k].colon + 1) + ' {' + eol + body + eol + indent + '}' + eol;
    cursor = end;
  }
  out += src.slice(cursor, switchClose);
  return src.slice(0, switchOpen + 1) + out + src.slice(switchClose);
}

/* ---------------- 3. 重复局部变量：最内层块内改名 ---------------- */
function fixDupVar(rel, errLine) {
  const src = read(rel);
  const m = mask(src);
  const starts = lineStarts(src);
  const lineStart = starts[errLine - 1];
  const lineEnd = errLine < starts.length ? starts[errLine] : src.length;
  const text = m.slice(lineStart, lineEnd);
  const dm = text.match(/\b([A-Za-z_$][\w$]*)\s*=/);
  if (!dm) return null;
  const name = dm[1];
  if (name.endsWith('Lte')) return null;
  const eq = text.indexOf('=');
  const declPos = lineStart + text.lastIndexOf(name, eq);
  const pre = text.slice(0, declPos - lineStart);
  if (!/^\s*(?:final\s+)?[A-Za-z_$][\w$.<>\[\], ?]*\s+$/.test(pre)) return null;

  let depth = 0, open = -1;
  for (let i = declPos; i >= 0; i--) {
    if (m[i] === '}') depth++;
    else if (m[i] === '{') { if (depth === 0) { open = i; break; } depth--; }
  }
  if (open < 0) return null;

  const preText = m.slice(0, open);
  const lastSwitch = preText.lastIndexOf('switch');
  const lastBoundary = Math.max(preText.lastIndexOf('{'), preText.lastIndexOf(';'), preText.lastIndexOf('}'));
  if (lastSwitch > lastBoundary) {
    write(rel, wrapSwitchCase(src, m, open), `switch case 局部变量 ${name} 摊平 → case 体加块作用域`);
    return 'switch';
  }

  depth = 0; let close = -1;
  for (let i = open; i < m.length; i++) {
    if (m[i] === '{') depth++;
    else if (m[i] === '}') { depth--; if (depth === 0) { close = i; break; } }
  }
  if (close < 0) return null;

  let newName = name + 'Lte';
  while (new RegExp(`\\b${newName}\\b`).test(m)) newName += '2';

  const seg = src.slice(declPos, close);
  const re = new RegExp(`(?<![\\w$.])${name}(?![\\w$(])`, 'g');
  const nseg = seg.replace(re, newName);
  if (nseg === seg) return null;
  write(rel, src.slice(0, declPos) + nseg + src.slice(close), `重复局部变量 ${name} → ${newName}（限最内层块）`);
  return 'rename';
}

/* ---------------- 主流程 ---------------- */
for (const e of TABLE) {
  let s = read(e.rel);
  if (e.from) {
    // java-gen 是 CRLF：字面量里的 \n 要按文件实际换行补齐，否则匹配不到。
    const variants = s.includes('\r\n')
      ? [[e.from, e.to], [e.from.replace(/\n/g, '\r\n'), e.to.replace(/\n/g, '\r\n')]]
      : [[e.from, e.to]];
    let hit = false;
    for (const [from, to] of variants) {
      if (!s.includes(from)) continue;
      s = s.split(from).join(to);
      hit = true;
    }
    if (hit) write(e.rel, s, e.why);
  } else if (e.re) {
    const before = s;
    s = s.replace(e.re, e.to);
    if (s !== before) write(e.rel, s, e.why);
  }
}

// Mixin 自身强转：`(Entity)this` → `(Entity)(Object)this`（javac 不认 @Mixin 的隐式父类）
for (const e of MIXIN_SELF_CAST) {
  const s = read(e.rel);
  const needle = `(${e.type})this`;
  if (!s.includes(needle)) continue;
  write(e.rel, s.split(needle).join(`(${e.type})(Object)this`), `mixin 自身强转 ${needle} → (${e.type})(Object)this`);
}

// decompiler 的 label-block 构造器：`var10000 = new X;` + 后面的 `var10000.<init>(...)`
for (const e of LABEL_CTOR) {
  const s = read(e.rel);
  const bare = new RegExp(`\\n[ \\t]*var10000 = new ${e.cls.replace(/\./g, '\\.')};\\r?\\n`);
  if (!bare.test(s) && !new RegExp(`var10000\\.<init>\\(`).test(s)) continue;
  let t = s.replace(bare, '\n');
  t = t.replace(new RegExp(`\\bvar10000\\.<init>\\(`, 'g'), `var10000 = new ${e.cls}(`);
  if (t !== s) write(e.rel, t, 'label 块拆开的构造器 → 删裸 new、<init> 改直接赋值');
}

if (fs.existsSync(LOG)) {
  const raw = fs.readFileSync(LOG, 'utf8').replace(/\r/g, '');
  const hits = [];
  const re = /^(.*?\.java):(\d+): (?:错误|error): 已在方法.*中定义了变量 (\w+)$/;
  for (const line of raw.split('\n')) {
    const mm = line.match(re);
    if (!mm) continue;
    const abs = mm[1].replace(/\\/g, '/');
    const idx = abs.indexOf('/forge/src/main/java-gen/');
    if (idx < 0) continue;
    hits.push({ rel: abs.slice(idx + '/forge/src/main/java-gen/'.length), line: +mm[2] });
  }
  hits.sort((a, b) => (a.rel === b.rel ? b.line - a.line : a.rel.localeCompare(b.rel)));
  const done = new Set();
  for (const h of hits) {
    if (done.has(h.rel)) continue;
    if (fixDupVar(h.rel, h.line)) done.add(h.rel);
  }
}

if (changed.length === 0) {
  console.log('无需修改（已幂等）');
} else {
  console.log(`${DRY ? '[dry-run] 将修改' : '已修改'} ${changed.length} 处：`);
  for (const c of changed) console.log('  - ' + c);
}

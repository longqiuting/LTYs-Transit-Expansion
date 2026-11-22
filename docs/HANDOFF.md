# HANDOFF —— 明天从这里继续

> 最后更新 2026-11-23（M4b-12：LTE 包根改名 `dev.js.lte`，两个模组已能在 Forge 共存）。两个仓库都在本地已提交、**未 push**。


## 一句话状态

| 目标 | 状态 |
|---|---|
| LTED（装饰，`mtrlte`）Fabric | ✅ 已产出 `LTYs-Transit-Expansion-Decoration-3.0.0.jar`，已装实例 |
| LTED Forge | ✅ **服务端 + 客户端双通**（`Done (2.004s)`、172 方块、7 个 mixin apply、客户端到主菜单） |
| LTE（优化+连接器，`lte`）Fabric | ✅ `LTYs-Transit-Expansion-3.0.0.jar`，已装实例 |
| LTE Forge | ✅ **服务端 + 客户端双通**（`Done (5.263s)` / 客户端到主菜单，见下「M4b-10 验收」） |
| Forge 单 jar（Forgix） | ✅ `merge/build/libs/LTYs-Transit-Expansion-3.0.0.jar`（1,562,133 B / 587 条目），Forge 服务端实机通过 |
| LTE + LTED 共存（M4b-12 包根改名后） | ✅ Forge 四 jar `Done (2.431s)` / Fabric 四 jar `Done (2.455s)`，见下「M4b-12」 |
| M5 仓库拆分（改名/新建仓/tag/release/README） | ❌ 未开始 |

## LTE Forge / 单 jar 现状（M4b-10 已完成）

`D:\MC-MTR-JS\lte`，HEAD `1ee5cbf`（M4b-10 的验收改动已在其后提交）。Forge 侧 0 编译错误，
`./forge/gradlew -p forge build --offline` 绿；`bash tools/merge-single-jar.sh` 一条命令产出单 jar。

### M4b-10 验收结果（2026-11-23）

| 项 | 结果 | 证据 |
|---|---|---|
| 服务端 mixin | ✅ | debug.log `Mixing TrainSimulateCaptureMixin from lte.forge.mtrserver.mixins.json into mtr.data.TrainServer`、`Mixing RailwayDataCaptureMixin ... into mtr.data.RailwayData` |
| 服务端启动 | ✅ | latest.log `Done (5.263s)`；`ERROR]` 0 行、`Registry is already frozen` 0、`NoClassDefFoundError` 0 |
| 「没捕获到列车」 | 正常 | 空世界无列车，`no MTR train was captured` 是 WARN，不判失败 |
| 客户端 mixin | ✅ | 9 条 `Mixing .* from lte.forge.*`，含 `TrainClientRegistryMixin ... into mtr.client.TrainClientRegistry`、`RenderSignalBaseMixin ... into mtr.render.RenderSignalBase`；`Mixin apply failed` 0、`ERROR]` 0 |
| 单 jar 结构 | ✅ | 同时含 `fabric.mod.json` 与 `META-INF/mods.toml`；`assets/` 只有根一份（24 条 `assets/lte`）；`unzip -Z1 \| uniq -d` 空；连跑两次条目数 587 一致、条目列表逐行相同 |
| 单 jar 实机 | ✅ | 单 jar 单独进 `forge-test/mods`（仅 MTR + architectury）：`Done (2.800s)`、0 ERROR、`Mixing ... from forge-lte.forge.mtrserver.mixins.json` |

### 根因（已实机否证旧结论，别再按旧的改）

旧结论「Forge 的 per-mod module classloader 看不到 MTR 条目 → `HostProbe.present()` 全假 → 整份
`lte.forge.mtrserver.mixins.json` 静默不应用」**不成立**：

- 早于 `MixinProbe` 存在的运行（11-22 23:34/23:35/23:37）里 `Mixing RailwayDataCaptureMixin ... into mtr.data.RailwayData` 已经出现；
- 客户端 `Mixing TrainClientRegistryMixin ... into mtr.client.TrainClientRegistry` 也是 `HostProbe.mtrTrainRegistry()` 放行的 ——
  说明 `ClassLoader.getResource("mtr/.../*.class")` 在 Forge 1.20.1 上**能**看到别的 mod 的条目。

真正发生的事：**空世界里 `mtr.data.TrainServer` 根本不会被加载**，Mixin 没有宿主类可注入，所以既不打印
`Mixing ... into mtr.data.TrainServer`，也永远不会捕获到列车 —— 这在日志里和「plugin 判定失败」完全同形。

现在的处理：`forge/.../platform/MixinProbe.java` 在 `FMLCommonSetupEvent` 里对 `TrainServer` / `RailwayData`
做 `Class.forName(name, false, loader)`（**只加载、不初始化**）并打印 `applied / NOT applied`，让宿主提前就位、
结论确定可读。`lte.mixins.json` 的 `HostProbe` 探测逻辑未改（Fabric 侧本来就对）。

**仍然禁止**：`Class.forName` 带初始化地探测宿主（会提前冻住 MTR 类，历史上害过 EntityCulling）。

## 环境速查

- 构建（Fabric，JDK 21）：`"/d/MC-MTR-JS/temp/gradle/gradle-8.14/bin/gradle" -p v1.20.1 build --no-daemon`
- 构建（Forge，JDK 17）：`./forge/gradlew -p forge clean build --offline`
- Forge 服务端：`D:\MC-MTR-JS\forge-test`（`win_args.txt`，**不要**用 `unix_args.txt`）
- Forge 客户端：`D:\MC-MTR-JS\forge-client`（`run-client.sh`，assets 是 PCL 的 junction，别改 PCL 目录）
- 实例 `mods/`：只放 `LTYs-Transit-Expansion-3.0.0.jar` 与 `LTYs-Transit-Expansion-Decoration-3.0.0.jar`
- 旧版归档：`D:\MC-MTR-JS\archive\lte-2.0.2\`

### M4b-10 复现命令（都是实测过的）

```bash
# 服务端（mods/ 只放 MTR + architectury + lte-forge；LTED_JAR 指到不存在的路径以免脚本又拷回来）
cd /d/MC-MTR-JS/forge-test && (sleep 50; echo stop) | LTED_JAR=/nonexistent bash run-test-server.sh
grep -aE "Mixing .*mtrserver|Done \(|mixin probe" logs/debug.log logs/latest.log

# 客户端（mods/ 只放 MTR + architectury + lte，到主菜单后 Ctrl-C）
cd /d/MC-MTR-JS/forge-client && bash run-client.sh
grep -aE "Mixing .* from lte\.forge|Mixin apply failed" logs/debug.log

# 单 jar（三步：Fabric → Forge → Forgix；只重跑合并用 SKIP_BUILD=1）
cd /d/MC-MTR-JS/lte && bash tools/merge-single-jar.sh
unzip -Z1 merge/build/libs/LTYs-Transit-Expansion-3.0.0.jar | wc -l    # 587
```

mixin 的 `Mixing ... into ...` 只在 `debug.log`（DEBUG 级），`latest.log` 里看不到。

## M4b-12：包根改名（两个模组在 Forge 共存）

问题：LTE（`lte`）与 LTED（`mtrlte`）共用包根 `dev.js.mtrheadlight`，Forge 用 JPMS，
两个 mod 模块导出同一包（Forgix 前缀后是 `fabric.dev.js.mtrheadlight.*`）直接
`ResolutionException: Modules lte and mtrlte export package ... to module minecraft`，
`forge/` 树里还有 17 个同名 class。LTED 保持不动，**LTE 的包根改成 `dev.js.lte`**。

改动（脚本一次跑完，`git mv` 4 个目录 + 633 处文本替换，共 333 个文件）：

- `common/`、`v1.20.1/src/main/java/`、`forge/src/main/java/`、`forge/src/main/java-gen/` 的
  `dev/js/mtrheadlight/**` → `dev/js/lte/**`，`package` / `import` 同步；
- 8+4 份 `*.mixins.json` 的 `package` / `plugin`、`fabric.mod.json` entrypoints（Forgix 之后再前缀成
  `fabric.dev.js.lte.*`）、`forge-lte.forge.refmap.json` 的 key、`tools/fix-java-gen-platform.js`、
  `tools/verify-decompiled.sh`、两侧 `maven_group`、`merge/build.gradle` 的 `group`。
- 构建产物仍是 `LTYs-Transit-Expansion-3.0.0.jar`（1,562,133 B / 587 条目）。
- 混入函数名前缀 `mtrheadlight$…` 与系统属性 `mtrheadlight.*` 是运行期名字，**没动**（与包名无关）。

| 验收项 | 结果 | 证据 |
|---|---|---|
| Fabric `clean build` | ✅ `BUILD SUCCESSFUL`，234 class | `temp/clean-fabric.log` |
| Forge `./forge/gradlew -p forge clean build --offline` | ✅ `BUILD SUCCESSFUL`，309 class | `temp/clean-forge.log` |
| Forge 四 jar 共存（MTR+architectury+LTE+LTED） | ✅ `Done (2.431s)`、`{lte}` 与 `{mtrlte}` 都在、`ERROR` 0 行、`ResolutionException`/`exports package` 0 | `forge-test/console-m4b12-coexist.txt` |
| 两个模组的 mixin 都生效 | ✅ `Mixing BlockPlatformDoorTypeMixin from forge-mtrlte.forge.mixins.json` + `Mixing RailwayDataCaptureMixin / TrainSimulateCaptureMixin from forge-lte.forge.mtrserver.mixins.json` | `forge-test/logs/debug.log` |
| Fabric 四 jar 共存 | ✅ `Done (2.455s)`，`- lte 3.0.0` / `- mtrlte 3.0.0`；11 行 `No data fixer registered for …` 是 MTR 既有噪声（改名前同数） | `fabric-test-lted/console-m4b12-coexist.txt` |
| 跨 jar class 交集 | ✅ `comm -12` = 0（LTE 543 / LTED 687 个 class） | — |
| 旧包根残留 | ✅ `git ls-files \| grep -v docs/` 0 命中；工作树（除 `docs/`、`.remap-work/`）0 命中 | — |

复现：

```bash
cd /d/MC-MTR-JS/lte && bash tools/merge-single-jar.sh          # 三步：Fabric→Forge→Forgix
cd /d/MC-MTR-JS/forge-test && (sleep 85; echo stop) | bash run-test-server.sh > console-m4b12-coexist.txt 2>&1
cd /d/MC-MTR-JS/fabric-test-lted && (sleep 70; echo stop) | "C:/Program Files/Java/jdk-17/bin/java.exe" -Xmx2G -jar fabric-server-launch.jar nogui > console-m4b12-coexist.txt 2>&1
```

两边 `mods/` 现在都是共存状态（MTR + architectury + LTE + LTED）。

## M4b-13：LTE 补回仪表板物品（`lte:dashboard`）+ 测试环境双 jar

拆分时把「打开配置界面的入口物品」落在 LTED，LTE 只剩界面、没有物品能打开它。已补回：

- 新类 `v1.20.1/src/main/java/dev/js/lte/bind/LteDashboardItem.java`（`dev.js.lte.bind`）：
  服务端 `PASS`；客户端反射 `dev.js.lte.MtrHeadlightClient#openConfigScreen()`（与 `/mtr_lte config`、
  键位同一个入口，反射是为了 dedicated server 上没有那个类也不炸）。
- `signal/LteCoreItems`：`DASHBOARD_ID = lte:dashboard`，注册 + `entries.add`，
  `TOOLS_TAB_ORDER` 最前（页签顺序：仪表板 → 灯光编辑器 → 三个连接器）。
- 资源：`assets/lte/models/item/dashboard.json`（`layer0: lte:item/dashboard`）、
  `assets/lte/textures/item/dashboard.png`（LTED 仓库 `654fa91^` 的 `lte_dashboard.png` 原图，32×32，
  3838 B，未缩放）、`lang` 中英 `item.lte.dashboard` + 两条 tooltip。
- Forge 侧：`tools/remap-to-mojmap.sh` → `tools/fix-java-gen.sh` → `forge/gradlew -p forge clean build`
  → `SKIP_BUILD=1 tools/merge-single-jar.sh`。

**踩过的坑**：`tools/remap-to-mojmap.sh` 清 `.remap-work/plain` 时只删非 `.java`
（`find "$WORK/plain" -type f ! -name '*.java' -delete`），改名前的
`forge/src/main/java-gen/dev/js/mtrheadlight/**` 会被 `cp -r` 原样带回，javac 报 2 个假错。
改完包名后必须先 `rm -rf .remap-work forge/src/main/java-gen/dev/js/mtrheadlight` 再跑。

验收：Fabric/Forge `clean build` 均绿；单 jar 591 条目（587 + 3 资源 + 1 类），无重复条目，
`assets/lte/{models,textures}/item/dashboard.*` 与 `fabric/`、`forge/` 两棵树里的
`bind/LteDashboardItem.class` 都在。Forge 服务端 `Done (2.463s)`、0 ERROR，
`summon item ... {Item:{id:"lte:dashboard"}}` → `Summoned new LTE dashboard`，
`data get` 打出 `{id: "lte:dashboard", Count: 1b}`；对照组 `lte:no_such_item_xyz` → `Unknown item`。
Forge 客户端到主菜单：`Found valid mod file ... {lte}` + `{mtrlte}`，0 ERROR。
**创造页签里是否真的显示，需用户目视确认**（服务端无法证明页签渲染）。

遗留（不在本次范围）：LTED 自己的 `mtrlte:lted_dashboard` 模型引用
`mtrlte:item/lte_dashboard`，但那张贴图在 LTED 仓库 `654fa91` 被删了 —— 客户端报
`Missing textures in model mtrlte:lted_dashboard#inventory`。LTED 侧补图即可。
另有 Forge 客户端 `lte:spray` 粒子注册晚于 RegisterEvent（WARN，回落 `minecraft:cloud`）。

## 已知遗留（不影响明天主线）

- `Reg.*` 目前仍收已构造实例（靠「把 onInitialize 挪进首个 RegisterEvent」解决冻结），彻底干净需改收 Supplier：java-gen 103 处 / 26 文件，牵动静态字段取实例语义
- 旧存档进 Forge 版会有 `Object did not get ID it asked for` 告警（全新世界 0 条）
- LTE 的 `Net` 仍引用 `Minecraft`，专用服务端理论上应拆 `ClientNet`

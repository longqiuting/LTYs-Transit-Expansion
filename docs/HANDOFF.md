# HANDOFF —— 明天从这里继续

> 写于 2026-11-22 晚，暂停点。两个仓库都在本地已提交、**未 push**。

## 一句话状态

| 目标 | 状态 |
|---|---|
| LTED（装饰，`mtrlte`）Fabric | ✅ 已产出 `LTYs-Transit-Expansion-Decoration-3.0.0.jar`，已装实例 |
| LTED Forge | ✅ **服务端 + 客户端双通**（`Done (2.004s)`、172 方块、7 个 mixin apply、客户端到主菜单） |
| LTE（优化+连接器，`lte`）Fabric | ✅ `LTYs-Transit-Expansion-3.0.0.jar`，已装实例 |
| LTE Forge | ⏳ **差最后一步**（见下） |
| Forge 单 jar（Forgix） | LTED 已验证链路；LTE 的 `merge/` 刚建好，未产出/未验 |
| M5 仓库拆分（改名/新建仓/tag/release/README） | ❌ 未开始 |

## LTE Forge 停在哪

`D:\MC-MTR-JS\lte`，HEAD `0190b5d`。Forge 侧 0 编译错误，`./forge/gradlew -p forge build --offline` 绿。

刚做完的改动（尚未实机验证）：

- 新增 `forge/src/main/java/dev/js/mtrheadlight/forge/platform/MixinProbe.java` —— 用 Forge 的 `ModList`/`ModFile` 按**条目名**探测宿主 mod（取代 `ClassLoader.getResource`，后者在 Forge 的 SecureJar module classloader 下必然失败）
- `LteForge.java`、`lte.forge.mixins.json`、`collision/CollisionSystem.java`、`tools/merge-single-jar.sh` 有对应调整
- `merge/`（Forgix 1.2.9）已建，未产出

### 根因（别重查）

`MtrServerMixinPlugin.shouldApplyMixin()` → `HostProbe.mtrServerTrain()` → `present("mtr.data.TrainServer")` 用
`ClassLoader.getResource("mtr/data/TrainServer.class")` 探测。Forge 每个 mod 独立 module classloader，
看不到 MTR 的条目 → 判定「MTR 不在」→ **整份 `lte.forge.mtrserver.mixins.json` 静默不应用** →
`TrainSimulateCaptureMixin` 从未加载 → 服务端日志 `no MTR train was captured after 400 ticks`。
Fabric 是同一 flat classpath，所以只有 Forge 出问题。

**注意**：不能改用 `Class.forName` 探测（会提前冻住 MTR 类，历史上害过 EntityCulling）。

### 明天要做的四件事

1. **验证 presence 修复**：起 `D:\MC-MTR-JS\forge-test` 服务端，验收日志出现
   `Mixing TrainSimulateCaptureMixin from lte.forge.mtrserver.mixins.json into mtr.data.TrainServer`
   和 `Mixing RailwayDataCaptureMixin ... into mtr.data.RailwayData`；空世界里「没捕获到列车」属正常，不是失败判据。
2. **客户端复验**：`D:\MC-MTR-JS\forge-client`，只放 lte（LTED 暂时移出），到主菜单 + `Mixing .* from lte.forge.mtr*` 有输出 + 0 个 `Mixin apply failed`。
3. **Forgix 单 jar**：产出 `merge/build/libs/LTYs-Transit-Expansion-3.0.0.jar`，要求同时含 `fabric.mod.json` 与 `META-INF/mods.toml`、`assets` 只在根一份、`unzip -Z1 | uniq -d` 为空。可照搬 `D:\MC-MTR-JS\mtr-headlight\merge\` 与 `tools/merge-single-jar.sh`。
4. **恢复测试环境**：`forge-test/mods/` 与 `forge-client/mods/` 恢复成 LTED + LTE 都在。

## 环境速查

- 构建（Fabric，JDK 21）：`"/d/MC-MTR-JS/temp/gradle/gradle-8.14/bin/gradle" -p v1.20.1 build --no-daemon`
- 构建（Forge，JDK 17）：`./forge/gradlew -p forge clean build --offline`
- Forge 服务端：`D:\MC-MTR-JS\forge-test`（`win_args.txt`，**不要**用 `unix_args.txt`）
- Forge 客户端：`D:\MC-MTR-JS\forge-client`（`run-client.sh`，assets 是 PCL 的 junction，别改 PCL 目录）
- 实例 `mods/`：只放 `LTYs-Transit-Expansion-3.0.0.jar` 与 `LTYs-Transit-Expansion-Decoration-3.0.0.jar`
- 旧版归档：`D:\MC-MTR-JS\archive\lte-2.0.2\`

## 已知遗留（不影响明天主线）

- `Reg.*` 目前仍收已构造实例（靠「把 onInitialize 挪进首个 RegisterEvent」解决冻结），彻底干净需改收 Supplier：java-gen 103 处 / 26 文件，牵动静态字段取实例语义
- 旧存档进 Forge 版会有 `Object did not get ID it asked for` 告警（全新世界 0 条）
- LTE 的 `Net` 仍引用 `Minecraft`，专用服务端理论上应拆 `ClientNet`

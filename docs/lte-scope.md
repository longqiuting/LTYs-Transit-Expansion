# LTE 仓库范围（拆分记录，2026-11-22）

> 源仓库 `D:\MC-MTR-JS\mtr-headlight`（分支 `split/decouple`，`3e83966`）**只读**；本仓库由它复制出
> 1.20.1 的「优化 + 连接器」部分。装饰内容留在 LTED（命名空间仍是 `mtrlte`）。

## 1. 保留

| 包 / 目录 | 文件 | 内容 |
|---|---:|---|
| `common/`（根） | 29 | 车灯 / 光束 / 散射几何（`ProtoBeamCone`、`BeamGlow`、`BeamRelight`、`BeamBlockLight`、`EndLight`、`ConeVolumeBeam`、`LightBackend`）、车灯绑定（`VehicleLights*`、`BuiltinLights`、`VehicleLightJson`）、灯控（`LightController`、`TrainLamps`、`TrainPose`、`HeadlightSwitch`、`DepartureState`）、天气与粒子（`RailWeather`、`RainHush`、`ParticleCull`、`ShaderPackProbe`、`DebugProbe`、`ClearDiag`）、配置 `HeadlightConfig`、`LteLog`、`MtrCompat`、`MtrHeadlightClient`、`ModMenuIntegration` |
| `common/{net,server,mixin,mtr}` | 6 | `net/LteChannel`、`net/ServerAuthority`、`server/MtrServerData`、`server/ServerConfig`、`mixin/HostProbe`、`mtr/BuiltInTrains` |
| `common/collision/` | 4 | `CarGeometry`、`RailBed`、`Trackbed`、`TrainTickCapture` |
| `common/bind/LteMtrLookup` | 1 | 连接器的反射门面（MTR 轨道 / 车站 / 站台） |
| `common/signal/SignalSourceCapture` | 1 | 信号灯状态快照（配合 `RenderSignalBaseMixin`） |
| `v1.20.1`（根） | 38 | `Beam*` 十件（3D 光束编辑器 / 打包）、`ConfigScreen` / `CategoryScreen` / `ParameterScreen` / `ConfigWriteTarget` / `ConfigCategory` / `ColorPickerScreen` / `ResetConfirmScreen`（配置 UI 框架）、`LightFlare`、`GlowCompat`、`LightBackends`、`LightTest`、`RenderHooks`、`RainSpray`、`SprayParticle`、`SprayType`、`TrainPreview`、`TrainNames`、`CustomPack`、`PackImportScreen`、`VehicleLightsJsonClient/Server`、`Commands`、`TabIcons`、`FangsuCompat`、`FangsuTurnoutLight`、`KeyHooks`、`LteClient`、`LteDebug` |
| `v1.20.1/collision/` | 8 | `CollisionSystem`、`CollisionImpact`、`CarBand`、`MobCollision`、`CollisionBootstrap`、`OnboardToolBypass`、`DriverKeyBypass`、`TrainImpactDamageSource` |
| `v1.20.1/sss/` | 5 | 屏幕空间散射（自编译 core shader） |
| `v1.20.1/server/` | 6 | `CarScan`、`LteServer`、`LteServerLights`、`RegistrySelfCheck`、`ServerCollision`、`ServerTrackbed` |
| `v1.20.1/bind/` | 12 | **连接器**：`BindTargets`、`BindUnbind`、`MtrTiles`、`PidsConnectorItem`、`PidsLinkStore`、`PidsStationIndex`、`PidsStationResolver`、`ProjectorConnectorItem`、`ProjectorLinkStore`、`DisplayPushTicker`、`LteBindCommand`、`BeamEditorItem` |
| `v1.20.1/signal/` | 10 | 信号连接器：`SignalLinkItem`、`SignalLinkNet`、`SignalLinkStore`、`SignalLinks`、`SignalLinkClient`、`ConnectorNodeHighlight`、`LteCoreItems`、`FangsuSignalCompat`、`FangsuSignalSync`、`FangsuTrafficLightMode` |
| `v1.20.1/mixin/` | 14 | A 侧 8 份配置：beamlight / collision / rain / scatter / registry / fangsu(2) / mtr(2+plugin) / mtrserver(2+plugin) |
| `v1.20.1/net/`、`v1.20.1/mtr/` | 3 | `ClientHandshake`、`ServerHandshake`、`BuiltInTrainsInit` |

资源：`assets/lte/{builtin_lights.json, shaders/core/*6, particles/spray.json, textures/{item×6, particle×3, vehicle/light_flare.png}, models/item/*5, lang/{en_us,zh_cn}.json}` —— 共 24 个文件。

## 2. 剔除

| 类别 | 内容 |
|---|---|
| 装饰包 | `xiamen/`（含 `pids/`、`fangsu/`、`dep/`、`sign/`、`ticket/`、`machine/`、`charger/`、`drinks/`、`eyecandy/`）、`clock/`、`decor/`、`railing/`、`security/`、`logo/`、`rawmodel/` |
| 装饰配置 | `decor/DecorConfig`（含 `lineColors` / `doorLightColor` / `doorLightByLine` 与旧键迁移） |
| 侧线浏览 | `SidingScreen`、`SidingMapScreen`、`SidingInfo`；`ConfigScreen` 的「侧线配置」行、`ParameterScreen.describe/stats`（`nameOf` 留给 `CategoryScreen` 的提示行） |
| 仪表板 | `bind/LteDashboardItem`、`decor/LtedDashboardScreen`、`bind/MtrBalance`（充电宝余额）、`PidsScheduleScope` + `PidsScheduleScopeMixin` |
| 连接器以外 | `DisplayPushTicker` 保留并改由 `LteServerLights` 每 20 tick 驱动（原先挂在装饰侧入口） |
| 入口 | `LtedClient`（14 个装饰客户端初始化器）、`server/LteServerDecor` |
| mixin | `mtr` 配置里的 PIDS / 线路图 / 屏蔽门贴图 / 站台门型 7 个类，`mtrserver` 的 `PidsScheduleScopeMixin` |
| 资源 | `assets/mtrlte` 全部方块 / 物品 / 贴图 / 模型 / 音效 / 广告 / eyecandy / 原版模型；`data/mtrlte`（loot table / recipe）；`assets/mtrsteamloco` |
| 其它 | `docs/`、`bench/`、`art/`、`temp/`、`tools/`、`.git`、`v1.18.2`、`v1.19.2`、`v1.19.4`、`_legacy-single` |

## 3. 改名

- `gradle.properties`：`mod_id=lte`、`mod_version=3.0.0`、`archives_base_name=lte`。
- `fabric.mod.json`：`id=${mod_id}`（`lte`）、`name=LTE`、8 份 mixin、`main` 2 个 / `client` 1 个入口、无 `icon`。
- 代码字面量 `mtrlte` → `lte`（221 行 / 53 文件）：`new Identifier(...)`、网络通道 id、粒子 / 声音 id、持久化文件名、配置文件名（`config/lte.json`）。
- `lang`：键前缀与内容 `mtrlte` → `lte`，894 键里只保留代码真正引用的 **318** 键。
- 包名仍是 `dev.js.mtrheadlight`（按约定不动）。

## 4. 连接器对 LTED 的依赖

**没有。** 三类连接器都指向 **MTR 自己的**方块实体：
`mtr.block.BlockArrivalProjectorBase$TileEntityArrivalProjectorBase`、`mtr.data.IPIDS$TileEntityPIDS`
（含天津地铁 / JCM 的子类，按运行时类名判定），通过 `bind/MtrTiles` 反射读写 `platformIds`。
所以拆分后连接器功能完整，不需要软探测降级。

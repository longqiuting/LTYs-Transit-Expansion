# LTE

MTR（Minecraft Transit Railway）的**优化与连接器**模组：只做对原版 MTR 的增强，不含装饰方块。

- mod id / 注册命名空间：**`lte`**
- 版本线：**3.x**（本仓库从 3.0.0 起）
- 当前支持：**1.20.1**（Fabric）

## 内容

| 分区 | 说明 |
|---|---|
| 车灯 / 光束 | 动态车头灯 / 车尾灯、可见光带、灯晕、屏幕空间散射（自编译 core shader）、方块重打光 |
| 车灯编辑器 | 3D 光束编辑器（`/mtr_lte beam3d`、`lte:beam_editor`）、车灯 JSON 资源包（`lte_vehicle_lights.json`） |
| 雨与粒子 | 雨中车灯水雾 / 溅水粒子、车内不画雨、粒子裁剪 |
| 碰撞与物理 | 列车 / 车厢碰撞、推开与撞飞、伤害与死亡来源、道床（`RailBed` / `Trackbed`）判定；服务端同时安装时由服务端结算 |
| 连接器 | 信号灯 → 轨道节点（`lte:signal_linker`）、投影机 → 车站（`lte:projector_connector`）、PIDS → 站台（`lte:pids_connector`），`/ltebind` 查询绑定 |
| 配置界面 | 由 Mod Menu 或 `/mtr_lte` 打开（光源、碰撞、粒子、伤害、其他）；旧的「仪表板」物品随装饰侧迁出 |

装饰内容（厦门风格屏蔽门、PIDS、屏蔽门 LCD、时钟、站牌、售票机、安检机、eyecandy 摆件…）**不在本仓库**，
它们在 LTED 仓库里继续维护，命名空间仍为 `mtrlte`（放弃改 id 会让存档里的方块变成未知方块）。

## 构建

```bash
JAVA_HOME="C:/Program Files/Java/jdk-21.0.10" gradle -p v1.20.1 build
# -> v1.20.1/build/libs/lte-3.0.0.jar
```

依赖：`libs/` 下的 MTR named jar、MTR-ANTE、Mod Menu（均为 `compileOnly`，运行期由玩家实例提供）。
`common/` 通过 `sourceSets` 直接并入 `v1.20.1` 的编译单元（不是独立模块）。

## 目录

```
v1.20.1/        # 1.20.1 构建（build.gradle / 资源 / 版本专属源码）
common/         # 跨版本共享源码（灯光 / 车灯 / 碰撞几何 / 天气 / 连接器头文件）
libs/           # 编译期依赖 jar（不发布）
```

## 许可

MIT（见 `LICENSE`）。第三方来源见 `NOTICE.md`。

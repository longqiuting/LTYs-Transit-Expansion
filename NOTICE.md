# 第三方来源与许可声明（NOTICE）

本仓库是 **LTE**（MTR 优化版）的独立工程，本体以 **MIT** 发布（见根目录 `LICENSE`）。

2026-11-22 从原项目（`longqiuting/mtr-external-lights`，仓库内置 id `lte`）拆分而来：
**优化与连接器**留在本仓库，**装饰内容**（厦门系列方块 / PIDS / 屏蔽门 LCD / 时钟 / 装饰物件）随 LTED 仓库继续维护，
其第三方素材（Nanbin Create / RCC / MSD / MTR PSD LCD 等）与相应许可文件也一并留在了 LTED。

---

## 1. Minecraft Transit Railway（MTR）

- 用途：本模组是 MTR 的**增强**，编译期以 MTR 的 named jar 为 `compileOnly` 依赖，运行期由玩家已安装的 MTR 提供
  （`fabric.mod.json` 声明 `depends.mtr`）。
- 未随包分发 MTR 的任何代码或资源：jar 内 `assets/` 只有本模组自有文件（shader、光束 / 水花贴图、粒子定义、语言文件）。
- `assets/lte/builtin_lights.json` 是**按 MTR 车型模型里 LIGHT 部件的坐标整理出的灯位表**（数值数据，非 MTR 文件拷贝）。
- 许可：**MIT**（上游 jonafanho/Minecraft-Transit-Railway）· 版权：Jonathan Ho 及 MTR 贡献者。

## 2. MTR-ANTE

- 用途：编译期 `compileOnly`（`RailRenderDispatcher#registerLightUpdate` 的 mixin 目标）；运行期由玩家已安装的 ANTE 提供。
- 未复制任何代码或资源。

## 3. 方速 MTR 扩展（Fangsu）

- 用途：`mixin/fangsu/*` 以**字符串类名**（`targets = "com.fangsu.…"`）做软注入，`FangsuMixinPlugin` 在未安装方速时整组跳过；
  另有反射调用其公开方法。编译期不依赖。
- 未复制任何代码或资源。

## 4. LambDynamicLights（LDL）

- 1.20.1 的构建**不再依赖** LDL（`LightBackends` 说明其已被本模组的方块光照挂钩取代）；
  源码注释里出现该名字仅用于说明历史与对比。
- 未复制任何代码或资源。

## 复核方法

```bash
# 随包分发的文件（应只有本模组自有资源）
unzip -l v1.20.1/build/libs/lte-*.jar
# 旧命名空间（拆分前仓库的 id）应为 0 命中
OLD=$(printf 'mtr%s' 'lte'); unzip -p v1.20.1/build/libs/lte-*.jar | grep -c "$OLD"
```

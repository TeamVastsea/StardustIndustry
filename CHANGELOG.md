# 更新日志 / Changelog

本项目每次推送都会在此记录变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。

## [0.1.0] - 2026-10-08

### 修复
- 修正仓库提交者身份：此前误用了一个临时的提交者名，现已统一为项目维护者账号的提交身份，并改写了历史提交的作者信息。

### 新增
- **真正的动态储罐多方块**：新增 `tank_frame`（12 条棱）、`tank_shell`（6 个面，其中一块承载方块实体，作为锚点与控制器）、`tank_glass`（透光面）。
- **`TankStructureProvider`**：从任意储罐方块洪水填充求出包围盒，校验棱=框架、面=外壳/玻璃/端口、内部必须为纯空气；出错时报告具体坐标。
- 储罐尺寸 3×3×3 ～ 9×9×9，容量 = 内部空气格 × 128 桶（3³ = 128 桶，9³ = 43904 桶），均可在配置中调整。
- 储罐**自动成型**，无需安装工具；安装工具仍可打开参数界面查看内容。
- **`FluidBufferModule` 支持动态容量**：容量由结构体积决定，并按端口等级限速（`EnergyTier.fluidTransfer()`）。
- `tank_shell` 直接暴露流体能力；LV 流体端口按等级限速（后续等级端口沿用同一模式）。
- **`TankMembershipRegistry` + `TankHudAccess`**：让没有方块实体的框架/玻璃也能反查到所属储罐，供安装工具与高亮模组使用。
- **`TankLiquidRenderer`**：以半透明流体盒渲染内部液位，透过玻璃可见（Tinkers' Smeltery 风格）。
- 配置项：`tankBucketsPerAirBlock`、`tankMinSize`、`tankMaxSize`。
- 自检新增储罐几何与容量校验。
- **高亮模组兼容（Jade / WTHIT / The One Probe）**：指向储罐**任意**方块（框架 / 外壳 / 玻璃），
  高亮栏都会显示尺寸、内部流体、存量与容量百分比。
  - `compat/hud/TankHudData`（只读数据快照）+ `TankHudLines`（三模组统一文案）。
  - `compat/jade/TankJadePlugin`（`@WailaPlugin` 发现）、`compat/wthit/TankWthitPlugin`
    （资源根 `waila_plugins.json` 发现）、`compat/top/TankTopPlugin`（IMC `getTheOneProbe`）。
  - 均为**软依赖**：不安装高亮模组时本模组照常运行；`build.gradle` 以 `compileOnly` + `localRuntime` 接入。

### 变更
- `EnergyTier` 新增 `fluidTransfer()`（流体端口吞吐率）。
- 中英文语言文件同步更新（方块名、结构提示、配置项）。

### 文档
- 新增 `docs/textures-placeholder-manual.md`：占位材质总清单，逐一标明每张贴图对应哪个方块、用途与建议美术方向，供美术替换。

### 移除
- 旧的演示储罐控制器 `tank` 与 `lv_frame` 方块，及其模型、方块状态、配方与语言条目。

### 验证
- `gradlew build --rerun-tasks` 通过（并已清空 configuration-cache 以复现真实编译）。
- `runServer` 到达 `Done`，common setup 自检通过，无缺失模型/材质告警。

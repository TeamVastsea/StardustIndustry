# 更新日志 / Changelog

本项目每次推送都会在此记录变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。

## [未发布]

### 新增
- **通用气体抽象（本模组自有，不依赖外部模组）**：`gas/Gas`（气体类型 + 色调）、
  `gas/GasStack`（气体 + 量，单位 **mB**，NBT 往返）、`gas/IGasHandler`（仿 `IFluidHandler`）、
  `gas/GasRegistry`（气体注册表）。内置氢/氧/氮/二氧化碳/蒸汽/天然气作占位。
- **`GasBufferModule`**：储罐/机器内的气体仓，暴露 `IGasHandler`，容量由结构体积决定。
- **`registry/ModCapabilityTypes`**：新增 `GAS_HANDLER`（`BlockCapability<IGasHandler, Void>`），
  仅由气体端口对外暴露。
- **`gas_tank_shell`（气体储罐外壳）与 `lv_gas_port`（LV 气体端口）**：含方块实体、分类、
  语言、模型、blockstate、配方、贴图（占位）。
- **`machine/tank/TankMedium`**：`FLUID` / `GAS` 介质枚举，决定外壳方块、缓冲模块与渲染。
- **`client/TankGasRenderer`**：气体**浓度**渲染（见下方「变更」）。
- 语言新增 `gas.stardustindustry.*`（内置气体名）与气体储罐相关键。
- **MekanismEx 独立附属模组（D7.11）**：气体端口现在可直接连接 Mekanism **加压管道**，
  且**全部 Mekanism 化学品**（气体、浆液、灌注物、颜料，含核材料/放射性气体）都能存进气罐。
  - 实现迁入 `StardustIndustry-MekanismEx`：`MekanismGasBridge`（化学品 ↔ 气体互转，ID 保留 `mekanism:` 命名空间）、
    `MekanismChemicalHandlerAdapter`（把本模组 `IGasHandler` 包成 Mek 的 `IChemicalHandler`）、
    `MekanismCompat`（能力注册 + 全量化学品扫描）。
  - 注册表冻结后一次性把全部化学品（实测 **65** 种）注册进 `GasRegistry`，另留惰性解析兑底。
  - Core 对 Mekanism 零依赖；MekanismEx 自身将 Core 与 Mekanism 声明为硬依赖。
  - `StardustIndustry-MekanismEx/build.gradle.kts` 以 `compileOnly` 编译 Mekanism API；Core 的
    `localRuntime` 仅用于统一开发客户端实机测试。
- **AE2Ex 独立附属模组骨架**：新增独立入口、模组 ID、元数据和发布 JAR，硬依赖 Core 与 AE2；
  当前没有 AE2 业务实现。

### 修复
- **跨区块多方块在区块卸载时误判解体（严重）**：多方块可横跨多个区块，而
  `Level.getBlockState` 对**未加载区块返回空气**。此前储罐每 20 tick 重扫整个包围盒，一旦
  储罐另一部分所在区块卸载（玩家走到视距边缘、或多人服务器里他人活动），未加载部分被当成
  空气 → 储罐被判未成型、**端口解绑**、管道断开，区块回来后又成型，**反复抖动**；
  更危险的是容量按结构体积计算，残缺包围盒可能导致**内容物被截断**。
  - **规则**：结构覆盖的区块没有全部加载时，**跳过本次求值、冻结现状**（不判失败、不解绑、
    不改容量、不广播），区块回来后自动恢复。
  - `StructureProvider` 新增 `footprint(controller, facing)`（默认 `null` = 无限制）；
    静态 provider 用模型精确几何，动态/储罐 provider 用 `reachChunks(controller, MAX_SIZE)` 预留最大范围。
  - 新增 `StructureChunkGuard`（`allLoaded` / `chunksOf` / `reachChunks`，用 `>> 4` 正确处理负坐标）。
  - `MachineBlockEntity.revalidate(...)` 与 `install()` 在求值/安装前检查，未全加载即冻结/拒绝。
  - 自检新增 `MultiblockSelfCheck.checkChunkFootprint()`，钉住几何（含负数坐标回归用例）。
  - **对静态与动态多方块一律生效**（不只是储罐）。
- **气体颜色显示错误（红石/颜料显示为蓝色，或暗到看不出颜色）**：两个原因叠加。
  1. **主因——采样了错误的贴图**：`RenderType.translucent()` 的着色器计算「纹理 × 顶点色」，
     而气体顶点 UV 此前写死 `(0,0)`，采样到方块图集左上角的**任意像素（偏蓝）**，于是
     **无论传什么 RGB 都被染成蓝色**。修复：新增 1×1 纯白方块贴图 `stardustindustry:block/gas_white`，
     气体采样它的 UV，`纹理 = 白`，颜色完全由色调决定。
  2. **次因——暗色调感知亮度低**：化学品原始色调过暗的（红石 `0xB30505`、碳 `0x2C2C2C`）
     融进阴影像空罐。`TankGasRenderer` 新增 `normalizeTint`，在 **HSL 空间**保持色相、把明度抬到
     0.62、饱和度抬到 0.55，近无彩色则给浅冷灰。红石修好后为鲜红、绿色颜料为鲜绿。
  - 颜色源与 Mekanism 一致：`Gas` 的颜色就是 `Chemical.getColorRepresentation()`（即 `getTint()`），
    与 Mek 自己的渲染用的是同一个值。
  - **保持浓度模型统一**：所有气体（含 Mek 的颜料、浆液等非气体化学品）一律按浓度渲染，
    最高透明度 **70%**，绝不完全不透明（否则看起来像一整块流体）。
- **气体名称不翻译（HUD / 参数屏显示 `gas.mekanism:redstone`）**：`Gas` 新增可选 `nameKey` 字段，
  Mekanism 桥接填入 `Chemical.getTranslationKey()`（即 `chemical.mekanism.redstone`），
  直接复用 Mekanism 自带语言文件，显示为「红石 / Redstone」等真实名称；无 `nameKey` 时回退到自建键。

### 变更
- **工程改为 Gradle Kotlin DSL 多项目构建**：根业务 `src/` 拆为 Core、UtilsEx、MekanismEx、
  AE2Ex 四个独立模块；每个模块拥有自己的 `src`、`@Mod` 入口、元数据和 JAR。根 `build` 汇总
  四个产物，根 `runClient` 只启动一个 Core 客户端并自动装入三个扩展 JAR。
- **第三方 API 与 Core 解耦**：JEI/Jade/WTHIT/TOP 迁入 UtilsEx，Mekanism 桥接迁入
  MekanismEx；Core 源码不再导入这些 API。UtilsEx 的工具模组依赖为可选，MekanismEx/AE2Ex
  的对应外部模组依赖为必需。
- **只有端口对外暴露能力**：储罐**外壳（墙）不再暴露任何能力**，两种介质一致。
  本次同时**移除了流体外壳原有的 `IFluidHandler` 注册**——此前流体储罐可被管道贴在罐壁上
  绕过端口速率，气体储罐则不能；现在两者都只能通过端口进出。
- **储罐重构为「同源双介质」**：抽出 `machine/tank/AbstractTankBlockEntity`（结构/锚点/容量/
  客户端同步/参数屏/成型广播，全部介质无关），`TankBlockEntity` 改为**流体**子类，
  新增 `GasTankBlockEntity`（**气体**子类，持 `GasBufferModule`）。两种储罐结构、规则、
  容量公式、参数屏**完全一致**，唯一区别是介质。
- **`TankShellBlock` 改为按介质构造**（`TankShellBlock(TankMedium, Properties)`），由介质决定 BE 类型。
- **`TankStructureProvider` 改为介质感知**：从锚点外壳读出介质，只接受同介质外壳与端口；
  `tank_frame` / `industrial_glass` 两种介质共用；混入异介质外壳视为搭建错误。
- **气体渲染与流体不同**：流体看**液面高度**；气体看**浓度**——气体始终充满内腔、
  **不随量改变高度**，空罐完全不渲染，随注入量颜色变浓，透明度最高到 **70%（alpha ≈ 0.70）**。
  `client/MachineRenderer` 按介质分派 `TankLiquidRenderer` / `TankGasRenderer`。
- **HUD 与参数屏介质化**：`TankHudData` / `TankHudLines` / TOP 插件识别两种外壳与气体端口，
  增加一行介质名；`MachineParamsScreen` 按介质显示「流体储罐 / 气体储罐」与
  「存储流体 / 存储气体」。
- **`PartRole` 新增 `PORT_GAS`**；`StructureMatcher.describe` 同步补充。
- **储罐部件命名正式化**（开发阶段一次改到位）：
  - `tank_shell` → **`fluid_tank_shell`**（流体储罐外壳）
  - `tank_glass` → **`industrial_glass`**（工业玻璃）
  - `tank_frame`（储罐框架）保持不变，**流体/气体储罐共用**。
  - 同步更新：注册 ID、Java 常量、语言键、方块/物品模型、blockstate、配方、贴图文件名、文档。

### 文档
- 新增 `docs/README.md` 文档索引与权威层级；新增 `docs/module-architecture.md`，记录模块职责、
  依赖图、构建/运行链路、安装组合和扩展准入规则。
- 重写 `docs/dev-environment.md`，覆盖跨平台首次构建、根任务、四 JAR 发布、联合客户端和验证矩阵；
  同步修正设计稿、术语与材质手册中的旧根 `src/` 路径和兼容层归属。
- **`docs/multiblock-design.md` 升级 v2.2**：新增 §15「气体子系统与同源储罐」；
  更新方块清单、目录结构、阶段表（D7.9 命名正式化、D7.10 气体储罐、D7.11 Mekanism 适配器）。
- **`docs/tank-multiblock-design.md` 升级 v1.1**：流体/气体**共用同一文档**；
  新增 §7「流体看液面，气体看浓度」、§16「气体子系统与 D7.10 落地清单」；
  术语表、方块清单、端口、参数屏、HUD 数据源全部介质化。
- `docs/terminology.md` 补充介质命名规则（`<medium>_tank_<part>` / 共用件无前缀）。

## [0.1.0] - 2026-10-08

### 修复
- 修正仓库提交者身份：此前误用了一个临时的提交者名，现已统一为项目维护者账号的提交身份，并改写了历史提交的作者信息。
- **修复 CI 构建失败**：高亮模组 jar 位于被忽略的 `libs/`，CI（Linux）检出后缺失导致编译失败。
  新增 `scripts/fetch-hud-libs.sh`（CI 用）与 `scripts/fetch-hud-libs.ps1`（本地用）在构建前自动下载，
  并在 `.github/workflows/build.yml` 接入；另加 `.gitattributes` 强制 `.sh` 用 LF 换行。
- **修复 Jade 启动报错**：Jade 会断言每个提供者都有配置翻译键，
  补上 `config.jade.plugin_stardustindustry.tank_contents` 与 `config.waila.plugin_stardustindustry.tank`
  （中英），避免启动时 `AssertionError` 并重置资源包。
- **修复放置储罐即崩溃**：`FluidBufferModule.save` 在流体为空时仍调用 `FluidStack.save`，
  而 NeoForge 1.21.1 禁止编码空 `FluidStack`，导致方块实体首次同步（`getUpdateTag`）时抛
  `IllegalStateException: Cannot encode empty FluidStack` 使整个世界 tick 崩溃。改为空流体时不写该键。
- **修复放置 LV 端口即崩溃**：`MachinePortBlock` 需要注册后「接线」方块实体类型，
  但 `wirePorts()` 只接了三种无等级端口，漏了 `lv_item_port`/`lv_fluid_port`/`lv_energy_port`
  （放置时 `newBlockEntity` 抛 `Port block used before its block entity type was wired`）。
  现在各端口类型共享同一方块实体类型并全部接线，类型构造器也纳入对应 LV 方块。
- **修复创造模式物品栏图标为空**：`MAIN_TAB` 的图标此前是 `ItemStack.EMPTY`（空框），
  改为储罐外壳。

### 变更
- **流体速率阶梯改为每级 ×4（按 tick）**：LV 5B/t、MV 20B/t、HV 80B/t、EHV 320B/t
  （即 5000 / 20000 / 80000 / 320000 mB/t）。数值直接存放在 `EnergyTier.fluidTransfer`
  字段，不再由电压推导。参数界面「最大传输速率」显示为 `LV 5B/t`（单位统一为 B/t）。
- **端口容器交互重写**（`PortFluidInteraction`）：
  - 转移**不再经过端口限流能力**，直接对接机器流体缓冲——端口限流的 5000 mB/t 是给管道每 tick
    用的，而手上一桶需要 1000 mB 一次性完成，限流会导致桶永远灌不满 / 倒不空。
  - **只在服务端执行**，客户端不做预测；拦截时同时设 `useItem=FALSE`/`useBlock=FALSE` 并取消事件，
    彻底阻止桶在世界里放水。
  - 数量**以手持容器自身能力为准**，支持大于 1 桶（如沉浸工程油桶）与小于 1 桶（如 500 mB 小瓶）。
- **创造模式调试交互**：创造玩家**潜行 + 右击**流体端口 = 直接注入 / 抽出一整容器量的流体，
  手上物品不消耗、不产出。
- **删除无等级端口**：移除 `item_port`/`fluid_port`/`energy_port` 三个无前缀端口
  （方块、物品、能力、方块实体、贴图、语言、创造栏均清理），
  只保留 `LV` 端口；后续 MV/HV/EHV 按同一模式扩展。
- **储罐参数界面**新增「已存储容量：100B」「剩余容量：412B」两行。
- **等级一律显示 LV/MV/HV/EHV**：`tier.stardustindustry.*` 中英均改为缩写
  （此前中文为「低压/中压/…」）。所有端口（含流体端口）统一用该模式。
- **流体端口支持手桶交互**：手持容器右击流体端口可灌入 / 取出流体。
- **流体端口也参与高亮显示**：Jade provider 改为注册在 `Block` 上（与 WTHIT 相同的做法）
  并在内部过滤「流体端口 / 框架 / 外壳 / 玻璃」，修复此前只对框架显示、对流体端口不显示的
  不一致问题。指向流体端口与指向框架显示同样的内容。
- **无等级端口配方清理**：删除 `item_port`/`fluid_port`/`energy_port` 三个配方，
  并重写 `lv_*_port` 配方使其不再以已删除的无等级端口为材料。

### 修复
- **钢化玻璃不透明**：`tank_glass` 模型未声明渲染层，被当作实心渲染，
  导致看不到内部液位。模型加上 `"render_type": "minecraft:translucent"`。
- **储罐液体一直不显示（根因）**：`FluidBufferModule.save` 把 `FluidStack.save` 的
  **返回值**丢弃，而 `FluidStack.save(provider, prefix)` 是**返回**编码后的 tag、
  **不写入**传入的 tag，导致服务端有液、客户端收到的 `Fluid` 子 tag 恒为空
  （`hasFluidKey=true` 但 `amount=0`）。改为 `tag.put("Fluid", stack.save(registries))`。
- **储罐液体发黑**：`TankLiquidRenderer` 原本用渲染器收到的**控制器格子**光照值，
  而控制器是外壳面板、储罐封闭后无天光无块光，光照接近 0 → 液体渲染为黑色；开夜视后片段
  被提亮才看似正常。改为采样**内腔中心**的 `LightLayer.SKY/BLOCK` 并设亮度下限。
- **储罐出现「两层流体」+ 大量「已成型」提示 + HUD 跳变（同一根因）**：每个 `tank_shell`
  方块都创建了 `TankBlockEntity`，整面外壳墙上的每个 shell 都独立成型：各自广播一次成型
  提示（9×9×9 有几十条）、各自渲染一份流体（一层恒满、一层跟随液位）、`TankHudAccess`
  每次可能查到不同锚点。修复：`TankStructureProvider` 用**确定性唯一锚点**（连通外壳中
  坐标最小的 `tank_shell`），其余 shell 评估一律未成型。
- **玻璃墙出现缝合网格**：玻璃 `noOcclusion`，相邻两块的重合面都被渲染，半透明叠加在每格
  边界形成亮缝。修复：`TankGlassBlock.skipRendering` 对同类玻璃邻居跳过该面。
- **储罐液体黑白条纹 / 颜色极淡**：`TankLiquidRenderer` 此前用一个面片把流体贴图 UV
  拉伸到整个面，采样越界到图集相邻像素（黑/白条纹），且透明度过低。改为**每格一个
  面片、每片贴一张完整贴图**，用 `RenderType.translucent()`、alpha 下限 `0.80`。
- **储罐液体被整体剔除**：`MachineRenderer` 未覆写 `getRenderBoundingBox`，默认只有
  控制器一格，控制器离开视锥时内部流体被剔除。现已扩大到整个机器。
- **WTHIT 与 Jade 同装崩溃**：开发环境 `localRuntime` 只保留 TOP；Jade / WTHIT
  仅 `compileOnly`。二者同时存在时 WTHIT 的 `IClientApiService` 服务加载冲突
  （`ServiceConfigurationError`），进入世界即崩。
- **水桶灌不进储罐**：水桶在自己的 `BucketItem.useOn` 里先把水倒进世界，
  抢在方块 `useItemOn` 之前。改由 `PlayerInteractEvent.RightClickBlock` 事件抢先拦截
  （`PortFluidInteraction`，显式取 `IFluidHandler` 能力后调用 `FluidUtil`），
  命中流体端口即转移流体并取消事件。
- **不同流体顶掉原流体**：端口手桶交互与 `MachinePortBlock.useItemOn` 原本是两套处理器，
  事件路径没转移成功时会落到第二套限流能力路径，造成看似顶替/缓慢。现在**只有一个手桶
  路径**（`PortFluidInteraction`），并在 `FluidBufferModule` 显式拒绝与已存流体不同的
  流体（管道 / 端口 / 调试均一致）。
- **流体变化不同步到客户端**：`FluidBufferModule` 灌入/抽出时只 `setChanged()`（仅存盘），
  导致 WTHIT 等读客户端缓存的显示仍为「空」。新增
  `MachineBlockEntity.markContentsChanged()`（`setChanged` + `sendBlockUpdated`）并在模块中调用。
- **WTHIT 插件加载报错**：`waila_plugins.json` 用了旧格式（顶层直接写 `initializer`），
  被 WTHIT 当作旧接口 `IWailaPlugin` 实例化而抛 `ClassCastException`。
  改为现代格式（`entrypoints.client`）。
- **动态结构不再画投影幽灵块**：储罐形状由玩家自定，没有唯一正确方块可作幽灵；
  `MachineBlockEntity.supportsProjection()` 默认 `true`，储罐覆写为 `false`，
  未成型时仍显示浮动文字提示。（沉浸式投影留待后续统一实现。）

### 测试
- 将机械动力 Create `1.21.1-6.0.10`（内嵌 Flywheel/Ponder/Registrate）拷贝进开发环境
  一并启动，验证与其流体管道、动力臂与放置逻辑的兼容性。
- 开发环境与本地测试整合包同时接入 **Immersive Engineering `1.21.1-12.4.2-194`**
  与 **Mekanism `1.21.1-10.7.19.85`**（含 IE 依赖 cloth-config / curios / geckolib /
  forgified-fabric-api），用于后续流体管道与电器（FE）兼容性测试；两者均已在开发客户端成功加载。
- 本地测试整合包的高亮模组**只保留 The One Probe** 一个，避免多个高亮叠加。

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
- 新增 `docs/dev-environment.md`：开发环境与本地测试整合包约定（目录、模组清单、高亮只留 TOP、
  jar 输出流程、各兼容模组测试现状）。

### 移除
- 旧的演示储罐控制器 `tank` 与 `lv_frame` 方块，及其模型、方块状态、配方与语言条目。

### 验证
- `gradlew build --rerun-tasks` 通过（并已清空 configuration-cache 以复现真实编译）。
- `runServer` 到达 `Done`，common setup 自检通过，无缺失模型/材质告警。

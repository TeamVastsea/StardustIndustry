# 更新日志 / Changelog

本项目每次推送都会在此记录变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。

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

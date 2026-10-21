# 更新日志 / Changelog

本项目每次推送都会在此记录变更。格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循语义化版本。

## [未发布]

### 新增
- **LV 端子座 + 端子（电力系统 E2 第一步，网格自由布置）**：按设计文档 §6.3 落地。
  端子拆成两件东西——**端子座**（架子）与**端子**（铜柱）。
  - **端子座**：每级一个方块，`lv_terminal_mount`（4×4 = 16 格）/`mv_terminal_mount`（2×2 = 4 格）/
    `hv_terminal_mount`（1×1）/`ehv_terminal_mount`（1×1）。等级越高网格越稀，因为端子本身越大。
    方块属性**只有 `facing`**（背板贴在哪一面，**六向**，贴墙 / 平铺地面 / 倒挂天花板）；
     等级由 Block 实例本身携带（每级一个方块类实例），**不做成方块属性**——否则
     每份 blockstate 要 6 朝向 × 4 等级 = 24 个变体，其中 18 个在本级并不存在，
     会刷出大量 `missing model for variant`。**格子占用存在方块实体里**
    （4×4 有 2^16 种组合，不能做成方块状态）。
  - **端子**：每级一个物品，`lv_terminal` / `mv_terminal` / `hv_terminal` / `ehv_terminal`，
    右键端子座的**任意格子**即装一颗；同一等级才能装在对应端子座上。
  - **交互**：
    - 端子物品 + 右键某格 → 装一颗（该格已占用则提示）。
    - **空手 + Shift + 右键某格** → 拆下那一颗，直接进背包（满了掉地上）。
    - **安装工具 + Shift + 右键** → 底座连同上面所有端子**完整取下**，先出底座再出端子，全部进背包。
    - **安装工具 + 右键** → 底座转到下一个朝向：墙 N→E→S→W → **天花板（up）→ 地面（down）**→ 回到 N。
    - **镐挖掘** → **散开掉落**：掉 1 个空底座 + 每颗端子各 1 个（读自方块实体）。
  - **网格只开在一个面上**（像配电盘），另外五面是空白外壳；方块形状是贴在该面上的薄板。
    端子座是**六向表面件**：点墙 → 网格朝外，点地板 → 网格朝上，点天花板 → 网格朝下。
  - **模型**：端子座 = 背板 + 格线 + 每格铜座；**六个朝向各生成一份模型**，
    生成器把 north 主形状的顶点绕方块中心 (8,8,8) 旋转到位，模型里不再有 `rotation` 块，
    所有朝向的包围盒都严格落在 `[0,16]` 内（旧版四向模型绕中心转却把背板写在 `z=15..16`，
    非 north 朝向会被推出方块 1 格）。占位贴图 `terminal_plate`（借用 `steel_casing`）/`terminal_seat`/`terminal_stud`。
  - **端子 = OBJ 模型 + 贴图**（`neoforge:obj` 加载器），每级一套 `.obj`/`.mtl`/两张贴图
    （瓷体 + 金属帽），由 `TerminalMountRenderer`（方块实体渲染器）逐格绘制；
    OBJ 直接建在方块坐标系里，所以物品图标与实机朝向一致。资源重载会清空 OBJ 缓存。
    BER 现在按**朝向基向量**（右 / 上 / 法线）求格子中心，并乘上与方块模型完全相同的
    "north → 该朝向"旋转变换，因此贴地、顶棚上的端子位置同样正确。
  - **端子摆放改为数据驱动**：偏移 / 旋转 / 缩放 / 原点写在
    `assets/stardustindustry/models/terminal/<tier>.json`，改完 `F3+T` 即生效，
    不用改代码重编译；配置缺失或写错时回退到默认姿态并打一行警告。
    格式说明见新增的 `docs/obj-model-manual.md`。
  - **外观全部是占位，待美术替换**（见 `docs/textures-placeholder-manual.md` §2.5）。
  - 新增 `energy/TerminalGrid`（等级 → 网格几何）、`machine/terminal/TerminalMountBlock` 与
    `TerminalMountBlockEntity`、`client/TerminalMountRenderer`；
    **旧的 `machine/TerminalBlock` 与 `TerminalOrientation` 已删除**。
  - **作废旧规则**：以前"一格最多 4 根导体、380 V 三相四线占满一格"不再适用；
    现在 LV 一格最多 16 颗端子、16 个独立节点。
  - **当时的范围**：仅 LV 可用，线缆与电网（DSU）尚未做。
    后续轮次已补上四级端子座、全部线缆与软线渲染；**电网电气计算仍未接线**。
- **柔性线缆（连接、悬垂、拆除）**：按设计文档 §6.9 落地，线缆是**连接实体**，不是方块。
  - **存储**：每条线缆存在两端端子座的方块实体里（各存一半），随区块存读档与同步；
    增删全部走 `CableService`，它是唯一知道"两端要同时改"的地方。
  - **两端有序**：`CableConnection` 构造时把端点排成固定顺序，所以 `(A,B)` 与 `(B,A)`
    是同一根线，从另一端拉起也不会产生重复。
  - **渲染归属**：每条线只由持有**较小端点**（`a`）的一端绘制，避免两端各画一遍。
  - **悬垂**：抛物线 `4t(1-t)`，**纯视觉**；电气长度仍按两端**直线距离**四舍五入到整数格。
  - **交互**：一次点击只做一件事——空手右键**移动线头**，手持线缆右键才**真正成线**
    并从手上扣 1 个；**拿端子不会拉线**；**一个端子可接任意多根线**。
  - **拆除**：**双手都空**时右键线缆可以单独剪断并回收（任一只手拿线缆都只算"接线"，
    绝不会误剪）；拆端子 / 挖掉端子座时线缆一并回收。
  - **渲染器扩大包围盒 + `shouldRenderOffScreen`**，长线不会因为端子座出屏而消失。
- **线缆规格：材料 × 载流量 × 绝缘（17 种物品）**：按设计文档 §6.1 落地。
  - **线缆不分电压等级**，用**载流量**与**绝缘**两个正交属性描述，另加**导体材料**。
  - **铜导体 5 档**：50 / 150 / 500 / 1500 / 5000 A，每档电阻约为下一档的 3 倍。
  - **钢芯铝绞线（ACSR）2 档**：800 A / 1600 A，**只有裸线版本**。
    比铜轻，所以**下坠更小（×0.35）**、**能架得更远**；代价是**电阻 ×1.3**、
    且**永远裸线**（§6.11 一律按裸线判触电）。现实中架空高压线普遍用它，原因正在于此。
  - **绝缘 3 档**：裸线 / 一类 / 二类，载流量系数 **100 % / 90 % / 80 %**
    （绝缘越厚散热越差，避免"好绝缘"变成免费升级）。
  - **绝缘只决定触电**，**不再因电压超过绝缘而炸线**：裸线 LV 轻伤、MV 以上必死；
    一类 LV 无伤、MV 轻伤、HV 以上必死；二类 LV 与 MV 无伤、HV 以上必死。
  - 注册名 `cable_<载流量>a_<绝缘>`，例如 `cable_500a_class_2`、`cable_1600a_bare`；
    `ModRegistries.MAIN_TAB` 直接填充，修复"创造栏里看不到线缆"。
- **线缆染色与剪线**：
  - **颜色是线缆的数据，不是物品变体**：默认**未上色**（绝缘线显示灰外皮，
    裸线显示导体本色），**手持染料右键已铺设的线缆**给它上色。
    16 色 × 17 种规格若做成物品就是 272 种，全部电气相同，只会灌满创造栏与 JEI。
  - 颜色写进线缆记录，随端子方块实体存读档与同步；**服务端校验玩家真的持有对应染料**，
    颜色取自**染料物品本身**而不是客户端上报值。
  - **裸线不可染色**（设计而非遗漏）：未上色时裸铜是铜色 `#B87333`、
    钢芯铝绞线是亮铝色 `#B8BEC4`。金属本色是玩家分辨"这是裸线，碰了会死"的唯一线索，
    允许染色等于抹掉这条线索；客户端、服务端、`CableService` 三层拦截。
    另外黑线在远处分不清 1600 A 与 500 A。
- **线缆改用实心管渲染**：从 `RenderType.lines()` 的 4 条平行细线改为
  **沿悬链曲线扫掠的实心棱柱**（两端封盖），走实体渲染管线。
  GL 线图元**没有粗细模型**，宽度由驱动在屏幕空间拉伸，结果是几条断开的细长矩形——
  看起来像线束而不是线缆。改成真实几何后，粗细在任何距离和分辨率下都一致。
  面数、粗细公式、贴图的最终取值见「变更 · 线缆外观本轮定稿」。
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
- **打掉端子座后线缆"复活"（数据没真正删除）**：挖掉端子座时只清了自己这一半记录，
  **远端座子里的那一半没清**。把新座子放回同一坐标，远端记录又能找到端点，线缆就凭空出现了。
  现在拆端子 / 挖座子 / 完整取座都会**两端一起清**并整条掉落；远端区块未加载时，
  由远端自己在 `TerminalMountBlockEntity#onLoad` 时校验并丢弃"对端已不存在"的记录
  （不掉物品，避免重复给）。对端未加载**不算**消失，跨区块长线不会被误删。
- **线缆"有概率连不上"（四个独立原因，全部修掉）**：
  1. **主因——服务端要求两端都在交互距离内**。玩家在 A 端点一下拿到线头，再走到 B 端点一下，
     此时 A 早已超出 4.5–5 格，请求被静默拒绝。于是**站在中间让两端都够得着才成功**，
     比手臂长的线缆必败——表现就是"有概率连不上"。
     现在改为：**刚点的那一端**必须在交互距离内（眼睛到方块中心，另加 1 格容差吸收发包延迟），
     **另一端**只需在 **64 格**（`MAX_CABLE_SPAN`）以内。
  2. **距离检查用了脚部位置**。客户端方块射线从**眼睛**出发，服务端却从**脚**量距离，
     挂在墙上的高处端子在眼睛够得到、脚够不到时被服务端拒绝。已改为眼睛位置。
  3. **"手持线缆"只看进行这次交互的那只手**。把线盘放副手、空手右键时，客户端判定"没拿线缆"，
     于是把点击当成"移动线头"，不发连接请求——点击看起来毫无反应。客户端与服务端现在都走
     `PendingCable#heldCable`（主手 → 副手）。
  4. **Shift 优先于接线**。潜行点端子时，主手持线 → 什么都不发生；**副手持线 + 主手空 → 服务端把端子拆了**。
     现在接线手势优先于 Shift：**手持线缆时 Shift+右键照样接线**，空手 Shift 才是拆端子。
  另外：**任意一只手拿着线缆就不可能是"剪线"**，避免瞄准射线擦过既有线缆时把旧线剪掉。
  `handleCableEdit` 先于一切处理，这条规则必须在那之前生效。
- **重复拉同一对端子的同规格线缆会白白消耗线缆物品**：`putCable` 用 `contains` 去重后
  `CableService#connect` 仍返回 `true`，于是物品照扣、线却没多。`putCable` 现在返回"是否真的存进去了"，
  `connect` 据此返回失败，玩家不扣物品，并收到 `rejected` 提示。
- **同一端子座上两个格子接力时，拆掉较大格不连带断线**：同座线缆只登记在较小格（`a`）的列表里，
  拆另一格时找不到它，留下一条挂在已拆除端子上的线。`removeTerminal` 现在会额外扫整个端子座，
  把"另一端正好是这个格子"的线一并拆下。
- **连接失败不再静默**：新增 `cable.stale`（点击已过期）与 `cable.too_far`（超过 64 格）提示，
  `cable.rejected` 文案补上"或两端之间已有同规格线缆"。静默失败正是这个问题难以定位的原因。
- **端子座朝向与端子外观**：
  - 端子座此前用六向 `facing`，物品放置时拿不到稳定的点击面，导致"贴哪边都朝向一样"；
    一度退成**水平四向**，现已恢复**六向**：`FACING` 的语义统一为
    **"背板贴在哪一面"**，端子从背板朝**方块外侧**凸出。
  - 方块模型原先"底板 + 每格钉柱"混画，视觉上是若干方块叠在一起；现在只画背板、格线、
    每格铜座，端子本体交给渲染器。
  - 端子改为 **OBJ 模型 + 贴图**，直接建在方块坐标系里（轴向 = 方块中心线、底部坐在网格平面），
    **物品图标与实机摆放不再各自一套朝向**。
  - 修好端子物品提示里的缺失翻译键（`tier.tier.stardustindustry.lv` → `tier.stardustindustry.lv`）。
- **端子座显示为紫黑棋盘（贴图丢失）**：方块模型与贴图都在，但四份 blockstate 的变体键
  仍是旧的 `"facing=north,tier=lv"`。`TIER` 属性已从 `TerminalMountBlock` 移除，
  含 `tier=` 的键**永远匹配不到任何真实状态**，24 个变体全部回退到缺省模型。
  修复：变体键只保留 `facing=<dir>`。
- **端子柱身在东、西、上、下四个朝向朝方块内部生长**（北、南正常）：
  `TerminalMountRenderer#faceRotation` 用 JOML 的 9 参数 `Matrix3f` 构造把
  `(right, up, normal)` 当作列并排写进了参数表，但该构造按**行**读取
  （`m00, m01, m02` 是第一行），因此实际得到的是**转置矩阵**。north/south 的目标矩阵
  恰好对称，转置后不变，所以只有这两个朝向下显示正常；west/east/up/down 的矩阵不对称，
  转置等价于取逆，OBJ 的 `+z` 生长轴于是指向方块内部，柱身钻进方块。
  修复：改用 `new Matrix3f().set(col0, col1, col2)` 显式按列赋值。
  同一处矩阵也用于 `alignDir`（配置里的 `offset X/Y/Z`），因此这四项偏移在四个朝向
  上的错位一并修复。已用真实 JOML 逐朝向验证 `M*(0,0,1) == normal`，并做六向 × 16 格全链路校验。
- **气体储罐参数屏容量/存量显示为 0B**：参数屏的「储罐容积 / 已存储容量 / 剩余容量」此前从
  **通用字段 `fluidAmount`/`fluidCapacity`** 取数，而这两个字段来自 `FluidBufferModule`——
  气体储罐没有流体模块，取到 `-1` 后除以 1000 得 `0B`（满罐也显示 0B；流体储罐因有流体模块而正常）。
  修复：把介质中性的**存量与容量（mB）**加入 `MachineParamsData.TankParams`
  （新增 `storedMb`/`capacityMb` 字段与 `StreamCodec` 编解码），由 `AbstractTankBlockEntity`
  统一填写（存量 = `storedAmountMb()`、容量 = `currentCapacityMb()`），参数屏改读这两项。
  流体与气体由此走同一条数据路径；TOP/Jade/WTHIT 的 HUD 本就直读方块实体，不受影响。
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
- **线缆管体加贴图（不再是一根纯色管子）**：
  - 新增 4 张**灰阶**长条贴图 `textures/block/cable_{bare,acsr,class_1,class_2}.png`（占位 32×128）。
    裸线画**绞合线股的斜向螺旋**，绝缘线画**纵向纹 + 周期性标识带**，ACSR 画更粗的铝股。
  - 颜色仍由顶点色（材料本色 / 染料色）乘上去，所以**一张贴图服务所有颜色与所有电流档**。
  - UV：横向 `u 0..1` = 绕截面一整圈（12 面等分）；纵向 `v` 沿**曲线长度**推进，
    **每 2 格**重复一次（`TEXTURE_REPEAT_BLOCKS`），长跨距与短跨距印刷尺度一致。
  - 管线改为原版 `RenderType.entitySolid`（受光照 / 剔除 / 不透明），按纹理缓存 RenderType。
    顶点格式随之改为 `NEW_ENTITY`（补 UV、overlay、lightmap）。
  - 光照取**端子座所在格**的光照并整根线共用：逐顶点采样会让细线在跨格处闪烁。
- **线缆外观本轮定稿（颜色 / 粗细 / 下坠 / 分段）**：
  - **默认不再"黑色"，改为"未上色"**（`CableColor.UNDYED`，哨兵 `-1`）。黑线在暗处与阴影融在一起，
    玩家看不出那里有线。未上色时按产品求值：**绝缘线 = 灰外皮 `#8C8C8C`**、
    **裸铜 = 铜色 `#B87333`**、**钢芯铝绞线 = 铝色 `#B8BEC4`**（`CableColor#renderedColor`）。
    旧存档里缺失或写 `0` 的颜色一律迁移成"未上色"。
  - **裸线不可染色**：金属色是玩家分辨"这是裸线、碰了会死"的唯一视觉线索，染色会抹掉它。
    客户端、服务端、`CableService#recolor` 三层拦截。
  - **粗细改为四次方根**：`直径 = 0.05 × (I/50)^(1/4)`。立方根把 5000 A 画到 `0.232` 格，
    是一根管子；四次方根不动 50 A 的下限、只压缩顶端，5000 A 降到 **`0.158` 格**。
    七档直径：0.050 / 0.066 / 0.089 / 0.100 / 0.117 / 0.119 / **0.158** 格。
  - **管体 8 面 → 12 面**，顶点缓冲相应扩到 65536。
  - **下坠压缩并加短距拉直**：`SAG_RATIO = 0.06`、`MAX_SAG = 1.0` 格、
    `SHORT_SPAN_THRESHOLD = 3.0` 格（smoothstep 压到近 0）、**跨距 < 1 格直接拉直**。
  - **分段动态化**：`TerminalGeometry#sagSegmentsFor`，跨距 < 1 格取 2 段，
    否则 `clamp(ceil(跨距 × 2), 4, 64)`；渲染与拾取共用同一个分段数，避免"看着点中了却没反应"。
- **修复构建屏蔽规则误伤模块源码**：上一轮的"排除仓库根解包产物"用的是
  `sourceSet.exclude("com", "net", ...)`。Gradle 的 source set 排除按**相对源码根的路径**匹配，
  而模块自己的源码就在 `src/main/java/com/stardustindustry/...`，于是 `com` 把**整个模块源码**排掉了：
  `compileJava` 变成 `NO-SOURCE`，构建却仍然报告 **BUILD SUCCESSFUL**——因为只是复用了旧的增量产物，
  错误被完全掩盖。现在改为按**规范化绝对路径**过滤，只排除真正位于仓库根那些解包目录下的文件。
  教训：看到 `NO-SOURCE` 必须当失败处理。
- **线缆（E2 第二步）：连接与渲染已落地，尚未接电**。线缆是**两个端子之间的一条连接**，
  不是方块：没有方块形态、不占格子、没有碰撞箱。
  - **两端各存一半，存在端子的方块实体里**，随区块加载/卸载、随方块实体自动同步。
    这里放弃了"全服一张大表"的做法：线缆上万根时，全表方案要么让每次查询和同步都过全表，
    要么逼客户端自己写增量协议（区块加载/卸载、重连、跨维度各一套边界情况）。
    存放进方块实体后，**区块不加载的线缆不占内存、不发包**，这是后期线缆密集时不出性能事故的关键。
    代价是"一条线存在两个地方"，因此增删**全部走 `CableService`**，它是唯一知道"两端要同时改"的地方。
  - **每条线只由一端绘制**（持有较小端点 `a` 的一方）。两端各画一次会让线被画两遍，亮度加倍且重叠处出伪影。
  - **渲染器扩大包围盒**：方块实体渲染器默认按自己那一格裁剪，而线缆跨越几十格，
    不扩大包围盒就会在端子座出屏时**整根消失**。`TerminalMountRenderer` 已开启
    `shouldRenderOffScreen` 并把盒子扩到所有线缆远端。
  - **几何只有一份实现**：新增 `TerminalGeometry`，把"端子的脚点"从渲染器里提出来，
    供端子渲染、线缆渲染、交互三处共用；基向量表也一并搬入。此前三处各算一遍是
    "线没接在柱子上"这类问题的来源。`TerminalMountRenderer` 已改为调用它。
  - **拉线手势**：右键端子拿线头 → 右键另一端子成线；再点同一端子取消。
    线头是纯客户端状态，服务端不记；每次请求都把两端发过去重新校验
    （两端都是端子座、格子里真有端子、这条线还不存在），改包最多得到
    "玩家本来也能拉出来"的线，不能穿墙或连不存在的端子。协议见 `ConnectCablePayload`。
    两边距离规则见「修复 · 线缆有概率连不上」。
  - **一个端子可接多根线**（§6.3.5）；拆端子、挖端子座、完整取下时，**两端记录一起清**并整条掉落。
  - **悬链线用抛物线近似**（`4t(1-t)`），下坠**纯视觉**；电气长度仍按两点直线距离四舍五入到整格。
  - **17 种线缆物品**：铜 5 档 × 3 种绝缘 + 钢芯铝绞线 2 档（仅裸线），即
    `cable_<安培>a_<绝缘>`。规格完全由**物品**决定，所以"一种真实产品一个物品"。
  - **必须手持线缆才能成线**：成线时从**手上那一叠**扣 1 个，手上没有就拒绝。
    （最初为了绕过尚未实装的配方而允许"没有也照样成线"，该宽容已按要求移除。）
  - **尚未实现（下一轮）**：电网 DSU 连通性、线损 `I²R`、绝缘击穿与过流、多导体缺项判定。
    线缆目前只记录拓扑，不带电。
- **端子尺寸不再由渲染器自动适配，改由美术在 OBJ 里决定**：`TerminalMountRenderer` 现在只做
  OBJ 单位 → 方块的换算（`×1/16`）再乘配置里的 `scale`，**没有任何自动缩放或深度钳制**。
  旧逻辑会按"格子可用宽 ÷ OBJ 宽"和"剩余深度 ÷ OBJ 深"取较小值缩放，结果取决于模型的
  **长宽比**而不是模型的绝对尺寸：占位件截面 `5.6`、柱长 `10`，被压到格子宽度的约 `1/9`，
  在 4×4 网格里柱身只剩 **0.4 像素**——实际上是一张薄片。
  移除后同一个占位件恢复为 `0.625` 方块长（10 像素），外观上从"一张薄片"变回"
  一根细柱子"——它依旧细，是因为**占位 OBJ 的截面本来就只有 `0.2` 方块（LV），
  四级柱长也都是 `10` 单位**，这是占位件的美术尺寸，不是渲染问题。
  尺寸归美术决定，程序不再代为缩放，已写入 `docs/obj-model-manual.md` §3.6。
  随之移除 `objWidthBu` / `objDepthBu` / `Extent` 等测量辅助与 `CELL_FILL` 常量。
- **端子模型配置加载失败时不再重置为默认姿态**：`TerminalModelConfigs.onResourceManagerReload`
  原先把新地图初始化为 `loadDefaults()` 再逐个覆盖，于是任何**解析失败或文件缺失**的等级
  都会悄悄退回"原始 OBJ 姿态"，与手册承诺的"保持上一版"相反。现在以**当前配置**为基础，
  失败项保留上一版姿态，只有首次启动时才是默认值。
- **"点在网格外"不再被误报**：`cellFromHit` 原先把"没有可用格子"（网格为空、等级缺失）
  和"射线没打中面板"都返回 `-1`。现在拆成两个语义明确的返回值：
  `CELL_OUTSIDE_PLATE = -2`（真没打中，仍然提示）与 `CELL_UNAVAILABLE = -1`（没有格子可点，静默）。
  同时补上 `cellAt` 越界时的归一处理，避免退化射线量化出异常值。
- **端子占位贴图改为 UV 校验网格**：原先的 `terminal_<tier>_body/metal.png` 是 16×16
  **纯色图且四角 alpha=0**，而端子 OBJ 的所有面共用同一组 `vt (0,0)(1,0)(1,1)(0,1)`，
  于是每个面都被整张贴图拉满，四角透明让面边缘消失，看起来像"破面"。
  现在改为**不透明**的 4×4 明暗棋盘 + 1px 深色边框，并在四角放彩色标记
  （左上红 / 右上绿 / 左下蓝 / 右下黄），贴图被翻转或拉伸时一眼可辨。
  仍是占位图，待美术替换（见 `docs/textures-placeholder-manual.md`）。
- **电力系统 E1：`EnergyTier` 概念分离（电压 vs 功率档）**：把原先单一的 `voltage` 拆成两个概念。
  - `volts()`：**物理线电压**（380 V / 10 kV / 110 kV / 500 kV），用于绝缘/过压判定与显示；
  - `powerClass()`：**FE/t 功率档**，沿用旧值（32 / 128 / 512 / 2048），即原 `voltage` 的语义；
  - `maxTransfer() = powerClass × maxAmperage`，**数值未变、现有平衡不变**；
  - `accepts(incoming)` 改为按 `volts()` 判定；`maxAmperage()` 视为真载流量。
  - 调用点同步：`MachineBlockEntity.idleDraw()`、`FluidBufferModule`、`GasBufferModule` 改用 `powerClass()`。
  - 等级显示改为「**等级 + 电压**」并存：`tier.stardustindustry.*` 语言键改为 `LV · 380 V`
    （`MV · 10 kV` / `HV · 110 kV` / `EHV · 500 kV`）。
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
- **`docs/electric-power-system-design.md` 同步本轮线缆定稿**：
  - §6.3.2 拆除规则改为"**两端一起清 + 整条掉落**"，并记录远端未加载时由
    `TerminalMountBlockEntity#onLoad` 兜底校验、对端未加载不算消失；
  - §6.8 默认色由"黑色"改为"**未上色**"，补裸线不可染色及其三层拦截、双手都空才剪线；
  - §6.9.2 补齐拉线手势与**两种距离规则**（近端在交互距离内、远端 ≤ 64 格）、
    潜行不改变接线语义、失败提示分类；
  - §6.9.3 新增粗细四次方根表、12 面几何、下坠参数表、动态分段、贴图规格
    （灰阶、32×128、`u` 绕一圈、`v` 每 2 格重复、整根共用一格光照）；
  - 修正 §6.9.3 原地排在 §6.9 之前的章节错位。
- **`docs/terminology.md` 补线缆命名**：`cable_<载流量>a_<绝缘>`、导体材料表与绝缘三档释义。

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

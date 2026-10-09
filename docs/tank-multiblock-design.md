# 星砾工业 · 储罐多方块设计文档（v1.2）

> 本文档是**储罐（Tank）**这一多方块结构的完整、独立、权威设计依据，**流体储罐与气体储罐共用本文档**。
> 凡与储罐相关的内容，以本文档为准；总框架文档
> [`multiblock-design.md`](./multiblock-design.md) 只描述**通用机制**（静态/动态双轨、
> StructureProvider 抽象、安装工具、投影、加成系统等），不再单独描述储罐细则。
>
> v1.1 变更：**储罐分介质**——流体储罐与气体储罐**结构、规则完全一致**，仅介质不同；
> 部件**命名正式化**（`fluid_tank_shell` / `gas_tank_shell` / `industrial_glass` / `lv_gas_port`）；
> 新增**罐内气体渲染 = 浓度**（区别于流体的液面）；新增**通用气体抽象**（见 §16）；
> 新增独立的 **MekanismEx 附属模组**（§17），含色调归一化与名称翻译。
> v1.2 变更：明确 Core/UtilsEx/MekanismEx 的代码归属，并按当前 `TankHudData` 实现修正 HUD 字段、
> 显示内容和依赖说明。
>
> 模块边界与依赖规则见 [`module-architecture.md`](./module-architecture.md)。
>
> 文档状态：**设计定稿，流体版与气体版均已实现**。文中标注「本轮」= 第一版落地范围；
> 标注「后续」= 明确不在本轮、为将来预留。

---

## 0. 一句话概述

储罐是**动态机器**：玩家用「储罐框架」搭 12 条棱、用「储罐外壳」或「工业玻璃」封 6 个面
（外壳位置可换成**对应介质的端口**），内部保持中空并灌满空气，结构即**自动成型**；
储量 = 内部空气格数 × 每格桶数（默认 **128 桶/格**）；
仓内存储**单一介质**——**流体储罐**存一种液体、**气体储罐**存一种气体。

> **两种储罐是同一个多方块**：结构、规则、容量公式、参数屏**完全一致**，唯一区别是
> **介质**（液体 / 气体）、**外壳方块与端口**，以及**罐内渲染**（流体看液面，气体看浓度）。
> 详见 §3.1 与 §7。

---

## 1. 设计目标与原则

1. **无等级**。储罐本体**不分 LV/MV/HV/EHV**：它只是一口缸。
2. **结构部件按介质分化**：储罐框架、工业玻璃两种介质共用；外壳与端口各有一套（流体 / 气体）。
3. **端口只影响吞吐**，不影响储量、不影响结构等级。
4. **储量只由内部体积决定**，是一个玩家能心算的公式。
5. **看得见**。与静态机器「成型后机体隐形」完全不同：储罐成型后**所有方块照常渲染**，
   不做任何视觉隐藏或重绘。
6. **自动成型**。放下 → 结构完整即成型，无需安装工具。（安装工具仍用于查看参数 GUI。）
7. **纯读取判定**。结构求值不改动世界，客户端与服务端跑同一份判定逻辑。
8. **主流高亮模组可读**。指向储罐**任意**方块（框架 / 外壳 / 玻璃 / 端口）时，
   高亮模组都能显示内部介质、存量与容积，而不只是外壳方块能显示。
9. **同源双介质**。流体储罐与气体储罐**共用同一套代码与文档**（`AbstractTankBlockEntity`），
   差异收敛到「介质」这一个枚举；扩一种介质只需加外壳 + 端口 + 缓冲模块。

---

## 2. 术语表

| 术语 | 含义 |
|---|---|
| 储罐框架（Tank Frame） | 无等级的框架方块，搭建矩形体的 **12 条棱**；**两种介质共用** |
| 储罐外壳（Tank Shell） | 无等级的面方块，构成 6 个面的非棱部分；**自带方块实体，兼任控制器锚点**。分**流体储罐外壳** `fluid_tank_shell` 与**气体储罐外壳** `gas_tank_shell` |
| 工业玻璃（Industrial Glass） | 透明方块，可替代任意介质储罐的外壳；结构判定中与对应外壳等价；**两种介质共用** |
| 流体端口（Fluid Port） | 方块 `lv/mv/hv/ehv_fluid_port`；只用于流体储罐，可放在**非框架**的面位置 |
| 气体端口（Gas Port） | 方块 `lv/mv/hv/ehv_gas_port`；只用于气体储罐（先实现 LV） |
| 介质（Medium） | 储罐存的是**流体**还是**气体**；由锚点的外壳方块决定，决定外壳、端口与渲染 |
| 内部空气格 | 外壳内、四面封闭的纯空气格；储量按它的数量计算 |
| 锚点（Anchor） | 玩家右键打开 GUI、机器逻辑归属的那一个储罐外壳方块 |
| 液面 | 客户端根据存量/容量在内部空腔渲染的**流体高度**（流体储罐专属） |
| 浓度 | 客户端把气体**充满整个内腔**、颜色随存量变浓的渲染方式（气体储罐专属） |
| 气体栈（GasStack） | 本模组自有的「气体 + 量」对象，单位 mB；与 `FluidStack` 对称 |

---

## 3. 方块清单

> **流体储罐与气体储罐共用同一个文档**：两者结构、规则完全一致，唯一区别是**介质**
> （一个存液体，一个存气体）与**外壳方块/端口**。下表用「共用 / 各一套」标注。

| 方块 ID | 中文名 | 分类 | 等级 | 说明 |
|---|---|---|---|---|
| `tank_frame` | 储罐框架 | FRAME | 无 | 12 条棱；**流体/气体共用** |
| `fluid_tank_shell` | 流体储罐外壳 | SHELL + 控制器 | 无 | 6 个面；自带 BE，兼任锚点 |
| `gas_tank_shell` | 气体储罐外壳 | SHELL + 控制器 | 无 | 同上；介质为气体 |
| `industrial_glass` | 工业玻璃 | SHELL | 无 | 替代外壳；透明；**流体/气体共用** |
| `lv_fluid_port` ~ `ehv_fluid_port` | LV~EHV 流体端口 | PORT | LV/MV/HV/EHV | 流体储罐用 |
| `lv_gas_port` ~ `ehv_gas_port` | LV~EHV 气体端口 | PORT | LV/MV/HV/EHV | 气体储罐用（先实现 LV） |
| ~~`tank`~~ | ~~储罐（旧演示）~~ | — | — | **删除**：旧的演示控制器，被 `fluid_tank_shell` 取代 |
| ~~`lv_frame`~~ | ~~LV 框架~~ | — | — | **删除**：旧演示储罐专用；新的储罐框架无等级 |

> **关于 `steel_casing`**：它目前仍是**破碎机机体的合法方块**（`CrusherStructure` 使用），
> 因此**保留**，不随旧储罐一起删除。

### 3.1 两种储罐的差异一览

| 方面 | 流体储罐 | 气体储罐 |
|---|---|---|
| 外壳 | `fluid_tank_shell` | `gas_tank_shell` |
| 端口 | `lv_fluid_port` 等 | `lv_gas_port` 等 |
| 框架 / 玻璃 | `tank_frame` / `industrial_glass`（**共用**） | 同左（**共用**） |
| 介质 | 单个液体（`FluidStack`） | 单个气体（`GasStack`，通用抽象） |
| 计量单位 | mB | **mB**（与流体一致） |
| 罐内渲染 | **液面高度**（从底部升高） | **浓度**（充满内腔，颜色随量变浓，最高 alpha ≈ 0.70） |
| 结构规则 | 完全相同 | 完全相同 |
| 参数屏 / HUD | 相同布局，介质名不同 | 同左 |

---

## 4. 结构规则

### 4.1 形状

一个**轴对齐的实心矩形外壳**，内部中空。设矩形在 X/Y/Z 三轴上的**外壳尺寸**均为 `n`：

```
n = 3 … 9       （每轴；默认上限 9，见配置）
内部尺寸 = n − 2 （每轴）
内部空气格数 V = (nX−2) × (nY−2) × (nZ−2)    ≥ 1
```

- **最小 3×3×3**（内部 1×1×1）。
  *理由：2×2×2 内部为 0 格、容量为 0，是一个「什么都放不进去的储罐」，无意义。*
- **最大 9×9×9**（内部 7×7×7 = 343 格）。

### 4.2 每个位置的要求

以「该格触及矩形几条边（edgesTouched）」分类：

| 触及边数 | 位置 | 允许的方块 | 观测角色 |
|---|---|---|---|
| ≥ 2 | 棱 / 角 | **必须**是储罐框架 `tank_frame` | FRAME |
| = 1 | 面（去棱） | 本介质的储罐外壳 / 工业玻璃 / 本介质的端口 | PANEL 或 PORT |
| = 0 | 内部 | **必须是空气** | INTERIOR |

硬性约束：

1. **端口只能放在面位置**，绝不能落在棱上。棱上出现端口 → 失败，报「该位置需要储罐框架」。
2. **内部必须是纯空气**。任何非空气方块 → 结构不成立，**对话框报错并给出该方块坐标**。
3. **内部维持中空**，不放置任何填充块、框架或外壳。
   *（与通用动态机器不同：储罐的填充块机制本轮**不启用**。）*
4. **外壳必须闭合**：任意面缺块（含控制器锚点本身由外壳承担）都不成立。

### 4.3 结构判定算法：洪水填充（Flood Fill）

从**任意一个储罐结构方块**出发，沿同类外壳相邻扩散，得到连通外壳区域，再取其**包围盒**：

1. 收集与起点连通的「外壳类方块」集合：
   起点为 `tank_frame` / 本介质外壳 / `industrial_glass` / 本介质端口；
   仅当邻居也是**同介质的外壳类方块**时才继续扩散（6 邻域，非对角）。
2. 由该连通集合的极值得到包围盒 `min..max`。
3. 校验 4.1 的尺寸范围与 4.2 的逐格要求。
4. 若洪水填充的连通集合**少于包围盒的全部外壳格**，说明存在「悬空的外壳块彼此不相连」
   → 结构不成立。

> **为什么用洪水填充而非射线扫描**：储罐的外壳天生是「闭合的一圈」，洪水填充天然吻合
> 「连通的一圈 + 内部一个腔」的语义，也天然处理「锚点是壳的一部分」（不依赖「控制器
> 贴在壳面」这种别扭锚点）。总框架文档 §5.1 的射线扫描仅用于其它动态机器。

### 4.4 锚点与自动成型

- 储罐外壳方块**自带方块实体**。玩家放置的**第一个储罐外壳**即成为这台机器的
  **锚点**（记录在方块实体中：机器的「代表坐标」= 锚点坐标）。
- 其余外壳 / 玻璃 / 端口不各自成为控制器，而是通过洪水填充**归属**到同一个连通外壳区域，
  从而归属到同一台机器。
- 结构完整时**在下一个心跳自动成型**（无需安装工具）。成型前若有错误，锚点附近给出
  投影与文字提示。

> 简化实现：由于储罐外壳自带 BE，判定以「扫描某个外壳方块所属的连通区域」为单位。
> 锚点只承担「谁拥有本机的数据（流体、容量、速率）」与「GUI 入口」两件事。

### 4.5 成型后的渲染

- **不做任何隐藏**：框架、外壳、玻璃、端口全部照常渲染，玩家看到的就是搭出来的样子。
- 与静态机器的 `INVISIBLE_STRUCTURE` + 控制器重绘**完全无关**。

---

## 5. 容量

```
容量（mB） = 内部空气格数 V × 每格桶数 B
默认 B = 128 桶/格 = 128 000 mB/格
```

| 尺寸 | 内部 | V | 容量（默认 B=128） |
|---|---|---|---|
| 3×3×3 | 1×1×1 | 1 | **128 桶**（128 000 mB） |
| 9×9×9 | 7×7×7 | 343 | **43 904 桶**（43 904 000 mB） |

- 容量**只**由体积决定；端口等级、框架种类都不改变容量。
- `V`、`B` 均支持配置（见 §8）。
- 容量上限以 `int`（mB）计：9³ 时约 4.39×10⁷ mB，安全落在 `int` 范围内。

---

## 6. 端口与吞吐

储罐**只接受对应介质的端口**：流体储罐只认流体端口，气体储罐只认气体端口
（两者都不接受物品、能量端口）。把气体端口插进流体罐（或反之）属于搭建错误。

| 端口等级 | 吞吐含义 | 本轮 | 后续 |
|---|---|---|---|
| LV | 5 B/t（5000 mB/t） | ✅ 按端口等级提供不同 mB/t | — |
| MV | 20 B/t（20000 mB/t） | ✅ | — |
| HV | 80 B/t（80000 mB/t） | ✅ | — |
| EHV | 320 B/t（320000 mB/t）+ **精准速率调节 + 过滤** | 速率 ✅；调节/过滤「后续」 | 精准速率调节、过滤 GUI |

> 速率独立于能量阶梯：**每级是上一级的 4 倍**，即 5 / 20 / 80 / 320 B/t。
> 数值直接存放在 `EnergyTier`（`fluidTransfer` 字段），**流体与气体端口共用同一速率表**，
> 不从电压推导。

- 端口吞吐**只影响传输速率**，不影响容量、不改变结构是否成立。
- 同一台储罐可以插多个（同介质）端口；它们共享同一个内部仓。
- 端口在面位置时，流体端口向外暴露 `IFluidHandler`，气体端口暴露本模组的
  `IGasHandler`（`ModCapabilityTypes.GAS_HANDLER`）；装了 Mekanism 时气体端口**同时**暴露
  Mek 的 `IChemicalHandler`（见 §17），故 Mek 加压管道可正常对接。
- **只有端口对外**：储罐外壳（墙）**不暴露任何能力**，两种介质一致——管道不能贴壁绕过端口速率。
- **后续**：高级端口（HV/EHV）的「精准速率调节」（设定 mB/t 上限）与「过滤」（只接受
  指定介质）留作独立界面，本轮不实现，仅保留数据模型的预留位。
- **后续**：气体容器的手动右键交互（手持气体罐右键气体端口）待补；本轮气体端口只做管道对接（Mek 管道已可对接）。

---

## 7. 罐内显示：流体看液面，气体看浓度

### 7.1 流体储罐 —— 液面

参考**匠魂（Tinkercraft Smeltery）**的做法，用客户端 BER 在内部空腔绘制液面：

1. **内部区域** = 包围盒去掉外壳后的内腔，尺寸 `(nX−2) × (nY−2) × (nZ−2)`。
2. **底面积** `A = (nX−2) × (nZ−2)`。
3. **总液面高度（以格为单位）**：
   ```
   h = 存量(mB) / (B × A)
   ```
4. 自底面往上绘制：
   - **整满的层**画满流体方块；
   - **最上层余量**按比例画一个**较矮的液面**（高度 = 小数部分）。
5. 流体使用**真实流体贴图**；可带轻微波动效果（非必须）。
6. **仅在内腔绘制**：外壳 / 玻璃 / 框架照常渲染，玻璃处可直接透视看到液面高低。

> 这样「存了什么、存了多少」在玻璃储罐上一眼可见；不外露液面到外壳之外。

### 7.2 气体储罐 —— 浓度

气体与流体的观感**刻意不同**：流体看「液面多高」，气体看「有多浓」。

1. **空罐完全不渲染**：存量 = 0 时内腔无任何内容，与没装东西时一致。
2. **始终充满整个内腔**：气体在罐内自由扩散，**不随存量改变高度**，永远铺满内腔。
3. **颜色随存量加深**：存量越多，颜色越浓（越不透明），视觉上"压力越大"。
4. **透明度上限 alpha ≈ 0.70（70%）**：无论装多满，都不会完全不透明——
   保证仍能透过罐体看到内部结构与轮廓。
5. 颜色取自气体自身的色调（`Gas` 自带），例如氢气浅、蒸汽白、二氧化碳偏灰。
6. **色调归一化（`normalizeTint`）**：Mekanism 化学品的原始色调有两个极端在内腔里读不出来——
   过暗的（红石 `0xB30505`、碳 `0x2C2C2C`）**感知亮度**低（红石亮度仅 ~0.21），融进阴影像空罐；
   近白的（氢气、蒸汽 `0xFFFFFF`）没有色相、只剩灰白雾。渲染器因此在 **HSL 空间**里提升：
   **保持色相不变**，把明度抬到 0.62 的下限、饱和度抬到 0.55，近无彩色（`max-min < 0.06`）
   则给一层浅冷灰。例：红石 `(0.70,0.02,0.02)` → `(0.98,0.26,0.26)` 鲜红。
7. **必须采样纯白贴图（`gas_white`）**：`RenderType.translucent()` 的着色器是
   `纹理 × 顶点色`。此前顶点 UV 写死 `(0,0)`，采样到方块图集左上角的任意像素（偏蓝），
   **无论传什么 RGB 都被染蓝**——这是「红石显示成蓝色」的真正根因。现改为采样模组自带的
   1×1 纯白方块贴图 `stardustindustry:block/gas_white`（`texture = 白`），颜色才完全由色调决定。
   该贴图放在 `textures/block/` 下，由原版方块图集的 `directory` 源自动拼接。
8. **透明度恒 ≤ 70%**：无论什么介质（包括 Mekanism 的颜料、浆液等非气体化学品），
   一律按浓度模型渲染，**绝不完全不透明**——满罐也是 70%，否则会看成一整块流体。

> 一句话记忆：**流体看「液面多高」，气体看「有多浓」。**

---

## 8. 配置项（预留）

放在 NeoForge **common** 配置（`Config.java`），本轮先给出条目与默认值，数值整理后续再做：

| 键 | 默认 | 含义 |
|---|---|---|
| `tankBucketsPerAirBlock` | `128` | 每个内部空气格提供的桶数（1 桶 = 1000 mB） |
| `tankMaxSize` | `9` | 储罐每轴最大外壳尺寸 |
| `tankMinSize` | `3` | 储罐每轴最小外壳尺寸 |

> **`tankMaxSize` < 9 的后果**：9×9×9 不再成立；上限按配置收紧。
> **`tankBucketsPerAirBlock` 变更的后果**：既有储罐的容量立即按新值重算（流体不丢失，
> 超出新容量的部分如何处理见实现时的约定；本轮默认容量只增不减场景）。

---

## 9. 交互与 GUI

- **安装工具**：右键储罐的**任意外壳 / 玻璃 / 端口** → 打开机器参数 GUI（储罐外壳兼任
  锚点；其它方块通过连通区域反查锚点）。
- **容器交互（流体，已实现）**：玩家手持装有流体的容器（水桶、油桶等）右击**流体端口**→ 灌入；
  手持空容器右击 → 取出。物品端口 / 能量端口不参与。这样在铺管道之前也能手动装卸流体。
  - 实现在 `PlayerInteractEvent.RightClickBlock` 事件阶段**抢先拦截**（否则水桶会先把水倒进世界），
    同时设 `useItem=FALSE`/`useBlock=FALSE` 并取消事件；转移**只在服务端执行**，客户端不做预测。
  - 转移**不经过端口的限流能力**（端口限流是给管道每 tick 用的），而是直接对接机器的缓冲，
    因此一次点击即可搬完一整桶。管道连接端口时仍受端口速率限制。
  - 数量**以手持物品自身的能力为准**，因此支持大于 1 桶（如沉浸工程油桶）与小于 1 桶
    （如 500 mB 小瓶）的容器，无硬编码。
- **创造模式调试交互**：创造玩家**潜行 + 右击**端口 = 直接给机器注入 / 抽出一整容器量的
  介质，**手上物品不消耗、不产出**（用于建造与调试）。手持满容器则注入该容器容量，
  手持空容器则抽出该容器可容纳的量。
- **气体容器交互（后续 D7.11）**：气体版本的容器右键交互依赖各气体模组（Mekanism 等）的
  容器物品，待对应扩展实现后补齐；本轮气体端口只做管道对接。
- 参数 GUI 采用 §4.6.1 的统一新格式（已实现），**流体 / 气体同一版式**，字段固定为：
  ```
  多方块结构已成型！          ← 亮黄色；未成型时改为中性提示且不再列后续字段
  多方块尺寸：3X3X3
  多方块结构：流体储罐         ← 介质不同，此处显示「流体储罐」/「气体储罐」
  储罐容积：512B               ← 整数桶（总容积）
  存储流体：水 Water           ← 介质名随罐变化：「存储流体」/「存储气体」
  已存储容量：100B             ← 当前存量（整数桶）
  剩余容量：412B              ← 总容积 − 存量
  已安装端口：LV流体端口 x1      ← 每等级一项，多等级依次列出；无端口显示「无」
  最大传输速率：LV 5B/t         ← 取最高等级端口，速率 = 该等级 mB/t ÷1000
  ```
- **未成型时的错误反馈**：
  - 控制器（锚点）旁的投影幽灵块标出缺失位置；
  - 浮动文字给出**第一条**失败原因；
  - 若内部存在非法方块，**报错信息中必须包含该方块的坐标**（例：
    `（123, 64, -45）需要：空气`）。
- **成型播报**：结构首次成型时，向锚点 24 格内的玩家广播一条「储罐已成型：X×Y×Z，
  容量 N mB」的中文提示（沿用现有 `broadcastNearby` 机制）。
- **无拆除按钮**：储罐自动成型、拆掉一块即解体，故参数界面不提供「拆除」按钮
  （该按钮仅对有安装账本的静态机器显示）。
- **无投影**：储罐是动态结构，形状由玩家自定，没有唯一正确方块可作投影幽灵，
  故 `supportsProjection()` 返回 `false`。未成型时仅显示浮动文字提示；
  沉浸式（能看出具体方块）的投影方案留待后续统一实现。

---

## 10. 高亮模组兼容（HUD / 指向显示）

> 目标：由 UtilsEx **支持 Jade、WTHIT 与 The One Probe（TOP）**；默认开发客户端只加载 TOP，
> Jade/WTHIT 使用独立实例验证。
> 让玩家把准星指向储罐的**任意一个方块**（框架 / 外壳 / 工业玻璃 / 端口），
> 高亮信息栏都能直接读出这台储罐的**介质与容积**。
> 本节写的是当前已经实现的「统一数据源 + 每模组一个薄插件」。

### 10.1 目标行为

| 指向的方块 | 显示内容 |
|---|---|
| 储罐外壳（锚点） | 成型状态、尺寸、内容物、存量、容积/百分比、介质 |
| 储罐框架 / 工业玻璃 | 反查锚点后显示同一份储罐信息 |
| 对应介质端口 | 反查锚点后显示同一份储罐信息；当前不单独显示端口等级 |
| 未成型储罐 | 显示「未成型」；若仍有内容则继续显示内容物和存量 |

- 指向框架、玻璃和端口时都通过 `TankHudAccess` 找到同一锚点。
- 数量小于 1000 mB 时显示整数 `mB`；达到 1000 mB 后显示一位小数的桶数，例如 `12.4 B`。
- 容量行在有内容且容量有效时附加整数百分比。
- HUD 从客户端已同步的方块实体状态构建只读快照，不修改世界状态。

### 10.2 统一数据源：TankHudData

UtilsEx 使用一个与具体 HUD 模组无关的只读快照：

```java
public record TankHudData(
    TankMedium medium,
    boolean formed,
    int sizeX,
    int sizeY,
    int sizeZ,
    Component contentsName,
    int contentsAmount,
    int capacityMb,
    int interiorCells
) { }
```

- 生成入口：`TankHudData.of(AbstractTankBlockEntity tank, BlockPos clickedPos)`。
- 服务端有 `StructureEvaluation` 时直接读取尺寸；客户端则从同步的包围盒重建尺寸。
- 内容物、存量和容量统一通过 `AbstractTankBlockEntity` 的介质无关接口读取。
- `clickedPos` 当前仅为将来的逐面/端口信息预留，不影响现有快照内容。

### 10.3 成员归属表：TankMembershipRegistry

**关键难点**：框架、玻璃没有方块实体，无法直接「从被指向的方块找到机器」。为此储罐引入
一张**成员归属表**（客户端 + 服务端各一份，内存表）：

- **建表**：储罐成型（洪水填充成功）时，把该连通区域**每一个格子**（框架 / 外壳 / 玻璃 /
  端口）映射到锚点坐标：`Map<BlockPos, BlockPos anchor>`。
- **更新**：结构被破坏 / 尺寸变化时，对旧区域整体注销、对新区域重建。
- **查询**：`TankMembershipRegistry.anchorOf(Level, BlockPos)` ——任意储罐方块 → 锚点坐标，
  找不到返回 `null`。
- **与既有机制的关系**：端口已有 `bind(controllerPos)`（§既有端口绑定）；成员表是更宽的
  一层，把**无 BE 的框架/玻璃**也纳入，端口查询可优先走 `bind`，走不到再查表。

### 10.4 每模组的薄插件（已实现）

| 模组 | 实际 API 包（1.21.1） | 插件职责 | 发现机制 |
|---|---|---|---|
| Jade | `snownee.jade.api.*` | `TankJadePlugin` 对储罐三类方块注册 `IBlockComponentProvider`，`appendTooltip` 填 `TankHudData` | `@WailaPlugin` 注解 |
| WTHIT / WAILA | `mcp.mobius.waila.api.*` | `TankWthitPlugin`（`IWailaClientPlugin`）注册到 `Block`，`appendBody` 填数据 | 资源根 `waila_plugins.json` |
| The One Probe | `mcjty.theoneprobe.api.*` | `TankTopPlugin` 实现 `IProbeInfoProvider`，`addProbeInfo` 填数据 | IMC `getTheOneProbe` |
| HWYLA | — | **无需单独插件**：HWYLA 是 WTHIT 的前身，1.21.1 上活跃的是 WTHIT；装有 HWYLA 时由其自身兼容层处理 | — |

- 每个插件类**只做一件事**：拿到 `TankHudData`，转成该模组的文本行。
  文本措辞与格式统一由 UtilsEx 的 `hud/TankHudLines` 生成，三种模组显示一致。
- 插件类引用各自模组的 API；**仅在对应模组被加载时**由该模组自身的插件发现机制实例化，
  避免 `ClassNotFound`（详见 §10.5）。TOP 无发现机制，由 UtilsEx 的 `hud/HighlightCompat`
  在 `ModList` 守卫下主动发送 IMC 消息。


### 10.5 UtilsEx 的可选依赖策略

- 编译期：`compileOnly` 引入各高亮模组 API（WTHIT / Jade / TOP），类只在这些模组存在时加载。
- 运行期：**可选**——不安装高亮模组时储罐功能完全不受影响。
- `neoforge.mods.toml`：为各高亮模组声明 **optional 依赖**，便于日志与依赖管理，
  但**不强制**。
- 开发环境：Core 的 `localRuntime` **只挂 The One Probe 一个高亮模组**（`build.gradle.kts`）。
  Jade / WTHIT 仅编译期参与。这是刻意为之：WTHIT 与 Jade 同处一个 classpath 时，
  WTHIT 的 `IClientApiService` 服务加载器会发生冲突
  （`ServiceConfigurationError: ClientApiService not a subtype`），进入世界即崩溃。
  要验证 Jade / WTHIT 插件，用一个只装该模组的整合包单独跑。
- 与 JEI 一致：实现位于独立 UtilsEx 模块，通过 `IBlockComponentProvider` / 插件注册，
  保持 Core 对第三方模组 API 零依赖。

### 10.6 显示内容在哪些状态出现

| 状态 | 高亮栏内容 |
|---|---|
| 已成型 | 流体名 + `mB` 与桶双单位存量/容积 + 输入/输出速率 |
| 未成型 | 「未成型」+ 第一条失败原因（含坐标） |
| 指向端口 | 额外一行该端口等级 |
| 破坏后（无机器） | 高亮栏不出现任何 Stardust 条目（`anchorOf` 返回空） |

---

## 11. 数据与生命周期

### 11.1 归属

- 储罐外壳方块实体保存：**锚点坐标**、**内部流体**、**容量快照**、**输入/输出速率**。
- 一个连通外壳区域对应**一台**储罐，只有一个「有效锚点」（首个放置的外壳）。
- 若玩家破坏锚点：本机 dismantle（流体照常处理，见 §11.3），另一外壳在下次扫描时可
  重新成为锚点并重建成型。

### 11.2 成型 / 破坏

- 结构完整 → 自动成型；结构被破坏（缺块 / 内部放了方块 / 端口放错）→ 立即回到未成型，
  停止输入输出，**保留已存流体**。
- 破坏任意外壳方块 = 结构破坏（不整机移除，因为储罐不隐藏/不留账本）。
  *（静态机器「破坏任意胞 → 整机拆除」的规则不适用于储罐。）*

### 11.3 拆除与内容

- 本储罐**无安装账本**（不隐藏机体），故无「还原」问题。
- 破坏锚点方块本身：其内流体**按本模组通用约定处理**（掉落为桶 / 就地销毁，二选一，
  在实现时按主文档既有约定统一）。

---

## 12. 当前实现归属

表中的 Core Java 路径相对于
`StardustIndustry-Core/src/main/java/com/stardustindustry/stardustindustry/`；UtilsEx Java 路径相对于
`StardustIndustry-UtilsEx/src/main/java/com/stardustindustry/utilsex/`。

| 作用 | 模块 | 相对路径 |
|---|---|---|
| 结构判定 | Core | `multiblock/provider/TankStructureProvider.java` |
| 介质枚举 | Core | `machine/tank/TankMedium.java` |
| 储罐共用基类 | Core | `machine/tank/AbstractTankBlockEntity.java` |
| 流体/气体控制器 | Core | `machine/tank/TankBlockEntity.java`、`GasTankBlockEntity.java` |
| 外壳/框架/玻璃 | Core | `machine/tank/TankShellBlock.java`、`TankFrameBlock.java`、`TankGlassBlock.java` |
| 流体/气体缓冲 | Core | `machine/module/FluidBufferModule.java`、`GasBufferModule.java` |
| 气体抽象 | Core | `gas/Gas.java`、`GasStack.java`、`IGasHandler.java`、`GasRegistry.java` |
| 气体端口 BE | Core | `machine/port/GasPortBlockEntity.java` |
| 能力与注册 | Core | `registry/ModCapabilityTypes.java`、`ModCapabilities.java`、`ModBlocks.java`、`ModBlockEntities.java` |
| 成员归属/HUD 查询 | Core | `multiblock/TankMembershipRegistry.java`、`machine/tank/TankHudAccess.java` |
| 液面/浓度渲染 | Core | `client/TankLiquidRenderer.java`、`TankGasRenderer.java`、`MachineRenderer.java` |
| HUD 数据与统一文案 | UtilsEx | `hud/TankHudData.java`、`hud/TankHudLines.java` |
| HUD 插件 | UtilsEx | `jade/TankJadePlugin.java`、`wthit/TankWthitPlugin.java`、`top/TankTopPlugin.java` |
| JEI 插件 | UtilsEx | `jei/StardustJeiPlugin.java`、`jei/CrushingCategory.java` |
| Mekanism chemical 桥接 | MekanismEx | `com/stardustindustry/mekanismex/` 下三个桥接类 |
| Core 语言与模型 | Core | `src/main/resources/assets/stardustindustry/` |
| UtilsEx 翻译 | UtilsEx | `src/main/resources/assets/stardustindustry_utilsex/lang/` |

---

## 13. 实现清单
### 13.1 T1 —— 流体储罐第一版（已完成）

- [x] **T1.1 清理旧演示储罐**：删除 `TankBlock`、`tank` 方块/BE 注册、`lv_frame` 注册与资源、
      旧 `tank` 资源；保留 `steel_casing`（破碎机仍需）。
- [x] **T1.2 注册三方块**：`tank_frame`、`tank_shell`、`tank_glass`（**后于 D7.9 更名为**
      `fluid_tank_shell` / `industrial_glass`）+ 方块实体 + 分类 + 语言 +
      模型贴图 + 配方（占位或基础配方）。
- [x] **T1.3 洪水填充判定器**：`TankStructureProvider`，实现 §4.2/§4.3，含尺寸校验、
      棱必框架、面=外壳/玻璃/端口、内部必须空气（失败带坐标）。
- [x] **T1.4 容量与流体模块**：`FluidBufferModule` 支持「容量由内部体积决定」；
      `TankBlockEntity` 去等级、只保留流体模块。
- [x] **T1.5 端口吞吐**：按流体端口等级设置传输速率上限（`EnergyTier.fluidTransfer()`，
      每级 ×4：5 / 20 / 80 / 320 B/t）。
- [x] **T1.6 自动成型与播报**：完整结构自动成型；首成型广播中文提示。
- [x] **T1.7 GUI 与错误反馈**：安装工具任意部件开 GUI；未成型错误含坐标。
- [x] **T1.8 客户端液面**：`TankLiquidRenderer` 按 §7 绘制液面。
- [x] **T1.9 配置预留**：`Config.java` 增加 §8 三键，默认值生效。
- [x] **T1.10 自检**：`MultiblockSelfCheck` 增加储罐几何/容量自检（最少/最大尺寸、容量公式）。
- [x] **T1.11 HUD 统一数据与成员表**：`TankHudData` / `TankHudAccess` / `TankMembershipRegistry`
      （见 §10.2/§10.3），成型建表、破坏注销。
- [x] **T1.12 HUD 插件**：Jade / WTHIT / TOP 插件各一个薄层（见 §10.4），
      UtilsEx 以 `compileOnly` 编译 API，Core 开发运行时加载 TOP；任意储罐方块指向可见流体与容积。
- [x] **T1.13 验证**：全量编译 + `runServer`（自检通过）。
- [x] **T1.14 打包同步**：重新打包 `0.1.0`，同步到客户端 `mods/` 与 `dist/`。
- [x] **T1.16 储罐参数界面**：按 §4.6.1 统一格式重做 `MachineParamsScreen` 的储罐布局
      （亮黄成型提示 + 尺寸/结构/容积/存储流体/已存储/剩余/端口/最大速率）；快照新增
      `MachineParamsData.TankParams`（服务端附带，其它机器为 `null`）。
- [x] **T1.17 端口容器交互**：流体端口支持玩家用容器灌入 / 取出。桶的 `useOn` 会抢在
      方块之前把流体倒进世界，故改由 `PlayerInteractEvent.RightClickBlock` 抢先拦截
      （`PortFluidInteraction`：设 `useItem/useBlock=FALSE` 并取消事件；转移只在服务端执行，
      走机器流体缓冲不限流；数量以手持容器自身能力为准，支持 >1B 与 <1B 容器）。
      另含创造模式「潜行右键」调试注入 / 抽出。
- [x] **T1.18 创造栏图标**：`MAIN_TAB` 图标从空改为储罐外壳。
- [x] **T1.19 端口高亮**：TOP provider 增加对流体端口的注册与角色过滤，
      指向流体端口与指向框架/外壳/玻璃显示同样内容（端口是储罐的一部分）。
- [x] **T1.20 流体同步客户端**：`FluidBufferModule` 内容变化时改为
      `MachineBlockEntity.markContentsChanged()`（`setChanged` + `sendBlockUpdated`），
      修复读客户端缓存的高亮模组（WTHIT）仍显示「空」的问题。
      **根因修复**：`FluidBufferModule.save` 曾把流体写进一个新建的 `CompoundTag` 再
      `put("Fluid", …)`，但 `FluidStack.save` 是**返回**编码后的 tag 而**不写入**传入的
      tag，导致服务端有液、客户端解析出的 `Fluid` 子 tag 恒为空（日志 `hasFluidKey=true`
      但 `amount=0`）。改为 `tag.put("Fluid", stack.save(registries))` 用返回值。
- [x] **T1.21 玻璃透光**：`industrial_glass` 模型补 `"render_type": "minecraft:translucent"`，
      使内部液位可见。
- [x] **T1.23 液面渲染（匠魂风格）**：`TankLiquidRenderer` 把内部流体直接渲染为**实体盒子**，
      从底部到当前液位，玻璃处一眼可见「是什么流体、大概多少」。
      - **每格一个面片**、每片贴一张完整流体贴图：此前用单个面片把 UV 拉伸到整个面，
        采样越界到图集相邻像素，表现为**黑白条纹**。
      - 用 `RenderType.translucent()` 且 alpha 下限 `0.80`：既不透明得像颜料、又保持色泽。
      - `MachineRenderer.getRenderBoundingBox` 扩大到整个机器：否则控制器单格离开视锥时
        内部流体被整体剔除，玻璃仍可见但流体消失。
- [x] **T1.24 流体发黑（光照根因）**：渲染器拿到的是**控制器格子**的光照值，而控制器是外壳
      面板，储罐封闭后既无天光也无块光，光照接近 0，流体因此渲染成**黑色**；开夜视后片段被
      提亮才显示正常（这正是「开夜视就对了」的原因）。修复：`TankLiquidRenderer` 改为采样
      **内腔中心**的天光/块光（`LightLayer.SKY/BLOCK`），并设下限（块光 ≥4、天光 ≥6），
      使暗室中的储罐也能看清液体而不是一团黑。
- [x] **T1.25 唯一控制器（多锚点根因）**：每个储罐外壳方块都创建了 `TankBlockEntity`，
      于是整面外壳墙上的**每一个**外壳都独立成型：各自广播一条「储罐已成型」（提示数量随
      表面积增长，9×9×9 出现几十条）、各自渲染一份流体（表现为**一层恒满 + 一层跟随液位**）、
      HUD 每次可能查到不同锚点（表现为**跳变**）。修复：`TankStructureProvider` 引入
      **确定性唯一锚点**——取连通外壳中坐标最小的外壳作为锚点；其余外壳的评估
      一律返回未成型（无失败项），故不起作用、不广播、不渲染。
- [x] **T1.26 玻璃接缝**：玻璃是 `noOcclusion`，相邻两块的重合面都会被渲染，两条半透明面片
      叠加在每格边界形成**亮缝网格**。修复：`TankGlassBlock.skipRendering` 在相邻方块为同类
      玻璃时跳过该面，整面玻璃从两侧看都是一整块。
- [x] **T1.22 删除无等级端口**：移除 `item_port`/`fluid_port`/`energy_port` 及其资源，
      端口一律带等级前缀（当前仅 LV，MV/HV/EHV 按同模式扩展）。
- [ ] **T1.15 美术资源替换**：按 `docs/textures-placeholder-manual.md` 替换全部占位贴图。

---

## 14. 后续（不在本轮）

- 精准速率调节（按 mB/t 设定端口吞吐上限）。
- 介质过滤界面（白名单 / 黑名单）。
- **气体端口 MV/HV/EHV**（本轮气体端口先实现 LV，其余按同模式扩展）。
- **MekanismEx 独立附属模组（D7.11）**：全部化学品（含核材料/放射性气体）→ `Gas`；
  气体容器手动右键交互。
- 蒸汽锅炉、发酵罐等其它动态结构（将各自单独立文档）。
- 储罐与管道网络的统一路由（若主文档后续引入）。
- 其余机器的 HUD 兼容（高亮显示当前以**储罐**为优先；破碎机等其它机器的 HUD 提示
  在各自机器文档中再定）。

---

## 15. 批复记录

| 要点 | 结论 |
|---|---|
| 最小组件 | **3×3×3**（内部 1 格） |
| 有无等级 | **无**；只有框架、外壳、玻璃三种结构方块 |
| 端口范围 | 按储罐介质只接受对应的 **LV/MV/HV/EHV 流体或气体端口**，只放**非框架**位置 |
| 储量公式 | `内部空气格数 × 128 桶`；可配置 |
| 内部要求 | **必须全空气**；否则报错**并给坐标** |
| 成型方式 | **自动成型**，无需安装工具 |
| 成型后视觉 | **全部可见**，不隐藏、不重绘 |
| 液面 | 参考**匠魂**，玻璃处可见 |
| 锚点 | **储罐外壳兼任**；安装工具右键任意部件开 GUI |
| 判定算法 | **洪水填充** |
| 高亮模组 | UtilsEx 支持 Jade/WTHIT/TOP；默认开发客户端使用 TOP，任意储罐方块显示同一份介质与容积信息 |
| 旧演示储罐 | **删除** |

---

## 16. 气体子系统与"D7.10 气体储罐"（v1.1 新增）

### 16.1 为什么自建气体抽象

原版没有「气体」，唯一通用来源是 Mekanism 的化学品（chemical）。让 Core 把 Mekanism 当硬前置
会使核心功能被外部模组绑架；完全不做则储罐永远只有液体。故 Core **自建一层最小气体抽象**，
再由可选安装、但自身硬依赖 Mekanism 的 MekanismEx 模块完成对接：

| 层 | 内容 | 说明 |
|---|---|---|
| 核心抽象 | `Gas` / `GasStack` / `IGasHandler` / `GasRegistry` | 本模组自有，**不依赖外部模组**；单位 **mB**（与流体一致） |
| 缓冲模块 | `GasBufferModule` | 储罐/机器内的气体仓，暴露 `IGasHandler` |
| 能力注册 | `ModCapabilityTypes.GAS_HANDLER`（`BlockCapability<IGasHandler, Void>`） | **仅气体端口**对外暴露（外壳不暴露，见 §17.4） |
| 兼容层（D7.11） | `StardustIndustry-MekanismEx` | 把 Mekanism 全部化学品映射为 `Gas`；Core 不依赖 Mekanism |

- `GasRegistry` 内置常见工业气体（氢/氧/氮/二氧化碳/蒸汽/天然气）作占位；
  同时安装 Mekanism 与 MekanismEx 后，其**全部**化学品（含**核材料、放射性气体**）经适配器进入注册表。
- 单位与流体一致（mB），故容量公式、速率表、参数屏格式**两种介质完全通用**。

### 16.2 同源实现（代码结构）

```
AbstractTankBlockEntity           ← 结构/锚点/容量/同步/参数屏/成型广播（介质无关）
├── TankBlockEntity               ← 流体：持 FluidBufferModule，暴露 IFluidHandler
└── GasTankBlockEntity            ← 气体：持 GasBufferModule，暴露 IGasHandler
```

- 差异收敛到 `TankMedium`（`FLUID`/`GAS`）一个枚举：它决定**外壳方块**、**缓冲模块**、
  **端口角色**（`PORT_FLUID`/`PORT_GAS`）与**客户端渲染器**。
- `TankShellBlock(TankMedium, Properties)` 由 `medium()` 决定注册哪种 BE 类型。
- `TankStructureProvider` 介质感知：`mediumOf(锚点外壳)` 定介质，随后只认同介质外壳/端口；
  `tank_frame` / `industrial_glass` 两种介质都认。

### 16.3 本轮气体落地清单（D7.10）

- [x] **T2.1 气体抽象**：`Gas` / `GasStack` / `IGasHandler` / `GasRegistry`。
- [x] **T2.2 气体缓冲**：`GasBufferModule`（容量由体积决定，行为对齐 `FluidBufferModule`）。
- [x] **T2.3 介质枚举与共用基类**：`TankMedium`、`AbstractTankBlockEntity`；
      `TankBlockEntity` 改为流体子类，新增 `GasTankBlockEntity`。
- [x] **T2.4 方块与端口**：新增 `gas_tank_shell`、`lv_gas_port`（+ BE、分类、语言、模型、配方）。
- [x] **T2.5 能力注册**：`ModCapabilityTypes.GAS_HANDLER`；**仅气体端口**对外暴露（外壳不暴露，
      见 §17.4；流体外壳同样已移除注册）。
- [x] **T2.6 结构求值介质化**：`TankStructureProvider` 支持两种介质与共用件。
- [x] **T2.7 HUD 介质化**：`TankHudData` / `TankHudLines` / TOP 插件识别两种外壳与气体端口，
      增加一行介质名。
- [x] **T2.8 气体浓度渲染**：`TankGasRenderer` 实现 §7.2（空罐不渲染、充满内腔、颜色随量加深、
      alpha ≤ 0.70）。
- [x] **T2.9 参数屏介质化**：`MachineParamsScreen` 按介质显示「流体储罐 / 气体储罐」
      与「存储流体 / 存储气体」。
- [x] **T2.10 自检**：`MultiblockSelfCheck` 增加两种外壳的 SHELL 分类、介质自报、储罐部件不得带等级。
- [x] **T2.11 MekanismEx 独立附属模组（D7.11）**：见 §17。
- [x] **T2.13 色调归一化与名称翻译**：`TankGasRenderer.normalizeTint` 修正过暗/纯白化学品色调
      （见 §7.2 第 6 条）；`Gas.nameKey` 复用 Mekanism 真实翻译键（见 §17.5）。
- [ ] **T2.12 气体容器手动右键交互**：待补（依赖各气体模组的容器物品）。

---

## 17. MekanismEx 独立附属模组（D7.11，v1.1 新增）

### 17.1 目标

让**气体端口**能直接连接 Mekanism 的**加压管道（Pressurized Tube）**，并让**全部 Mekanism
化学品**（气体、浆液、灌注物、颜料，含**核材料、放射性气体**）都能存进气体储罐。
Core 不依赖 Mekanism，未安装扩展时仍保留自建的气体能力。`StardustIndustry-MekanismEx`
同时硬依赖 Core 与 Mekanism；选择安装该扩展时必须提供这两个前置。

### 17.2 Mekanism 1.21.1 的现实

Mekanism 1.21.1 **已无独立的「气体」类型**：气体、浆液、灌注物、颜料统一为
`mekanism.api.chemical.Chemical`，放在同一个注册表里。管道能力是
`mekanism.common.capabilities.Capabilities.CHEMICAL.block()`，处理器接口为
`mekanism.api.chemical.IChemicalHandler`。因此本适配器是**化学品级**的桥接，气体只是其中一类。

### 17.3 实现

实现位于 `StardustIndustry-MekanismEx/src/main/java/com/stardustindustry/mekanismex/`：

| 类 | 职责 |
|---|---|
| `MekanismGasBridge` | `ChemicalStack` ↔ `GasStack` 互转。化学品注册 ID 原样作为气体 ID（保留 `mekanism:` 命名空间），色调取 `Chemical.getColorRepresentation()`、名称键取 `Chemical.getTranslationKey()`；量从 `long` 钳到 `int`（本模组容量上限远低于该值） |
| `MekanismChemicalHandlerAdapter` | 把本模组 `IGasHandler` 包装成 `IChemicalHandler`，把 Mek 管道的 fill/drain 转发到气体仓 |
| `MekanismCompat` | 入口：`registerCapabilities`（给 `lv_gas_port` 注册 `Capabilities.CHEMICAL`）与 `registerGases`（全量化学品扫描入 `GasRegistry`） |

- **ID 不合并**：Mek 的 `hydrogen` 记为 `mekanism:hydrogen`，与本模组的
  `stardustindustry:hydrogen` 是两种气体——否则会静默吞掉一个外部 ID。
- **全量注册 + 惰性兑底**：注册表冻结后一次性把全部化学品（实测 **65** 种）注册进 `GasRegistry`；
  `MekanismGasBridge.gasForId` 另留惰性解析，覆盖数据包/附属模组后加的化学品。
- **加载期安全**：Core 不引用任何 Mekanism 类型；扩展由模组元数据声明 Mekanism 硬依赖，
  前置缺失时由 NeoForge 在加载扩展类之前给出明确的依赖错误。

### 17.4 只有端口能连（流体与气体一致）

气体端口与流体端口是**唯一**对外的接口；**储罐外壳（墙）不暴露任何能力**。
本次同时**移除了流体外壳原本的 `IFluidHandler` 注册**，使两种介质行为一致：
管道不能贴在罐壁上绕过端口速率。储罐只能通过端口进出。

### 17.5 结果

实机验证（开发环境，装有 Mekanism 10.7.19）：启动日志输出
`Mekanism bridge: registered 65 chemicals as storable gases` 与
`Mekanism detected: gas ports now also accept Mekanism chemical pipes`，服务器到达 `Done`，
自检通过。Mek 加压管道可直接对接气体端口抽/灌气体。

### 17.6 颜色与名称（实机调试记录）

实机测试发现的问题与最终根因：

1. **颜色读不出（红石/过热钠不变色、甚至发蓝）**：
   - 第一层原因：化学品原始色调有两个极端——过暗（红石 `0xB30505`、碳 `0x2C2C2C`）
     感知亮度低，融进阴影像空罐；近白（氢气、蒸汽 `0xFFFFFF`）无色相。
     由 `TankGasRenderer.normalizeTint` 在 **HSL 空间**归一化（保持色相、明度抬到 0.62、饱和度抬到 0.55），
     见 §7.2 第 6 条。
   - **真正根因**：`RenderType.translucent()` 着色器计算 `纹理 × 顶点色`，而顶点 UV 写死 `(0,0)`，
     采样到方块图集左上角的任意像素（偏蓝），**把任何 RGB 都染蓝**。修复：采样模组自带的
     1×1 纯白贴图 `gas_white`（§7.2 第 7 条）。修好后红石＝鲜红、绿颜料＝鲜绿，与 Mek 一致。
   - **介质颜色源**：`Gas` 的颜色就是 Mekanism 的 `Chemical.getColorRepresentation()`，
     其内部即 `getTint()`——**与 Mek 自己渲染用的是同一个值**，无需另找来源。
2. **名称不翻译**：`Gas` 自建键 `gas.mekanism.redstone` 在语言文件里不存在，HUD 直接漏出原始键。
   `Gas` 现带可选 `nameKey`，桥接时填 `Chemical.getTranslationKey()`（`chemical.mekanism.redstone`），
   直接复用 Mekanism 的语言文件，界面显示真实名称。无 `nameKey` 时仍回退到自建键。
   `Gas` 仍是「一个 id + 一个色 + 一个可选名字键」的纯数据，核心不含 Mek 类型。
3. **不透明度恒 ≤ 70%**：曾尝试对非气体化学品（红石等）改用不透明渲染以贴近 Mek，
   但**废弃**——用户明确要求气体一律按浓度渲染、最高 70%，否则看起来像一整块流体。
   最终所有介质共用同一条浓度曲线，不区分气体/非气体。

---

## 18. 跨区块与区块卸载（v1.1 新增）

储罐最大 9×9×9，一个区块只有 16×16，**横跨两个甚至更多区块是常态**。而整个储罐只有
**一个方块实体**：它挂在锚点方块所在的区块上，只有该区块加载时才 tick。

### 18.1 问题

服务器或单机在**锚点区块已加载、储罐另一部分所在区块被卸载**时（玩家走到视距边缘、
多人服务器里另一名玩家在附近活动），锚点区块仍在 tick，于是储罐**每 20 tick 重扫整个包围盒**
（`TankStructureProvider.revalidateWhileFormed()` 返回 `true`）。

而 `Level.getBlockState(pos)` 对**未加载区块返回空气（`VOID_AIR`）**——不会加载区块，也不报错。
后果：

1. 未加载那部分的框架/外壳被当成"空气" → 扫描失败；
2. 储罐被判为**未成型** → `onFormedChanged(false)`、**端口解绑**、管道/Mek 加压管断开；
3. 玩家返回、区块重新加载 → 再次成型 → **成型/解体的抖动循环**，反复广播与端口绑定；
4. 更危险：**容量由结构体积决定**（`interiorAirCells × bucketsPerAirBlock`）。若洪水填充在
   未加载处中断，会算出**偏小的包围盒与容量**，存在**内容物被截断**的风险。

### 18.2 规则：未全加载就"冻结"

> **一句话规则：结构覆盖的区块没全部加载，就跳过本次求值，保持现状。**

- 不判失败、不解绑端口、不改容量、不广播；
- 区块全部回来后再正常扫描，自动恢复；
- 对所有多方块通用（动态与静态一致）。

### 18.3 实现

| 组件 | 职责 |
| --- | --- |
| `StructureProvider.footprint(controller, facing)` | 结构求值**需要哪些区块已加载**；默认返回 `null` = 无限制 |
| `StructureChunkGuard.allLoaded(level, footprint)` | `null` 直接放行；否则逐块 `level.hasChunk(cx, cz)` |
| `StructureChunkGuard.chunksOf(min, max)` | 由包围盒算区块集合（用 `>> 4`，负数坐标正确） |
| `StructureChunkGuard.reachChunks(controller, reach)` | 由锚点算"最远能伸到哪"的区块集合 |
| `MachineBlockEntity.revalidate(...)` | 求值**前**检查；未全加载则 `return formed;`（冻结） |
| `MachineBlockEntity.install()` | 安装前同样检查；未全加载则拒绝，避免把"有洞"的结构拍进账本 |

各 provider 的 footprint：

- **静态（`StaticStructureProvider`）**：由 `StructureModel` 的固定格与 base 槽 + 当前朝向算出精确覆盖区块。
- **旧式定义（`StructureDefinition`）**：由 `parts()` 偏移 + 朝向算出，见 `MachineBlockEntity.definitionFootprint`。
- **动态（`DynamicStructureProvider`）**：盒子在扫描时才知道，所以**预留最大可达范围**
  `reachChunks(controller, MAX_SIZE)`。
- **储罐（`TankStructureProvider`）**：同上，预留 `reachChunks(controller, TANK_MAX_SIZE)`。
  洪水填充**只会在区块齐全时运行**，锚点判定（字典序最小）因此不会被缺失区块干扰。

### 18.4 自检

`MultiblockSelfCheck.checkChunkFootprint()` 在启动时钉住几何：

- 单区块内的盒子 → 恰好 1 个区块；
- 跨越 X/Z 边界的盒子 → 恰好 4 个区块；
- `reachChunks(ZERO, 9)`（跨原点）→ 恰好 4 个区块且含 `(-1,-1)` 与 `(0,0)`
  （验证负数坐标不被"向零取整"漏掉）；
- `footprint == null` → 恒放行（"无限制"契约）。

> 记忆：**流体看液面、气体看浓度，多方块看区块——区块不齐就冻结。**

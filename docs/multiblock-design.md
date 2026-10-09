# 星砾工业 · 多方块框架设计文档（v2.2）

> 状态：**设计稿（大部分已锁定，待少量批复）**
> v2 变更：由"全动态"修正为 **静态机器为主 + 动态机器为辅** 的双轨制。
> v2.1 变更：吸收第二轮批复——**电压等级体系**（LV/MV/HV/EHV，合成表区分、不绑定材质）、**基座槽不得为空**、**端口仅在基座层**、**安装工具兼拆除 + 参数 GUI + 拆解返还**（未产出则输入全额返还）、**控制端口 = 3×3 九格输入/输出面板（带过滤）**、**加成无边际递减**、**成型只替换上方主体、底座保留**。
> v2.2 变更：**储罐分介质**（流体储罐 / 气体储罐，结构同源、仅介质不同）与**部件命名正式化**（`fluid_tank_shell` / `gas_tank_shell` / `industrial_glass` / `lv_gas_port` 等）；新增**通用气体抽象**（`Gas` / `GasStack` / `IGasHandler` / `GasRegistry` + `GasBufferModule`）；**气体渲染按浓度**（非液面）。详见 §15。
> 关联代码：`com.stardustindustry.stardustindustry.multiblock` / `.machine` / `.gas`
> **本文档是框架主文档**：双轨制、等级、加成、结构求值、投影、参数 GUI 等**通用机制**以本文为准；
> 储罐（流体/气体共用）的专项设计见 [`tank-multiblock-design.md`](./tank-multiblock-design.md)。

---

## 0. v2 核心变更摘要

v1 假设所有机器都是动态矩形。**这是错的。** 真实的 Minecraft 工业模组里，绝大多数机器有**固定外形**——它们要套用精美的模型和动画（像沉浸工程那样），固定形状才能让美术与结构一致。

v2 确立**双轨制**：

| | **静态机器（主力，~90%）** | **动态机器（少数）** |
|---|---|---|
| 例子 | 粉碎机、研磨机、离心机、熔炉、反应釜… | 电容库、大型储罐、锅炉、塔器 |
| 形状 | 固定，逐格 DSL 定义（占地如 5×10×4） | 动态矩形 2×2×2 ~ 9×9×9 |
| 美术 | 套用专属模型 + 动画 | 用统一的框架/外壳方块拼 |
| 自由发挥处 | **仅"基座层"**（最底 5×10×1） | 整个外壳与内部 |
| 成型方式 | 用**安装工具**右击控制器 | 搭好自动成型 |
| 加成来源 | 基座格放**工程块**（与端口/底座块争位） | 内部填充块 |
| 改动 | 成型后锁定，破坏即整体拆解 | 可随时改 |
| 成型后外观 | 主体方块变**隐形**，控制器 BER 绘制整体模型+动画（**无碰撞**）；**底座保持可见** | 保持方块外观 |
| 等级（电压） | **底座方块等级**决定整机电压；端口须与底座同级 | **框架方块等级**决定电压；端口须与框架同级 |

**统一之处**：两者共用 `MachineBlockEntity`、模块系统、端口、加成（`ModifierSet`）机制，只是"结构从哪来"不同——由一个 **`StructureProvider`** 抽象决定。

---

## 1. 设计目标与原则

### 1.1 目标

- 让机器**看起来是真实的工业设备**：有底座、有主体、有动画。
- 让玩家有**有限但真实的自由度**：不是无脑方形，而是"在指定的接线区自由布线"。
- 让**结构即工厂**：加成来自实体方块（工程块），不是抽象卡片。
- 让**判定可解释**：搭错能指出具体哪一格、该放什么。

### 1.2 五条铁律

1. **绝大多数机器是静态的**。动态只是给"容器类"用的特例。
2. **静态机器只在基座层自由**，上方主体严格投影；**成型只替换上方主体，底座保留可见**。
3. **成型是玩家主动动作**（安装工具），不是自动吸附；成型后锁定（底座除外）。
4. **破坏已成型机器 = 整体拆解**；拆除机返还全部加工产物与原材料，但**破坏结构格（非正常拆除）则销毁**。
5. **等级统一**：一台机器只允许一种电压等级，由底座（静态）/框架（动态）的**方块等级**决定；所有端口必须同级。等级由**合成表**区分，不绑定材质外观。

---

## 2. 术语表

| 术语 | 定义 |
|---|---|
| 静态机器 | 外形固定、逐格定义的机器。主力类型。 |
| 动态机器 | 外壳尺寸可变的机器（2³~9³）。存储/容器/锅炉类。 |
| 基座层 | 静态机器最底部的一层（如 5×10×1）。玩家可自由放**底座块/端口/工程块**。 |
| 底座块 | 基座槽的默认填充（不放端口/工程块时）。**同层底座等级统一，决定整机电压等级**。 |
| 接线区 | 基座层的别称，强调其"接管道电缆"的用途。 |
| 槽位 | 基座层中的一个格子。可放：**端口 / 工程块 / 底座块**（三选一，不得为空）。 |
| 安装工具 | 玩家手持、右击控制器以"确认搭建"的物品；**Shift+右键任意结构格打开参数 GUI，GUI 内可拆除**。 |
| 成型 | 结构校验通过 + 用安装工具激活后的状态。套用正式模型，**只隐形主体**、底座保留。 |
| 拆解 | 通过 GUI 按钮拆除 → 整体还原 + **返还**（未产出则输入全额返还）。 |
| 破坏 | 用镐子挖掉结构格 → 整体解体，**加工品销毁**（惩罚）。 |
| 电压等级 | 整机能力档位（LV/MV/HV/EHV），由底座/框架的**方块等级**决定（合成表区分，不绑定材质）；端口须同级。 |
| 控制端口 | 一个方块 = 3×3 共 **9 个信号格**，每格配置为输入或输出并带过滤，可桥接红石与 CC。 |
| 加成（ModifierSet） | 由基座工程块（静态）/内部填充块（动态）统计出的效率/能耗/并行/缓冲。 |

---

## 3. 双轨总览

```
                        ┌───────────────────────────────┐
                        │      MachineBlockEntity         │  ← 唯一基类
                        │  · ModuleHost (模块)            │
                        │  · formed / locked 状态          │
                        │  · ModifierSet 缓存              │
                        │  · StructureProvider (策略)      │
                        └──────────┬────────────────────┘
                                   │
              ┌────────────────────┴───────────────────┐
              ▼                                         ▼
   StaticStructureProvider                   DynamicStructureProvider
   ───────────────────────                   ───────────────────────
   · 逐格 DSL 定义主体                        · BoundingBoxScanner
   · 基座层槽位校验（端口/工程块/底座块）        · 框架/外壳/内部角色
   · 需要安装工具激活                          · 自动成型
   · 成型只隐形主体、底座保留                    · 可自由增删
   · 拆除返还 / 破坏销毁                        · 框架等级决定电压
              │                                         │
              └────────────────┬────────────────────────┘
                               ▼
                     ModifierSet（共用加成机制）
                               ▼
              RecipeRunnerModule / EnergyBufferModule 读取
```

**端口、模块、能力、加成全部共用。**两种机器的差异被隔离在 `StructureProvider` 一个接口后面。

---

## 4. 静态机器（主力）

### 4.1 结构组成（以粉碎机 5×10×4 为例）

```
 y=3  ┌─────────────────────┐   ← 主体（严格投影）
      │  固定：机身/传动/刀盘   │
 y=2  │  固定                 │
 y=1  │  固定（主体上不开端口） │
 y=0  └──┬─┬─┬─┬─┬─┬─┬─┬─┬─┘   ← 基座层（5×10×1，自由）
         ↑ 每个格子 = 一个"槽位"，三选一，不得为空
```

- **主体（y ≥ 1）**：DSL 逐格写死"这一格必须是某方块"。完全固定。
  **端口不允许开在主体上**（你确认：所有端口只能开在基座层）。
- **基座层（y = 0）**：一格一个槽位。每槽**必须**放三者之一（**不允许空**）：
  - **端口**（物品/流体/能量/自定义控制）——用于连接外部
  - **工程块（填充块）**——换取加成
  - **底座块**——默认填充，不放口也不换加成时用它填满
- **同层底座等级必须统一**：整台机器只有一种电压等级，由底座方块等级决定。
  端口等级也必须与该等级一致（见 §4.5）。

> 这就是你说的"基座就是机器底部，留给玩家随意发挥"。**空槽是非法结构**，
> 所以未放满的基座层会导致安装失败并投影提示。

### 4.2 结构与模型同源

你确认："**结构与模型同源**"。因此 DSL 定义的不只是校验规则，还携带：

```java
StructureModel.builder("crusher")
    // 逐格：位置 -> 可接受方块（标签或具体块）
    .slot(0,0,0, SlotType.BASE_SLOT)              // 基座槽（自由）
    .fixed(1,0,0, Blocks.IRON_BLOCK)              // 固定块
    // 模型与动画引用
    .model(StardustIndustry.id("block/crusher"))  // 客户端模型
    .animation(AnimationSlot.SPIN, ...)           // 动画定义
    .build();
```

客户端渲染与结构校验读**同一份数据**，不会出现"模型和结构对不上"。

### 4.3 安装工具与成型流程

```
1. 玩家按投影把主体搭好，基座层放满底座块/端口/工程块（不得留空）
2. 手持"安装工具"，右击控制器
3. 服务端校验结构：
   ├─ 通过 → formed = true, locked = true
   │         ① 记录每一格"成型前的原始方块状态"（用于将来拆解还原）
   │         ② 把**上方主体**的方块换成"隐形方块状态"
   │            （★ 基座层保持原样可见，不隐形；你确认"底座不变，只替换上面"）
   │         ③ 客户端由控制器 BER 以控制器为锚点绘制整体模型 + 动画
   │         ★ 无碰撞：成型模型不注册碰撞箱，玩家可直接穿过
   └─ 失败 → 不成型，投影指出缺失格（见 §7）
4. 成型后：
   ├─ 主体结构格不可再交互修改（挖掉会触发解体）
   ├─ 基座层的端口照常工作（暴露能力）
   └─ 机器开始工作
```

**成型后"原方块变隐形"的机制**（你确认的方案）：

- 每个参与的结构方块，成型时被替换为一个**隐形方块状态**。
- 隐形方块仍属于该机器结构（可被扫描器识别为"属于这台已成型机器"），
  但它**不渲染**，也没有碰撞。
- 客户端由**控制器的 `BlockEntityRenderer`** 以控制器位置 + 朝向为锚点，
  一次性绘制整台机器的模型与动画。美术设计时会保证模型与原方块布局对齐，
  因此"方块变隐形、模型盖上来"在视觉上是无缝的。

**必须配套：原始方块记录（Original State Ledger）**

因为成型后方块已被换成隐形状态，**无法再从世界反推原来是什么**。所以成型那一刻必须把每格的原始 `BlockState` 与额外数据（例如端口类型、工程块类型）记录进控制器的 BlockEntity：

```java
// 控制器 BlockEntity 持有
List<PlacedPart> placedParts;   // { offset, originalState, role, fillerKind, portKind }
```

拆解时用这份记录逐一还原。这也让"整体拆解"变得确定、可测试。

### 4.4 拆除 vs 破坏（两种退出方式）

你确认："安装工具可以**安全拆除**；Shift+右键任意结构格弹出参数 GUI，内含拆除按钮；
破坏任意一格则整体拆解（加工品销毁）"。

**路线 A：安装工具安全拆除（正常方式）**

```
手持安装工具，Shift + 右键机器的任意结构格
  └─ 打开"机器参数 GUI"（见 §4.6）
       └─ 点"拆除"按钮
            ├─ 整个结构解锁、解除成型
            ├─ 依 Original State Ledger 把每格还原为"成型前的原始方块"并**全部掉落返还**
            ├─ 机器内部已完成的产物 → 全部返还给玩家
            ├─ 加工中的配方 → **只要还没产出任何输出，输入 100% 全额返还**
            │   （已产出输出则按完成处理，产出照返）
            └─ 控制器回到未成型状态（需重新用安装工具激活）
```

**路线 B：镐子破坏任一结构格（粗暴方式 / 惩罚）**

```
玩家挖掉成型机器的任意一格
  ├─ 整个结构解锁、解除成型
  ├─ 依 Ledger 还原/掉落结构方块
  ├─ 控制器回到未成型状态
  └─ 机器内部正在加工的、以及缓存的物品 → **直接销毁**（惩罚）
```

**为什么这样设计**：安全拆除让"建造—调试—重建"不折磨玩家；
破坏惩罚则防止把机器当成无损中转站或边跑边改结构 exploit。
两者语义清晰：**你想回收就用工具，图省事就承受损失。**

### 4.5 电压等级体系（本轮修订）

你确认："**用合成表区分等级即可，不必用金属区分。直接叫 LV 基座、MV 基座……**
这样自由度更高，不受直觉影响。"

**等级阶梯**（沿用业界惯例，见 `energy/EnergyTier`）：

| 等级 | 底座 / 框架 / 端口方块命名 | 如何区分 | 传输速率 | 内部缓存 | 基础待机耗电 |
|---|---|---|---|---|---|
| LV | `lv_base` / `lv_frame` / `lv_*_port` | 合成表 | 低 | 小 | 低 |
| MV | `mv_base` / `mv_frame` / `mv_*_port` | 合成表 | 中 | 中 | 中 |
| HV | `hv_base` / `hv_frame` / `hv_*_port` | 合成表 | 高 | 大 | 高 |
| EHV | `ehv_base` / `ehv_frame` / `ehv_*_port` | 合成表 | 极高 | 极大 | 极高 |

**关键设计点**：

1. **等级不带材质语义**。方块就叫"MV 底座"，用什么材料是美术与合成表的自由，
   不被"铜=低压"这类现实直觉绑死。
2. **等级由合成表 + 配方成本决定**：越高级，配方越贵、越需要前置产能。
   这是纯粹的**进度门槛（progression gate）**，干净可调。
3. **底座方块等级 = 整机电压等级**（静态机器）。底座层所有底座块必须**同等级**。
4. **端口等级必须与底座同级**。混级（MV 底座配 HV 端口）= 非法结构，安装失败并提示。
5. **框架等级 = 整机电压等级**（动态机器）。12 条棱同级，其上端口同级。
6. **等级越高 ≠ 无脑更好**：待机耗电随等级上升，低负载机器用高等级会浪费电，
   形成真实的"选型"决策。
7. **视觉一体化**：同等级的底座与端口做"连接材质（connected textures）"，
   拼起来像一整块（你明确要求）。
8. **可共享外观**：若之后想让不同等级用不同金属外观，只需换模型/贴图，
   **不影响任何逻辑**——因为等级只在数据里，不绑定材质。

> 与 `ModifierSet` 的关系：**等级决定"能力上限"（速率/缓存/电压/待机），
> 工程块决定"相对倍率"（效率/能耗/并行）。**二者独立相乘。

### 4.6 机器参数 GUI（安装工具 Shift+右键）

你确认：Shift+右键任意结构格 → 弹出 GUI，显示工程块数量与对应加成，并提供拆除按钮。

```
┌─ 粉碎机 · 参数 ───────────────── MV ─┐
│ 结构尺寸 : 5 × 10 × 4                │
│ 电压等级 : MV（底座：不锈钢）          │
│                                      │
│ 工程块统计          加成              │
│  研磨核心 ×3    效率 +300% / 能耗 +360%│
│  换热核心 ×1    能耗 −30%             │
│  并行核心 ×2    并行 +2 / 效率 −80%   │
│  ─────────────────────────────       │
│ 合计 : 效率 ×2.2  能耗 ×3.3  并行 3   │
│                                      │
│ [ 拆除机器 ]        [ 关闭 ]          │
└──────────────────────────────────────┘
```

- **只读展示** + 一个拆除动作；不改结构（结构已锁定）。
- 未成型时 Shift+右键可显示"还缺什么"（等价于投影文字版）。
- 该 GUI 也是未来机器"状态面板"的雏形。

#### 4.6.1 统一参数界面（规划）

> **决定（2026-10-08）**：参数 GUI 最终对**所有机器**采用统一的新格式，即
> "先一句成型状态，再逐行列出该机器真正有意义的数值"。**储罐已按此格式实现**，
> 其余机器按此规范逐个迁移（迁移时机由后续批复确定）。

统一格式的骨架：

```
<成型状态行，亮黄色>
多方块尺寸：3X3X3
多方块结构：储罐
<该机器专属字段……>
```

约定：

- **第一行**是成型状态。成型时用亮黄色（`0xFFFF55`）的
  `多方块结构已成型！`；未成型时用中性色提示，且不列出后续字段
  （避免"未成型却报数值"的歧义）。
- **其余字段**用深色正文（`0x404040`），面板底色为浅灰（`0xFFC6C6C6`），
  保证深色字可读。
- **字段随机器而异**，由服务端快照决定，界面不猜测。储罐字段见
  `tank-multiblock-design.md` §9。
- 快照对"有专属讲法的机器"附带一个额外数据块（当前是
  `MachineParamsData.TankParams`），普通机器为 `null`，界面自动回落到
  通用字段布局。这样一套快照类型即可承载两种界面，无需为每种机器新增类型。

---

## 5. 动态机器（少数）

适用于：**电容库、大型储罐、锅炉、塔器**等"本身就是一大坨容器"的设备。

> **本节的通用规则适用于"按等级"的动态机器（如将来的锅炉/塔器）。**
> **储罐是特例：它无等级、框架无等级、内部必须纯空气、且用洪水填充判定而非射线扫描。**
> 储罐还分**两种介质**：**流体储罐**（`fluid_tank_shell`）与**气体储罐**（`gas_tank_shell`）。
> 两者**结构、规则完全一致**，唯一区别是**介质**（一个存液体、一个存气体）与**外壳方块/端口**；
> **储罐框架 `tank_frame` 与工业玻璃 `industrial_glass` 两者共用**。
> 储罐的完整、权威设计见独立文档
> [`tank-multiblock-design.md`](./tank-multiblock-design.md)。本节不再描述储罐细则。

### 5.1 规则（通用动态机器）

- 矩形外壳，每维 3~9。
- **12 条棱 = 框架块**（必须先成型）。**框架有 LV/MV/HV/EHV 等级，棱上等级统一 = 整机电压等级**；
  等级越高效率越高，但**基础待机耗电越高**（与静态机器同规则，见 §4.5）。
- **6 个面（去掉棱）= 外壳块**（可换材料，只影响外观/耐久）。
- **控制器占壳面上的一格**（等价于该面少放一块外壳），因此玩家可以先把控制器嵌进墙里再围出密闭体；扫描器把控制器格视作该面的一部分，沿墙行进不会被同面墙块截断（详见 §12.1②）。
- **内部 = 空气或填充块**，填充块按类型×数量加加成。
- 端口嵌在**外壳的面**上；**不允许放在棱上**（你确认：棱上只允许框架）。
- **端口等级必须与框架同级**，混级非法。
- 搭好**自动成型**（不需要安装工具，因为形状本身开放）；成型时向附近玩家广播中文提示。

### 5.2 为什么只有这些用动态

因为它们的"美观"本来就来自体积与堆叠，而不是精细模型；且玩家会想要任意大小的储罐/锅炉。而粉碎机这类有机械运转动画的设备，必须固定外形才能把模型做好。

---

## 6. 加成系统（静态与动态共用）

### 6.1 数据模型

```java
public record ModifierSet(
    float speedMultiplier,   // 效率（工程块）
    float energyMultiplier,  // 能耗倍率（工程块）
    int   parallelBonus,     // 并行（工程块）
    float bufferMultiplier   // 缓冲（工程块）
) {
    public static final ModifierSet BASE = new ModifierSet(1f, 1f, 0, 1f);
}
```

**等级不在 `ModifierSet` 里**：等级是"能力上限"，单独由机器结构决定，
用 `EnergyTier` + 一组等级系数表达（传输速率、缓存基数、待机耗电）。
最终能力 = `等级基数 × ModifierSet 倍率`（§4.5 已说明二者独立相乘）。

### 6.2 填充块 = 成对取舍

你给的例子："效率 +1 倍，能耗 +1.2 倍"。

```java
public record FillerModifier(
    float speedDelta,     // 例如 +1.0 表示 +100%
    float energyDelta,    // 例如 +0.2 表示 +20%
    int   parallelDelta,
    float bufferDelta
) {}
```

| 工程块 | 效率 | 能耗 | 并行 | 缓冲 | 定位 |
|---|---|---|---|---|---|
| 研磨核心 | +100% | +120% | — | — | 高产出高耗能（你给的例子） |
| 换热核心 | — | −30% | — | — | 省电 |
| 并行核心 | −40% | +50% | +1 | — | 多任务 |
| 缓冲核心 | — | — | — | +50% | 存储 |

> 数值只是示意，机制才是重点：**没有纯白给的加成**。玩家要在"要速度还是要电费"里做选择，这也是你要的"真实感"。

### 6.3 静态机器如何拿到加成

- 基座层每个**工程块**贡献一次对应加成。
- 基座层每个**端口**不贡献加成（但它连接了外部）。
- 于是玩家在"多接几个口"和"多要一点加成"之间权衡——你确认的核心玩法。

### 6.4 动态机器如何拿到加成

- 内部每个填充块贡献加成。
- 尺寸参与缓冲基数（大容器天然缓冲大）。
- **不做边际递减**，因为工程块本身已带负面惩罚（见 §6.2），无需再加一层。
  （你确认："加工块不需要边际递减，已经有负面惩罚了。"）

### 6.5 接入机器

`RecipeRunnerModule`：

```java
ModifierSet m = machine.modifiers();
int time  = Math.max(1, Math.round(recipe.processingTime() / m.speedMultiplier()));
int cost  = Math.max(1, Math.round(recipe.energyCost() * m.energyMultiplier()));
int par   = 1 + m.parallelBonus();
```

`EnergyBufferModule.capacity()` × `bufferMultiplier()` × `tier.capacityFactor()`。

`ModifierSet` 在**结构成型/变化时**重算并缓存，不每 tick 计算。

### 6.6 自定义控制端口（本轮修订）

你确认（最终版）：

> "**输入输出为同一个方块**，左边按钮可把每格配置为输入或输出。
> **默认 9 个格子**，每格可设**过滤**，类似 AE2 的 ME 接口。
> 可扩展，可联动 CC 电脑。"

因此控制端口是**一个方块、内含 3×3 = 9 个可配置"信号格"**：

```
┌─ 控制端口 · 配置（MV）─────────────┐
│  [输入] [输入] [输出]   ← 每格左上角按钮切换 输入/输出
│ ┌────┐┌────┐┌────┐                  │
│ │ 输入││ 输入││ 输出│   ← 9 格 (3×3)  │
│ │ 过滤││ 过滤││ 过滤│      每格可设过滤│
│ └────┘└────┘└────┘                  │
│ ┌────┐┌────┐┌────┐                  │
│ │ 输入││ 输出││ 输出│                  │
│ └────┘└────┘└────┘                  │
│ ┌────┐┌────┐┌────┐                  │
│ │ 输出││ 输入││ 输出│                  │
│ └────┘└────┘└────┘                  │
└──────────────────────────────────────┘
```

**规则**：

| 项 | 说明 |
|---|---|
| 通道数 | 默认 **9**（3×3），等于格数——**一格就是一个通道**，不再另设固定 4+4 |
| 每格方向 | **输入** 或 **输出**，由该格左上角按钮切换 |
| 每格过滤 | 类似 AE2 ME 接口的过滤槽：只有匹配的信号/内容才通过 |
| 输入格语义 | 接收外部信号 → 驱动机器（开机/停机/暂停/锁定并行数…） |
| 输出格语义 | 由机器状态触发的信号（没材料/缓存满/能量不足/运行中/进度阈值…） |
| 红石 | 每格可绑定红石信号（读或写） |
| 过滤的作用 | 让"同一条线上不同信号"可被分流；也让信号与"某物品/某流体"绑定 |
| CC:Tweaked | 把 9 格暴露为一个**外设（peripheral）**，提供按格读写的方法 |
| 可扩展 | 通道数、条件类型都可随版本增加，界面自动容纳 |

**为什么这样更好**：不再需要"输入端口方块"和"输出端口方块"两种实体，
一个端口方块就是一块**可自由配线的逻辑面板**，和 AE2 接口的心智模型一致。

> 与功能端口（物品/流体/能量）并列：功能端口管**物理资源**，控制端口管**逻辑信号**。

---

## 7. 结构与投影（StructureProvider 抽象）

### 7.1 接口

```java
public interface StructureProvider {
    /** 对控制器所在世界做一次结构求值。 */
    StructureEvaluation evaluate(Level level, BlockPos controller, Direction facing);

    /** 结构变化时是否自动重扫（静态机器在成型后不重扫）。 */
    default boolean revalidateWhileFormed() { return false; }

    /** 求值需要哪些区块已加载；null = 无限制（见 §7.5）。 */
    default Collection<ChunkPos> footprint(BlockPos controller, Direction facing) { return null; }
}
```

两个实现：

- `StaticStructureProvider`：逐格 DSL + 基座槽校验。
- `DynamicStructureProvider`：调用 `BoundingBoxScanner`。

### 7.5 跨区块与"未加载就冻结"

多方块可以横跨多个区块，而 `Level.getBlockState` 对**未加载区块返回空气**——若此时求值，
机器会被误判解体、解绑端口、甚至按残缺体积重算容量。规则：

> **结构覆盖的区块没有全部加载时，跳过本次求值，冻结现状（不判失败、不解绑、不改容量、不广播），
> 区块全部回来后自动恢复。**

- `StructureProvider.footprint(...)` 报告结构可能触及的区块；`null` 表示无限制。
- `StructureChunkGuard.allLoaded(level, footprint)` 逐块 `level.hasChunk(cx, cz)` 检查。
- `MachineBlockEntity.revalidate(...)` 与 `install()` 在求值/安装前检查，未全加载即冻结/拒绝。
- 静态 provider 用模型精确几何；动态/储罐 provider 用 `reachChunks(controller, MAX_SIZE)` 预留最大范围。
- 自检：`MultiblockSelfCheck.checkChunkFootprint()`（含负数坐标向零取整的回归用例）。

详见 `docs/tank-multiblock-design.md` §18。**这一条对静态与动态多方块一律生效。**

### 7.2 求值结果

```java
public record StructureEvaluation(
    boolean formed,
    ModifierSet modifiers,
    List<ScanFailure> failures,       // 失败格（供投影）
    Map<BlockPos, BlockRole> roles,   // 每格角色
    Map<BlockKind, Integer> fillers   // 填充统计
) {}

public record ScanFailure(BlockPos expectedPos, String expectation) {}
```

### 7.3 投影

- 未成型时，`StructureProjector` 输出失败格。
- 客户端渲染半透明幽灵方块（按角色着色）。
- 静态机器：投影覆盖整个主体 + 基座槽的"可放端口/工程块/底座块"提示。
- 采用"高亮下一处需修复的格子"策略，避免刷屏。
- **合法性反馈**：基座槽留空、端口等级与底座不同级 → 投影用**醒目红色**标出并给出文字原因。

### 7.4 成型后的整体渲染（客户端）

- 成型后**主体**方块已被换成**隐形方块状态**，因此主体不再有可见几何；
  **底座层保持原样渲染**（你确认"底座不变，只是替换上面的内容"）。
- 由**控制器的 BlockEntityRenderer** 负责：
  - 读取控制器的位置与朝向作为锚点；
  - 绘制**主体**模型（`formedModel`）与循环动画；
  - **不注册碰撞箱**——玩家可穿过（你确认的"直接穿过"）。
- 未成型时，控制器 BER 可绘制**投影态模型**（`unformedModel`），
  用于提示"这里将来会长成什么样"。
- 美术约束：模型尺寸与方块布局对齐，避免"方块隐形后模型对不上"的割裂感。
- **底座与端口做连接材质**，让同材质的底座/端口视觉上连成一体（见 §4.5）。

---

## 8. 方块注册与分类

### 8.1 新增方块

| 类别 | 例子 | 参与数值 | 用途 |
|---|---|---|---|
| 框架块 | `lv_frame`, `mv_frame`, `hv_frame`, `ehv_frame` | 决定等级 | 动态机器 12 棱 |
| 外壳块 | `steel_casing`, `aluminium_casing` | 否（外观/耐久） | 动态机器 6 面 |
| 底座块 | `lv_base`, `mv_base`, `hv_base`, `ehv_base` | 决定等级 | 静态机器基座层填充 |
| 填充块/工程块 | `grinding_core`, `heat_exchanger_core` | **是** | 基座槽 或 动态机器内部 |
| 功能端口 | `lv_item_port`, `mv_fluid_port`, … | 否 | 基座槽 / 动态外壳面 |
| 控制端口 | `lv_control_port` … `ehv_control_port` | 否 | 基座槽 / 动态外壳面，9 格信号面板 |
| 控制器 | `crusher`, ... | — | 每台机器一个 |
| **储罐框架** | `tank_frame` | 否 | 储罐 12 棱；**流体/气体共用** |
| **工业玻璃** | `industrial_glass` | 否 | 储罐透明面；**流体/气体共用** |
| **流体储罐外壳** | `fluid_tank_shell` | 否（体积定容量） | 流体储罐 6 面 + 控制器 |
| **气体储罐外壳** | `gas_tank_shell` | 否（体积定容量） | 气体储罐 6 面 + 控制器 |
| **流体端口（储罐用）** | `lv_fluid_port` … | 否 | 流体储罐外壳面 |
| **气体端口（储罐用）** | `lv_gas_port` … | 否 | 气体储罐外壳面 |

> **命名规则**：同一部件若因介质不同而分化，用 `<medium>_tank_<part>` 命名
> （`fluid_tank_shell` / `gas_tank_shell`）；共用部件不带介质前缀
> （`tank_frame` / `industrial_glass`）。详见 [`terminology.md`](./terminology.md)。

> **等级命名而非材质命名**：方块直接叫 LV/MV/HV/EHV，用什么材料是**合成表 + 美术**
> 的事，不承担等级语义（你确认的方案）。这样外观自由、进度门槛干净。

### 8.2 分类靠标签

```json
// data/stardustindustry/tags/block/frame.json
{ "values": ["stardustindustry:lv_frame", "stardustindustry:mv_frame", "stardustindustry:hv_frame", "stardustindustry:ehv_frame"] }
// data/stardustindustry/tags/block/shell.json
{ "values": ["stardustindustry:steel_casing", "stardustindustry:aluminium_casing"] }
// data/stardustindustry/tags/block/base.json
{ "values": ["stardustindustry:lv_base", "stardustindustry:mv_base", "stardustindustry:hv_base", "stardustindustry:ehv_base"] }
// data/stardustindustry/tags/block/filler.json
{ "values": ["stardustindustry:grinding_core", "stardustindustry:heat_exchanger_core"] }
// data/stardustindustry/tags/block/port.json
{ "values": ["stardustindustry:lv_item_port", "stardustindustry:lv_fluid_port", "stardustindustry:lv_energy_port", "stardustindustry:lv_control_port", "..."] }
// 等级标签（用于校验"同级"）
// data/stardustindustry/tags/block/tier_lv.json / tier_mv.json / tier_hv.json / tier_ehv.json
```

**等级校验**：扫描/校验时把底座方块与各端口方块各自映射到 `EnergyTier`，
不同级即判非法。等级映射集中在一处（`TierMaterials`），**加等级/改材质只需加一行**。

加新材料 = 加标签项；对外提供 `c:` 通用标签（`c:frames/*`、`c:casing/*`）以便其他模组接入。

---

## 9. 目录结构（v2）

```
com.stardustindustry.stardustindustry
├── StardustIndustry.java
├── Config.java
├── capability/           ResourceType, ResourceStack
├── gas/                                     ← 新增（通用气体抽象，本模组自有）
│   ├── Gas.java                             （气体类型：id + 色调）
│   ├── GasStack.java                        （气体 + 量，单位 mB；NBT 往返）
│   ├── IGasHandler.java                     （气体处理器接口，仿 IFluidHandler）
│   └── GasRegistry.java                     （气体注册表；内置常见工业气体 + 兼容层注册）
├── energy/               EnergyTier
├── multiblock/                              ← 静态（保留 + 扩展）
│   ├── PartRole.java                        （扩展：+BASE_SLOT）
│   ├── StructureDefinition.java             （保留）
│   ├── StructureModel.java                  ← 新增：结构+模型同源
│   ├── StructurePart.java                   （保留）
│   ├── StructureMatcher.java                （保留）
│   ├── StructureProjector.java              （扩展：双轨投影）
│   ├── StructureRotation.java               （保留）
│   ├── TierMaterials.java                   ← 新增（方块 → EnergyTier 映射）
│   ├── provider/                            ← 新增
│   │   ├── StructureProvider.java           （接口）
│   │   ├── StaticStructureProvider.java
│   │   ├── DynamicStructureProvider.java
│   │   ├── StructureEvaluation.java
│   │   └── ScanFailure.java
│   └── dynamic/                             ← 新增（仅动态机器用）
│       ├── BoundingBoxScanner.java
│       ├── MultiblockShape.java
│       ├── ShellRoleMap.java
│       └── ShellTags.java
├── modifier/                                ← 新增（双轨共用）
│   ├── ModifierSet.java
│   ├── FillerRegistry.java
│   └── FillerModifier.java
├── signal/                                  ← 新增（控制端口逻辑层）
│   ├── SignalChannel.java                   （一个输入/输出通道）
│   ├── SignalCondition.java                 （触发条件枚举）
│   └── SignalBus.java                       （绑定到机器状态/红石）
├── machine/
│   ├── MachineBlock.java                    （已改造 facing）
│   ├── MachineBlockEntity.java              （接 StructureProvider）
│   ├── MachineModule.java
│   ├── ModuleHost.java
│   ├── MachineTier.java
│   ├── CasingBlock.java                     （外壳）
│   ├── FrameBlock.java                      ← 新增
│   ├── FillerBlock.java                     ← 新增
│   ├── BaseBlock.java                       ← 新增（底座块，带等级）
│   ├── InvisibleStructureBlock.java         ← 新增（成型后的隐形结构格）
│   ├── MachinePortBlock.java
│   ├── MachinePortBlockEntity.java
│   ├── PlacedPart.java                      ← 新增（原始方块记录条目）
│   ├── tool/InstallationToolItem.java       ← 新增（安装工具：右击安装 / Shift+右键参数GUI）
│   ├── module/  （Energy / ItemInventory / RecipeRunner / FluidBuffer / **GasBuffer**）
│   ├── port/    （Item / Fluid / **Gas** / Energy / Control）
│   ├── tank/    ← 储罐（流体/气体同源）
│   │   ├── TankMedium.java                  （FLUID / GAS：介质枚举，决定外壳块与文案）
│   │   ├── AbstractTankBlockEntity.java     （共用：结构/锚点/容量/同步/参数/广播）
│   │   ├── TankBlockEntity.java             （流体储罐控制器：FluidBufferModule）
│   │   ├── GasTankBlockEntity.java          （气体储罐控制器：GasBufferModule）
│   │   ├── TankShellBlock.java              （外壳块，按介质选自 BE 类型）
│   │   ├── TankFrameBlock.java / TankGlassBlock.java （共用框架 / 工业玻璃）
│   │   └── TankHudAccess.java               （任意格 → 储罐控制器）
│   └── crusher/ （重写为静态示例）
├── recipe/               ModRecipes, ProcessingRecipe, ProcessingRecipeInput
├── compat/jei/           StardustJeiPlugin, CrushingCategory
├── compat/cc/            ← 新增（CC:Tweaked 软依赖）
│   └── ControlPortPeripheral.java           （把控制端口暴露为计算机外设）
├── client/                                  ← 新增（客户端渲染）
│   ├── MachineRenderer.java                 ← 控制器 BER：成型/投影模型 + 动画
│   ├── StructureGhostRenderer.java          ← 幽灵方块投影渲染
│   ├── MachineParamsScreen.java             ← 机器参数 GUI（Shift+右键）
│   ├── ControlPortScreen.java               ← 控制端口通道配置 GUI
│   └── ClientSetup.java                     ← 注册 BER
└── registry/             ModRegistries, ModBlocks, ModItems, ModBlockEntities, ModCapabilities
```

---

## 10. 关键运行流程

### 10.1 静态机器一生

```
放置控制器 → (未成型, 投影显示需搭建)
   ↓ 玩家搭主体 + 基座层放满底座/端口/工程块（同级！）
安装工具右键 → StaticStructureProvider.evaluate()
   ├─ 失败：投影指出缺失格 / 空槽 / 混级（红色）
   └─ 成功：formed=true, locked=true
            主体方块 → 隐形状态（底座保留可见）
            客户端切换成型模型 + 动画
            电压等级由底座方块等级决定
            ModifierSet 由基座工程块算出并缓存
   ↓
工作循环：RecipeRunnerModule / EnergyBufferModule 读 ModifierSet + 等级
   ↓
   ├─ 安装工具 Shift+右键 → 参数 GUI → [拆除] → 返还全部（加工中返还原材料）
   └─ 镐子破坏任意格     → 整体解体 + 加工品销毁（惩罚）
        两者都回到未成型
```

### 10.2 动态机器一生

```
放置控制器（锚点）
   ↓ 玩家向外搭矩形外壳
自动扫描（邻居变化/每 20 tick）
   ├─ 未成型：投影指出缺失的棱/面/非法内部
   └─ 成型：ModifierSet 由内部填充块 + 尺寸算出
   ↓
工作循环（同上）
   ↓
改动结构块 → 自动重扫（动态机器允许边改边用）
```

---

## 11. 性能策略

- 静态机器：安装后**锁定**，成型期间不重扫；只在拆解时回到未成型。
- 动态机器：邻居变化置脏；未成型每 20 tick 重扫；成型后仅 baseline 变化时重扫。
- `ModifierSet` 只在结构求值时计算，缓存在 BlockEntity。

---

## 12. 分阶段落地计划（修订）

| 阶段 | 内容 | 产出 | 状态 |
|---|---|---|---|
| **D0.5** | 既有 M1（静态 DSL、端口、JEI、破碎机多方块） | 基线 | ✅ 已完成 |
| **D1** | `StructureProvider` / `StructureEvaluation` / `ScanFailure` / `TierMaterials` / `ModifierSet` / `FillerModifier` / `FillerKind` / `BlockRole` / `PartRole.BASE_SLOT` | 抽象 + 等级映射 + 加成模型就位 | ✅ 已完成（compileJava 通过）|
| **D2** | `StaticStructureProvider` + `StructureModel`（结构/模型同源）+ 基座槽三选一校验（含"不得为空"与"同级"校验）+ `MachinePartTypes` 分类 + `MachineBlockEntity` 提供器接入点 | 静态机器可校验 | ✅ 已完成（runServer `Done`，自检通过）|
| **D3** | `InstallationToolItem`（安装/拆除）+ `InvisibleStructureBlock`（只隐形主体）+ `PlacedPart` Ledger + 拆除/破坏双路径 + 加工品返还/销毁 | 安装/拆解闭环 | ✅ 已完成（runServer `Done`，自检通过）|
| **D4** | `FillerRegistry` / `FillerBlock` / `BaseBlock`（LV–EHV 等级）+ 接入 RecipeRunner/Energy（含待机耗电） | 加成 + 等级生效 | ✅ 已完成（LV 一套闭环；runServer `Done`，自检通过）|
| **D5** | `MachineParamsScreen`（参数 GUI + 拆除按钮） | 玩家可查看参数、可安全拆除 | ✅ 已完成（runServer `Done`，自检通过）|
| **D6** | `DynamicStructureProvider` + `BoundingBoxScanner` + 框架/外壳块（含框架方块→等级） | 动态机器可校验 | ✅ 已完成（LV 框架 + 演示储罐；runServer `Done`，自检通过）|
| **D7** | 控制器 BER（投影幽灵渲染）+ 失败格客户端同步 | 未成型可看见投影 | ✅ 已完成（BER 由控制器绘制；runClient 无崩溃、`runServer` `Done`）|
| **D7.5** | 成型整机重绘（Ledger 重绘）+ 动画（`MachineAnimators`）+ 连接材质 | 视觉闭环 | ✅ 已完成（粉碎机迁 provider；runServer `Done`、runClient 无崩溃）|
| **D7.6** | 实机测试反馈修复：LV 端口贴图 / 储罐锚点与自动成型 / 投影改空位+控制器旁文字 / 全中文输出 / 任意部件开参数 GUI / 流体模块 / 隐形方块碰撞箱 | 可实测体验 | ✅ 已完成（详见 §12.1）|
| **D7.7** | **储罐重做**：无等级框架/外壳/玻璃（旧名，D7.9 已更名 `fluid_tank_shell`/`industrial_glass`）+ 洪水填充判定 + 体积定容量 + 匠魂式液面 + **主流高亮模组兼容（HUD）** | 储罐可实机使用 | ✅ 已完成（见 [`tank-multiblock-design.md`](./tank-multiblock-design.md)；高亮兼容见 §10.4） |
| **D7.8** | **占位美术资源手册**：逐张标明每张贴图对应方块，供美术替换 | 美术可接手 | ✅ 已完成（见 [`textures-placeholder-manual.md`](./textures-placeholder-manual.md)） |
| **D7.9** | **储罐部件命名正式化**：`tank_shell`→`fluid_tank_shell`、`tank_glass`→`industrial_glass`；注册 ID / 常量 / 语言键 / 模型 / 配方 / 贴图 / 文档同步 | 命名一次到位 | ✅ 已完成（不向后兼容，开发阶段） |
| **D7.10** | **储罐分介质 + 通用气体抽象**：`Gas`/`GasStack`/`IGasHandler`/`GasRegistry` + `GasBufferModule`；`TankMedium` 抽公共基类；`gas_tank_shell` / `lv_gas_port`；气体能力注册；气体**浓度**渲染 | 气体储罐可实机使用 | 🔄 进行中（见 §15） |
| **D7.11** | **Mekanism 软依赖适配器**：把 Mekanism 全部化学品（含核材料/放射性气体）映射为本模组 `Gas`；气体端口对接 Mek 加压管道；外壳不对外 | 与 Mekanism 互通 | ✅ 已完成（见 [`tank-multiblock-design.md`](./tank-multiblock-design.md) §17；实测注册 65 种化学品） |
| **D8** | `signal/` 控制端口逻辑 + `ControlPortScreen` + `compat/cc` | 逻辑接口可用 | ⬜ |
| **D9** | 重写破碎机为静态示例；数值调优 | 可玩 | ⬜ |

### D1 落地清单（已完成）

新增文件：

- `multiblock/provider/StructureProvider.java` — 双轨结构求值接口（`evaluate` + `revalidateWhileFormed`）
- `multiblock/provider/StructureEvaluation.java` — 求值结果（formed / tier / modifiers / failures / roles / fillers）
- `multiblock/provider/ScanFailure.java` — 单个失败格（世界坐标 + 期望角色 + 文案）
- `multiblock/provider/FillerKind.java` — 工程块身份枚举（含 trade 摘要）
- `multiblock/TierMaterials.java` — 方块 → `EnergyTier` 中央映射 + 同级校验
- `multiblock/BlockRole.java` — 观测角色（FRAME/PANEL/BASE_SLOT/CONTROLLER/PORT/CONTROL_PORT/FILLER/BASE/INTERIOR）
- `multiblock/modifier/ModifierSet.java` — 效率/能耗/并行/缓冲，带上限与换算
- `multiblock/modifier/FillerModifier.java` — 单个工程块的取舍增量

修改：

- `multiblock/PartRole.java` — 新增 `BASE_SLOT` + `isBaseSlot()`
- `multiblock/StructureMatcher.java` — `describe` 补 `BASE_SLOT` 分支

> D1 只搭骨架，不接入 `MachineBlockEntity`，因此现有破碎机行为不变。`TierMaterials`
> 暂为空表，待 D2/D4 引入 LV–EHV 方块后注册。

### D2 落地清单（进行中）

新增文件：

- `multiblock/model/StructureModel.java` — 静态机器描述（固定主体 + 基座槽 + 模型/动画引用），结构与模型同源
- `multiblock/model/StructureSlot.java` — 单个授权格（偏移 + 类型 + 匹配器）
- `multiblock/model/StructureSlotType.java` — `FIXED` / `BASE_SLOT` / `CONTROLLER`
- `multiblock/provider/StaticStructureProvider.java` — 求值实现：固定格匹配、基座槽三选一（不得为空）、等级一致、填充统计、角色记录
- `multiblock/MachinePartTypes.java` — 方块分类中央注册表（PORT / FILLER / BASE / CASING）
- `multiblock/MultiblockSelfCheck.java` — 启动自检（模型不变量、加成算术、等级一致性、填充枚举）

修改：

- `machine/MachineBlockEntity.java` — 新增 `provider()` 与 `evaluation()`；`maintainStructure` 分支到提供器路径；`bind/unbindProviderPorts` 按观测角色绑定端口；`setRemoved` 同时解绑
- `registry/ModBlocks.java` — `registerPartTypes()` 注册现有方块分类
- `StardustIndustry.java` — common setup 调用 `registerPartTypes()` 与 `MultiblockSelfCheck.run()`

每阶段结束 `compileJava` + `runServer` 验证 0 错误。

### D3 落地清单（已完成）

新增文件：

- `machine/PlacedPart.java` — Ledger 单条记录（世界坐标 + 原始方块状态 + 角色），NBT 往返可测
- `machine/InvisibleStructureBlock.java` — 隐形主体占位块（`RenderShape.INVISIBLE`、空碰撞/空外形）
- `machine/InvisibleStructureBlockEntity.java` — 持有控制器坐标，供任意格破坏时反查机器
- `machine/tool/InstallationToolItem.java` — 右击控制器=安装；潜行右击=拆除（D5 将改为打开参数 GUI）

修改：

- `machine/MachineBlockEntity.java` — `installed` 标志 + `ledger`；`install()`（校验→快照→只隐形主体，端口/控制器保持可见）/`dismantle(return)`（还原 + 返还或销毁）/`onStructureBroken(brokenPos)`（镐破单格→整体解体但保留被破格）；`setRemoved` 拆机还原；Ledger 随 NBT 存取
- `machine/module/ItemInventoryModule.java` — 实现 `ContentHolder`（`contentsToDrop` / `clearContents`）
- `registry/ModBlocks.java` / `ModBlockEntities.java` — 注册隐形块与其 BE，`wirePorts()` 接线
- `registry/ModItems.java` — 注册安装工具
- `StardustIndustry.java` — 创造栏加入安装工具
- `multiblock/MultiblockSelfCheck.java` — 新增 Ledger NBT 往返自检
- 资源：隐形块 blockstate/model（无几何）、安装工具模型/贴图/配方、中英文 lang

> 与批复 #4 的差异：Shift+右键"参数 GUI（含拆除按钮）"里的 **GUI 属 D5**；
> D3 先让 Shift+右键直接执行拆除，D5 再把该手势接到 GUI。行为语义（拆除返还全部、
> 镐破销毁加工品、未产出输出则输入全额返还）已在 D3 落地。

### D4 落地清单（已完成，LV 一套闭环）

新增文件：

- `multiblock/modifier/FillerRegistry.java` — 工程块数值中央注册表（`FillerKind → FillerModifier`）+ `registerDefaults()`
- `machine/FillerBlock.java` — 工程块方块（绑定 `FillerKind`；方块本身惰性）
- `machine/BaseBlock.java` — 等级底座方块（绑定 `EnergyTier`；声明机器电压档）

修改：

- `MachinePortBlock.java` — 新增可选 `EnergyTier`（保留无等级旧构造器）
- `multiblock/provider/StaticStructureProvider.java` — `modifiersOf` 改为委托 `FillerRegistry`（删除内联 switch）
- `registry/ModBlocks.java` — 注册 LV 一套：`lv_base`、`lv_item_port`、`lv_fluid_port`、`lv_energy_port`、四个工程块；`registerPartTypes()` 补分类与 `TierMaterials` 等级映射
- `machine/module/RecipeRunnerModule.java` — 接入 modifier：速度→处理时间、能耗→每 tick 耗能；`onStructureChanged` 时放弃进行中配方以便按新加成重算
- `machine/module/EnergyBufferModule.java` — `capacity()` 随 buffer 倍率动态变化
- `machine/MachineBlockEntity.java` — 新增 `modifiers()` 便捷读取；`chargeIdleDraw()` 待机耗电（仅成型且有能量模块时按 tier 电压扣电，欠能即停机保留进度）；`isPowered()`
- `StardustIndustry.java` — common setup 调用 `FillerRegistry::registerDefaults`；创造栏加入 LV 一套
- `multiblock/MultiblockSelfCheck.java` — 新增工程块注册表自检、LV 一套接线自检
- 资源：8 个 LV 方块的 blockstate/模型/物品模型/贴图/配方 + 中英文 lang

> 范围取舍（已与你确认）：D4 只注册 **LV 一套**作为可验证闭环；
> MV/HV/EHV 用同一套 `BaseBlock`/`MachinePortBlock`/`FillerBlock` 模式，
> 随 D9 铺开；届时只需新增方块注册 + 配方 + lang。
> 端口等级化采用「每档注册带等级的端口变体」（`lv_item_port` 等）。
> 待机耗电按「停机保留进度」实现（批复 #9）。

### D5 落地清单（已完成）

新增文件：

- `machine/MachineParamsData.java` — 参数快照记录（tier/速度/能耗/并行/缓冲/电量/工程块数）+ `StreamCodec`
- `machine/MachineParamsMenu.java` — 参数菜单（无槽位；携带快照；`stillValid` 校验距离与存在）
- `client/MachineParamsScreen.java` — 参数界面（只读渲染快照 + 拆除按钮）
- `network/DismantleRequestPayload.java` — 客户端→服务端拆除请求包（仅携带坐标）
- `network/ModNetwork.java` — 网络通道注册 + 服务端处理（重校验权限/距离/已安装/菜单匹配）
- `registry/ModMenus.java` — `MenuType` 注册（`IMenuTypeExtension`，带额外数据打开）

修改：

- `machine/MachineBlockEntity.java` — 新增 `paramsData()`：服务端组装快照
- `machine/tool/InstallationToolItem.java` — Shift+右键改为打开参数 GUI（→ D3 的直拆手势退役）
- `StardustIndustry.java` — 注册 `ModMenus` 与 `ModNetwork` 监听
- `StardustIndustryClient.java` — `RegisterMenuScreensEvent` 绑定菜单与界面
- lang — screen 标题/各标签/按钮（中英）

> 拆除按钮**不走菜单点击**，而是独立请求包：服务端在处理器里重新校验
> （是玩家 / 是控制器 / 已安装 / 菜单仍是该机器 / 触达距离），
> 因此伪造或重放的包无害。批复 #4 的「Shift+右键任意格=参数 GUI（含拆除按钮）」
> 至此闭环；拆除语义（返还全部、未产出则输入全额返还）在 D3 已落地。

### D6 落地清单（已完成，LV 框架 + 演示储罐）

新增文件：

- `multiblock/provider/ScanBounds.java` — 包围盒记录（含 `size` / `contains` / `edgesTouched`）
- `multiblock/provider/BoundingBoxScanner.java` — 从控制器向外探测包围盒（空气/填充可穿过，壳块为边界，`MAX_SIZE=9`）
- `multiblock/provider/DynamicStructureProvider.java` — 动态求值：12 棱=框架（同级）、6 面=外壳/端口、内部=空气/填充、端口须与框架同级、尺寸 2~9；`revalidateWhileFormed()=true`
- `machine/FrameBlock.java` — 等级框架块（绑定 `EnergyTier`）
- `machine/ShellBlock.java` — 惰性外壳块
- `machine/tank/TankBlock.java` / `TankBlockEntity.java` — 演示动态机器（open-shape 储罐，无安装步骤）

修改：

- `multiblock/MachinePartTypes.java` — `PartType` 新增 `FRAME` / `SHELL`；新增 `isFrame`/`isShell`/`isShellMaterial`/`isInteriorMaterial`
- `registry/ModBlocks.java` — 注册 `lv_frame`、`tank`；`steel_casing` 分类由 `CASING` 改为 `SHELL`；LV 框架登记等级
- `registry/ModBlockEntities.java` — 注册 `tank` BE
- `registry/ModCapabilities.java` — LV 端口复用同一控制器能力解析
- `StardustIndustry.java` — 创造栏加入 `lv_frame`、`tank`
- `multiblock/MultiblockSelfCheck.java` — 新增动态几何自检（角/棱/面/内部的 `edgesTouched` 计数）+ LV 框架/外壳分类自检
- 资源：`lv_frame`、`tank` 的 blockstate/模型/物品模型/贴图/配方 + 中英文 lang

> 锚点约定（已与你确认）：**控制器在矩形外壳的某个面上**。扫描从控制器沿每个轴向
> 正负两向走，穿过空气/填充，直到遇到壳块（框架/外壳/端口）即为该轴边界；
> 中途遇到非容器方块则判失败。包围盒每维 2~9。
> 动态机器**自动成型**（形状开放，无需安装工具），并允许边改边用
> （`revalidateWhileFormed()=true`，邻居变化置脏 / 未成型每 20 tick 重扫）。
> 「控制器在面上」使得控制器所在行必然能碰到壳，算法因此可用且无歧义。

### D7 落地清单（已完成，投影幽灵 + 客户端同步）

新增文件：

- `machine/ClientFailure.java` — 面向客户端的失败格记录（世界坐标 + 颜色组 + 期望文案），自带 NBT 往返；颜色组 `STRUCTURE`/`CHOICE`/`ERROR` 分别对应缺失结构、待玩家选择（基座槽/端口）、硬错误（混级）
- `client/MachineRenderer.java` — 控制器 BER：未成型时对每个失败格绘制半透明幽灵方块（按颜色组选 `*_stained_glass`），成型后不绘制（交给 D7.5 的整机模型）；`shouldRenderOffScreen=true` 与 96 格视距保证大机器整体可见

修改：

- `machine/MachineBlockEntity.java` — 新增 `clientFailures` 列表与 `colourGroupOf(...)` 映射；provider 重扫后更新失败格并 `sendBlockUpdated(..., 3)` 主动推送；`getUpdateTag` 在未成型时附加 `Failures` 列表；新增 `handleUpdateTag` 在客户端读回；`clientFailures()` 访问器
- `StardustIndustryClient.java` — `RegisterRenderers` 事件中把 `MachineRenderer` 绑定到所有机器 BE 类型（`crusher`/`tank`）；新机器只需在此登记即获得投影
- `multiblock/MultiblockSelfCheck.java` — 新增 `ClientFailure` NBT 往返自检

> **为何由控制器 BER 绘制**（你已确认）：控制器是机器的锚点，也是未成型时唯一保证存在的方块。
> 失败格由更新包同步到客户端，渲染器因此不需要全区扫描，也不需要为每种机器写特例。
> 幽灵复用真实方块模型，玩家看到的是"要放的方块"本身，而非抽象轮廓。
> **D7 只做投影**；成型整机模型、动画与连接材质归入 **D7.5**（需要美术资源与自定义模型渲染）。

### D7.5 落地清单（已完成，成型重绘 + 动画 + 连接材质）

新增文件：

- `machine/MachineRoles.java` — 「哪些角色在安装时被隐形」的唯一权威规则。经复核修正为**只隐形 `FRAME` 主体**；`BASE`/`FILLER`（基座层）与 `PORT`/`CONTROLLER` 一律保持可见（否则地板消失、端口失去 BE）。服务端 `install()` 与客户端 BER 共用此规则，二者永不失配。
- `client/MachineAnimator.java` — 客户端动画接口（`animate(...)`），以控制器为锚点、与主体同坐标系
- `client/MachineAnimators.java` — 按 BE 类型查动画器的注册表；无登记即静止
- `client/CrusherAnimator.java` — 粉碎机转子：由世界游戏刻驱动绕 Y 轴旋转（每 2 秒一圈，尺寸 0.6×0.25×0.6），同世界的粉碎机同步转动，重载后不跳零
- `machine/crusher/CrusherStructure.java` — 粉碎机的 `StructureModel`（结构与模型同源）：3×3 基座层（控制器 + 8 个自由槽）+ 上方 3 层中空外壳；**端口只在基座层**（§4.1）
- `machine/ConnectedPartProperties.java` — 六个邻居朝向布尔属性 + `recompute`/`connectsTo`；**同级才连接**，与结构求值同一套等级语义

修改：

- `client/MachineRenderer.java` — 重写为三态：未成型画投影幽灵；**成型时从 Ledger 逐格重绘原始方块**（`renderSingleBlock`，内缩 0.998 防 z-fighting），因此成型外观 = 玩家实际摆放的样子，无需另做美术模型即可像素级对齐；随后调用该机器的动画器。`ledgerParts()` 访问器随之加入 `MachineBlockEntity`
- `machine/BaseBlock.java` / `FrameBlock.java` / `MachinePortBlock.java` — 实现连接：`createBlockStateDefinition` 加六属性、`getStateForPlacement` 与 `updateShape` 逐面重算
- `machine/crusher/CrusherBlockEntity.java` — **迁移到 provider 路径**：`provider()` 返回 `StaticStructureProvider(CrusherStructure.build())`，删除旧的 `StructureDefinition` 外壳定义（旧定义是主体开端口，已不符合 §4.1）
- `StardustIndustryClient.java` — 客户端 setup 中登记粉碎机动画器
- `multiblock/MultiblockSelfCheck.java` — 新增连接属性自检（五类部件必须带六属性；`lv_base` 可连接、未分级旧端口不可连接）与 `MachineRoles` 自检（主体可隐形、基座层与端口不可隐形）
- 资源生成：`lv_base`/`lv_frame`/`lv_item_port`/`lv_energy_port`/`lv_fluid_port` 各自改为 **multipart blockstate**（core + 六向 overlay 模型，共 5×7 个模型文件）

> **打包后测试暴露并修复的两个缺陷（D7.5 补丁）**
>
> 1. **启动崩溃 — 未分级端口可连接性自检与实现不符**：
>    `ConnectedPartProperties.isConnectable` 原先把任何 `MachinePortBlock` 都算作可连接，
>    但文档契约是「未分级旧端口永不连接（没有等级可共享）」。
>    自检 `checkConnectedParts` 如实断言了契约，于是启动即抛
>    `IllegalStateException: self-check: an untiered port must not be connectable`。
>    **修复**：`isConnectable` 增加 `tierOf(block) != null` 前置条件，实现向契约看齐。
> 2. **开发启动失败 — Gradle 配置缓存与 NeoForge 启动任务不兼容**：
>    `gradle.properties` 全局开启 `org.gradle.configuration-cache=true`，
>    而 NeoForge 的 `BootstrapLauncher.main()` 在**执行期**调用 `Task.project` 并注册 `Gradle.addListener`，
>    二者都是配置缓存明令禁止的，导致 `runClient`/`runServer`/IDE 启动一律 `BUILD FAILED`。
>    **修复**：`build.gradle` 中对 `run*` 与 `BootstrapLauncher` 任务调用
>    `notCompatibleWithConfigurationCache(...)`，只对这几个任务关闭缓存，其余任务仍享受缓存加速。

> **成型重绘为何胜过另做模型**：设计文档要求「模型与原方块布局对齐」，而重绘玩家摆放的原始方块，
> 布局**必然**对齐——因为它就是那些方块。既省去美术资产，也彻底消除「方块隐形后模型对不上」的割裂感。
> 动画、连接材质因此都是叠加层，而非替代层。
> **D7.5 只重绘主体**；基座层按 §4.3「底座不变，只替换上面」保持世界可见。

---

### 12.1 D7.6 落地清单（实机测试反馈修复，已完成）

第一版分发 jar 在真实游戏中暴露了六个问题，本轮全部修复。逐条对应：

#### ① LV 端口材质错误（紫黑缺失贴图）
**根因**：`models/block/lv_item_port.json` 等引用 `stardustindustry:block/lv_item_port`，但 `textures/block/` 下**没有这些 png**（只有未分级的 `port_*_*.png`）。
**修复**：生成 `lv_{item,fluid,energy}_port_{side,top,bottom}.png`（九张），并把三个 LV 端口的 base 模型改为 `cube_bottom_top`、六个 `conn_*` overlay 模型分别指向同名的对应面贴图。
- 新增资源：`textures/block/lv_{item,fluid,energy}_port_{side,top,bottom}.png`
- 改写：`models/block/lv_{item,fluid,energy}_port.json` 及各自 6 个 `_conn_*` 模型（共 21 个模型）

#### ② 储罐无法自动成型 / 必须安装工具
**根因**（两处叠加）：
1. `BoundingBoxScanner` 从控制器沿各轴双向走、碰到 shell 即停。控制器贴**墙面**时，沿墙面切向会**立刻碰到相邻墙块**，得到 1 格厚盒子 → 永远扫不出体。
2. `InstallationToolItem` 对 `provider()!=null` 的机器一律走安装流程，储罐被误当成静态机器。

**修复（锚点语义：控制器在壳面上，当作该面的一格壳）**：
- `BoundingBoxScanner` 重写：控制器自身即该面的边界格。每个方向游走时**区分「穿过内部」与「沿着墙走」**——把「后面还有更多 shell」的 shell 格当作正在沿墙行进（跳过），只把**外侧最后一层 shell**（其后是空气）当作边界；某方向一开始就是空气/无分类则认为控制器本身就是该侧边界。彻底解决「切向立刻撞墙」。
- `DynamicStructureProvider`：控制器格视为该面的一格（`BlockRole.CONTROLLER`，跳过面板规则），其余照旧。
- `InstallationToolItem`：对 `provider().revalidateWhileFormed()` 的动态机器**拒绝安装**，提示「这台机器会自动成型」。
- `BoundingBoxScanner`/`DynamicStructureProvider` 的失败文案改为翻译键。

#### ③ 没有投影，只有坐标
**根因**：幽灵画在失败格上，而储罐的失败格**就是控制器自身**（不透明方块）→ 幽灵嵌在方块里看不见；空地无失败格可画。
**修复**：两处——
- `MachineRenderer.renderProjection`：只在**空气格**画幽灵（失败格已有实心方块则跳过，避免埋进方块里）；
- 新增 `renderStatusLabel`：在控制器上方 1.4 格用 `Font` 渲染**第一条失败原因**（`SEE_THROUGH`，始终朝向相机），把「缺什么」用文字补上。

#### ④ 输出改中文（含破碎机与储罐）
**根因**：`InstallationToolItem` 与两个 provider 的文案都是硬编码英文字面量。
**修复**：`ScanFailure` / `ClientFailure` 改为携带**翻译键 + 参数**（`expectationComponent()`），不再存成品句子；`InstallationToolItem` 全部走 `Component.translatable`；补齐 `zh_cn.json` / `en_us.json` 的 `message.stardustindustry.*` 与 `structure.stardustindustry.need.*` 全部条目。储罐成型时向 24 格内的玩家广播中文提示（`onFormedChanged`），含体积与容量。

#### ⑤ Shift+右键任意部件打开 GUI + 流体信息
**根因**：
1. `useOn` 只在点击**控制器**（`MachineBlockEntity`）时生效；端口/隐形格是别的 BE，打不开。
2. **根本没有流体系统**：无流体模块，fluid port 能力返回 `null`。

**修复**：
- 新增 `machine/module/FluidBufferModule.java`：单流体 `FluidTank`，容量 = `tier.voltage() * 1000` 且随 `bufferMultiplier()` 实时放大；暴露标准 `IFluidHandler`；带输入/输出速率滚动统计（每秒减半，读数稳定）。
- `ModCapabilities`：`fluid_port` / `lv_fluid_port` 接到该模块能力（无流体模块的机器返回 `null`）。
- `TankBlockEntity`：模块集改为**流体缓冲 + 能量缓冲**（储罐的本来用途）。
- `MachineParamsData`：新增 `fluidName/fluidAmount/fluidCapacity/fluidInRate/fluidOutRate` 字段（手写 `StreamCodec.of`，已超 composite 字段上限）。
- `MachineParamsScreen`：新增「流体 / 装载 / 输入速率 / 输出速率」四行（中文）。
- `InstallationToolItem.resolveController`：点击**端口**经 `port.controller()`、点击**隐形格**经 `InvisibleStructureBlockEntity.controller()` 反查控制器；三者统一打开参数 GUI。

#### ⑥ 隐形方块碰撞箱
**根因**：`InvisibleStructureBlock.getCollisionShape` 返回 `Shapes.empty()`，玩家可穿过机体。
**修复**：`getCollisionShape` / `getBlockSupportShape` 返回 `Shapes.block()`（**透明≠消失**）；渲染仍为 `RenderShape.INVISIBLE`，`getShape` 保持空（不产生选中描边）。

**验证**：`compileJava --rerun-tasks` ✅；`runServer` `Done (2.772s)`、自检全过 ✅；`runClient` 无缺失模型/贴图告警、到主菜单 ✅。

> **D6 锚点规则修订**：原设计「控制器在壳外侧，扫描器从控制器朝六向找壳」在**密闭矩形**下不成立
> （切向会立刻撞到同面墙块）。本轮回退为用户确认的「控制器是壳面上的一格」语义。§5.1 的措辞随之统一。

> **版本与发布**：本轮起版本号降为 **`0.1.0`**（早期测试语义）。产物 `stardustindustry-0.1.0.jar`
> 已同步到 `K:\Minecraft MODS\dist\`（分发）与客户端实例
> `...\.minecraft\versions\1.21.1-NeoForge\mods\`（测试）。三处 SHA-256 一致。
>
> **构建踩坑**：`clean` 会删除 `buildDir`（重定向到 `%TEMP%\stardustindustry-build`），
> 连带删掉 NeoForm 缓存；若 Gradle 构建缓存（`org.gradle.caching=true`）里存了**残缺的
> `neoFormDecompile` 产物**，重新解压出的反编译源码会缺包（如 `net.minecraft.advancements`），
> 导致 `neoFormRecompile` 报「找不到符号/程序包不存在」。**解法**：`--no-build-cache --rerun-tasks`
> 强制真正重新反编译一次。日常改代码只需 `jar`/`build`，**不要频繁 `clean`**。
>
> **自检抓到真 bug（扫描器 v2 修正）**：首版 `scan(BlockPos, Function)` 自检在客户端启动时抛
> `self-check: dynamic scan failed on a valid 5x5x5 box`——正是它该抓的东西。
> 根因：`walk` 把**开放天空的空气**也当成「内部空气」并继续前进，直到步数上限后误判为「受阻」。
> **修正**：空气/填充一律穿过；若走完整段**从未遇到墙**，说明控制器就在这一侧的外表面 →
> 边界即控制器自身（`boundaryAtOrigin`）。只有当**已见到机器材质后**再遇到无分类方块才算受阻。
> 修正后 `runServer` `Done`、自检通过。
> **教训**：这种「控制器贴在壳面上」的扫描对「内部空气 vs 外部空气」的区分是天然的坑，
> 靠「最终有没有墙」来消歧比靠「第一格是不是空气」稳得多。

---

## 13. 批复状态

**已确认（全部）**：

| # | 问题 | 结论 | 落点 |
|---|---|---|---|
| 1 | 成型后碰撞箱 | **保留完整碰撞箱**（透明≠消失，玩家撞得到机体）；只隐形主体（底座保留）；控制器 BER 锚点整体建模；模型与原布局对齐 | §4.3 / §7.4 / §12.1⑥ |
| 2 | 基座槽能否为空 | **不得为空**；不放端口/工程块时必须放**底座块** | §4.1 |
| 3 | 端口位置 | **所有端口只在基座层**；主体不开端口 | §4.1 |
| 4 | 安装工具用途 | 右击=安装；Shift+右键任意格=**参数 GUI（含拆除按钮）**；拆除**返还全部**，未产出输出则**输入 100% 全额返还**；镐子破坏则销毁（惩罚） | §4.3 / §4.4 / §4.6 |
| 5 | 动态端口放棱上 | **不同意**；棱上只允许框架 | §5.1 |
| 6 | 控制端口 | **一个方块 = 3×3 共 9 个信号格**，每格切换输入/输出 + 过滤（类 AE2 ME 接口）；红石、CC 可联动、可扩展 | §6.6 |
| 7 | 工程块边际递减 | **不做**；工程块已带负面惩罚 | §6.4 |
| 8 | 等级命名/区分 | **LV / MV / HV / EHV 四档**；**方块直接以等级命名**（`lv_base` 等），**合成表区分**，不绑定金属材质 | §4.5 / §8.1 |
| 9 | 待机耗电断电 | 停机保留加工进度（待你最终点头） | §4.5 |

**无剩余阻塞问题**。若对第 9 条（断电是"停机保留进度"还是"进度回退"）没有异议，
即按**停机保留进度**实现。

---

## 14. 当前进度与下一步

D0.5 ~ D7.10 见 §12 的阶段表（D7.10 气体储罐进行中，见 §15）。
**下一步**：完成 D7.10 的气体储罐实机闭环 → 进入 **D7.11 Mekanism 软依赖适配器**
（把全部化学品映射为 `Gas`）→ 再回到 **D8 控制端口逻辑层**。
每阶段 `compileJava` + `runServer` 验证 0 错误。

---

## 15. 气体子系统与"同源储罐"（v2.2 新增）

### 15.1 为什么自建气体抽象

原版没有"气体"这种资源，唯一通用来源是 Mekanism 的化学品（chemical）。若把 Mekanism
当作硬前置，则本模组的所有机器都被一个外部模组绑架；而若完全不做，储罐就永远只有液体。

因此本模组**自建一层最小的气体抽象**，把 Mekanism 当作**软依赖**适配进来：

| 层 | 内容 | 说明 |
|---|---|---|
| 核心抽象 | `Gas` / `GasStack` / `IGasHandler` / `GasRegistry` | 本模组自有，**不依赖任何外部模组**；单位 **mB**（与流体一致） |
| 缓冲模块 | `GasBufferModule` | 机器/储罐里的气体缓存，暴露 `IGasHandler` 能力 |
| 能力注册 | `ModCapabilityTypes.GAS_HANDLER`（`BlockCapability<IGasHandler, Void>`） | 气体端口与气体储罐外壳对外暴露 |
| 兼容层（D7.11 ✅） | `compat/mekanism` | 把 Mekanism 全部化学品映射为 `Gas`；Mekanism 不在时本模组照常运行 |

> **设计原则**：一个整合包**没有 Mekanism 也能跑**——`GasRegistry` 内置一组常见工业气体
> （氢/氧/氮/二氧化碳/蒸汽/天然气）作为占位；装了 Mekanism 则其**全部**化学品（含**核材料、
> 放射性气体**）经适配器进入 `GasRegistry`，可直接存进气罐、走气管。

### 15.2 气体与流体：同结构、异介质、异渲染

储罐的**流体版与气体版是同一个多方块**，共用 `AbstractTankBlockEntity`（结构、锚点、容量、
客户端同步、参数屏、成型广播全部共用）。差异集中在三处：

| 方面 | 流体储罐 | 气体储罐 |
|---|---|---|
| 外壳 | `fluid_tank_shell` | `gas_tank_shell` |
| 端口 | `lv_fluid_port` 等 | `lv_gas_port` 等（先 LV，MV/HV/EHV later） |
| 缓冲模块 | `FluidBufferModule`（`FluidStack`） | `GasBufferModule`（`GasStack`） |
| 能力 | `IFluidHandler` | `IGasHandler`（本模组能力） |
| **罐内渲染** | **液面高度**：从底部逐格升高 | **浓度**：始终充满内腔，颜色随量变浓，透明度最高 **70%** |

**介质如何决定**：结构求值器 `TankStructureProvider` 从**锚点的外壳块**读出介质
（`TankMedium`），随后只接受**同介质**的外壳与端口；共用件 `tank_frame` / `industrial_glass`
两种介质都认。把气体外壳混进流体罐（或反之）视为搭建错误。

**渲染记忆点**：**流体看"液面多高"，气体看"有多浓"。** 见
[`terminology.md`](./terminology.md) 的"气体渲染约定"。

### 15.3 端口与速率

- 气体端口与流体端口**同一套 `MachinePortBlock` 与 `MachinePortBlockEntity`**，只是 `PartRole`
  为 `PORT_GAS`（新增），能力注册指向气体缓冲。
- 速率：流体端口为 LV/MV/HV/EHV = **5 / 20 / 80 / 320 B/t**；气体端口沿用同一速率表
  （同一 `EnergyTier.fluidTransfer()`）。
- 手动交互（手持容器右键端口）本阶段先只做**流体**；气体容器的右键交互在兼容层就绪后补（D7.11）。

### 15.4 储罐参数屏（两种介质共用布局）

参数屏对流体/气体**同一版式**，仅"介质名"与内容名不同；储罐字段清单见
[`tank-multiblock-design.md`](./tank-multiblock-design.md) §9。

---

## 附录 A：示例 DSL（静态粉碎机）

```java
StructureModel.builder("crusher")
    // 主体（严格）
    .fixed(-2, 1, 0, Blocks.IRON_BLOCK)     // ... 逐格
    .fixed(-1, 1, 0, Blocks.IRON_BLOCK)
    // 基座层（自由槽），控制器所在的 z 行
    // 槽位三选一，且不得为空
    .baseSlot(-2, 0, 0)                     // 端点 / 工程块 / 底座块
    .baseSlot(-1, 0, 0)
    .baseSlot( 0, 0, 0)
    .baseSlot( 1, 0, 0)
    .baseSlot( 2, 0, 0)
    // 控制器
    .controller(0, 1, 0)
    // 模型与动画（同源）
    .model(id("block/crusher_formed"))
    .unformedModel(id("block/crusher_project"))
    // 该机器允许的等级（由底座方块实际决定，此处声明允许范围）
    .tiers(EnergyTier.LV, EnergyTier.MV, EnergyTier.HV, EnergyTier.EHV)
    .build();
```

## 附录 B：一次求值结果示例

```java
StructureEvaluation eval = provider.evaluate(level, pos, facing);
// eval.formed()      -> true
// eval.tier()        -> EnergyTier.MV          （由底座方块等级得出）
// eval.modifiers()   -> ModifierSet(speed=2.0, energy=2.2, parallel=0, buffer=1.5)
// eval.fillers()     -> { GRINDING_CORE=3, HEAT_EXCHANGER_CORE=1 }
// eval.failures()    -> []
```

---

*文档结束。请批复第 13 节末尾的 4 个小问题，我据此进入 D1。*

# 术语规范

> 本项目的**中英文术语约定**。所有策划案、美术案、设计文档、代码注释、游戏内文本
> 都必须遵守，避免出现读起来别扭或让人误解的翻译。

---

## 一、必须遵守的术语

| 英文标识 / 概念 | 中文统一用词 | 说明 |
|---|---|---|
| Tank（容器总称） | **储罐** | **绝对不要**译为「坦克」。游戏内叫储罐，策划案、美术案、UI、提示文案一律用「储罐」。 |
| Fluid Tank（存液体的） | **流体储罐** | 存液体介质的储罐。 |
| Gas Tank（存气体的） | **气体储罐** | 存气体介质的储罐，结构与流体储罐一致，仅介质不同。 |
| Tank Frame | 储罐框架 | 储罐的十二条棱边。**流体/气体储罐共用**。 |
| Tank Glass | **工业玻璃** | 储罐壁上的透明板。**流体/气体储罐共用**。（旧名「钢化玻璃」已停用。） |
| Fluid Tank Shell | **流体储罐外壳** | 流体储罐的六面壁板，其中一块承载方块实体、充当控制器。 |
| Gas Tank Shell | **气体储罐外壳** | 气体储罐的六面壁板，同上。 |
| Fluid Port | **流体端口** | 按等级分为 LV / MV / HV / EHV 流体端口。 |
| Gas Port | **气体端口** | 按等级分为 LV / MV / HV / EHV 气体端口。 |
| Tier（等级） | **LV / MV / HV / EHV** | 中英文**都**直接显示这四个缩写，不要写「低压/中压」等。 |
| Gas | **气体** | 计量单位与流体一致，用 **mB**。 |

## 一之二、注册 ID 命名（正式，勿随意再改）

| 方块 | 注册 ID | 英文名 | 中文名 |
|---|---|---|---|
| 储罐框架（共用） | `stardustindustry:tank_frame` | Tank Frame | 储罐框架 |
| 工业玻璃（共用） | `stardustindustry:industrial_glass` | Industrial Glass | 工业玻璃 |
| 流体储罐外壳 | `stardustindustry:fluid_tank_shell` | Fluid Tank Shell | 流体储罐外壳 |
| 气体储罐外壳 | `stardustindustry:gas_tank_shell` | Gas Tank Shell | 气体储罐外壳 |
| LV 流体端口 | `stardustindustry:lv_fluid_port` | LV Fluid Port | LV 流体端口 |
| LV 气体端口 | `stardustindustry:lv_gas_port` | LV Gas Port | LV 气体端口 |
| 端子座 | `stardustindustry:<tier>_terminal_mount` | LV Terminal Mount (4x4) | LV 端子座（4×4） |
| 端子 | `stardustindustry:<tier>_terminal` | LV Terminal | LV 端子 |
| 线缆 | `stardustindustry:cable_<载流量>a_<绝缘>` | 500 A Class 1 Cable | 500 A 一类绝缘线缆 |

> **端子座与端子是两个独立的东西，不要混称**：
> **端子座**（Terminal Mount）是架在墙面/地面/天花板上、带网格的**方块**；
> **端子**（Terminal）是插进格子里、成为**一个电气节点**的**物品**。
> 说"装一个端子"指后者，说"放一个端子座"指前者。

> **线缆不是方块**：**线缆**（Cable）是拉在两个端子之间的**连接**，没有方块形态、
> 不占格子、不参与方块状态。说"拉一根线"指的就是它。

> **线缆没有电压等级**（旧命名 `<tier>_cable` 已废弃）。线缆由**两个正交属性**描述：
> **载流量**（`50` / `150` / `500` / `800` / `1500` / `1600` / `5000` A）
> 与**绝缘**（`bare` / `class_1` / `class_2`）。
> 例：`cable_500a_class_1`（500 A 一类绝缘线缆）、`cable_1600a_bare`（1600 A 钢芯铝绞线）。

### 一之二·甲、导体材料术语

| 英文标识 / 概念 | 中文统一用词 | 说明 |
|---|---|---|
| Copper | **铜** | 默认导体。电阻最低，**可以有绝缘**，是室内/近距布线的默认选择。 |
| ACSR（Aluminium Conductor Steel Reinforced） | **钢芯铝绞线** | 钢芯承力 + 铝层导电。**比铜轻**，所以**能架得更远、下坠更小**；代价是**电阻更大**、**只有裸线版本**。 |
| Bare | **裸线** | 无绝缘。LV 以上碰到会触电。 |
| Class 1 insulation | **一类绝缘** | 护住 LV，MV 轻伤，HV 及以上必死。 |
| Class 2 insulation | **二类绝缘** | 护住 LV 与 MV，HV 及以上必死。 |
| Ampacity | **载流量** | 线缆能连续通过的电流，超过就**冒烟 → 失效**。 |

> **为什么钢芯铝绞线没有绝缘版**：它本来就是**架空长跨距**用的，
> 现实中没有任何一条 110 kV 架空线会去加绝缘。加了绝缘它既重又硬，
> 那就该直接用铜了。所以它**永远裸线**，也就**永远碰不得**。

> **粗细只看载流量，不看序号**。绘制直径按**电流的四次方根**映射
> （`直径 = 0.05 × (I/50)^(1/4)`），所以 800 A 一定比 500 A 粗，
> 不管它排在枚举的第几位。用四次方根而不是平方根/立方根，是因为后两者会把
> 5000 A 画成 0.09 / 0.23 格的"管子"，而线缆必须看起来像线。

> **颜色是线缆的数据，不是物品变体**。默认状态叫**未上色**（`CableColor.UNDYED`），
> 此时绝缘线显示**灰外皮**、裸线显示**导体本色**（铜色 / 铝色）。
> **裸线不可染色**——金属色是玩家判断"这根线碰不得"的唯一线索。

### 一之二·补、端子座网格容量

| 等级 | 网格 | 每座格数 |
|---|---|---|
| LV | 4×4 | 16 |
| MV | 2×2 | 4 |
| HV | 1×1 | 1 |
| EHV | 1×1 | 1 |

> 等级越高端子本身越大，所以格子越稀。**每格是一个独立节点**。

> **命名规则**：同一部件若因介质不同而分化，用 `<medium>_tank_<part>` 命名
> （`fluid_tank_shell` / `gas_tank_shell`）；共用部件不带介质前缀
> （`tank_frame` / `industrial_glass`）。

---

## 一之三、气体渲染约定

气体与流体的**渲染表现不同**，这是有意设计，不要照抄流体：

| 介质 | 表现方式 | 说明 |
|---|---|---|
| **流体** | **液面高度** | 液体像真实液体一样从底部逐格升高，看液面就知道大概有多少。 |
| **气体** | **浓度**（颜色/透明度） | 气体**始终充满内腔**，不随量改变高度。空罐时完全无内容（不渲染）；随注入量增加，颜色**越来越浓**，透明度**最高到 70%**（alpha ≈ 0.70）为止。 |

> 记忆点：流体看「装了多少高度」，气体看「有多浓」。

---

## 一之四、气体与 Mekanism 化学品的对应

Core 的气体是**自有抽象**。独立的 MekanismEx 附属模组把 Mekanism 的**全部化学品**映射进来；
Core 可单独运行，而安装 MekanismEx 时 Mekanism 是硬依赖：

| 概念 | 说明 |
|---|---|
| Mekanism 化学品 | 1.21.1 已无独立「气体」类型，气体/浆液/灌注物/颜料统一叫 **Chemical** |
| 映射规则 | 化学品注册 ID **原样**作为气体 ID，保留 `mekanism:` 命名空间（如 `mekanism:hydrogen`） |
| 不合并 | `mekanism:hydrogen` 与本模组 `stardustindustry:hydrogen` 是**两种**气体，绝不合并 |
| 色调 | 取 Mek 化学品自身颜色（`getColorRepresentation`），罐内表现一致 |
| 单位 | 与流体一致，均为 **mB** |

---

## 二、适用范围

1. **中文文档**：`docs/` 下所有 Markdown、`CHANGELOG.md`。
2. **代码注释**：Java 的 JavaDoc 与行内注释里，指代该容器时用「储罐」而非「坦克」。
   （代码**标识符**仍用英文 `tank` 系列命名，不强制翻译。）
3. **游戏内文本**：Core 的
   `StardustIndustry-Core/src/main/resources/assets/stardustindustry/lang/`，以及扩展各自资源命名空间下的
   `lang/zh_cn.json` 与 `lang/en_us.json`。

---

## 三、自查方式

```powershell
# 全仓搜「坦克」，应为 0 结果
Select-String -Path (Get-ChildItem -Recurse -Include *.md,*.json,*.java).FullName -Pattern "坦克"
```

发现即改。当前状态：**0 处**。

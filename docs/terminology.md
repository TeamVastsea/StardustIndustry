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

本模组的气体是**自有抽象**，Mekanism 适配器把其**全部化学品**映射进来（软依赖，不装 Mek 照常运行）：

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
3. **游戏内文本**：`assets/stardustindustry/lang/zh_cn.json` 与 `en_us.json` 的所有显示文案。

---

## 三、自查方式

```powershell
# 全仓搜「坦克」，应为 0 结果
Select-String -Path (Get-ChildItem -Recurse -Include *.md,*.json,*.java).FullName -Pattern "坦克"
```

发现即改。当前状态：**0 处**。

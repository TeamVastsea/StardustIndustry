# 术语规范

> 本项目的**中英文术语约定**。所有策划案、美术案、设计文档、代码注释、游戏内文本
> 都必须遵守，避免出现读起来别扭或让人误解的翻译。

---

## 一、必须遵守的术语

| 英文标识 / 概念 | 中文统一用词 | 说明 |
|---|---|---|
| Tank（储液容器） | **储罐** | **绝对不要**译为「坦克」。游戏内叫储罐，策划案、美术案、UI、提示文案一律用「储罐」。 |
| Tank Shell | 储罐外壳 | 储罐的六面壁板，其中一块承载方块实体、充当控制器。 |
| Tank Frame | 储罐框架 | 储罐的十二条棱边。 |
| Tank Glass | 钢化玻璃 | 储罐壁上的透明板。 |
| Fluid Port | 流体端口 | 按等级分为 LV / MV / HV / EHV 流体端口。 |
| Tier（等级） | **LV / MV / HV / EHV** | 中英文**都**直接显示这四个缩写，不要写「低压/中压」等。 |

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

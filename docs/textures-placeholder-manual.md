# 材质占位手册（美术替换指引）

> 本文件是**美术资源替换的唯一权威清单**。
> 当前仓库内**所有贴图均为临时占位资源**（纯色/简单色块，16×16），
> 仅为让方块在游戏里可辨认、可测试。正式美术资源到位后，请按本表逐一替换。
>
> 最后更新：0.1.0（高亮模组兼容 + 储罐动态多方块）

---

## 一、命名约定（新资源请遵守）

| 约定 | 说明 |
| --- | --- |
| 分辨率 | 方块贴图 `16×16`（可后续升级 32×32，需同步 `pack.mcmeta`）；物品贴图 `16×16` |
| 命名 | 小写 + 下划线，与**方块注册名**保持一致；多面方块用 `_top/_bottom/_side/_front` 后缀 |
| 路径 | 方块：`src/main/resources/assets/stardustindustry/textures/block/<名称>.png`<br>物品：`src/main/resources/assets/stardustindustry/textures/item/<名称>.png` |
| 透明 | 需要透明/半透明的方块（如钢化玻璃）必须用带 alpha 的 PNG |
| 格式 | PNG，非动画方块不要用 `.mcmeta` |

> ⚠️ **替换时不要改名**。模型（`models/block/*.json`）通过固定的贴图键名引用这些文件；
> 改名会导致模型丢失贴图（黑紫方块）。若确需改名，请同时修改对应的 model JSON。

---

## 二、方块贴图对照表

“当前占位来源”一列标明该贴图现在复用/借用了什么，方便美术判断替换对象。

### 2.1 储罐（Tank）——**优先替换**

| 贴图文件 | 对应方块 | 用途 / 贴图键 | 当前占位来源 | 建议美术方向 |
| --- | --- | --- | --- | --- |
| `textures/block/lv_frame.png` | **储罐框架** `tank_frame` | `cube_all` 全六面 | 复用旧 LV 框架占位 | 金属桁架/角钢，强调“边缘骨架”，与外壳区分 |
| `textures/block/tank_shell.png` *(缺失)* | **储罐外壳** `tank_shell` | `orientable` 的 `front` | ⚠️ 模型引用 `tank_front.png` | 需要独立钢板外壳贴图（正面） |
| `textures/block/tank_side.png` | **储罐外壳** `tank_shell` | `orientable` 的 `side` | 旧坦克侧面占位 | 钢板外壳侧面 |
| `textures/block/tank_top.png` | **储罐外壳** `tank_shell` | `orientable` 的 `top` | 旧坦克顶面占位 | 钢板外壳顶面 |
| `textures/block/tank_front.png` | **储罐外壳** `tank_shell` | `orientable` 的 `front` | 旧坦克正面占位 | 钢板外壳正面 |
| （无独立贴图） | **钢化玻璃** `tank_glass` | `cube_all` → 引用原版 `minecraft:block/glass` | 原版玻璃 | **可替换为自制钢化玻璃贴图**（见 §四·待办 1） |

> 说明：`tank_shell` 目前用的是 `minecraft:block/orientable` 父模型（区分 top/front/side），
> 因此外壳需要 **3 张** 独立贴图；`tank_front.png` 与 `tank_shell.png` 命名不一致，
> 详见 §四·待办 2 的整理建议。

### 2.2 机器与结构

| 贴图文件 | 对应方块 | 贴图键 | 当前占位 | 建议美术方向 |
| --- | --- | --- | --- | --- |
| `textures/block/crusher.png` | **破碎机** `crusher` | 物品图标用 | 占位 | 整机预览图（物品栏用） |
| `textures/block/crusher_top.png` | 破碎机 | `top` | 占位 | 进料口/顶盖 |
| `textures/block/crusher_front.png` | 破碎机 | `front` | 占位 | 出料口/观察窗 |
| `textures/block/crusher_side.png` | 破碎机 | `side` | 占位 | 机身侧面 |
| `textures/block/steel_casing.png` | **钢制机壳** `steel_casing` | `cube_all` | 占位 | 通用钢制机壳纹理（多方块基座重复使用） |

### 2.3 端口（普通 + LV）

| 贴图文件 | 对应方块 | 贴图键 | 建议美术方向 |
| --- | --- | --- | --- |
| `textures/block/port_item_top.png` | **物品端口** `item_port` | `top` | 物品输入/输出口 |
| `textures/block/port_item_side.png` | 物品端口 | `side` | 端口侧面 |
| `textures/block/port_item_bottom.png` | 物品端口 | `bottom` | 端口底面 |
| `textures/block/port_fluid_top.png` | **流体端口** `fluid_port` | `top` | 管口 |
| `textures/block/port_fluid_side.png` | 流体端口 | `side` | 管口侧面 |
| `textures/block/port_fluid_bottom.png` | 流体端口 | `bottom` | 管口底面 |
| `textures/block/port_energy_top.png` | **能量端口** `energy_port` | `top` | 接线端子 |
| `textures/block/port_energy_side.png` | 能量端口 | `side` | 线缆/端子侧面 |
| `textures/block/port_energy_bottom.png` | 能量端口 | `bottom` | 端子底面 |
| `textures/block/lv_item_port_top.png` | **LV 物品端口** `lv_item_port` | `top` | 带 LV 标识的物品口 |
| `textures/block/lv_item_port_side.png` | LV 物品端口 | `side` | 同上侧面 |
| `textures/block/lv_item_port_bottom.png` | LV 物品端口 | `bottom` | 同上底面 |
| `textures/block/lv_fluid_port_top.png` | **LV 流体端口** `lv_fluid_port` | `top` | 带 LV 标识的流体口 |
| `textures/block/lv_fluid_port_side.png` | LV 流体端口 | `side` | 同上侧面 |
| `textures/block/lv_fluid_port_bottom.png` | LV 流体端口 | `bottom` | 同上底面 |
| `textures/block/lv_energy_port_top.png` | **LV 能量端口** `lv_energy_port` | `top` | 带 LV 标识的能量口 |
| `textures/block/lv_energy_port_side.png` | LV 能量端口 | `side` | 同上侧面 |
| `textures/block/lv_energy_port_bottom.png` | LV 能量端口 | `bottom` | 同上底面 |

### 2.4 多方块基座与核心

| 贴图文件 | 对应方块 | 贴图键 | 建议美术方向 |
| --- | --- | --- | --- |
| `textures/block/lv_base.png` | **LV 底座** `lv_base` | `cube_all` | 多方块通用底座/机架 |
| `textures/block/lv_grinding_core.png` | **LV 研磨核心** `lv_grinding_core` | `cube_all` | 磨盘/研磨结构 |
| `textures/block/lv_heat_exchanger_core.png` | **LV 换热核心** `lv_heat_exchanger_core` | `cube_all` | 散热鳍片 |
| `textures/block/lv_parallel_core.png` | **LV 并行核心** `lv_parallel_core` | `cube_all` | 多通道/分流结构 |
| `textures/block/lv_buffer_core.png` | **LV 缓冲核心** `lv_buffer_core` | `cube_all` | 储能/缓存罐体 |

> 以上“核心/基座”方块的**六面朝向模型**（`*_conn_*`）不引用独立贴图，
> 它们复用本表同名主贴图，无需单独出图。

### 2.5 结构占位方块（几乎不可见）

| 贴图文件 | 对应方块 | 说明 |
| --- | --- | --- |
| （无贴图，仅粒子） | **机器结构** `invisible_structure` | 结构投影用“隐形”方块，模型仅指定 `particle` = `steel_casing`，正常游戏不显示本体。**无需美术资源**。 |

---

## 三、物品贴图对照表

| 贴图文件 | 对应物品 | 建议美术方向 |
| --- | --- | --- |
| `textures/item/installation_tool.png` | **安装工具** `installation_tool` | 扳手/扳手+锤子风格工具图标 |
| `textures/item/crushed_iron.png` | **铁碎矿** `crushed_iron` | 碎矿石粉/颗粒图标 |

> 所有方块物品（`*_item`）直接复用其方块模型，无需单独物品贴图。

---

## 四、待办与已知问题（美术/程序需协同）

1. **钢化玻璃 `tank_glass` 目前直接引用原版玻璃**（`minecraft:block/glass`）。
   计划新增 `textures/block/tank_glass.png`，并把
   `models/block/tank_glass.json` 的 `all` 指向它。
   —— 对应 `docs/tank-multiblock-design.md` 中“玻璃让玩家看到液位”的要求。

2. **储罐外壳贴图命名不一致**：外壳模型 `tank_shell.json` 引用的正面贴图叫
   `tank_front`（旧坦克遗留），其余是 `tank_top/side`。
   建议统一为 `tank_shell_top/_side/_front`，并同步改 model JSON（需程序侧配合）。

3. **储罐框架复用了旧 LV 框架贴图 `lv_frame`**。框架与外壳视觉上应明确区分
   （框架=骨架，外壳=实心钢板）。正式资源到位后，建议新增独立 `tank_frame.png`。

4. **旧坦克遗留贴图**：`tank_front.png / tank_side.png / tank_top.png` 是上一版
   单方块坦克的遗留资源，现被新储罐外壳暂时复用。美术出图后应确认是否全部重新绘制。

5. 所有占位贴图均为纯色块，**没有任何像素级细节**，请勿直接用于发布版本。

---

## 五、替换流程（给美术/程序）

1. 按 §二 / §三 找到目标文件名，**保持文件名与路径不变**，用新 PNG 覆盖。
2. 分辨率保持 `16×16`（如需 32×32，需同时告知程序调整 `pack.mcmeta` 的 `pack_format`/ 分辨率）。
3. 本地验证：
   ```powershell
   .\gradlew.bat runClient
   ```
   进入游戏后用创造模式物品栏取出对应方块，确认无黑紫丢失贴图。
4. 更新 `CHANGELOG.md` 记录“替换了哪些贴图”。
5. 提交并按 `scripts/push.ps1` 推送。

---

## 六、占位贴图总量速览

| 类别 | 数量 |
| --- | --- |
| 方块贴图（`textures/block`） | 32 |
| 物品贴图（`textures/item`） | 2 |
| 需新增（见 §四） | 2（`tank_glass.png`、建议的 `tank_frame.png`） |

> 全部 34 张现存贴图**均为占位**。任何一张出现在正式版本里都视为未完成。

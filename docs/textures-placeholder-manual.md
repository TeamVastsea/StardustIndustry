# 材质占位手册（美术替换指引）

> 本文件是**美术资源替换的唯一权威清单**。
> 当前仓库内**所有贴图均为临时占位资源**（纯色/简单色块，16×16），
> 仅为让方块在游戏里可辨认、可测试。正式美术资源到位后，请按本表逐一替换。
>
> 最后更新：0.1.0（高亮模组兼容 + 储罐动态多方块）
>
> 关于储罐框架/外壳/玻璃的**连接材质方案与美术出图清单**，请另见
> [`docs/connected-textures-plan.md`](connected-textures-plan.md)。

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

> **连接材质**：储罐采用**方案 A（原生 6 面连接，两套贴图）**。每个参与连接的贴图都要出
> **两张**：带边框（外露时用）与 `_inner` 无边框（与该方向邻居相连时用）。
> 程序不做 UV 裁切。详见 [`docs/connected-textures-plan.md`](connected-textures-plan.md)。

| 贴图文件 | 对应方块 | 用途 / 贴图键 | 建议美术方向 |
| --- | --- | --- | --- |
| `textures/block/tank_frame.png` | **储罐框架** `tank_frame` | `cube_all` 全六面（带边框） | 金属桁架/角钢，强调“边缘骨架” |
| `textures/block/tank_frame_inner.png` | 同上 | 相连方向用的**无边框**版 | 连续纹理 |
| `textures/block/tank_shell_top.png` | **储罐外壳** `tank_shell` | `cube_bottom_top` 的 `top`/`bottom`（带边框） | 钢板顶面 |
| `textures/block/tank_shell_top_inner.png` | 同上 | 相连方向的无边框版 | 连续纹理 |
| `textures/block/tank_shell_side.png` | **储罐外壳** `tank_shell` | `cube_bottom_top` 的 `side`（带边框） | 钢板侧面 |
| `textures/block/tank_shell_side_inner.png` | 同上 | 相连方向的无边框版 | 连续纹理 |
| `textures/block/tank_glass.png` | **钢化玻璃** `tank_glass` | `cube_all`（`render_type: translucent`，带边框，**须带 alpha**） | 自制钢化玻璃，中心足够透 |
| `textures/block/tank_glass_inner.png` | 同上 | 相连方向的无边框版（**须带 alpha**） | 连续玻璃 |

> 说明：外壳模型已从旧版 `orientable`（top/front/side 三面）**简化为 `cube_bottom_top`**
> （top + side 两面）。原因：连接覆盖模型按**世界方向**映射贴图，若外壳随 `facing` 旋转，
> 连接方向会与贴图朝向错位。去掉正面区分后，连接方向与贴图一一对应。
> 原 `tank_front` 贴图（旧储罐遗留）不再使用。
>
> 全部 18 个连接覆盖模型（`tank_frame/shell/glass_conn_<方向>.json`）由
> `scripts/gen-tank-conn-models.ps1` 生成，占位贴图由 `scripts/gen-tank-conn-textures.py` 生成。

### 2.2 机器与结构

| 贴图文件 | 对应方块 | 贴图键 | 当前占位 | 建议美术方向 |
| --- | --- | --- | --- | --- |
| `textures/block/crusher.png` | **破碎机** `crusher` | 物品图标用 | 占位 | 整机预览图（物品栏用） |
| `textures/block/crusher_top.png` | 破碎机 | `top` | 占位 | 进料口/顶盖 |
| `textures/block/crusher_front.png` | 破碎机 | `front` | 占位 | 出料口/观察窗 |
| `textures/block/crusher_side.png` | 破碎机 | `side` | 占位 | 机身侧面 |
| `textures/block/steel_casing.png` | **钢制机壳** `steel_casing` | `cube_all` | 占位 | 通用钢制机壳纹理（多方块基座重复使用） |

### 2.3 端口（按等级，仅保留 LV/MV/HV/EHV）

> **无等级（无前缀）端口已删除**。所有端口一律带等级前缀，当前实现 LV 一套；
> MV/HV/EHV 按同一模式扩展（`mv_item_port` 等）。

| 贴图文件 | 对应方块 | 贴图键 | 建议美术方向 |
| --- | --- | --- | --- |
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

1. ~~钢化玻璃引用原版玻璃~~ **已解决**：新增 `tank_glass.png`（占位），模型已指向它。
2. ~~外壳贴图命名不一致~~ **已解决**：外壳模型改为 `cube_bottom_top`（top/side 两面），
   统一使用 `tank_shell_top` / `tank_shell_side`；旧 `tank_front` 不再使用。
3. ~~框架复用旧 LV 框架贴图~~ **已解决**：新增独立 `tank_frame.png`（占位）。
4. **旧储罐遗留贴图可删除**：`tank_front.png / tank_side.png / tank_top.png` 已无任何模型引用，
   可在美术确认后删除（`lv_frame.png` 仍被多方块基座使用，**不要删**）。
5. **连接贴图的 `_inner` 系列**：储罐框架/外壳/玻璃各需带边框 + `_inner` 两张，共 8 张新图
   （见 §2.1 与 `docs/connected-textures-plan.md`）。
6. 所有占位贴图均为纯色块，**没有任何像素级细节**，请勿直接用于发布版本。

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

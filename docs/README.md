# Stardust Industry 文档索引

> 文档状态基于当前 `0.1.0` 多模块工程。代码行为与文档冲突时，以当前代码和
> `neoforge.mods.toml` 为准，并应在同一变更中修正文档。

## 从哪里开始

| 目标 | 文档 |
|---|---|
| 理解四个模组如何拆分 | [`module-architecture.md`](./module-architecture.md) |
| 搭建环境、构建、运行和排错 | [`dev-environment.md`](./dev-environment.md) |
| 理解多方块通用框架 | [`multiblock-design.md`](./multiblock-design.md) |
| 理解流体/气体储罐 | [`tank-multiblock-design.md`](./tank-multiblock-design.md) |
| 查询统一术语和注册 ID | [`terminology.md`](./terminology.md) |
| 替换占位贴图 | [`textures-placeholder-manual.md`](./textures-placeholder-manual.md) |
| 评估未来连接材质扩展 | [`connected-textures-plan.md`](./connected-textures-plan.md) |
| 查询版本变化 | [`../CHANGELOG.md`](../CHANGELOG.md) |

## 权威层级

同一问题在多份文档中出现时，按以下顺序判断：

1. 模组边界、依赖和构建规则：`module-architecture.md`。
2. 本地命令、运行目录和测试方法：`dev-environment.md`。
3. 储罐细节：`tank-multiblock-design.md`。
4. 通用多方块机制：`multiblock-design.md`。
5. 名称、翻译和注册 ID：`terminology.md`。

设计文档中的“规划”“后续”和未勾选条目不代表当前代码已经实现。当前模块状态及已验证范围
以 `module-architecture.md` 和 `dev-environment.md` 为准。

## 当前工程状态

| 模块 | 状态 | 说明 |
|---|---|---|
| Core | 已实现 | 原创玩法、机器、多方块、资源与渲染 |
| UtilsEx | 已实现 | JEI、Jade、WTHIT、TOP 对接 |
| MekanismEx | 已实现 | 化学品映射与气体端口能力桥接 |
| AE2Ex | 可独立加载的骨架 | 依赖和入口已隔离，尚无 AE2 业务功能 |

## 文档维护规则

- 路径必须从仓库根目录写起，例如
  `StardustIndustry-Core/src/main/java/...`，不要再写含糊的根 `src/main/...`。
- “Core 可独立运行”与“某扩展的外部硬依赖”要分开描述。Mekanism 对 Core 不是依赖，
  但对 MekanismEx 是硬依赖。
- 第三方 API 代码必须标明所属扩展模块，不能再使用未限定的 `compat/...` 路径。
- 新增或调整模块时，同步更新 `module-architecture.md`、`dev-environment.md`、根 README
  和 `CHANGELOG.md`。
- 文档中的测试结论必须写清测试范围；“成功加载”不等于“所有游戏内交互均已验证”。

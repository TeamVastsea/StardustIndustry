# 多模块架构与依赖边界

> 本文是 Stardust Industry 模块拆分、依赖方向和发布产物的权威说明。
> 最后核对：2026-10-10，版本 `0.1.0`。

## 1. 拆分目标

工程使用 Gradle Kotlin DSL 多项目构建。原创玩法全部留在 Core；凡是直接引用其他模组 API 的
实现都进入独立扩展。目标是：

- 只安装 Core 时不要求 Mekanism、AE2、JEI 或高亮模组存在。
- 外部模组 API 升级或失效时，只影响对应扩展的编译和发布。
- 每个模块拥有独立 `src`、`@Mod` 入口、模组 ID、元数据和发布 JAR。
- 根项目仍提供统一的 `build` 与 `runClient`。

## 2. 依赖图

```text
Minecraft + NeoForge
        |
        v
StardustIndustry-Core
        |
        +----> StardustIndustry-UtilsEx ----> JEI / Jade / WTHIT / TOP（均可选）
        |
        +----> StardustIndustry-MekanismEx -> Mekanism（必需）
        |
        `----> StardustIndustry-AE2Ex ------> AE2（必需）
                                                   `-> GuideME（AE2 运行依赖）
```

箭头表示“左侧模块是右侧模块的前置”。所有扩展可以依赖 Core；Core 不得反向依赖扩展，
扩展之间也不得互相依赖。

## 3. 模块清单

| Gradle 项目 | 模组 ID | Java 包 | 发布文件 | 状态 |
|---|---|---|---|---|
| `StardustIndustry-Core` | `stardustindustry` | `com.stardustindustry.stardustindustry` | `stardustindustry-core-<version>.jar` | 已实现 |
| `StardustIndustry-UtilsEx` | `stardustindustry_utilsex` | `com.stardustindustry.utilsex` | `stardustindustry-utilsex-<version>.jar` | 已实现 |
| `StardustIndustry-MekanismEx` | `stardustindustry_mekanismex` | `com.stardustindustry.mekanismex` | `stardustindustry-mekanismex-<version>.jar` | 已实现 |
| `StardustIndustry-AE2Ex` | `stardustindustry_ae2ex` | `com.stardustindustry.ae2ex` | `stardustindustry-ae2ex-<version>.jar` | 骨架 |

四个模块当前共享根 `gradle.properties` 中的版本号，发布时版本保持一致。

## 4. 目录结构

```text
StardustIndustry/
|-- build.gradle.kts                  根生命周期与统一任务
|-- settings.gradle.kts               子项目注册
|-- gradle.properties                 Minecraft、NeoForge 与模组版本
|-- StardustIndustry-Core/
|   |-- build.gradle.kts
|   `-- src/main/{java,resources}/
|-- StardustIndustry-UtilsEx/
|   |-- build.gradle.kts
|   `-- src/main/{java,resources}/
|-- StardustIndustry-MekanismEx/
|   |-- build.gradle.kts
|   `-- src/main/{java,resources}/
`-- StardustIndustry-AE2Ex/
    |-- build.gradle.kts
    `-- src/main/{java,resources}/
```

根目录没有业务 `src/`。`.gradle/`、`runs/`、`run/`、`build/` 和临时构建目录都是生成物。

## 5. 各模块职责

### 5.1 Core

Core 拥有全部原创、可独立运行的功能：

- 方块、物品、菜单、方块实体和能力注册。
- 静态/动态多方块框架、机器模块和配方系统。
- 自有气体抽象 `Gas`、`GasStack`、`IGasHandler`、`GasRegistry`。
- 流体/气体储罐及客户端渲染。
- 网络数据包、配置和 `stardustindustry` 命名空间资源。

Core 可以定义供扩展调用的稳定接口，但不得 `import` 第三方模组包，也不得通过反射主动加载扩展。

### 5.2 UtilsEx

UtilsEx 汇总不改变核心存储语义的工具类兼容：

- JEI 配方分类与配方注册。
- Jade 储罐信息插件。
- WTHIT/WAILA 储罐信息插件。
- The One Probe 储罐信息插件。

UtilsEx 硬依赖 Core。JEI、Jade、WTHIT、TOP 在元数据中都是客户端可选依赖；缺少其中任意一个
不会阻止 UtilsEx 加载。插件类必须依靠各自的发现机制或加载检查，避免在目标模组缺失时链接其类型。

### 5.3 MekanismEx

MekanismEx 硬依赖 Core 与 Mekanism，负责：

- 将 Mekanism `Chemical` 注册表映射到 Core 的 `GasRegistry`。
- 在气体端口暴露 Mekanism chemical capability。
- 在 `ChemicalStack` 与 Core `GasStack` 之间转换。

Mekanism 未安装时不要安装 MekanismEx。Core 本身仍可正常运行。

### 5.4 AE2Ex

AE2Ex 已拥有独立入口、包、元数据和构建产物，并声明 Core 与 AE2 为硬依赖。目前没有迁移对象，
因此只记录加载成功，尚未提供 AE2 网络、存储或配方业务功能。后续 AE2 功能必须直接加入本模块，
不得回写 Core。

### 5.5 暂不创建的扩展

当前代码没有 Create 或 Immersive Engineering 专用 API 调用；现有互操作来自 NeoForge 标准能力。
因此不创建空的 CreateEx/IEEx。只有出现必须直接引用对应 API 的实现时，才新增独立模块。

## 6. 硬依赖与软依赖

依赖必须同时在 Gradle 编译配置和 `META-INF/neoforge.mods.toml` 中表达，二者职责不同：

| 场景 | Gradle | 模组元数据 |
|---|---|---|
| 扩展调用 Core | `implementation(project(...))` | `type="required"` |
| 扩展必须依赖外部模组 | `compileOnly(files(...))` 或 Maven API | `type="required"` |
| 插件只在目标模组存在时生效 | `compileOnly(...)` | `type="optional"` |
| 根开发客户端需要实机验证 | Core `localRuntime` | 不改变发布依赖 |

`compileOnly` 只说明编译 classpath，不等于运行时软依赖；玩家侧是否必需由模组元数据决定。

## 7. 构建与产物

根 `build.gradle.kts` 负责：

1. 为所有模块统一 Java 21、版本、仓库和资源变量展开。
2. 调用 `fetchCompatLibs` 下载未入库的第三方编译/开发运行依赖。
3. 构建每个模块自己的普通 JAR，不把 Core 或第三方模组打进扩展 JAR。
4. 通过 `collectModJars` 将四个发布 JAR 汇总到根 `build/libs/`。

```bash
./gradlew build
./gradlew collectModJars
./gradlew :StardustIndustry-Core:build
./gradlew :StardustIndustry-UtilsEx:build
```

各模块的中间产物位于系统临时目录：

```text
<java.io.tmpdir>/stardustindustry-build/<Gradle项目名>/
```

## 8. 根 runClient 的工作方式

根 `runClient` 只启动 `StardustIndustry-Core:runClient`：

1. 下载/确认第三方开发依赖。
2. 构建 UtilsEx、MekanismEx、AE2Ex。
3. 清理 `run/client/mods/` 中这三个扩展的旧版本。
4. 复制最新扩展 JAR。
5. 以 Core 源码集启动单个 Minecraft 客户端。

扩展模块关闭 NeoGradle 默认 runs，防止从根目录执行 `runClient` 时同时启动多个客户端。
JEI、TOP、Mekanism、AE2 和 GuideME 由 Core 的开发运行 classpath 提供。Jade 与 WTHIT 因服务加载
冲突风险不与 TOP 同时加入默认开发客户端，需要分别测试。

## 9. 安装组合

| 需求 | 需要安装 |
|---|---|
| 只使用原创内容 | Core |
| 配方查看/方块信息 | Core + UtilsEx + 至少一个受支持工具模组 |
| Mekanism 化学品与气体端口互通 | Core + MekanismEx + Mekanism |
| 当前 AE2 扩展骨架 | Core + AE2Ex + AE2 + AE2 所需运行依赖 |

不要单独安装任一扩展，也不要把四个 JAR 合并成一个 JAR。

## 10. 新增扩展的准入规则

新增第三方对接前依次确认：

1. 标准 NeoForge capability 或数据包是否已经能完成互操作。
2. 是否确实存在直接引用第三方 API 的代码。
3. 是加入现有 UtilsEx，还是需要独立硬依赖模块。
4. 是否有明确的模组 ID、Java 包、入口类、元数据和发布文件名。
5. Core 是否保持零第三方 API 引用。
6. 根 `modules` 映射、`settings.gradle.kts`、依赖下载脚本、运行环境和本文是否同步更新。

功能型硬依赖通常单独建 `<ModName>Ex`；多个互不要求存在的界面/信息插件可以放入 UtilsEx。

## 11. 边界验证

提交模块调整前至少执行：

```bash
./gradlew build
./gradlew runClient --dry-run
```

并确认：

- 根 `build/libs/` 恰好生成四个独立 JAR。
- Core 源码和 Core JAR 不含 Mekanism、AE2、JEI、Jade、WTHIT、TOP 类型。
- 扩展 JAR 不打包 Core 类或第三方依赖类。
- 每个 JAR 的 `neoforge.mods.toml` 与本文件依赖表一致。
- dry-run 只有 Core 的 `runClient` 会启动 Java 进程。
- 实际启动日志包含四个 Stardust 模组且没有缺失依赖或类加载错误。

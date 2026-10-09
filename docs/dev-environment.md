# 开发、构建与测试环境

> 本文说明如何从仓库根目录构建和运行当前四模块工程。模块职责和依赖语义见
> [`module-architecture.md`](./module-architecture.md)。

## 1. 环境要求

| 项目 | 要求 |
|---|---|
| JDK | 21；Gradle toolchain 可在缺失时自动获取 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256（以 `gradle.properties` 为准） |
| Gradle | 使用仓库自带 wrapper，不依赖全局 Gradle |
| 网络 | 首次构建需要访问 Maven 仓库和 Modrinth CDN |

所有命令默认从仓库根目录执行。macOS/Linux 使用 `./gradlew`，Windows PowerShell 使用
`.\gradlew.bat`。

## 2. 首次构建

```bash
./gradlew build
```

根构建会自动执行 `fetchCompatLibs`，下载未提交到 Git 的第三方扩展依赖，然后编译四个模块。
无需手工准备 `libs/`。下载脚本分别为：

- macOS/Linux：`scripts/fetch-hud-libs.sh`
- Windows：`scripts/fetch-hud-libs.ps1`

脚本名称保留历史命名，但现在同时下载 HUD、Mekanism、AE2 和 GuideME 依赖。

## 3. 常用任务

| 命令 | 作用 |
|---|---|
| `./gradlew build` | 编译、检查四个模块并汇总发布 JAR |
| `./gradlew collectModJars` | 只把已有/需构建的模块 JAR 汇总到根 `build/libs/` |
| `./gradlew fetchCompatLibs` | 单独下载扩展编译与开发运行依赖 |
| `./gradlew runClient` | 构建三个扩展并随 Core 启动一个开发客户端 |
| `./gradlew runServer` | 启动 Core 开发服务端，不自动加载三个扩展 JAR |
| `./gradlew runData` | 执行 Core 数据生成器 |
| `./gradlew :StardustIndustry-Core:compileJava` | 只编译 Core |
| `./gradlew :StardustIndustry-UtilsEx:build` | 只构建 UtilsEx 及其项目依赖 |
| `./gradlew :StardustIndustry-MekanismEx:build` | 只构建 MekanismEx 及其项目依赖 |
| `./gradlew :StardustIndustry-AE2Ex:build` | 只构建 AE2Ex 及其项目依赖 |

日常不需要执行 `clean`。模块中间产物位于系统临时目录，清理后会触发耗时的 NeoForm 重建。
确实怀疑缓存损坏时再使用：

```bash
./gradlew build --no-build-cache --rerun-tasks
```

## 4. 构建输出

根构建只在 `build/libs/` 收集可发布 JAR：

```text
build/libs/
|-- stardustindustry-core-0.1.0.jar
|-- stardustindustry-utilsex-0.1.0.jar
|-- stardustindustry-mekanismex-0.1.0.jar
`-- stardustindustry-ae2ex-0.1.0.jar
```

模块自己的中间目录是：

```text
<java.io.tmpdir>/stardustindustry-build/StardustIndustry-Core/
<java.io.tmpdir>/stardustindustry-build/StardustIndustry-UtilsEx/
<java.io.tmpdir>/stardustindustry-build/StardustIndustry-MekanismEx/
<java.io.tmpdir>/stardustindustry-build/StardustIndustry-AE2Ex/
```

不要从临时目录发布；统一使用根 `build/libs/`。

## 5. 开发客户端

根 `runClient` 的运行目录是 `run/client/`。启动前会自动：

1. 构建 UtilsEx、MekanismEx、AE2Ex。
2. 删除 `run/client/mods/` 中这三个扩展的旧版本。
3. 复制最新扩展 JAR。
4. 在 Core 的开发 classpath 上加入 JEI、TOP、Mekanism、AE2 和 GuideME。
5. 启动唯一的 Core 客户端进程。

不要手工把三个 Stardust 扩展再次复制到 `run/client/mods/`。其他测试模组可以放入该目录，
但应记录版本和它们引入的依赖，以便复现问题。

`runClient` 不会自行退出。看到以下日志通常表示已经完成主菜单所需的资源加载：

```text
Sound engine started
Reloading ResourceManager: ... mod/stardustindustry ...
```

退出游戏窗口或终止 Gradle 前台任务即可结束开发客户端。

### 5.1 默认运行模组

| 模组 | 版本 | 用途 |
|---|---|---|
| Core / UtilsEx / MekanismEx / AE2Ex | `0.1.0` | 本项目四模块联合启动 |
| JEI | `19.56.0.441` | UtilsEx 配方查看验证 |
| The One Probe | `12.0.8` | UtilsEx HUD 验证 |
| Mekanism | `10.7.19.85` | MekanismEx 化学品与端口验证 |
| AE2 | `19.2.18` | AE2Ex 加载边界验证 |
| GuideME | `21.1.19` | AE2 运行依赖 |

Jade 与 WTHIT 默认只参与编译，不与 TOP 同时进入开发客户端。两者同时存在时可能触发 WTHIT
服务加载器冲突；验证插件时使用仅安装其中一个目标 HUD 模组的独立实例。

## 6. 依赖文件

`libs/` 被 Git 忽略，只保存本地第三方二进制。当前脚本管理：

| 文件 | 使用者 | 发布语义 |
|---|---|---|
| Jade | UtilsEx | 可选 |
| WTHIT + Bad Packets | UtilsEx | 可选 |
| The One Probe | UtilsEx | 可选 |
| Mekanism | MekanismEx | 硬依赖 |
| AE2 | AE2Ex | 硬依赖 |
| GuideME | AE2 开发运行环境 | 由 AE2 运行时需要 |

这些 JAR 不会被打包进任何 Stardust JAR。CI 也调用同一下载脚本，避免本地与 CI classpath 漂移。

## 7. 发布安装组合

Core 是唯一必装模块。按整合包需要选择扩展：

| 场景 | Stardust JAR | 外部前置 |
|---|---|---|
| 纯 Core | `stardustindustry-core` | 无第三方模组前置 |
| JEI/HUD 兼容 | Core + UtilsEx | 所需的 JEI/Jade/WTHIT/TOP，可一个都不装 |
| Mekanism 互通 | Core + MekanismEx | Mekanism |
| AE2 扩展骨架 | Core + AE2Ex | AE2 及 AE2 自身运行依赖 |

PowerShell 更新示例：

```powershell
.\gradlew.bat build

$target = Join-Path $env:USERPROFILE "Documents\Minecraft\instance\mods"
Remove-Item "$target\stardustindustry-*.jar" -Force -ErrorAction SilentlyContinue
Copy-Item ".\build\libs\stardustindustry-core-*.jar" $target -Force
Copy-Item ".\build\libs\stardustindustry-utilsex-*.jar" $target -Force
```

示例只安装 Core 与 UtilsEx。不要无条件复制 MekanismEx/AE2Ex；目标实例缺少对应硬依赖时，
NeoForge 会正确拒绝加载。

## 8. 验证矩阵

截至 2026-10-10 已完成：

| 范围 | 结果 | 说明 |
|---|---|---|
| 根 `build` | 通过 | 四个 JAR 独立生成并汇总 |
| 根 `runClient --dry-run` | 通过 | 只存在一个实际客户端启动任务 |
| 四模块联合客户端 | 通过 | 四个模组均被 NeoForge 发现并完成资源加载 |
| MekanismEx 初始化 | 通过 | 实测注册 65 种 chemical，并注册气体端口桥接 |
| UtilsEx + TOP | 通过 | 储罐信息插件加载 |
| AE2Ex | 仅加载验证 | 当前没有 AE2 业务功能可测 |
| Jade 单独运行 | 待独立实例复测 | 默认客户端不加载 Jade |
| WTHIT 单独运行 | 待独立实例复测 | 默认客户端不加载 WTHIT |
| Create / IE 标准能力互操作 | 部分待测 | 当前没有专用扩展代码 |

“客户端成功加载”只证明依赖、类加载、注册和资源阶段通过；管道抽取、容器交互、世界保存等
游戏内行为仍应按功能逐项测试。

## 9. 常见问题

### 9.1 配置缓存提示被丢弃

NeoForge 的 JavaExec 开发启动任务在执行期访问 Gradle project，因此 Core 的运行任务被显式标记为
不兼容配置缓存。这只影响 `runClient`/`runServer` 的缓存复用，不影响普通 `build`；普通构建应能保存
和复用配置缓存。

### 9.2 JEI 配置翻译警告

下面这类警告来自 NeoForge/JEI 的配置翻译检查，不表示 Stardust 模组加载失败：

```text
ConfigTranslationChecker: Missing config translations for ... jei-client.ini
```

### 9.3 缺少扩展前置

如果安装 MekanismEx 却没有 Mekanism，或安装 AE2Ex 却没有 AE2，NeoForge 会在模组加载阶段给出
明确的 mandatory dependency 错误。正确处理是补齐前置或移除该扩展，不要把外部 API 塞回 Core。

### 9.4 出现重复模组

确认 `run/client/mods/` 没有手工复制的 Core，也没有同一扩展的额外版本。根 `runClient` 只自动清理
它管理的三个扩展文件名，不会删除其他测试模组。

## 10. 提交前检查

```bash
./gradlew build
./gradlew runClient --dry-run
git diff --check
```

同时确认：

- 根目录没有重新出现业务 `src/`。
- Core 没有第三方模组 API import。
- 根 `build/libs/` 是四个独立 JAR。
- 修改依赖或模块时同步更新 [`module-architecture.md`](./module-architecture.md)。

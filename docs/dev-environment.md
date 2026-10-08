# 开发与测试环境

> 本文档记录本项目的**开发环境**与**本地测试整合包**的约定，供维护者与协作者复现。
> 与代码无关的环境信息都放这里，免得散落在提交说明或聊天记录里。

---

## 一、开发环境（本机）

| 项 | 值 |
|---|---|
| 项目目录 | `K:\Minecraft MODS\StardustIndustry` |
| JDK | 21（Gradle toolchain 自动拉取；**不要**手动设置 `JAVA_HOME`） |
| Minecraft / 加载器 | 1.21.1 / NeoForge 21.1.x |
| 构建输出目录 | `%TEMP%\stardustindustry-build`（刻意重定向出 NAS 重解析点树） |
| 构建产物 | `%TEMP%\stardustindustry-build\libs\stardustindustry-0.1.0.jar` |

常用命令：

```bash
./gradlew build          # 编译 + 打包
./gradlew runClient      # 启动开发客户端（不自动退出）
./gradlew runServer      # 启动开发服务端（不自动退出）
./gradlew compileJava --no-configuration-cache   # 只编译，快速验证
```

> **不要**执行 `clean`：构建输出在临时目录，清掉会拖慢整个重新编译。
> PowerShell 下 `git`/`gradle` 即便成功也常打印 `Exited with code 1`，以输出内容为准。

### 开发客户端的模组目录

`run/client/mods/`。把测试用模组（Create、高亮、IE、Mekanism 等）放进这里，
`runClient` 会一并加载。**不要把构建产物 jar 复制进来**——开发环境已通过 classpath
加载本模组，重复会出现两份。

---

## 二、本地测试整合包

用于把打包好的 jar 放进"真实"整合包目录做端到端测试。

| 项 | 值 |
|---|---|
| 整合包 mods 目录 | `C:\Users\MCTV\Documents\Minecraft\.minecraft\versions\1.21.1-NeoForge\mods` |
| 更新 jar | 构建后把 `stardustindustry-0.1.0.jar` 复制进去，**先删旧版** |
| 高亮模组 | 整合包内**只保留 The One Probe（TOP）**一个，避免多个高亮叠加 |

### 当前放置的模组清单

| 模组 | 版本 | 用途 |
|---|---|---|
| stardustindustry | 0.1.0（本项目） | 主体 |
| stardustindustrymetaladdon | 1.0.1 | 金属/矿物基础 |
| create | 1.21.1-6.0.10 | 兼容性测试（流体管道、动力臂、放置） |
| ImmersiveEngineering | 1.21.1-12.4.2-194 | 兼容性测试（管道、油桶等容器） |
| Mekanism | 1.21.1-10.7.19.85 | 兼容性测试（管道、气体、能量） |
| theoneprobe | 1.21-neo-12.0.8 | 高亮显示（唯一保留） |
| jei | 1.21.1-19.56.0.441 | 配方查看 |
| jecharacters | 4.5.25 | 中文搜索优化 |
| cloth-config / curios / geckolib / forgified-fabric-api | 见文件名 | IE 的依赖 |

### 模组的来源

- **Create / Jecharacters / MetalAddon**：从开发环境 `run/client/mods` 复制。
- **Immersive Engineering + 其依赖**：从 `...\versions\TerraFirma Rebirth\mods` 复制。
- **Mekanism**：从 Modrinth 下载（`mekanism` 项目，1.21.1 / neoforge）。
- **TOP**：从本项目 `libs/`（由 `scripts/fetch-hud-libs.ps1` 下载）复制。

---

## 三、高亮模组的开发依赖

本项目对三个高亮模组都提供兼容插件（Jade / WTHIT / TOP），它们是 **可选软依赖**：

- 编译期：`libs/` 下的 jar 以 `compileOnly` + `localRuntime` 接入，
  由 `scripts/fetch-hud-libs.sh`（CI）与 `scripts/fetch-hud-libs.ps1`（本地）在构建前下载。
- 运行期：`libs/` 被 git 忽略，不进仓库；开发客户端在 `run/client/mods` 放哪个高亮就测哪个。
  本地整合包**只保留 TOP 一个**。

各插件的配置翻译键（避免 Jade 因缺少键而断言）：

- Jade：`config.jade.plugin_stardustindustry.tank_contents`
- WTHIT：`config.waila.plugin_stardustindustry.tank`

---

## 四、更新整合包 jar 的流程

```powershell
# 1. 构建
.\gradlew.bat build --no-configuration-cache

# 2. 删除旧版并复制新版
$target = "C:\Users\MCTV\Documents\Minecraft\.minecraft\versions\1.21.1-NeoForge\mods"
Remove-Item "$target\stardustindustry-*.jar" -Force -ErrorAction SilentlyContinue
Copy-Item "$env:TEMP\stardustindustry-build\libs\stardustindustry-0.1.0.jar" $target -Force
```

---

## 五、兼容性测试现状

| 模组 | 加载 | 已测内容 | 待测内容 |
|---|---|---|---|
| Create | ✅ | —— | 流体泵抽取/灌入、动力臂（Deployer）交互、蓝图炮（Schematicannon）、稳定性 |
| Immersive Engineering | ✅ | —— | 流体管道抽取/灌入、**油桶等大容量容器**的手持交互 |
| Mekanism | ✅ | —— | 流体管道抽取/灌入、能量（FE）互通 |
| The One Probe | ✅ | 储罐任意方块指向显示内部流体与容量 | —— |

> 三个兼容模组与高亮模组均已在开发客户端**成功加载**（日志到达 `Sound engine started`，
> 无加载失败）。上表「待测内容」需要在游戏内实际连接管道/机器后逐项验证。

---

## 六、已知环境注意事项

- `runClient` / `runServer` **不会自行退出**：在后台启动，用日志中的 `Sound engine started` /
  `Done (` 判断是否加载完成，用 `Stop-Process` 结束 java 进程。
- 启动日志里的 `Error loading class: net.minecraftforge.common.ForgeTier` 来自 Jecharacters 的
  旧 Forge 兼容分支，属**无害警告**，可忽略。
- 启动日志里的 `ConfigTranslationChecker: Missing config translations for '...\jei\client\jei-client.ini'`
  来自 **NeoForge 自身**（`net.neoforged.fml.config`）对 JEI 配置文件的检查，**与本模组无关**，可忽略。
- 删除端口等注册项后，旧存档里残留的方块实体类型 id 会被丢弃而**不会崩溃**。

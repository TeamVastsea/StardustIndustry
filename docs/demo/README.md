# 电力闭环 Demo

这个 demo 用真实电网状态验证完整链路，不以静态陈列或预写方块实体 NBT 作为验收结果。
展示区固定从主世界 `(0, 64, 0)` 开始，`build` 和 `reset` 都是幂等操作，便于 quickPlay、
GameTest 和人工复测得到同一布局。

## 启动

第一次验收先新建一个名为 `Stardust Power Demo`、允许命令的超平坦世界。之后从仓库根目录
直接 quickPlay 进入该世界：

```text
./gradlew runClient --args='--quickPlaySingleplayer "Stardust Power Demo"' --no-configuration-cache
```

进入世界后执行：

```mcfunction
/stardustindustry demo build
```

需要重新生成时执行 `/stardustindustry demo reset`，再执行 `build`。不要用 `/fill` 清展示区；
正式 reset 还负责清除维度级权威线缆记录、方块实体缓存/状态和 demo 自己的控制数据。

## 验收顺序

1. 给燃料发电机加入燃料并合闸，确认它真实产生焦耳，而不是由创造电源代替。
2. 合上 LV 断路器，观察 LV/MV 升压、MV 长线传输和远端降压。
3. 启动破碎机，确认输入原料变成产物，断电后机器停机且不会免费完成。
4. FE 转换器初始为原生电网 → FE、分闸状态。保持它分闸，并在一个粉碎配方周期内关闭发电机；
   储能站预充 `284,750 J`，会接管粉碎机约 42.5 秒（计入线损后会略短），随后耗尽并让机器
   保留进度暂停。若让发电机长时间给储能充电，接管时间会相应延长。
5. 断开断路器，确认下游机器、储能充电和 FE 输出同时停止。
6. 完成储能演示后再合上 FE 转换器，先验证原生电网 -> FE；随后分闸、切换方向并重新合闸，
   验证 FE -> 原生电网，核对 95% 效率。转换方向不会改变固定连接面。
7. 读取发电机、变压器、保护器、储能、机器和 FE 转换器的电压、功率、电流与故障状态。

创造电源只用于故障和高压调试，不计作“燃料发电成功”。平衡三相线路按
`P=√3·U_line·I` 求每相电流，线路损耗是三根相线各自 `I²R` 的总和。

## 故障场景

下面四个命令是可重复的验收开关：

```mcfunction
/stardustindustry demo fault overload
/stardustindustry demo fault phase_loss
/stardustindustry demo fault overvoltage
/stardustindustry demo fault reset
```

`overload` 用于观察保护器和线缆热量；`phase_loss` 验证带电缺相的 40 tick 确认窗口；
`overvoltage` 验证额定等级与端口保护；`fault reset` 恢复正常接线。测试故障前先确认展示区
已由 `build` 成功生成。

## 数据包入口

要让一个现有世界在玩家首次进入时自动构建 demo：

```text
node tools/generate-showcase-datapack.mjs "/path/to/world"
```

生成器还提供不需要 Minecraft 世界的自检：

```text
node tools/generate-showcase-datapack.mjs --check
```

数据包提供 `stardust_showcase:build`、`reset` 和四个 `fault_*` 函数。它只是公开命令的
启动器，绝不直接写旧版 `Cables` NBT 或其他内部格式；线缆必须由模组服务层建立，确保
权威记录、渲染缓存失效、电网注册和后续存档迁移始终走同一条代码路径。

## 交付包

构建四个第一方 jar、依赖说明、版本化 Zip 和 SHA-256：

```text
./gradlew packageFirstPartyMods
```

输出位于 `build/distributions/`。Zip 只含 Core、UtilsEx、MekanismEx、AE2Ex 四个第一方
jar 和 `DEPENDENCIES.txt`；Mekanism、Applied Energistics 2、GuideME 与可选 HUD/配方查看器
不会被混入第一方包。把 Zip 交给其他玩家时应同时提供旁边的 `.sha256` 文件。校验时在
输出目录中执行 `shasum -a 256 -c StardustIndustry-0.1.0-mc1.21.1-first-party.zip.sha256`。

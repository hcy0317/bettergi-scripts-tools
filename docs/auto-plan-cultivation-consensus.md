# AutoPlan 养成规划闭环共识

状态：已达成实现共识
日期：2026-08-23
适用范围：BetterGI、AutoPlan、bettergi-scripts-tools，以及被规划器调用的现有采集/刷怪脚本

## 1. 目标

把 AutoPlan 从“用户预先填写固定次数，然后按日重复执行”的配置执行器，升级为类似 StarRailCopilot（下文简称 SRC）养成规划器的持久化闭环：

1. 导入原神养成计算器导出的长图，识别材料需求、当前持有量和初始缺口。
2. 将全部需求保存在同一个可恢复、可审计的材料账本中。
3. 根据材料来源、开放日期、树脂、周次数、路线冷却和用户策略选择下一项行动。
4. 每个执行批次结束后使用实际掉落或背包持有总量更新账本并重新规划。
5. 只在全部需求均已得到确认时结束；等待体力、等待开放日、等待路线刷新、需要人工操作和暂不支持都不是完成。

本文件是实现与验收的共同依据。后续发现实现选择与本文冲突时，应先更新共识，不得在调用方、脚本和后端各自维护不同规则。

### 1.1 完成范围与非目标

“全部需求完成”指本次规划中所有材料取得需求均已确认完成，不等于自动点击角色升级、天赋升级或武器强化。用户在规划期间手动消耗材料时，按本文的重新对账规则处理。

初始范围不包含圣遗物主副词条规划、抽卡、消耗原石购买树脂、自动使用皇冠等不可逆养成操作。活动、纪行、商店等无法稳定自动取得的来源仍要进入账本并显示明确人工行动，不能从需求中消失。

## 2. 证据范围

### 2.1 SRC 对照范围

本轮检查了 SRC 中与养成规划直接相关的完整调用链，而不只限于扫描和模型两个文件：

- `tasks/planner/scan.py`：坐标分列、多页扫描、材料名匹配、行校验、去重。
- `tasks/planner/model.py`：稀有度族、3:1 合成、持久化、进度、预计天数、来源选择和优先级。
- `tasks/planner/target.py`：目标配置和每日重新扫描。
- `tasks/combat/obtain.py`：从“可能获得”详情重新读取当前背包总量，并回写规划器。
- `tasks/combat/combat.py`：接近目标时提高复核频率、每 30 波周期纠偏、开打前停止。
- `tasks/dungeon/dungeon.py`：双倍活动、体力耗尽、合成前后检查、任务延迟和恢复。
- `tasks/dungeon/weekly.py`：周次数检查与下周一恢复。
- `tasks/item/synthesize.py`：合成前重新读取持有量、按稀有度执行、合成后再次回写。

### 2.2 BetterGI 与本机脚本现状

- `AutoDomainTask` 和 `AutoBossTask` 均能在启用奖励识别后返回奖励汇总。
- `RunCountInventoryItemTask` 能按名称批量读取背包持有量，底层使用当前 `OcrFactory.Paddle`。
- `genshin.CraftMaterial()` 已提供材料合成及结果返回。
- `AutoLeyLineOutcropTask` 当前不返回奖励明细。
- `MapLazyAssets` 的自动秘境列表当前只包含 `BlessDomain`、`ForgeryDomain`、`MasteryDomain`，不包含 `TrounceDomain`。
- AutoPlan 当前仍以 `domainRoundNum`、`runCount`、静态列表和当日执行记录为核心；执行后丢弃副本/Boss 返回的奖励字典，循环时也不重新获取最新规划。
- bettergi-scripts-tools 的上传 OCR 仍固定使用 Java RapidOCR 0.0.7 的 PP-OCRv4；BetterGI 本体已经具备 PP-OCRv6 small 检测和识别模型。
- `CD-Aware-AutoGather` 已具备材料目标、按材料设置不同目标、路线扫描、冷却管理、缺口排序和接近目标时背包复核。
- `背包材料统计` 已具备材料/怪物双向映射、背包统计、路线冷却、怪物路线、历史产量和时间成本记录。
- `FullyAutoAndSemiAutoTools` 可作为通用路径执行器，但本身不应成为材料账本的事实来源。

## 3. 从 SRC 迁移的核心不变量

以下逻辑必须迁移：

1. **规划状态持久化**：应用或脚本重启后继续同一规划，不重新从固定次数开始。
2. **材料族建模**：同一材料的低、中、高稀有度属于一个族；允许低级向高级 3:1 合成，不允许逆向分解。
3. **实际持有量优先**：预计掉落只用于调度和决定检查频率，不能作为最终完成证据。
4. **接近目标时收紧批次**：缺口较大时允许批量执行；接近目标时改为单轮并增加权威复核。
5. **周期纠偏**：即使预测没有接近目标，也要周期性重新读取背包，限制长期漂移。
6. **执行前停止**：确认已满足需求后，必须在消耗下一轮资源之前停止。
7. **合成闭环**：合成前重新读取材料，按实际可合成量执行，合成后重新读取并持久化。
8. **来源与限制分离**：普通来源、周本来源、不可日常刷取来源分别处理。
9. **进度和 ETA**：总体进度按取得成本加权，而不是对所有材料行做简单平均。
10. **过期数据清理**：新规划替换旧规划时，已经不再需要的材料行必须从活动规划中移除，但审计记录保留。

## 4. 不直接照搬 SRC 的部分

SRC 是单进程、本地配置、星铁材料体系。下列行为不能原样复制：

- 不把硬编码平均掉率当作账本事实；BetterGI 已能识别结算奖励，应优先使用实际结果。
- 不用“找到的第一条材料行”作为唯一调度策略；原神存在按星期开放、周次数、浓缩树脂和多种野外刷新周期。
- 不用固定像素坐标解析所有导出图；导入器必须支持布局版本、归一化坐标和长图分片。
- 不在损坏配置时静默回退为零；数据异常必须进入 `NeedsReconcile` 或导入预览错误。
- 不用一个材料行同时充当需求、库存和执行历史；工具集后端是分布式边界，需要版本、租约和幂等事件。
- 不忽略无法日常刷取的材料。它们不计入 SRC 的日常总体进度，但本项目必须继续显示并指导，直到确认完成。

## 5. 领域模型

### 5.1 三类事实必须分开

1. **需求快照（Requirement Snapshot）**：计算器在某一时刻给出的目标需求。
2. **库存观测（Inventory Observation）**：某一时刻 OCR 到的背包持有总量。
3. **取得事件（Acquisition Event）**：副本、Boss、地脉、采集、刷怪或合成产生的已确认变化。

不得只保存一个可被任意覆盖的 `remaining` 数字。

每个导入版本至少保存：

```text
requiredAtImport
ownedAtImport
initialDeficit = max(requiredAtImport - ownedAtImport, 0)
confirmedAcquired
confirmedCraftInput
confirmedCraftOutput
manualAdjustment
remainingProcurement
evidenceState
```

### 5.2 消耗与重新基线

用户在养成过程中会消耗材料。背包数量下降不等于规划倒退：这些材料可能已经用于当前目标，也可能被用在其他目标上，系统无法仅凭库存差值区分。

因此：

- 已确认取得的进度不能因随后库存下降而自动撤销。
- 与系统已知合成操作对应的负变化按合成事件处理。
- 其他无法解释的负变化进入 `NeedsReconcile`，暂停受影响材料的自动执行。
- 用户应重新导入最新计算器图片，或明确标注“用于本规划”/“用于其他用途”。
- 重新导入默认替换同一规划的活动需求版本，并以新快照重新基线；旧版本和执行事件只读保留。

### 5.3 多目标与共享库存

同一 UID 的库存只能分配一次。多张图片或多个目标不得分别完整扣除同一批现有材料。

- 默认导入模式为“替换当前养成规划”。
- “合并目标”必须由用户显式选择。
- 合并时保留目标来源，并由统一分配器按优先级分配全局库存。
- 删除或降低一个目标后必须释放其库存分配，并重新计算其他目标。
- 图片哈希和规范化需求哈希用于阻止重复导入。

### 5.4 材料族与转换

- 每个材料有稳定 `materialId`、规范名称、别名、类型、稀有度、族、背包页和图标标识。
- 转换规则是有向图，默认只允许 3 个低级材料合成 1 个高级材料。
- 计算时必须先保留本级直接需求，再用真实剩余量向上合成，不能把仍有低级需求的材料全部消耗。
- 角色合成天赋产生的返还或双倍只按实际结果计入，不预先假定。
- 周本材料互转、梦之溶媒等特殊规则独立配置，未实现前显示为人工操作，不隐式折算。

## 6. 材料目录

工具集后端维护版本化材料目录，不能依赖 OCR 文本即时猜测来源。每项至少包含：

```text
materialId, canonicalName, aliases, gameVersion
category, inventoryGrid, rarity, familyId
counterType, iconRecognitionMode
sourceCandidates[{
  sourceType, sourceId, sourceName
  availableWeekdays, sundayRewardIndex
  resinCost, condensedResinAllowed
  expectedYieldRange, refreshPolicy
  adapterType, adapterConfig
}]
```

一个材料可以有多个来源，例如摩拉和经验书可来自地脉、尘歌壶或人工活动。调度器只能在用户允许的来源集合内选择；星辉、星尘、原石等高价值货币来源默认禁用。

`counterType` 至少区分 `InventoryGrid`、`CurrencyOcr`、`RewardResult` 和 `Manual`。摩拉不应被假定为普通背包网格物品；精锻用矿虽然能由现有背包计数任务在武器页特殊处理，但锻造本身尚无可用的 `QuickForgeTask` 实现。

数据来源按以下顺序复用：

1. BetterGI `Assets/Model/ItemV2/item.csv` 的规范名称、类型和图标识别数据。
2. AutoPlan `config/domain.json`、`config/bossList.json` 的秘境/Boss 名称及已有映射。
3. BetterGI 地图与任务参数提供的秘境、星期选择、树脂规则。
4. `CD-Aware-AutoGather` 和 `背包材料统计` 已维护的路线、材料、怪物和冷却元数据。
5. 工具集新增的少量覆盖表，只补齐上述来源没有的关系。

目录升级后旧规划继续绑定原 `catalogVersion`；执行前若来源已失效，进入重新映射，不静默换到同名来源。

## 7. 图片导入与 OCR

### 7.1 OCR 边界

计算器图片上传、预览和人工校正属于 bettergi-scripts-tools。游戏内结算和背包 OCR 属于 BetterGI。两者不得共享一个没有版本说明的 `/ocr/file -> String` 契约。

导入器使用 PP-OCRv6 small，并在上线前以真实长图做 V4/V6 A/B 基准。后端 Java RapidOCR 0.0.7 不支持直接切换到 V6，因此采用支持 V6 的独立 OCR 运行时；不要求 BetterGI 正在运行才能上传图片。

BetterGI 继续复用自身 V6 OCR 处理游戏画面。不得为了导入图片再维护第三套游戏内 OCR。

### 7.2 结构化识别

`POST /auto/plan/cultivation/import/preview` 必须返回结构化结果，而非拼接文本：

```text
imageHash, imageWidth, imageHeight
ocrEngineVersion, layoutProfileVersion, catalogVersion
textBlocks[{text, polygon, confidence}]
rows[{materialId, rawName, required, owned, missing, confidence, evidenceBoxes}]
warnings[], unresolvedRows[]
```

解析流程：

1. 校正方向、缩放并保留原图坐标映射。
2. 长图按重叠区域分片 OCR，随后按坐标和文本去重。
3. 识别表头和区段锚点，确定“材料、需求、持有/缺失”列的真实语义。
4. 以归一化 X 区间分列，以 Y 中心距离关联同一行。
5. 使用规范名称和别名匹配材料；模糊匹配不能自动覆盖高风险数字。
6. 校验 `required >= 0`、`owned >= 0`、列完整性、重复行和材料族一致性。
7. 下方“来源分组”只用于交叉校验来源，不覆盖材料目录。
8. 用户在预览页确认或修正后，`import/confirm` 才创建新规划版本。

当前 `OcrResultVo` 会在构造末尾用空列表覆盖已转换的 `textBlocks`，`TextBlockVo` 也以零初始化配合 `Math.min` 导致正坐标错误。即使替换 OCR 引擎，这两个坐标契约问题也必须修复或彻底绕开，并增加回归测试。

## 8. 规划状态机

规划状态：

```text
DraftImport
Ready
Active
WaitingForResin
WaitingForDay
WaitingForWeeklyReset
WaitingForRouteRefresh
NeedsManualAction
NeedsReconcile
BlockedUnsupported
Completed
ClosedWithExceptions
Cancelled
```

- `Completed` 只表示全部需求都有完成证据。
- `ClosedWithExceptions` 只能由用户显式关闭，并列出未完成项，不能在 UI 中显示为完成。
- 任何等待状态仍是活动规划，调度器必须保存 `nextEligibleAt` 并在条件满足后恢复。
- 新导入版本产生时，旧版本变为 `Superseded`，旧执行器不得继续回传到新版本。

行动状态：

```text
Queued -> Leased -> Running -> AwaitingEvidence -> Succeeded
                                  |-> Failed
                                  |-> NeedsReconcile
Queued/Leased/Running -> Cancelled
Leased -> Expired -> Queued
```

## 9. 调度规则

调度器每次只发放“下一项行动”，而不是一次生成并反复执行静态列表。每次证据回写后必须重新选择。

候选行动包括：

- `DomainRun`
- `WorldBossRun`
- `TrounceDomainRun`
- `LeyLineRun`
- `CraftMaterial`
- `InventoryReconcile`
- `GatherRoute`
- `MonsterRoute`
- `ForgeWeaponOre`
- `SereniteaPotPurchase`
- `ManualAction`

排序因素至少包括：

1. 用户目标优先级。
2. 周次数或活动次数即将过期。
3. 今天可刷、未来开放日稀少的材料。
4. 双倍活动剩余次数。
5. 多个缺口能否被同一来源同时满足。
6. 剩余缺口与保守产出范围。
7. 树脂和高级树脂使用策略。
8. 路线是否刷新、历史单位产出时间和预计耗时。
9. 最近失败及冷却退避。

默认来源优先级可借鉴 SRC 的“周本 -> 突破 -> 技能 -> 经验 -> 货币”，但最终排序必须服从开放日、周重置、双倍事件和用户策略。

总体 ETA 分开显示“预计树脂/树脂恢复时间”“最早日历完成时间”“预计路线耗时”和“人工阻塞项”。这些性质不同的时间不能相加成一个看似精确的完成日期。

### 9.1 资源策略

- 浓缩树脂只用于允许的秘境和地脉，并按实际双倍奖励计账。
- Boss 和周本不得错误使用浓缩树脂。
- 须臾树脂、脆弱树脂等稀缺资源默认不自动使用，只有用户明确策略允许才可使用。
- 调度器记录的是“允许使用上限”，执行器仍须在每批次前读取实时树脂。
- 体力不足时计算下一次可执行时间，进入 `WaitingForResin`，不得把当前行动写成完成。

### 9.2 时间边界

- 每个 UID 保存服务器区域、时区、每日重置时刻和周重置规则。
- 星期开放按游戏服务器时间计算，不能使用后端所在机器的本地星期替代。
- 周次数先读取游戏内剩余次数，再决定行动。
- 采集和怪物路线直接复用已有脚本的刷新记录，不在后端复制另一套冷却算法。

## 10. 执行与复核策略

### 10.1 初次执行和恢复

以下情况必须先对可通过背包网格读取的目标材料做批量复核，并按 `inventoryGrid` 分组，避免为每个材料重复扫描同一页：

- 新规划首次执行。
- BetterGI 或 AutoPlan 重启后恢复。
- 用户手动游玩、合成或养成后。
- 上一批次奖励识别为空、部分失败或超出合理范围。
- 账本与新导入快照发生冲突。

`RunCountInventoryItemTask` 的 `-1`（未找到）和 `-2`（数量 OCR 失败）是未知值，不得当作零。

摩拉等非网格对象使用材料目录指定的专用计数器；尚无专用计数器时，依赖无缺口的详细奖励事件并在完成前要求重新导入或人工确认，不能伪装成背包复核成功。

### 10.2 普通秘境和世界 Boss

1. 执行前检查规划版本、行动租约、缺口、来源、树脂和开放条件。
2. 强制开启 `RewardRecognitionEnabled`。
3. 缺口较大时执行保守批次；接近目标时批次固定为 1。
4. 每批次接收奖励识别结果，更新所有受该来源影响的材料行，而非只更新主目标。
5. 奖励结果缺少预期材料、为空或无法证明领取次数时，立即做定向背包复核。
6. 更新账本后重新获取下一行动，不继续使用启动时的旧列表。

当前 `Dictionary<string,int>` 只包含汇总材料，不能表达尝试轮数、成功领奖轮数、每轮 OCR 是否成功、树脂消耗和提前结束原因。正式闭环新增兼容接口，例如：

```text
TaskExecutionResult {
  actionId, planRevision
  roundsAttempted, roundsCompleted, rewardsClaimed
  resinBefore, resinAfter, resinConsumed
  rewardItems
  recognition[{round, status, confidence}]
  terminationReason
}
```

现有 `RunAutoDomainTask`/`RunAutoBossTask` 保持兼容；新增详细结果方法供养成执行器使用。在详细接口落地前，MVP 对接只能使用单轮执行加背包兜底，不能把非空字典等同于完整成功。

### 10.3 自适应复核

复用 SRC 的思想，但利用 BetterGI 更便宜的结算识别：

- 结算奖励识别：每次领奖执行。
- 权威背包复核：首次、恢复、异常、接近目标、批次边界和周期纠偏时执行。
- 周期纠偏阈值按行动类型配置；默认普通副本最多累计 10 次已确认领奖或 30 分钟后复核一次，取先到者。
- 当保守估计下一批次可能达到目标时，下一批次降为 1。
- 任何完成判断前必须有不早于最后一次取得/合成事件的权威证据，或一条完整、无缺轮次的详细任务结果。

### 10.4 地脉

`AutoLeyLineOutcropTask` 当前没有奖励结果。第一阶段可执行单个小批次：经验书使用背包复核，摩拉使用专用货币 OCR；若专用计数尚未实现，则必须新增地脉详细奖励结果。最终应提供与其他任务相同的返回契约，不得按“执行次数 x 固定产出”直接完成规划。

### 10.5 合成

- 调用 `genshin.CraftMaterial()`，不另写点击流程。
- 合成前重新读取同族各稀有度数量并重新求解。
- 合成量不得超过需求，也不得侵占低级材料的直接需求。
- 合成返回结果和合成后库存共同形成证据。
- 失败、取消或奖励结果不完整时进入复核，不预扣原料。

### 10.6 采集和怪物材料

- 地方特产、矿物优先复用 `CD-Aware-AutoGather` 的目标 CSV、路线扫描、冷却和接近目标复核。
- 怪物掉落使用 `AutoHoeingOneDragon/assets/monsterInfo.json` 反查材料对应怪物，并复用 `FullyAutoAndSemiAutoTools` 的 `敌人与魔物` 路线层级、CD、队伍和限次配置执行。
- `背包材料统计` 的历史产量与时间成本可作为后续排序证据，但不替换已经选定的 `FullyAutoAndSemiAutoTools` 执行适配器。
- 不复制这些脚本的路径执行、CD 和统计算法到 AutoPlan。
- 为两个脚本增加窄适配契约：接收 `actionId + materialTargets + deadline`，返回前后库存、已运行路线、失败路线、下一刷新时间和取消状态。
- 当前 BetterGI 没有公开的 JS 脚本内再启动另一个 JS 项目的 Dispatcher 方法。在该能力建立前，规划器可以生成明确的待执行脚本行动和目标文件，但不得宣称已全自动完成。
- 精锻用矿的背包计数复用 `RunCountInventoryItemTask` 的武器页特殊处理；矿物采集复用现有采集脚本。锻造能力未实现前生成 `ForgeWeaponOre` 人工行动，不把空的 `QuickForgeTask` 当作可执行能力。
- 尘歌壶可购买摩拉、经验书和精锻用矿，候选来源可复用现有 `GoToSereniteaPotTask`，但必须同时遵守洞天宝钱预算和用户购买优先级。

## 11. 工具集后端边界

bettergi-scripts-tools 是规划事实来源，负责：

- 图片上传、OCR、预览确认和材料目录。
- 需求版本、库存观测、取得事件、转换事件和派生缺口。
- 下一行动选择、等待时间、执行租约、幂等和进度展示。
- 保存原始证据引用、解析版本和执行审计。

AutoPlan JS 是执行代理，负责：

- 获取一个带租约的下一行动。
- 做游戏内即时预检。
- 调用 BetterGI 已有任务或适配脚本。
- 上报详细结果、心跳、取消和无法确认的状态。

BetterGI 核心只提供通用游戏执行与识别能力，不保存跨日养成规划。

### 11.1 并发与幂等

- 每个计划版本有单调递增 `revision`。
- 每个行动有唯一 `actionId` 和幂等键。
- 同一 UID 同时只允许一个有效执行租约；租约有执行器实例、过期时间和心跳。
- 结果回传携带 `expectedRevision`；旧版本结果进入审计但不得修改新账本。
- 重复事件按幂等键只应用一次。
- Web 端修改规划与执行器回传使用乐观并发，冲突时重新获取而不是最后写入覆盖。

### 11.2 建议接口

```text
POST /auto/plan/cultivation/import/preview
POST /auto/plan/cultivation/import/confirm
GET  /auto/plan/cultivation/{uid}
POST /auto/plan/cultivation/{uid}/next-action
POST /auto/plan/cultivation/actions/{actionId}/heartbeat
POST /auto/plan/cultivation/actions/{actionId}/events
POST /auto/plan/cultivation/{uid}/reconcile
POST /auto/plan/cultivation/{uid}/cancel
```

新接口与旧 `/auto/plan/json` 分开。旧固定次数计划继续可用，但标记为 `LegacyFixedPlan`，不自动迁移成闭环规划。

### 11.3 最小持久化对象

```text
CultivationPlan
PlanRevision
GoalRequirement
MaterialCatalog / MaterialSource
InventoryObservation
MaterialLedger
ExecutionAction
ExecutionEvent
ImportArtifact
```

实现可以在第一阶段使用 JSON 列承载部分细节，但需求版本、行动、事件和幂等键必须是独立可查询对象，不能塞回现有 `AutoPlanConfig` 后依赖整表覆盖。

### 11.4 集中式脚本设置代管中心

- 养成计划页面同时承担按 UID 的脚本设置代管职责，但不直接拥有各脚本的业务实现。
- 每个执行模块通过统一 `CultivationExecutionModule` 接口注册，至少公开稳定模块 ID、展示名、适配器版本、能力列表、接入状态、设置结构和默认设置。
- 模块配置使用 `(uid, moduleId)` 唯一键独立持久化，包含启停状态、适配器版本和设置 JSON；不得继续向单个养成偏好表追加脚本专属列。
- Web 端按后端返回的设置结构生成模块卡片。队伍等可枚举字段允许选择已有值并允许直接输入；只读契约字段由适配器强制覆盖。
- 模块升级时仅保留新设置结构仍支持的旧字段，移除的字段自动忽略；同 ID 模块可替换实现，新模块通过注册即可加入，删除模块不影响账本。
- 第一批模块为一条龙配置组、AutoPlan、`CD-Aware-AutoGather`、`FullyAutoAndSemiAutoTools` 和 `WeeklyBoss`。配置组自身的跑图、队伍、战斗、周期、前置配置组和 Shell 设置必须独立代管并写入根级 `config`，不得从任意通用配置组继承；地方特产设置包必须使用现有 `partyName`、`partyName2nd`、`targetCountOfSelected=csv` 和 `manualSetAccountName` 契约。
- “养成计划”是唯一的一条龙控制台；AutoPlan 旧配置页只维护通用固定计划，不再重复展示养成总控。
- 控制台使用稳定文件名 `养成一条龙-{uid}.json` 和固定配置组名 `养成一条龙-{uid}` 覆盖维护 UID 专属脚本组；变化的是组内 JS 任务名，必须按实际启用内容生成，例如 `养成怪物：原海异种·镀金旅团·蕈兽`，不得继承“400精英”等无关名称。BetterGI 只通过既有 `--startGroups` 命令行入口承担启动和执行，不在 BetterGI 内复制规划事实。
- 同一 UID 的养成配置组必须保持唯一；生成时发现旧动态文件名或重复根名称时，先写入可回滚备份，再删除非规范副本，只保留 `养成一条龙-{uid}.json`。
- 网页模块卡片、UID 专属配置组和 BetterGI 的 JS 设置卡片必须读取同一份按账本裁剪后的有效配置投影；生成专属组时同时备份并同步脚本根目录 `settings.json` 中的动态地区、材料和具体路线字段，确保三处展示与实际执行配置一致。
- 生成专属组时只加入当前缺口实际需要的模块；周本未确认风险条款时不得进入脚本组。
- `CD-Aware-AutoGather` 的所有选择项和通用关键词过滤必须先清空，再只写入账本地方特产；`FullyAutoAndSemiAutoTools` 必须只选择账本怪物路线族，不得带入晶蝶、水晶矿、400 精英、锄地专区等通用路线，其 CD API 必须指向当前工具集后端端口。
- 角色经验书按流浪者、冒险家、大英雄三档分别识别库存，并按每本 1000/5000/20000 经验折算总缺口；不得把三档本数直接相加，也不得只扫描最高档。
- 每个执行模块在卡片右上角提供即时启停开关；配置组根设置固定启用。开关保存后统一完整重建 UID 专属脚本组，不恢复旧的单模块增量同步按钮。
- UID 专属脚本组只包含“用户已启用且当前仍有有效缺口”的模块。目标满足时暂时移除对应项目但保留启用偏好；后续出现新缺口时自动添加回来。停用模块只从 UID 组的 `projects` 中移除，不删除共享 JS 安装目录。
- 代管中心输出的是可审计的模块设置和行动投影；在 Dispatcher、执行租约、结果事件和战后复核接通前，不得将“已生成配置”显示为“已自动完成”。

## 12. AutoPlan 行为变更

- 培养规划不再以 `domainRoundNum`、`runCount` 或当日 `Record` 判断完成。
- `loop_plan` 的“反复执行启动时列表直到体力不足”不适用于闭环培养模式。
- 培养模式每次只执行后端发放的当前行动，并在回写后重新拉取。
- 日常计划仍保留当前记录与固定次数语义，和培养规划互不覆盖。
- 只有证据确认成功才提交行动；异常被现有兼容逻辑吞掉时也不得写成功。
- 取消操作传播到 BetterGI 任务；取消后的部分奖励先进入待复核事件，不能丢失，也不能重复应用。

## 13. 完成条件

规划只能在以下条件全部满足时进入 `Completed`：

1. 当前活动 revision 的每个需求行 `confirmedRemaining <= 0`。
2. 没有能够继续降低缺口的待执行合成。
3. 最后一个取得/合成事件已经被权威库存或完整详细结果覆盖。
4. 没有 `AwaitingEvidence`、未知 OCR 值或无法解释的负库存变化。
5. 地方特产、怪物材料、周本、经验/摩拉和手动材料也已确认，而不只是树脂副本完成。
6. 没有仍处于 `NeedsManualAction` 或 `BlockedUnsupported` 的需求。

用户可以显式选择 `ClosedWithExceptions`，但系统必须列出剩余材料、原因和最后证据，不得显示“全部完成”。

## 14. 前端要求

工具集页面至少提供：

- 图片拖入/选择、OCR 进度、结构化预览和逐行修正。
- 替换规划与合并目标的明确选择，默认替换。
- 总体进度、预计树脂/天数、下一行动及选择原因。
- 按材料展示需求、初始持有、已确认取得、合成、剩余、来源和证据时间。
- `Waiting`、`NeedsReconcile`、`NeedsManualAction`、`BlockedUnsupported` 的可操作说明。
- 重新导入、重新清点、暂停、取消和显式例外关闭。
- OCR 引擎、布局版本、材料目录版本和图片哈希的审计信息。

## 15. 验收与测试

### 15.1 OCR

- 使用当前真实长图作为固定测试样本。
- 覆盖不同缩放、压缩、裁切、长图分片、重复分片、列缺失、未知材料和数字误识别。
- 对 V4/V6 记录材料名准确率、数字准确率、行关联准确率和耗时。
- 坐标 VO 测试必须证明非零正坐标、四点 min/max 和 `textBlocks` 不被清空。

### 15.2 领域模型

- 3:1 多级合成、混合低/中级材料、保留直接需求、合成奖励。
- 多目标共享库存不重复分配，目标删除后重新分配。
- 重复导入、替换、合并和旧 revision 回传。
- 用户消耗、未知负变化和重新基线。
- 进度、树脂成本和 ETA 边界。

### 15.3 状态机与接口

- 重复事件只应用一次。
- 租约过期后可恢复，两个执行器不能同时领取同一行动。
- 取消、崩溃、网络失败、部分成功、奖励 OCR 为空和背包 OCR 失败。
- 等待体力、等待星期、等待周重置和路线刷新后自动恢复。
- `Completed` 门禁拒绝任何未确认或人工待办行。

### 15.4 端到端

至少覆盖：

1. 导入图片 -> 修正预览 -> 首次背包对账 -> 普通秘境 -> 奖励回写 -> 单轮收尾 -> 完成。
2. 导入图片 -> 世界 Boss -> 中断 -> 恢复 -> 幂等回传。
3. 地脉无奖励结果 -> 背包兜底。
4. 材料族合成前后复核。
5. 跨星期等待和周本次数耗尽。
6. 地方特产路线冷却与怪物路线部分失败。
7. 用户中途养成导致库存下降 -> `NeedsReconcile` -> 新图片重新基线。

## 16. 分阶段交付

### P0：导入与账本

- PP-OCRv6 结构化导入、预览修正、材料目录、版本化需求和持久化状态机。

### P1：普通秘境与世界 Boss

- 下一行动接口、执行租约、单轮/动态批次、奖励回写、背包兜底和完成门禁。

### P2：地脉、合成、周本

- 地脉详细结果或强制背包复核、`CraftMaterial` 闭环、征讨领域支持和周次数调度。

### P3：采集与怪物路线

- 对接 `CD-Aware-AutoGather` 与 `背包材料统计` 的窄适配契约，复用路线/CD/产量逻辑。

### P4：全来源恢复与正式验收

- 跨日/跨周恢复、并发幂等、完整 UI、全量端到端测试和异常审计。

P0-P3 可以独立作为预览或测试版本交付，但在 P4 的完成条件全部通过前，产品不得宣称“自动指导所有养成需求完成”。

## 17. 最终共识

1. **后端材料账本是唯一规划事实来源，AutoPlan 不再用固定次数或当日记录代表培养完成。**
2. **奖励增量用于低成本实时更新，背包持有总量用于首次、异常、接近目标和周期性权威纠偏。**
3. **执行批次必须动态化；接近目标时单轮执行并在下一次资源消耗前停止。**
4. **需求、库存、取得事件和材料消耗分开建模；无法解释的库存下降必须重新对账。**
5. **所有来源都进入同一状态机；暂不支持或需要人工处理的材料保持活动状态，不得被忽略。**
6. **优先复用 BetterGI 的奖励识别、背包计数、合成、秘境、Boss 和地脉任务，以及现有采集/刷怪脚本的成熟逻辑。**
7. **旧 AutoPlan 日常计划保持兼容，新养成接口和状态独立演进。**
8. **只有当前规划版本的全部需求都有完成证据时，系统才结束。**
9. **所有参与一条龙的脚本设置由按 UID 的模块代管中心统一编排，脚本通过稳定适配接口替换、升级或新增。**

## 18. 实施状态（2026-08-23）

### 已完成：P0 导入、账本与执行控制面

- 在 `bettergi-scripts-tools` 的 `codex/auto-plan-cultivation-p0` 工作分支实现 PP-OCRv6 长图分片识别、坐标/置信度保留、表格解析和逐行校正。
- OCR 适配器优先复用 BetterGI 已安装的 V6 det/rec ONNX 与 YAML 字典；找不到时使用 RapidOCR V6 自带资产，模型来源随预览和 revision 留档。
- 新增导入草稿与不可变计划 revision 两类持久化记录，支持 SQLite、MySQL 和 PostgreSQL；旧 AutoPlan 固定次数配置及接口未改动。
- 新增“养成计划”一条龙控制台，支持上传、待确认标记、修改、删除、补录、确认、读取最新账本、模块设置、生成专属脚本组和交给 BetterGI 启动。
- 当前真实导出长图已通过跨进程集成测试：Java 调用 Python V6 桥后解析出 50 条以上材料，关键行“摩拉”为 `31964305 / 7621233`。
- P0 状态固定为 `IMPORTED`，不得显示或推断为 `Completed`。
- UID 已统一为可输入、可下拉选择和可设默认的账号列表，并接入养成计划、自动体力计划和其他 UID 引用入口。
- 新增按 UID 的模块设置代管中心、统一模块注册接口和通用配置表；已注册一条龙配置组、AutoPlan、`CD-Aware-AutoGather`、`FullyAutoAndSemiAutoTools`、`WeeklyBoss` 五个模块。配置组根设置独立保存，脚本模块从已安装脚本组读取当前设置作为代管基线。
- 最新账本可投影为秘境/地脉、世界 Boss、周本、地方特产和怪物路线；普通 Boss 不再显示为待接入，怪物材料通过 `monsterInfo.json` 映射到 `FullyAutoAndSemiAutoTools` 路线。
- 代管中心可在独立配置对话框中编辑完整设置、显式同步单个脚本并保留回滚备份，也可生成 UID 唯一的专属脚本组；配置组名固定，组内 JS 任务名随实际内容变化，采集和怪物路线严格限定为账本缺口，AutoPlan 培养计划只生成单轮行动，执行后必须重新确认缺口。
- AutoPlan 旧配置页已移除重复的养成总控，所有养成编排集中在“养成计划”的“一条龙执行”页。
- BetterGI 已有 `--startGroups`、组名完整校验和 `RunMulti` 链路被直接复用；网页不新建第二套游戏执行引擎。

### 已完成：P1 普通秘境与世界 Boss 的计划驱动 MVP

- 新增按 UID/revision 的单行动租约：养成执行器每次只领取一个当前行动，不再从旧 `/auto/plan/json` 获取静态培养列表。
- UID 专属养成组启用 `cultivation_plan_mode`；AutoPlan 在每个安全批次后调用 `RunCountInventoryItemTask` 读取目标材料背包总量，回写后重新领取下一行动。
- 权威库存观察独立于不可变导入 revision 持久化；有效账本从最新观察派生缺口，所有需求均为零后才进入 `COMPLETED`。
- 最新账本同时展示不可变的 `baselineOwned` 与权威观测派生的 `currentOwned`；每次有效行动结果提交后自动重新生成同一 UID 的专属组，使后续加载的秘境/Boss、地方特产和怪物路线都按最新缺口裁剪。
- 首次确认或确认新的账本 revision 后立即自动生成同一 UID 的专属组，不要求用户再手动执行 prepare；UID 在导入、查询、配置和执行边界统一限制为 6 至 12 位数字，所有专属组写路径在归一化后必须仍位于 `User/ScriptGroup`。
- 网页与 BetterGI 的可编辑脚本设置按最后修改优先双向收敛：只读取精确的 `养成一条龙-{uid}.json`，比较网页配置 `update_time` 与专属组文件修改时间，严格较新的来源生效，相同时保留网页托管值；网页保存后立即原子写回，执行页静默轮询吸收 BetterGI 较新修改。账本派生的材料、路线和副本字段保持只读，由最新缺口重新生成，不参加人工配置冲突裁决。
- 行动结果使用稳定幂等键；同一 UID 同一 revision 只允许一个有效租约。库存返回 `-1`、`-2` 或复核异常时进入 `NEEDS_RECONCILE`，后续只补清点，不继续消耗树脂。
- 一条龙准备会删除旧 `cultivate=true` 固定次数记录，并为已安装 AutoPlan 幂等安装带备份的计划驱动桥接；旧日常固定次数计划保持兼容。
- 当前批次上限固定为 1 是 P1 的安全执行边界，不是计划完成条件；是否继续完全由回写后的账本缺口和下一行动决定。
- BetterGI 的秘境/Boss 调度返回值统一转换为 JS 可枚举奖励对象，AutoPlan 将单批实际奖励随幂等结果回传；后端对天赋材料的“教导/指引/哲学”按 3:1 累计整份可合成量。
- 低/中阶实际奖励足以覆盖目标但尚未完成合成时进入 `NEEDS_CRAFT`，不再刷取也不标记完成；高阶库存增长会抵扣已合成进去的低阶奖励，避免双计。
- 任意一次权威库存低于导入基线或低于本计划先前观察值时进入 `NEEDS_RECONCILE`，不得把材料消耗误判为新的刷取缺口。
- UID 专属组在地方特产、怪物与周本任务之后追加一次批量权威背包复核；复核先取得同 UID/revision 的行动租约，再用服务端 `actionId` 派生稳定幂等键整批回写。请求必须覆盖全部目标，任一 `-1`、`-2` 或缺失值都会持久化为 `AWAITING_RECONCILE` 并停止后续执行；全部可识别时才回写账本并自动重生成下一次加载的路线。
- 地方特产与怪物任务只选择经文件结构检查的安全路线；所有候选均标记低成功率/低效/不可用或路线 JSON 损坏时跳过该材料任务并给出警告，不回退到已知坏路线。数据库启动门禁同时核对行动表必要列、两项唯一约束及 UID/revision/status 查询索引。

### 尚未完成：P1 增强项与 P2-P4 执行闭环

- 普通秘境与世界 Boss 尚需接入详细轮次/奖励事件、租约心跳、材料族 3:1 合成求解和用户目标优先级；当前以单轮加权威背包复核保证正确停止。
- 地脉、周本和合成仍按 P2-P4 接入详细行动/事件闭环；地方特产与怪物路线已具备组末权威库存闭环，但尚未记录逐路线掉落、尝试轮数和提前结束原因。
- 已加载到 BetterGI 内存中的当前脚本组不做热替换；行动回写触发的自动同步供后续脚本加载使用。
- 在 P4 验收完成前，仍不得宣称系统会自动指导全部养成需求完成。

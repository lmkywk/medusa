# P0 核心契约与冻结清单

> 项目：Medusa / Alpay 注入模块  
> 历史契约基线：`1a43622`（3.2.0，host 10.8.30.8000，账号 2088000000000000）；当前发布版本 5.9.0。
> 日期：2026-10-25  
> 状态：五条决策已确认，进入 P0 实现  
> 原则：只对齐事实与契约，不改任务行为、不改设置默认值、不动域逻辑。

---

## 0. 已确认的五条决策

| # | 决策 | 结论 |
|---|---|---|
| 1 | 系统状态页入口 | **设置页底部 → 系统状态**；首页状态行保持现状 |
| 2 | 命令冻结范围 | **12 条现有业务命令冻结**；4 条调试命令不承诺兼容；**不新增命令**。系统状态页用现有 `status` 命令的 add-only 载荷 `detail=true` 触发报告刷新 |
| 3 | 能力检查是否拦截 | **P0 只报告、不拦截**；P1 才让 preflight 记 `BLOCKED` |
| 4 | 是否改 JSON schema | **P0 不改任何现有 JSON schema**；`schemaVersion`、`reasonCode` 等字段到 P1 再加 |
| 5 | 敏感文件 | 诊断导出**排除 token**；全量备份保留 token 用于恢复，但必须提示；P0 不改备份 manifest schema |

---

## 1. 目标与边界

### 1.1 背景（2026-10-25）

- 01:29:35 系统整批强停支付宝与 Medusa，02:00–03:50 夜间计划全部未跑；计划闹钟随进程被系统清掉。
- 07:00 宿主重启后主链路检查连续超时，计划链没有重新排上，直到 08:54 手动执行。
- 08:53 UI 触发的账本刷新早于宿主 RPC 就绪，出现一批 NPE 与“任务账本刷新失败”。

P0 只解决“看得见、能解释、可验收”；调度自愈、补跑策略与 OEM 强停处理放在 P1/设备侧。

### 1.2 P0 目标

1. 把“全局设置、系统能力、运行状态、命令、schema、ID”从口头约定变成可检查的冻结清单。
2. 让“看起来已开启、实际不能跑”变得可见：存储、精确闹钟、电池、通知、网络、RPC、登录、闹钟健康。
3. 在注入进程生成只读 `system_status.json`，UI 只读展示，不再靠 adb 手工拼时间线。
4. 为 P1 的调度自愈、preflight、ProbeRegistry 提供稳定地基。

### 1.3 P0 不做

- 不改计划算法、补跑策略、兜底逻辑、域任务实现。
- 不改首页/域页/计划页视觉（除新增只读“系统状态”页）。
- 不改 `appConfig.json`、`config_v2.json`、`plans.json`、`plan_history.json`、`plan_runtime.json`、`risk_state.json`、账本等现有 schema。
- 不做 SnapshotService、DomainAdapter、DomainCatalog 合并、Gradle 多模块、DI、JSONL 事件日志。
- 不新增系统权限。

### 1.4 P0 产出物

| 产出 | 说明 |
|---|---|
| `docs/P0-contracts.md` | 本文档：冻结清单与验收标准 |
| `CapabilityService` | 注入进程内执行能力检查，返回 `CapabilityReport` |
| `system_status.json` | 注入进程写的只读状态报告（schema 1） |
| 系统状态页 | 设置页底部入口，只读展示报告 |
| 验收记录 | 设备截图、命令回归、能力值与 adb 实测对照 |

---

## 2. P0-1 全局设置（用户意图）

### 2.1 `appConfig.json`：23 个全局键

来源：`app/src/main/java/io/github/aw1y2z/sesame/data/AppConfig.java`

| 分组 | 键 | 类型 | 默认值 | 说明 |
|---|---|---|---|---|
| 外观 | `newUI` | Boolean | `true` | 新 UI 开关 |
| 外观 | `languageSimplifiedChinese` | Boolean | `true` | 简体中文 |
| 外观 | `darkMode` | Boolean | `false` | 深色模式 |
| 外观 | `followSystem` | Boolean | `true` | 跟随系统主题 |
| 日志 | `enableDebugLog` | Boolean | `false` | 抓包记录（RPC 原始日志） |
| 日志 | `enableForestLog` | Boolean | `true` | forest 分类日志 |
| 日志 | `enableFarmLog` | Boolean | `true` | farm 分类日志 |
| 日志 | `enableGoldenBeansLog` | Boolean | `true` | goldenbeans 分类日志 |
| 日志 | `enableOtherLog` | Boolean | `true` | other 分类日志 |
| 日志 | `enableViewErrorLog` | Boolean | `true` | 日志页错误分类 |
| 日志 | `enableViewRuntimeLog` | Boolean | `true` | 日志页运行分类 |
| 运行 | `newRpc` | Boolean | `true` | 新 RPC 接口 |
| 运行 | `showToast` | Boolean | `true` | 操作提示 |
| 运行 | `toastOffsetY` | Integer | `0` | 提示纵向偏移 |
| 运行 | `enableOnGoing` | Boolean | `false` | 状态栏禁删/常驻通知 |
| 运行 | `closeCaptchaDialog` | Boolean | `true` | 关闭验证码弹窗 |
| 运行 | `wholeDomainFallback` | Boolean | `true` | 晚间整域兜底 |
| 系统 | `batteryPerm` | Boolean | `true` | 电池优化白名单引导标记 |
| 节律 | `manualCooldownGlobal` | Integer | `30` | 全局手动冷却（分钟） |
| 节律 | `manualCooldownTarget` | Integer | `60` | 单域手动冷却（分钟） |
| 节律 | `planFlowMs` | Integer | `5000` | 计划写请求最小间隔（ms） |
| 节律 | `planFlowJitterPercent` | Integer | `30` | 计划流抖动（%） |
| 风控 | `riskPolicy` | Integer | `0` | 0=停止并提示；1=只提示 |

**冻结**：键名、类型、默认值、取值范围、所属页面。  
**规则**：只增不改；改默认值必须在发布说明中单独标注；注入进程只读，写入统一由 UI 的保存流程完成。

### 2.2 `config_v2.json`：14 个模型、355 个字段

来源：`app/src/main/java/io/github/aw1y2z/sesame/data/ConfigV2.java` 与设备实际配置。

| 模型 | 字段数 | 模型 | 字段数 |
|---|---:|---|---:|
| `AntForestV2` | 92 | `AntFarm` | 57 |
| `AntSports` | 28 | `AntStall` | 26 |
| `AntDodo` | 24 | `AntMember` | 24 |
| `ProtectEcology` | 20 | `AntOrchard` | 20 |
| `AntOcean` | 17 | `goldenbeans` | 17 |
| `BaseModel` | 13 | `GreenFinance` | 8 |
| `AnswerAI` | 6 | `AncientTree` | 3 |

**冻结**：模型类名、字段 code、每个模型的 `enable` 字段。  
**规则**：字段 code 即 API，只增不改；删除/改名必须同时提供迁移与 fixtures；域开关/滑动交互保持现状。

### 2.3 UI 偏好（`medusa_home_v2` SharedPreferences）

| 键 | 归属 | 说明 |
|---|---|---|
| `grid` | UI | 单列/双列 |
| `hero_bg` | UI | 首页头图 |
| `module_order` | UI | 域卡顺序 |
| `module_hidden` | UI | 隐藏域卡 |
| `main_metric_ids` | UI | 主显示指标 |
| `domain_metrics_<key>` | UI | 单域指标选择 |
| `bg_<key>` / `bg_path_<key>` | UI | 单域背景 |
| `forest_manual_collect_at` | UI | 手动收能量 60 秒节流 |

**归属**：UI 独占，不进内核；键名冻结，允许自由调参。

### 2.4 全局设置冻结规则

1. 键名只能新增，不能删除/重命名；删除必须走迁移。
2. 默认值变更必须单独列出，不与功能改动混在同一发布说明。
3. 注入进程不直接写设置；UI 保存后通过 `sesame.reloadConfig` 让注入进程重读。
4. 域配置字段 code 与全局设置分开管理：`appConfig` 管“怎么跑”，`config_v2` 管“哪个域/哪个开关”。
5. P0 不添加 `schemaVersion`；P1 统一给 `appConfig`、`config_v2`、`plans`、`ledger` 增加版本字段。

---

## 3. P0-2 系统底层（能力与状态）

### 3.1 状态文件与唯一写者

| 文件 | 唯一写者 | 读者 | P0 处理 |
|---|---|---|---|
| `appConfig.json` | AppConfig（UI 保存） | 注入进程 | 冻结 23 个键 |
| `config_v2.json` | ConfigV2（UI 域开关） | 注入进程 | 冻结 14 个模型/字段 code |
| `plans.json` | PlanStore（计划页） | 调度器/UI | 冻结 schema v1 |
| `plan_history.json` | PlanExecutor / PlanScheduler | UI/计划页 | 冻结状态/触发源/记录字段 |
| `plan_runtime.json` | **只允许调度器/执行器写** | UI/通知 | UI 只读；P0 不改写者 |
| `risk_state.json` | **只允许 RiskGuard 写** | UI/注入进程 | 冻结字段 |
| `status.json` | 域任务 + 调度器 | 域/UI | 冻结核心命名空间与顶层结构 |
| `statistics.json` | 任务写入器 | UI | 冻结 day/month/year + DataType |
| `runtimeInfo.json` | 任务尝试策略 | UI | 冻结 `taskAttemptPolicy.*` |
| `ledger/<uid>/<day>/snapshot.json` | TaskLedgerStore | UI | 冻结顶层与任务字段 |
| `ledger/<uid>/<day>/summary.json` | TaskLedgerStore | UI | 冻结计数项 |
| `ledger/<uid>/<day>/report.json` | TaskLedgerStore | UI | 冻结顶层与域报告字段 |
| `ledger/<uid>/<day>/progress.json` | TaskLedgerStore | UI | 冻结 `day/handled/updatedAt` |
| `*_probe.json` | 探针 | 诊断 | P0 登记名称，P1 收进 ProbeRegistry |
| `current_account.txt` | 账号/会话 | UI/注入进程 | **敏感** |
| `profile.json` / `self.json` | 账号/会话 | UI | **敏感** |
| `token_config.json` / `http_token.txt` | 账号/会话 | 注入进程 | **密钥，禁止导出** |
| `friend.json` / `friendWatch.json` | 域数据 | UI | **好友 PII，禁止默认导出** |
| `bak/*.json` | 备份 | 恢复 | 冻结 manifest |
| `log/*.log` | Log / LogRotation | 诊断 | 冻结分类与轮转策略 |
| **`system_status.json`（P0 新增）** | **只允许注入进程写** | UI/诊断 | P0 新增，schema 1，UI 只读 |

**核心原则**：UI 永不直接写运行状态；写者唯一；读模型与写模型分离。

### 3.2 能力检查

**必须在支付宝注入进程执行**。模块 App 进程检查 `com.medusa.alipay` 的闹钟/电池权限会得到错误结果。能力检查使用宿主（支付宝）进程已有权限，模块 App 不单独申请新权限。

| ID | 检查 | 现状 | P0 目标 |
|---|---|---|---|
| `HOOK` | `ApplicationHook.isHooked()` + context | 已有 | 报告 |
| `LOGIN` | `getUserId()` / `UserIdMap.getCurrentUid()` | 已有 | 报告 |
| `STORAGE_PERMISSION` | `PermissionUtil.checkFilePermissions` | 已有 | 报告；未授予但媒体目录可写时标注差异 |
| `STORAGE_WRITABLE` | `FileUtil.isMainDirectoryWritable` | 已有 | 报告宿主媒体目录真实可写 |
| `EXACT_ALARM` | `AlarmManager.canScheduleExactAlarms()` | 已有 | 报告 |
| `BATTERY_OPTIMIZATION` | `PowerManager.isIgnoringBatteryOptimizations(Alipay)` | 已有 | 报告 |
| `NOTIFICATION` | `NotificationManagerCompat.areNotificationsEnabled()` | **缺失** | 新增 |
| `FOREGROUND_SERVICE` | `enableOnGoing` 与实际前台状态 | **缺失** | 新增 |
| `NETWORK` | `ConnectivityManager` 是否有已验证网络 | **缺失** | 新增 |
| `RPC_READY` | 登录后是否已有一次成功 `check` | **缺失** | 新增 |
| `ALARM_HEALTH` | 下一次计划时间 vs 当前时间、上次触发、错过次数 | **缺失** | 新增 |
| `OEM_BACKGROUND` | 三星休眠列表不可直接查询 | **缺失** | 推断 + 引导 |
| `DEVICE_IDLE` / `POWER_SAVE` | `PowerManager.isDeviceIdleMode` / `isPowerSaveMode` | **缺失** | 仅展示 |

**状态语义**：

- `OK`：检查通过。
- `MISSING`：检查失败，有可执行动作（打开系统页）。
- `DEGRADED`：部分可用（如权限已给但目录仍不可写、电池已白名单但 OEM 列表未知）。
- `UNKNOWN`：无法判定（如三星休眠列表）。

P0 只报告，不阻止任务；P1 才用 `MISSING` 生成 `BLOCKED_CAPABILITY`。

### 3.3 `system_status.json`（P0 新增）

- 路径：`/sdcard/Android/media/com.eg.android.AlipayGphone/Medusa/system_status.json`（作者自用包仍为 `sesame-M`）
- 写者：注入进程（`SystemStatusWriter`）
- 读者：模块 App 的“系统状态”页、诊断导出
- 写入时机：进程启动、`sesame.status`（`detail=true`）、计划轮次结束后（节流 ≥ 30 秒）
- 原子写：先写 `system_status.json.tmp` 再 rename；UI 读取失败时保留上一次报告
- 体积上限：64 KB；不写任务标题原文、好友列表、token

```json
{
  "schema": 1,
  "generatedAt": 1792895628817,
  "trigger": "startup|command|postRun",
  "process": {
    "pid": 24990,
    "startedAt": 1792890000000,
    "uptimeMs": 5628817,
    "hostVersion": "10.8.30.8000",
    "moduleVersion": "5.9.0",
    "hookReady": true,
    "lastStartReason": "unknown"
  },
  "account": {
    "uid": "2088000000000000",
    "label": "演示账号",
    "login": true
  },
  "capabilities": [
    {
      "id": "EXACT_ALARM",
      "state": "OK",
      "detail": "已允许精确闹钟",
      "action": "none",
      "checkedAt": 1792895628817
    }
  ],
  "scheduler": {
    "nextTarget": "物种·好友抽卡",
    "nextTime": "12:01",
    "nextAt": 1792900860000,
    "lastRun": {
      "planId": "default-0915-ocean-clean",
      "target": "ocean.clean",
      "status": "COMPLETED",
      "reasonCode": "",
      "reason": "",
      "startedAt": 1792891486513,
      "endedAt": 1792892707807,
      "durationMs": 1221294
    },
    "missedToday": 17,
    "alarmHealth": {
      "lastSetAt": 1792895628817,
      "lastFiredAt": 1792891486513,
      "lastMissedAt": 1792886400000,
      "suspectedKill": false
    }
  },  "risk": {
    "globalPaused": false,
    "verificationRequired": false,
    "scopes": [],
    "lastSignal": {
      "method": "com.alipay.antfarm.doFarmTask",
      "reason": "检测到风控（1009）",
      "at": 1792890025733
    }
  },
  "data": {
    "ledgerDay": "2026-10-25",
    "lastSyncAt": 1792889618089,
    "complete": true,
    "report": {
      "total": 271,
      "pending": 171,
      "manual": 84
    },
    "log": {
      "files": 14,
      "bytes": 170806580
    }
  },
  "device": {
    "manufacturer": "samsung",
    "model": "Android 16 test device",
    "android": "16",
    "oneUI": "8.0.x",
    "idle": false,
    "powerSave": false,
    "networkType": "wifi"
  },
  "oem": {
    "lastKillAt": 0,
    "guidance": ""
  }
}
```

**字段说明**：

- `lastStartReason`：P0 只能尽力推断，默认 `unknown`；不承诺准确。
- `lastSetAt` / `lastFiredAt`：`lastSetAt` 为进程内记录（进程重启后置 0）；`lastFiredAt` 从 `PlanRuntime` / `PlanHistory` 推导。
- `missedToday`：当天 `PlanHistory` 中 `reason=窗口已结束，未执行` 的条数。
- `suspectedKill`：满足“计划时间已过 + 无对应执行记录 + 进程在计划时间之后启动”时置 true。
- `oem.lastKillAt`：系统不提供直接查询，保留 0；用 `lastMissedAt` 近似。
- `guidance`：OEM 强停疑似时展示的排查文案，不包含自动操作。

### 3.4 闹钟健康与强停推断

P0 不新增 `alarm_state.json`，只从现有数据推导：

1. `nextAt` = 当天 `PlanRuntime.nextTime` 换算的 epoch。
2. `lastFiredAt` = `PlanRuntime.lastRun.endedAt` 或 `updatedAt`。
3. `lastMissedAt` = 当天 `PlanHistory` 中最近一条 `reason=窗口已结束，未执行` 的 `startedAt`。
4. `suspectedKill` = 进程启动时间 > `nextAt` 且该轮次无任何非 SKIPPED 记录。
5. 三星休眠列表无法读取，页面只给排查步骤，不自动改系统设置。

### 3.5 敏感文件清单

| 文件 | 敏感级别 | 处理 |
|---|---|---|
| `http_token.txt` | 密钥 | **禁止进入诊断导出** |
| `token_config.json` | 密钥 | **禁止进入诊断导出** |
| `current_account.txt` | 账号 | 导出时脱敏/可排除 |
| `profile.json` / `self.json` | 账号 | 导出时脱敏 |
| `friend.json` / `friendWatch.json` | 好友 PII | 默认不导出 |
| `risk_state.json` | 风控 | 可导出摘要，不导出原始 scopes |
| `log/*.log` | 可能含昵称/任务标题 | 诊断导出保留，但提示 |
| 备份 manifest | 含 label/userId | 提示“备份包含账号与 token” |

**P0 策略**：

- 诊断导出（`DiagnosticExport`）只保留日志 + 环境信息，不加入任何 token 文件与好友列表文件。
- 全量备份（`BackupManager`）保留 token 以便恢复，但在 UI 上增加提示；P0 不改 manifest schema。
- `system_status.json` 不写 token、好友列表、任务标题原文。
- P1 再给备份 manifest 加 `containsSensitive` 字段（schema bump）。

---

## 4. P0-3 框架契约

### 4.1 命令总线

Action 前缀：`com.eg.android.AlipayGphone.sesame.`

| 类型 | Action | 载荷 | 处理 |
|---|---|---|---|
| 稳定 | `restart` | `userId?`、`exec?` | 重载配置/可选执行 |
| 稳定 | `planChanged` | — | 重载配置 + 排下一跳 |
| 稳定 | `plan` | — | 计划闹钟触发 |
| 稳定 | `planRun` | `target`、`roundTime`、`ignoreCooldown?`、`forceCollect?` | 手动执行 |
| 稳定 | `planStop` | — | 停止当前运行 |
| 稳定 | `execute` | `group?` | 分组/全部执行 |
| 稳定 | `reLogin` | — | 重登录 |
| 稳定 | `status` | `detail?`（P0 add-only） | 回 `io.github.aw1y2z.sesame.status`；`detail=true` 时先写 `system_status.json` |
| 稳定 | `ledger` | — | 账本闹钟 |
| 稳定 | `ledgerRefresh` | `domain?` | 立即刷新账本 |
| 稳定 | `verifyDone` | — | 确认验证完成 |
| 稳定 | `reloadConfig` | `clearOffline?` | 重载设置 |
| 调试 | `rpctest` | `method`、`data`、`type` | 不承诺兼容 |
| 调试 | `greenStats` | — | 绿色行动探针 |
| 调试 | `oceanTasks` | — | 海洋任务探针 |
| 调试 | `forestTasks` | — | 森林任务探针 |

**回传事件**：`io.github.aw1y2z.sesame.status`，载荷 `userId?`、`enabled`。  
**发送方白名单**：同 uid、shell uid、`MODULE_PACKAGE_NAME`、`INVALID_UID`（系统闹钟）。  
**冻结规则**：12 条现有稳定命令的名称、必填载荷、处理语义不变；P0 不扩命令集合；调试命令不承诺兼容。

### 4.2 Schema

#### `plans.json`

```json
{
  "version": 1,
  "defaults": { "dailySchedule": true },
  "plans": [
    {
      "id": "default-0200-farm",
      "target": "farm",
      "windowStart": "02:00",
      "windowEnd": "02:50",
      "enabled": true,
      "mode": "ONCE",
      "paceMinutes": 0,
      "defaultPlan": true,
      "roundTimes": ["02:00"]
    }
  ]
}
```

#### `plan_history.json` 记录字段

`runId`、`planId`、`target`、`roundTime`、`startedAt`、`endedAt`、`status`、`reason`、`completedCount`、`actionCount`、`energyDelta`、`roundIndex`、`roundCount`、`trigger`。

#### `plan_runtime.json` 字段

`state`、`planId`、`target`、`roundTime`、`roundIndex`、`roundCount`、`completedCount`、`reason`、`nextTarget`、`nextTime`、`startedAt`、`endedAt`、`lastStatus`、`lastCompletedCount`、`lastActionCount`、`lastEnergyDelta`、`lastDurationMs`、`lastPlanId`、`lastTarget`、`lastRoundTime`、`updatedAt`、`runId`、`trigger`、`manualOverride`、`notice`、`noticeAt`、`activity`、`activityAt`、`lastManualStartedAt`、`lastManualAt`、`lastManualTarget`、`cooldownUntil`、`lastRequestAt`、`lastRequestAccepted`、`lastRejectReason`、`manualLastByTarget`。

#### `risk_state.json` 字段

`globalPaused`、`globalPausedAt`、`globalReason`、`globalPausedMethod`、`lastSignalAt`、`lastSignalMethod`、`lastSignalReason`、`verificationRequired`、`verificationAt`、`verificationMethod`、`verificationReason`、`scopes`。

#### `status.json`

顶层保留当前结构；核心命名空间冻结：

- `plan::fallback`、`plan::probe`
- `log::rotate`
- `Config::backup`
- `af1009::*`
- `attempt::*`、`attemptTrigger::*`
- `saveTime`

域自有标记（`forest::*`、`farm::*`、`ocean::*` 等）由域维护，只增不改。

#### `statistics.json`

`day` / `month` / `year` × `time`、`collected`、`helped`、`watered`、`wateredcount`、`wateringcount`、`cleaned`、`steps`、`eggs`、`drawn`、`collectedSpent`、`collectedRob`。

#### `runtimeInfo.json`

`<uid>.taskAttemptPolicy.triggeredFallback`（JSON 字符串）。

#### 账本

- `snapshot.json`：`day`、`domains`、`capturedAt`、`complete`
- 域对象：`state`、`capturedAt`、`tasks[]`
- 任务字段：`id`、`title`、`group`、`rawStatus`、`status`、`progressCurrent`、`progressTotal`、`award`、`manualReason`、`failCount`、`firstSeenAt`、`lastSeenAt`、`updatedAt`、`lastRunId`
- `summary.json`：`day`、`domains`、`capturedAt`；每域计数 `pending`、`claimable`、`untouched`、`inProgress`、`claimed`、`manual`、`blacklisted`、`skipped`、`total`
- `report.json`：`day`、`syncedAt`、`total`、`pending`、`manual`、`domains`；每域含 `newTasks`、`removedTasks`、`newCount`、`removedCount`
- `progress.json`：`day`、`handled`、`updatedAt`

#### 备份 manifest

`schema`、`type`、`label`、`userId`、`createdAt`、`version`、`fileCount`、`bytes`。

### 4.3 枚举

| 枚举 | 值 |
|---|---|
| PlanHistory status | `COMPLETED`、`INCOMPLETE`、`SKIPPED`、`RUNNING`、`RISK_STOPPED`、`STOPPED` |
| PlanHistory trigger | `SCHEDULED`、`CATCHUP`、`FALLBACK`、`PROBE`、`MANUAL`、`WINDOW` |
| PlanRuntime state | `IDLE`、`RUNNING`、`WAITING`、`RISK_PAUSED`、`RISK_STOPPED`、`STOPPING`、`STOPPED`、`OFFLINE` |
| Plan mode | `ONCE`、`COLLECT` |
| LedgerStatus | `UNTOUCHED`、`IN_PROGRESS`、`CLAIMABLE`、`CLAIMED`、`MANUAL`、`SKIPPED` |
| Statistics TimeType | `YEAR`、`MONTH`、`DAY` |
| Statistics DataType | `CLEANED`、`STEPS`、`EGGS`、`DRAWN`、`TIME`、`COLLECTED`、`HELPED`、`WATERED`、`WATEREDCOUNT`、`WATERINGCOUNT` |

### 4.4 ID 清单与漂移

- **75 个卡片 ID**：见附录 A。
- **25 个域指标 ID + 12 个主指标 ID**：见附录 C。
- **11 个首页模块 key**：见附录 C。
- **54 个计划目标**：见附录 B。
- **14 个账本域**：`ocean`、`ocean.fish`、`farm`、`farm.draw`、`forest.reward`、`species.task`、`golden`、`sports.kinetic`、`orchard.task`、`orchard.park`、`orchard.draw`、`orchard.challenge`、`forest.park`、`forest.patrol`。

**当前清单漂移（P0 只记录，P1 合并）**：

| 清单 | 数量 | 差异 |
|---|---:|---|
| 首页模块 | 11 | forest、sports、farm、orchard、ocean、species、golden、**protect**、greenAction、**member**、**stall** |
| DomainRegistry | 9 | 有 **ancient**，没有 protect/member/stall |
| 指标目录 | 6 + generic | 缺 sports、orchard、species、golden、protect、member、stall；这些域走 generic |
| 账本域 | 14 | 与首页/域页不是一一对应 |

**结论**：P0 不合并；P1 用 `DomainCatalog` 收口。

### 4.5 Reason code 映射

P0 只建立映射，不改历史 `reason` 字段；P1 才在 `PlanHistory.Record` 增加 `reasonCode`。

| 现有 reason | 建议 code |
|---|---|
| （空，正常完成） | `OK` |
| 用户覆盖节律建议 | `OK_MANUAL_OVERRIDE` |
| 窗口已结束，未执行 | `SKIP_WINDOW_ENDED` |
| 今日能量已收集完/没有可执行任务 | `SKIP_NO_TASK` |
| 没有可执行任务（今日已完成） | `SKIP_DONE` |
| 未启动：模块未开启 | `SKIP_DOMAIN_DISABLED` |
| 森林设置未开启：收集能量 | `SKIP_CONDITION` |
| 已有轮次在跑 / 上一轮尚未结束 | `SKIP_BUSY` |
| 需要验证 | `BLOCK_VERIFY_REQUIRED` |
| 检测到风控（1009） | `BLOCK_RISK_1009` |
| 支付宝要求安全验证（滑块） | `BLOCK_RISK_CAPTCHA` |
| 执行超时（20分钟） | `TIMEOUT` |
| 执行异常/失败 | `ERROR` |
| 上次执行被中断 | `INTERRUPTED` |
| 用户停止 | `STOPPED` |

### 4.6 冻结规则

1. 12 条现有稳定命令：名称、必填载荷、语义不变；P0 不扩命令集合。
2. 卡片 ID、指标 ID、首页模块 key、计划目标、账本域：只增不改、不复用。
3. 现有 JSON schema：P0 不改字段；P1 变更必须升版本并提供迁移。
4. 枚举值：只增不改；UI 必须容忍未知值。
5. 设置键：只增不改；默认值变更单独标注。
6. 任何写者变更（谁是唯一写者）必须单独评审。

---

## 5. P0-4 系统状态页

### 5.1 入口与行为

- 入口：**设置页底部 → 系统状态**。
- 页面只读；只提供一个“刷新”按钮（发送 `sesame.status`，附带 `detail=true`）。
- 数据来源：注入进程写的 `system_status.json`；UI 不自行判断宿主能力。
- 显示 `generatedAt` 与“X 秒前”；超过 5 分钟显示“数据可能已过期”。
- 字段缺失或未知时显示 `—` / `未知`，不猜测。

### 5.2 页面结构

```
系统状态
├─ 账号与进程
│  ├─ 账号：演示账号 (2088000000000000)
│  ├─ 宿主：10.8.30.8000 / 模块：5.9.0
│  └─ Hook：正常 · 启动于 10:53
├─ 运行能力
│  ├─ 存储：OK / 闹钟：OK / 电池：OK
│  ├─ 通知：OK / 前台服务：关闭 / 网络：WiFi
│  └─ RPC：就绪 / 登录：正常
├─ 计划与闹钟
│  ├─ 下一跳：12:01 物种·好友抽卡
│  ├─ 上次：09:15 海洋清理 · 已完成 · 20分21秒
│  ├─ 今日错过：17 轮
│  └─ 闹钟健康：正常 / 疑似被系统清理
├─ 风控
│  ├─ 账号：正常
│  ├─ 待验证：否
│  └─ 最后信号：09:00 森林抽奖 1009
├─ 数据
│  ├─ 账本：2026-10-25 · 09:00 同步 · 271 任务
│  └─ 日志：14 个文件 · 162.9 MB
└─ 设备与 OEM
   ├─ Samsung Android 16 test device
   └─ 疑似强停：否 / 排查指引
```

### 5.3 OEM 强停提示

当 `scheduler.alarmHealth.suspectedKill=true` 时，页面显示：

> 检测到计划时间已过但本轮没有执行记录，进程是在计划时间之后启动的。  
> 请检查：设置 → 电池 → 后台使用限制 → 休眠应用/深度休眠应用，确认“支付宝”和“Medusa”不在列表中，并加入“永不休眠的应用”。  
> 同时确认“设备维护 → 自动优化”不会关闭后台应用。

页面不自动跳转系统设置、不自动修改任何系统开关。

---

## 6. P0-5 实施顺序与发布

### 6.1 实施顺序

1. **CapabilityService**：注入进程内检查 13 项能力，产出 `CapabilityReport`。
2. **SystemStatusWriter**：写 `system_status.json`（schema 1）；启动时、命令时、计划轮次后（节流 30 秒）。
3. **命令**：复用现有 `status` 命令，新增可选载荷 `detail=true`；不新增 action。
4. **系统状态页**：设置页底部入口；读取报告；刷新按钮。
5. **冻结清单**：本文档 + 可选只读检查脚本（校验命令/ID/schema 关键字存在）。
6. **设备验收**：命令回归、能力值对照、首页/域页截图、备份/导出检查。

### 6.2 发布注意

- `ApplicationHook`、`CapabilityService`、`SystemStatusWriter` 属于**注入侧代码**，需要重新构建/安装 npatch host，不能只换模块 APK。
- 系统状态页属于模块 APK，可单独更新 UI。
- 保留原签名密钥；安装后重新拉起宿主并广播 `planChanged`。
- P0 不改任务行为，版本号由发布时决定；建议在发布说明中标注“P0 基础设施，无任务行为变更”。

### 6.3 预计改动文件

| 侧 | 文件 | 改动 |
|---|---|---|
| 注入 | `app/src/main/java/io/github/aw1y2z/sesame/util/CapabilityService.java` | 新增：13 项能力检查 |
| 注入 | `app/src/main/java/io/github/aw1y2z/sesame/data/system/SystemStatusWriter.java` | 新增：`system_status.json` 原子写与节流 |
| 注入 | `app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java` | `status` 命令支持 `detail=true`；启动/轮次后触发写入 |
| 模块 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixSystemStatusActivity.kt` | 新增：只读系统状态页 |
| 模块 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixMainActivity.kt` | 设置页底部入口 |
| 文档 | `docs/P0-contracts.md` | 本文档 |

---

## 7. 验收清单

- [ ] 12 条现有稳定命令行为不变；`status` 的 `detail=true` 可选载荷可用；4 条调试命令不受影响。
- [ ] `system_status.json` schema 1 生成成功，包含 process/account/capabilities/scheduler/risk/data/device/oem。
- [ ] 能力值与 adb 实测一致：精确闹钟 allow、电池 exempted、共享目录可写、通知状态、网络状态。
- [ ] 系统状态页从“设置 → 系统状态”进入；显示下一跳、上次运行、今日错过、风控、账本、日志、设备。
- [ ] 首页 11 张卡、域开关滑动、计划页视觉无变化（前后截图对比）。
- [ ] 不新增系统权限；不修改现有 JSON schema；不修改设置默认值。
- [ ] 全量备份有“包含账号与 token”的提示；诊断导出不含 token 文件与好友列表文件（日志中的昵称按现有日志保留并提示）。
- [ ] 证据保存在 `_evidence/20261025-p0/`；保留上一版 host/module APK 可回滚。

---

## 附录 A：75 个卡片 ID

```text
ancient (1)
ancient.settings

farm (12)
farm.competition, farm.daily, farm.donate, farm.draw, farm.family, farm.friend,
farm.kitchen, farm.life, farm.park, farm.settings, farm.sunflower, farm.task

forest (10)
forest.backpack, forest.friends, forest.home, forest.hunt, forest.ip, forest.park,
forest.patrol, forest.reward, forest.settings, forest.water

golden (9)
golden.exchange, golden.game, golden.mall, golden.miner, golden.overview,
golden.settings, golden.sign, golden.sprout, golden.task

green (7)
green.electricity, green.grain, green.paper, green.recycle, green.settings,
green.travel, green.water

kinetic (4)
kinetic.bubble, kinetic.center, kinetic.sign, kinetic.task

ocean (5)
ocean.clean, ocean.fish, ocean.reward, ocean.settings, ocean.welcome

orchard (13)
orchard.assist, orchard.challenge, orchard.draw, orchard.factory, orchard.fertilizer,
orchard.gamestay, orchard.park, orchard.settings, orchard.sign, orchard.spread,
orchard.task, orchard.team, orchard.tree

species (8)
species.book, species.draw, species.friend, species.friends, species.gift,
species.prop, species.settings, species.task

sports (6)
sports.club, sports.donate, sports.health, sports.settings, sports.tiyu, sports.walk
```

## 附录 B：54 个计划目标

```text
forest.collect, forest.hunt, forest.ip, forest.daily, forest.external,
farm, farm.daily, farm.task, farm.donate, farm.park, farm.draw,
farm.competition, farm.family, farm.friend, farm.kitchen, farm.life,
farm.sunflower, farm.external,
ocean, ocean.reward, ocean.clean, ocean.fish, ocean.welcome, ocean.external,
member, member.external,
sports, sports.external, sports.kinetic,
ancient, protect, dodo,
species.draw, species.friend, species.task, species.prop, species.book, species.gift,
stall,
orchard, orchard.daily, orchard.fertilizer, orchard.task, orchard.sign,
orchard.spread, orchard.park, orchard.assist, orchard.draw, orchard.challenge,
orchard.factory, orchard.gamestay,
green, golden, answerAI
```

## 附录 C：指标与模块 ID

### 25 个域指标 ID

```text
ancient.recent, ancient.switches, ancient.today,
common.recent, common.switches, common.today,
farm.drawn, farm.eggs, farm.switches, farm.totalEggs,
forest.day, forest.recent, forest.rob, forest.robbed, forest.switches,
forest.todayRuns, forest.totalCollected, forest.watered,
green.checkins, green.medals, green.streak, green.today,
ocean.cleaned, ocean.switches, ocean.total
```

### 12 个主指标 ID

```text
farm.drawn, farm.eggs, farm.switches,
forest.day, forest.rob, forest.robbed,
ocean.cleaned, ocean.switches, ocean.total,
plan.completed, plan.pending, plan.steps
```

### 11 个首页模块 key

```text
forest, sports, farm, orchard, ocean, species, golden, protect, greenAction, member, stall
```

### 9 个 DomainRegistry key

```text
forest, greenAction, ancient, ocean, farm, orchard, species, sports, golden
```

### 6 个指标目录 key

```text
forest, greenAction, ancient, farm, ocean, generic
```

## 附录 D：账本 schema 摘要

```text
14 个域：
ocean, ocean.fish, farm, farm.draw, forest.reward, species.task, golden,
sports.kinetic, orchard.task, orchard.park, orchard.draw, orchard.challenge,
forest.park, forest.patrol

任务字段：
id, title, group, rawStatus, status, progressCurrent, progressTotal, award,
manualReason, failCount, firstSeenAt, lastSeenAt, updatedAt, lastRunId

summary 计数：
pending, claimable, untouched, inProgress, claimed, manual, blacklisted,
skipped, total
```

## 附录 E：源文件索引

| 契约 | 源文件 |
|---|---|
| 全局设置 | `app/src/main/java/io/github/aw1y2z/sesame/data/AppConfig.java` |
| 域配置 | `app/src/main/java/io/github/aw1y2z/sesame/data/ConfigV2.java` |
| 计划存储 | `app/src/main/java/io/github/aw1y2z/sesame/data/plan/PlanStore.java` |
| 计划历史 | `app/src/main/java/io/github/aw1y2z/sesame/data/plan/PlanHistory.java` |
| 计划运行态 | `app/src/main/java/io/github/aw1y2z/sesame/data/plan/PlanRuntime.java` |
| 调度器 | `app/src/main/java/io/github/aw1y2z/sesame/data/plan/PlanScheduler.java` |
| 计划目标 | `app/src/main/java/io/github/aw1y2z/sesame/data/plan/PlanTargets.java` |
| 风控 | `app/src/main/java/io/github/aw1y2z/sesame/util/RiskGuard.java` |
| 权限/能力 | `app/src/main/java/io/github/aw1y2z/sesame/util/PermissionUtil.java` |
| 账本 | `app/src/main/java/io/github/aw1y2z/sesame/data/ledger/TaskLedgerStore.java` |
| 统计 | `app/src/main/java/io/github/aw1y2z/sesame/util/Statistics.java` |
| 命令接收 | `app/src/main/java/io/github/aw1y2z/sesame/hook/ApplicationHook.java` |
| 域清单 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/DomainRegistry.kt` |
| 指标目录 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/DomainMetricCatalog.kt` |
| 首页 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/MiuixHomeV2.kt` |
| 备份 | `app/src/main/java/io/github/aw1y2z/sesame/ui/miuix/BackupManager.kt` |
| 诊断导出 | `app/src/main/java/io/github/aw1y2z/sesame/util/DiagnosticExport.java` |

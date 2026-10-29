# 回退矩阵（4.0.x）

同签名就地升级，宿主与模块必须成对回退。回退前先备份
`sesame-M/config/<uid>/`、`appConfig.json`、`risk_state.json`、`ledger/`。

| 版本 | versionCode | 账本 schema | 配置 schema | 计划 schema | 可否直接被更高版本读取 | 降级注意 |
| --- | --- | --- | --- | --- | --- | --- |
| 4.0.0 | 40000 | 2 | 2 | 2 | 可以 | 无额外字段 |
| 4.0.1 | 40100 | 2 | 2 | 2 | 可以 | 无额外字段 |
| 4.0.2 | 40200 | 3 | 2 | 2 | 可以 | 账本首次出现 classKey/mechanism/costType/endpoint |
| 4.0.3 | 40300 | 3 | 2 | 2 | 可以 | 任务过滤器（plan.filters）、端点状态 capability_state.json |
| 4.0.4 | 40400 | 3 | 2 | 2 | 可以 | 段级过滤；classKey 追加 #sceneCode；按类跳过汇总 |
| 4.0.5 | 40500 | 3 | 2 | 2 | 可以 | 跳过样例；run_start 带 moduleVersion/filter |

## 降级步骤（例：4.0.5 → 4.0.3）

1. 停掉所有计划（计划页关闭或 `.planStop`），避免回退期间运行。
2. 备份上述配置与账本目录。
3. `adb install -r deliver/V4.0.3/美杜莎_V4.0.3_支付宝免root.apk`
4. `adb install -r deliver/V4.0.3/美杜莎_V4.0.3_模块_arm64.apk`
5. 启动支付宝，广播 `com.eg.android.AlipayGphone.sesame.planChanged`。
6. 核对 `system_status.json` 的 `moduleVersion` 与「下一跳」。

## 降级会丢什么

- 旧版读到新字段（classKey、filters、#sceneCode）会忽略；下一次写盘会把它们抹掉。
  因此**降级后不要立刻依赖账本分类**，需要回退前先导出 `ledger/` 存档。
- `capability_state.json` 旧版不读，留着无害；升回新版会按当天重新累积。
- 计划里的 `filters` 被旧版抹掉后，该计划会变成“整域全跑”，需要重新选任务组。

## 已知不能跨的边界

- 不要跨大版本回退（4.0.x ↔ 3.x）：账本目录、队列语义、手动直达都不同。
- 卸载宿主会删除 `Android/media/com.eg.android.AlipayGphone`（全部配置与账本），只能重装后手工恢复。

# 回放 harness（4.0.0）

不连设备、不发请求，把「服务端响应 → 本地策略」这一段拿真实语料反复跑。
用途：改风控/归类/任务能力时，先用语料证明没改坏，再上手机。

## 目录

```
app/src/test/java/io/github/aw1y2z/sesame/replay/
  Desensitizer.java    脱敏（字段级 + URL 查询参数级）
  ReplayEnv.java       可注入时钟 / 确定性随机
  ReplayCorpus.java    语料加载（JSONL）
  ReplayRunner.java    执行器 + 期望比对
app/src/test/resources/replay/
  starter.jsonl        starter 语料（数据期用真实抓包替换/补充）
```

## 语料格式（JSONL，一行一个用例）

```json
{"id":"用例名","domain":"ocean","method":"alipay.antocean.antiep.task.receive",
 "expect":{"outcome":"C1009","signal":"SERVER_REFUSAL"},
 "response":"{\"error\":\"1009\"}","note":"为什么留这条"}
```

- 期望字段可只写需要的：
  - `outcome`：`OK / EMPTY / C1009 / C1004 / FAIL`（RpcMetrics.classify）
  - `signal`：`NONE / LIMIT / SERVER_REFUSAL`（RiskGuard.classify）
  - `kind`：`CLAIM / WAIT / REAL_ACTION / GONE / UNKNOWN`（TaskCapability.classify，需配 `input`）
- `input`（只有 `kind` 用例需要）：`{"content":"任务标题","timeCount":15,"taskRequire":0,"taskProgress":0,"autoComplete":false}`
- `#` 或 `//` 开头的行是注释。

## 取值口径

- `SERVER_REFUSAL` = 1009 或命中风控文案；只暂停当前域。
- `LIMIT` = 1004 限流；只暂停当前域。
- `102` 是「稍后重试」，算 `OK` 不算失败（与 TaskAttemptPolicy 的 RETRY 语义一致）。
- `400000040` 走伪申报兜底，不是风控，算 `FAIL` 但不产生 signal。

## 加一条真实抓包

1. 从日志/抓包里取出**响应体**（不要请求头、不要 URL）。
2. 跑一遍脱敏：

   ```bash
   bash tools/replay/desensitize.sh < 原始响应.json
   ```

   脚本会把 `userId / uid / friendId / token / mobile / actionUrl 查询参数` 等替换为 `<redacted>`。
   没把握就别加——语料里出现真实账号信息等于把账号信息写进仓库。
3. 追加一行到 `starter.jsonl`（或新建 `<域>.jsonl`），写清 `expect` 与 `note`。
4. 跑测试：

   ```bash
   bash tools/ci.sh test
   ```

## 当前状态

- starter 语料是**已知信封形状**，用来锁住归类与风控解析的语义。
- 真实语料在 4.0.0 数据期采集后补入；采集前必须先脱敏。
- 回放目前覆盖「纯策略层」；网络/文件注入（把 ApplicationHook 的响应源换成回放源）留到后续版本。
# TaskRunEvent contract

`TaskRunEvent` is the machine-readable leaf-task stream. It is the primary
input for a local maintenance agent.

## Location

`<host media dir>/sesame-M/log/tasks.<yyyy-MM-dd>.jsonl`

One JSON object per line, UTF-8, append-only. Retention: 7 days; size cap:
16 MiB per day.

## Fields

| Field | Type | Meaning |
|---|---|---|
| `ts` | long | epoch millis |
| `at` | string | local time, `yyyy-MM-dd HH:mm:ss.SSS` |
| `uid` | string | account id (local only; never upload raw) |
| `runId` | string | `planId@startedAt` for this plan run |
| `planId` | string | plan id, or `manual-*` |
| `target` | string | leaf target, e.g. `forest.reward` |
| `domain` | string | domain prefix, e.g. `forest`, `farm` |
| `phase` | enum | `attempt`, `claim`, `skip` |
| `taskId` | string | stable task key used by the module |
| `title` | string | task display name |
| `mechanism` | enum | `CLAIM`, `BROWSE`, `GAME`, `SOCIAL`, `TIMED`, `CHANCE`, `VIRTUAL_SPEND`, `EXTERNAL_JUMP`, `TRANSACTION`, `PROGRESS`, `ANSWER`, `REAL_ACTION`, `AUTH` |
| `endpoint` | string | list field / capability endpoint |
| `outcome` | enum | see below |
| `durationMs` | long | attempt duration |
| `reason` | string | short reason code/text |
| `moduleVersion` | string | producer version |

## Phases and outcomes

- `attempt`: `DONE`, `RETRY`, `UNABLE`, `UNSUPPORTED`, `FORGED`, `TRIGGERED`,
  `TRIED_TODAY`, `SKIPPED`, `FAILED`.
- `claim`: `AWARDED`, `FAILED`.
- `skip`: `SKIPPED` (reason explains: filter, cooldown, missing list/number,
  no taskConfigId, etc.).

`DONE` means the task list confirmed completion, not just an RPC success.
`TRIGGERED`/`FORGED` mean an action was sent; the task list decides the final
result. `EMPTY` at the RPC metric layer is not the same as `SKIPPED` here.

## Example

```json
{"ts":1793274272327,"at":"2026-10-29 19:44:32.327","uid":"2088...","runId":"manual-1793274256584@1793274256584","planId":"manual-1793274256584","target":"forest.park","domain":"forest","phase":"attempt","taskId":"forest::optionalplay::ANTFOREST_LEYUAN_DAILY_TASK/NLGJ_DJDRW_LEYUAN#4","title":"玩一玩挪了个箭(5/6)","mechanism":"GAME","endpoint":"AntForestVitalityTaskList","outcome":"DONE","durationMs":7759,"reason":"","moduleVersion":"4.1.13"}
```

## Aggregation for an agent

- By `target` + `outcome`: which leaf is failing.
- By `taskId` + `reason`: the same task repeating (retry loop, unsupported).
- By `endpoint` + `outcome`: a changed RPC contract.
- By `runId`: one plan run; join with `events.*.jsonl` `run_end`.
- By day: coverage (how many leaves actually ran) and claimables.

## Privacy

Do not upload `uid`, raw task titles from private lists, or raw logs. Use
counts and redacted ids in issues. Keep the file local.

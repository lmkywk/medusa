# AI handoff packet

The handoff packet is the bridge from Medusa's local AI to the development AI.
It is generated locally, deterministically, and is small enough to paste into
a chat or attach to a GitHub issue.

## Files

- `ai/handoff-<yyyy-MM-dd>.json` — machine-readable packet (schema v1).
- `ai/handoff-<yyyy-MM-dd>.md` — the same packet for humans/agents.

The in-app AI page has:

- **生成维护交接包** — write both files and show the Markdown.
- **复制交接包 JSON** — copy the compact JSON to the clipboard.

The daily AI summary also embeds `handoff` (summary + top 5 anomalies + next
actions + plan items) in its digest, so the model reasons from leaf events
instead of only RPC aggregates.

## Schema v1

| Field | Meaning |
|---|---|
| `schemaVersion` | 1 |
| `day` | local day |
| `generatedAt` | epoch millis |
| `moduleVersion` | producer version |
| `fingerprint` | official build/source fingerprint (verify with `PROVENANCE.md`) |
| `device`, `android` | device context |
| `planLayoutVersion` | plan layout version |
| `tokenEstimate` | packet size / 4 |
| `summary` | event/plan counters |
| `anomalies` | top 8 leaf anomalies |
| `plans` | incomplete/risk plan items (top 5) |
| `ledger` | domains with pending/claimable (top 8) |
| `config.offFields` | disabled boolean fields (top 30) |
| `nextActions` | deterministic probes (max 6) |
| `doNotTouch` | safety boundaries |

Each anomaly:

```json
{
  "target": "member",
  "taskId": "看短剧5分钟",
  "title": "看短剧5分钟",
  "mechanism": "TIMED",
  "endpoint": "看短剧5分钟",
  "outcome": "SKIPPED",
  "reason": "缺少taskConfigId",
  "count": 14,
  "avgMs": 0,
  "firstAt": "2026-10-29 20:04:56.519",
  "lastAt": "2026-10-29 20:10:10.896",
  "suspectedLayer": "config",
  "changeClass": "A",
  "safeToAutoFix": true,
  "nextProbe": "A: 复核 member 的开关依赖…"
}
```

## Change classes

- **A** — config/plan only (switch, list, number, target, blacklist). Safe to
  auto-fix with user approval.
- **B** — task policy/ledger/metrics/UI. Needs tests and a canary.
- **C** — core hook/RPC/build/signing/risk. Human review required.

## How the development AI should use it

1. Read `summary` and `anomalies`; do not ask for raw logs.
2. For each anomaly, follow `nextProbe` in order; stop at the first confirmed
   cause.
3. Prefer class A changes; for class B, add a failing test first.
4. Never propose host APK/signing changes or disable risk controls.
5. Return a diff + test plan + rollback steps, not a restatement of metrics.

## Privacy

No `uid`, `runId`, tokens, friend lists, or raw RPC payloads are included.
Titles are task names. Keep the packet local; redact titles if a user considers
them sensitive.

## Token budget

The packet is capped at top 8 anomalies, 5 plan items, 8 ledger domains, and 30
off fields. `tokenEstimate` is a rough character/4 estimate. For a chat, send
the JSON or the top 5 anomalies only.

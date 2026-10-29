# Medusa AI Maintenance Manual (v0)

Audience: a local agent with read/write access to this repository and to the
user's device. The agent's job is to keep a user's assistant working without
requiring the original author to ship new versions.

## 0. Non-negotiables

1. **Do not distribute the host.** Never commit or upload an Alipay APK,
   patched host, npatch output, signing key, or keystore.
2. **Data stays local.** Read `tasks.*.jsonl`, status, ledger, and config on
   the device. Do not upload raw logs, account IDs, friend lists, or tokens.
3. **Diagnose before editing.** Start read-only. Propose a diff. Get user
   approval for code, risk-control, payment/donation, or build changes.
4. **Verify, then roll back if needed.** Unit tests, replay tests, device
   canary, and a rollback snapshot are required for code changes.
5. **Do not silently weaken safety.** Never enable payment/transaction or
   donation tasks, and never bypass risk cooldowns/1009 handling to "make it
   pass".

## 1. Baseline and source of truth

- Baseline: module `4.1.18`, plan `layoutVersion=5`, host tested with
  Alipay `10.8.30.8000` on Samsung `SM-S9480`.
- A release tag is the source of truth. Local changes are an overlay, not a
  new upstream. If a local fix is useful to others, propose a PR.
- Record the host version, module version, device model, and test result in
  release notes.

## 2. Architecture map

- `hook/ApplicationHook` — host injection and RPC bridge.
- `rpc/` — new/old RPC bridges, interval limits.
- `data/plan` — plans, targets, scheduler, executor, history, live actions.
- `data/task` — task attempt policy, capability registry, task filters,
  `TaskRunEvent`.
- `data/ledger` — status snapshots, task ledgers, daily reports.
- `model/task/*` — domain executors (forest/farm/ocean/orchard/species/…).
- `ui/miuix` — module UI, home/plan/data/settings.

A plan targets a **leaf** such as `forest.reward`, `forest.park`,
`forest.game`, `farm.task`, `ocean.fish`, etc. A leaf failure should not
block other leaves.

## 3. Local data to read (read-only first)

- `log/tasks.<date>.jsonl` — leaf task events (primary input).
- `log/events.<date>.jsonl` — plan/scheduler/risk/ledger events.
- `log/error.<date>.log`, `log/runtime.<date>.log` — human-readable details.
- `config_v2.json` — switches.
- `plans.json` — plan layout.
- `plan_history.json` / `plan_runtime.json` — last runs.
- `ledger/<uid>/<date>/{summary,report,snapshot}.json`.
- status files: `farm_status.json`, `ocean_status.json`, `forest_status.json`,
  `kinetic_status.json`, `member_status.json`, `stall_status.json`, etc.

Never read or transmit tokens, cookies, raw RPC payloads containing PII, or
friend lists.

## 4. Diagnosis workflow

1. **Scope**: which target/plan/domain and time window failed?
2. **Leaf events**: group `tasks.*.jsonl` by `target`, `taskId`, `outcome`,
   `reason`, `endpoint`. Identify the first failing leaf, not the last error.
3. **Identify the layer**:
   - config/plan (switch off, empty list/number, wrong target)
   - task policy (unsupported, retry loop, watchdog, cooldown)
   - RPC/endpoint (1009/1004/400000040/PARAM_ILLEGAL/empty/timeout)
   - device (freeze, process restart, network 48)
   - ledger/display (stale status, wrong denominator)
4. **Reproduce read-only** if possible: re-run the leaf with a short budget;
   prefer a canary over a full domain.
5. **Propose** the smallest change. Config/plan first; code second.
6. **Verify** with the protocol in section 7.
7. **Record** the event signature and fix in the manual/known issues.

## 5. Failure signatures

| Signature | Meaning | Usual action |
|---|---|---|
| `1009` | server requires verification | stop the domain, respect cooldown; do not retry blindly |
| `1004` | rate limited | back off; reduce pacing |
| `400000040` | RPC not supported for this task | use the documented fallback (`doFarmTask`) or mark UNABLE |
| `PARAM_ILLEGAL` + `retriable:false` | malformed/unsupported task payload | skip/blacklist; do not retry every 30s |
| `taskConfigId=0` | member browse task missing config | skip + event; do not call `applyTask` |
| empty/null response | local gate/host offline, not "no tasks" | check risk pause, offline, stop state |
| timeout | plan wall-clock budget | check device freeze; use leaf budgets |
| `SKIPPED` + reason | filtered, cooldown, missing list/number | fix config, not code |
| plan `RUNNING` after restart | stale history/runtime | run stale cleanup or restart |
| `network 48` | transient network | retry later; not an interface change |

## 6. Change classes

- **A. Config/plan only** (agent may apply with user approval): switches,
  lists/numbers, plan times, leaf targets, blacklists. Prefer this.
- **B. Task policy/ledger/metrics** (agent may patch with tests): retry
  classification, event fields, status capture, UI labels. Keep changes
  narrow.
- **C. Core hook/RPC/build** (human review required): `ApplicationHook`,
  RPC bridge, host patching, signing, dependency upgrades, risk controls.
  Do not auto-apply.

## 7. Verify and roll back

1. `./gradlew :app:testNormalDebugUnitTest`
2. Replay corpus tests (if the change touches parsing/policy).
3. Build the module; install on a test device.
4. Canary: one leaf target, read-only or low-risk, short budget.
5. Check `tasks.*.jsonl` for the expected outcome and no new failure class.
6. Keep the previous APK + `config_v2.json` + `plans.json`; restore on failure.

## 8. Build and host rules

- The user supplies their own host APK and signing key. See `BUILDING.md`.
- Never put the host or keys in the repository.
- A new signing key requires uninstall/reinstall; back up the media/config
  directory first.

## 9. Privacy and AI provider

- Bring your own AI provider/key. Do not ship a shared key.
- Send only aggregates or redacted event fields; never raw logs or account IDs.
- Keep the event stream local.

## 10. Federated help

When a fix is not local, produce a structured issue:

```text
Medusa version / host version / device:
Target + taskId + mechanism + endpoint:
Outcome/reason + first/last time:
Event digest (counts by outcome/reason, no raw logs):
Reproduction steps (read-only):
Proposed change class (A/B/C):
```

This is the "trusted help" input for the community/agent maintainers.

## 11. Known issues at 4.1.13

- Member no-task branch can remain `RUNNING` until the plan timeout; module
  exit needs a fix.
- Member `applyTask` with `taskConfigId=0` is skipped and evented now.
- Empty exchange lists (member benefits, golden mall, vitality exchange,
  dodo props) are disabled by preset v3.
- Sports walk can be long; sports has no default plan; its ledger is mostly
  manual/external tasks.
- Green finance is not activated for the test account; ancient tree
  conditions were not met.
- Farm food storage cap limits claims; auto-feed is bounded (4 friends,
  12 scans, 60s per round).
- The 02:00 nightly batch (15 leaves) is the first full-batch test.
- `TaskRunEvent` now feeds `ai/handoff-<date>.json` and the in-app AI summary;
  the handoff is the primary input for the development AI. The in-app summary
  still also contains RPC/ledger/plan aggregates for context.
- The handoff groups anomalies by problem class (e.g. member missing
  taskConfigId = 192 events / 50 tasks) and adds plan/ledger next actions,
  so it stays within ~500–1200 tokens.

## 12. Agent output template

```text
结论:
证据（event signature + counts）:
根因层:
建议改动（类 A/B/C）:
验证步骤:
回滚方式:
需要用户确认的点:
```

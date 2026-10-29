# 4.1.13 release notes

## Highlights

- Nightly batch: 15 leaf targets at 02:00 in one wake lock; morning batch at
  09:10 (orchard fertilizer/spread + ocean clean).
- Forest daily split into five leaves: reward, park chests, park game,
  patrol, reading. Measured: 21s / 9s / 11s / 1m44 / 8s (test account).
- Member and Stall real-time status capture (`member_status.json`,
  `stall_status.json`) and home cards.
- Config preset v3: almost all safe/resource-spend switches on; payment,
  transaction, donation, purchase, unselected friends, and empty list/number
  dependencies remain off.
- `TaskRunEvent` leaf event stream: `log/tasks.<date>.jsonl`.
- Home metric board pads short pages to three columns.
- Sync button refreshes config + all domain status/ledger + display,
  including sports, member, and stall.

## Fixes

- Farm: claim finished tasks before TODO; auto-feed friends to free food
  storage (bounded: 4 successes / 12 scans / 60s); watchdog tolerates process
  freeze; active-time budgets for short leaves.
- Forest: park chest vs 6/6 park game split; stale action counts fixed.
- Member: `taskConfigId=0` browse tasks skip instead of retrying
  `PARAM_ILLEGAL`; empty benefit list disabled by preset v3.
- Plan: stale `RUNNING` history is normalized after restart; stop reason no
  longer mislabeled as timeout.
- RPC metrics: local gate/offline nulls recorded as `BLOCKED`, not `EMPTY`.
- AI summary prompt distinguishes `empty`/`blocked`/`timeout`.

## Known issues

- Member no-task branch can remain `RUNNING` until plan timeout.
- Sports execution can be long; sports has no default plan and its ledger is
  mostly manual/external tasks.
- Green finance not activated for the test account; ancient tree conditions
  were not met.
- The 02:00 batch is the first full-batch test for this layout.
- The in-app AI summary still consumes RPC/ledger/plan aggregates;
  `TaskRunEvent` is available for local agents but not yet the summary input.

## Test baseline

- Host: Alipay 10.8.30.8000
- Device: Samsung SM-S9480, Android 16
- Module: 4.1.13 (versionCode 41034)
- Plan layout: 5 (24 plans)

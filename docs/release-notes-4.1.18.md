# 4.1.18 release notes

## Highlights

- **AI handoff packet**: `ai/handoff-<date>.json` and `.md` are generated
  deterministically from leaf events, plans, ledger, and config. The packet
  groups anomalies by problem class and includes `suspectedLayer`,
  `changeClass` (A/B/C), `safeToAutoFix`, `nextProbe`, plan/ledger actions,
  `doNotTouch`, and a token estimate. Measured 570–1200 tokens.
- **In-app AI summary** now embeds the handoff (summary + top 5 anomalies +
  next actions + plan items) and prompts the model to hand off actionable
  causes to the development AI instead of restating metrics.
- **AI page** adds “生成维护交接包” and “复制交接包 JSON”. No model call is
  needed for the packet; raw logs/account ids/runIds are not included.
- Handoff aggregation groups by problem class (e.g. member `taskConfigId=0`
  = 192 events across 50 tasks) so one repeated issue does not fill the
  top-8 list.
- `planLayoutVersion`, plans, ledger, and config-off fields are included;
  module-App processes resolve the account from `current_account.txt`.

## Fixes carried from 4.1.13–4.1.17

- Member `taskConfigId=0` tasks skip instead of retrying `PARAM_ILLEGAL`.
- Preset v3 disables empty-list dependencies (member benefits, golden mall,
  vitality exchange, dodo props).
- Forest five-leaf split and park chest/game swap.
- Member/Stall status capture and home cards.
- Sync includes sports/member/stall; home board pads to three columns.
- TaskRunEvent target fallback and plan/ledger next actions.

## Known issues

- Member no-task branch can remain `RUNNING` until plan timeout.
- Sports execution can be long; sports has no default plan.
- Green finance not activated for the test account; ancient tree conditions
  were not met.
- The 02:00 full batch is the next end-to-end test.

## Test baseline

- Host: Alipay 10.8.30.8000
- Device: Samsung SM-S9480, Android 16
- Module: 4.1.18 (versionCode 41044)
- Plan layout: 5 (24 plans)

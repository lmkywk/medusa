# 5.9.0 — early-access decentralized baseline

Released: 2026-10-29

This is the first public source baseline under the unified `5.9.0` version.
It is published for community testing and local-agent maintenance, not as a
promise of a stable daily driver. The targeted flows below were verified on a
real host; the full 02:00 batch on this exact build is still pending.

## Release identity

- Module version: `5.9.0`
- `versionCode`: `59000`
- Tag: `v5.9.0`
- Build fingerprint: `medusa-5.9.0-0fda2ce`
- Certificate and APK hashes: `PROVENANCE.md`

## Fish task chain

- Fixed the `optString` null check in `AntOcean.migrateFishBlacklist()`: the
  migration had never run because it compared `getString(...) != null` against
  an empty string.
- Time-based fish tasks (5s browse / 15s video) are removed from the stale
  blacklist and executed as `WAIT` tasks.
- `AIFISH_DIANZAN` now performs a **real like** before reporting the task:
  `queryNeedAppraiseFishList` → `fishAppraise(LIKE)` → `finishTask` → claim.
  No fake reporting.
- Chain-unlocked tasks (for example `AIFISH_ZIRANKETANG`) are picked up on the
  next fish run and executed by the same `WAIT` path.

## Ledger reconciliation

- Fixed duplicate sports sign-in rows: `KineticTaskLedger.save()` now removes
  the old `sign` row before marking missing tasks as `SERVER_GONE`.
- `SKIPPED` is displayed as **未探明** in the task list, status text, and
  domain summary. Plan-history `SKIPPED` (a missed window) is unchanged.
- Added member/stall status capture (task counts, sign-in, village, manure)
  to the ledger refresh.

## Claim fixes

- Limited-time orchard challenge: finished child tasks are claimed before
  processing TODO children. Previously a TODO parent hid their rewards
  forever.
- Orchard draw: the blacklist now only blocks TODO tasks, not `FINISHED`
  claim rewards. A finished blacklisted draw task can be claimed.
- Farm: claim-first ordering kept; auto-feed remains bounded.

## AI handoff and decentralized maintenance

- `ai/handoff-<date>.json/.md` is generated locally and is small enough to
  paste into a chat or a GitHub issue (~500–1200 tokens).
- `TaskRunEvent` (`log/tasks.<date>.jsonl`) records leaf attempts, skips, and
  claims with target/task/mechanism/endpoint/outcome.
- `AGENTS.md`, `docs/AI-MAINTENANCE-MANUAL.md`, `docs/AI-HANDOFF.md`,
  `docs/TASK-RUN-EVENT.md`, and `docs/DISTRIBUTION.md` define the local-agent
  update and verification contract.
- The handoff and `system_status.json` include the build fingerprint.

## Configuration preset

- `presetVersion=3` turns on almost all safe/resource-spend switches once,
  while keeping payment, transaction, donation, external-jump, and
  missing-list dependencies off.
- Empty lists disable their dependent switches instead of leaving a task
  stuck in `RUNNING`.

## Verification on 2026-10-29

- Host + module 5.9.0 installed; `system_status.json` reports
  `moduleVersion=5.9.0` and the build fingerprint.
- Fish: `AIFISH_XIANGMU_LIEBIAO`, `AIFISH_XUNBAO`, `AIFISH_DIANZAN` (real
  like), `AIFISH_ZIRANKETANG` all `DONE`; fish plan completed 12 actions.
- Claims: 7 of 19 claimable rewards collected (farm family event, orchard
  task/draw/challenge, sports sign-in). Remaining 13 are farm rewards blocked
  by the 1759/1800g feed cap and no hungry friend chickens.
- Ledger: 14 task-list domains refreshed with `state=ok`; sports duplicate
  removed; `未探明` label verified with `uiautomator`.
- Unit tests: `./gradlew :app:testNormalDebugUnitTest --offline` passes.

## Distribution

Two supported paths, both module-only:

1. signed module APK from this repository's Releases;
2. Xposed/LSPosed module repository or module market using `module.prop`.

The Alipay host APK is **not** included and must not be redistributed. See
`docs/DISTRIBUTION.md`.

## Migration notes

- No config schema change. `presetVersion` is written once; existing user
  choices are preserved after the preset is applied.
- The one-time fish blacklist cleanup uses `ocean.fishBlacklist410`.
- `SKIPPED` is still the persisted enum name; only its display label changed.
- Version numbers are unified to `5.9.0` in source, APK metadata, release
  notes, and in-app status.

## Known limitations

See `docs/AI-MAINTENANCE-MANUAL.md` §11. The short version:

- Full 02:00 batch on 5.9.0 is pending.
- 13 farm rewards remain capped by feed storage.
- Forest hunt and member/stall task lists are not yet in the task ledger.
- Sports and green finance remain manual/account-conditional.

## Security

This repository contains source only. It does **not** include the Alipay host
APK, patched host outputs, signing keys, user data, logs, tokens, or AI
provider keys. The host layer and signing key are your responsibility.

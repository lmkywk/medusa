# Medusa

Medusa is a source-only, agent-maintainable assistant for Alipay daily tasks.
It is derived from the Sesame family of projects and released under **GPL-3.0**.

This repository is the **last known-good source baseline** and the
maintenance contract for local agents. It intentionally does **not** ship:

- the Alipay host APK or any patched host output;
- signing keys or release certificates;
- user data, tokens, logs, or AI provider keys.

The host layer is the user's responsibility: obtain your own host package,
build/patch it locally, and sign it with your own key. See `BUILDING.md`.

## Current baseline

- Module version: `4.1.18`
- Plan layout: `layoutVersion=5` (24 plans; 15 nightly batch items)
- Tested host: Alipay `10.8.30.8000` on Samsung `SM-S9480` (Android 16)
- This is a tested baseline, not a guarantee for other host versions.

## Why this repository exists

The project is designed to be maintained by a local AI agent rather than by a
central maintainer. The repository provides:

- stable source and reproducible build instructions;
- a machine-readable leaf-task event stream (`log/tasks.<date>.jsonl`);
- a deterministic **AI handoff packet** (`ai/handoff-<date>.json`)
  that gives the development AI problem classes, affected plans, ledger
  claimables, and next probes in ~500–1200 tokens;
- a maintenance manual that tells an agent how to diagnose, patch, test, and
  roll back safely;
- a contract for RPC endpoints, task capabilities, and failure signatures.

Read `docs/AI-MAINTENANCE-MANUAL.md` first if you are an agent.
Read `docs/AI-HANDOFF.md` for the local-AI → development-AI handoff.
Read `docs/TASK-RUN-EVENT.md` for the event schema.

## Layout

- `app/` — module source (Java/Kotlin, resources)
- `docs/` — AI manual, contracts, release notes, third-party licenses
- `tools/` — build/release helpers; host APK and keys are supplied by the user
- `LICENSE` / `NOTICE.md` — GPL-3.0 and attribution

## Build (summary)

You need JDK 21, Android SDK 37, your own Alipay host APK, your own keystore,
and the build tools listed in `BUILDING.md`. No host APK or key is included.

## Agent-maintained model

A local agent should:

1. read `log/tasks.<date>.jsonl`, status files, and ledger summaries;
2. diagnose the failing leaf task, endpoint, or plan;
3. prefer config/plan changes over code changes;
4. run unit tests, replay tests, and a device canary;
5. ask the user before applying code or changing risk settings;
6. keep a rollback snapshot.

Do not upload raw logs or tokens. Keep data local.

## Legal

GPL-3.0. Alipay is a trademark of its owner. This project is not affiliated
with or endorsed by Alipay. Use only where permitted by law and the applicable
terms of service. No warranty.

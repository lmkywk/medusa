# Medusa

Medusa is a **source-only, decentralized assistant** for Alipay daily tasks.
It is derived from the Sesame family and released under **GPL-3.0**.

The project is built with **open-source AI assistance** and is itself fully
open source. There is no central maintainer: users run a local AI agent,
point it at this repository, and let it read the maintenance manual, diagnose
local evidence, and update the module. User data stays on the user's device.

This repository intentionally does **not** ship:

- the Alipay host APK or any patched host output;
- signing keys or release certificates;
- user data, accounts, logs, tokens, or AI provider keys.

The host layer is the user's responsibility: obtain your own host package,
patch it locally, and sign it with your own key. See `BUILDING.md` and
`docs/DISTRIBUTION.md`.

## Current release

- Module version: `5.11.0`
- Plan layout: `layoutVersion=8`
- Tested host: Alipay `10.8.30.8000` on a Samsung Android 16 test device
- Status: **early-access test baseline**. Targeted flows and the
  2026-10-31 owner canary were verified; a full multi-day soak is still running.
- This is a tested baseline, not a guarantee for other host versions.
- Known limitations: `docs/release-notes-5.11.0.md` and
  `docs/AI-MAINTENANCE-MANUAL.md` §11.
- Framework: the public build targets **libxposed API 101** and loads on both
  FPA 101 and LSPosed/Vector 102. The author build stays on API 102.
- Data directory: `Android/media/com.eg.android.AlipayGphone/Medusa` for the
  public build; existing `sesame-M` data is copied on first launch and the old
  directory is kept. The author build still uses `sesame-M`.
- Defaults: new installs/new accounts start with all task/domain switches off;
  existing configs are not reset. The author build keeps its previous defaults.

## Two ways to install

### 1. Download the module APK

Download the signed module APK from this repository's **Releases** page:

- `Medusa-V5.11.0-module-arm64.apk` for most arm64 devices;
- `Medusa-V5.11.0-module-universal.apk` when unsure.

The release also contains `SHA256SUMS.txt`. The Alipay host APK is **not**
included; patch your own host with npatch/LSPosed and install this module.

### 2. Framework / module market

The module is a normal Xposed/LSPosed module:

- package: `com.medusa.alipay`
- module metadata: generated from `app/module.prop.template` into
  `META-INF/xposed/module.prop` (API 101 for public, API 102 for author)
- scope: `com.eg.android.AlipayGphone`
- entry hook: `io.github.aw1y2z.sesame.hook.ApplicationHook`

The release tag, asset names, `module.prop` fields, and market checklist are
documented in `docs/DISTRIBUTION.md`. The host APK must not be redistributed
by module markets.

## Decentralized maintenance

`Code is cheap; the agent is the maintainer.` A local agent should:

1. read `AGENTS.md` and `docs/AI-MAINTENANCE-MANUAL.md`;
2. read `log/tasks.<date>.jsonl`, status files, ledger summaries, and
   `ai/handoff-<date>.json` (the compact local-AI → development-AI packet);
3. diagnose the failing leaf task, endpoint, or plan;
4. prefer config/plan changes over code changes;
5. run unit tests, replay tests, and a device canary;
6. verify the release fingerprint before installing;
7. keep all user data local and never upload accounts, logs, or tokens.

To update from a repository link, paste a prompt like this into a local agent:

```text
Read AGENTS.md and docs/AI-MAINTENANCE-MANUAL.md from <this-repository-url>.
Update this Medusa installation to the latest release tag. Keep all user data
local, do not touch the host APK or signing keys, run the unit tests and a
device canary, and report the old/new fingerprint.
```

The agent-facing documents are:

- `AGENTS.md` — bootstrap and safety rules;
- `docs/AI-MAINTENANCE-MANUAL.md` — architecture, diagnosis, patch, test,
  rollback;
- `docs/AI-HANDOFF.md` — handoff packet schema;
- `docs/TASK-RUN-EVENT.md` — leaf event schema;
- `docs/P0-contracts.md` — RPC/task/ledger contracts.

## Authenticity and fingerprint

Only this repository and its release tags are the authoritative source.
Forks and repackaged APKs are not official.

Every official build carries a fingerprint that is written to
`system_status.json` and `ai/handoff-<date>.json`, for example:

```text
medusa-5.11.0-<build-commit>
```

Before installing, compare it with `PROVENANCE.md`, which also records:

- the official source tag and commit;
- the signing certificate SHA-256;
- the release APK SHA-256;
- the build fingerprint.

If the fingerprint, certificate, or APK hash does not match, do not install.
See `PROVENANCE.md` for the exact values of 5.11.0.

## Layout

- `app/` — module source (Java/Kotlin, resources)
- `docs/` — AI manual, contracts, distribution, release notes, licenses
- `tools/` — build/release helpers; host APK and keys are supplied by the user
- `LICENSE` / `NOTICE.md` — GPL-3.0 and attribution

## Build (summary)

You need JDK 21, Android SDK 37, your own Alipay host APK, your own keystore,
and the build tools listed in `BUILDING.md`. No host APK or key is included.

```bash
./gradlew :app:testNormalDebugUnitTest
# Public build (API 101, Medusa dir, new installs default off)
./gradlew :app:assembleNormalRelease -Pversion=5.11.0
# Author build (API 102, sesame-M dir, existing defaults)
./gradlew :app:assembleNormalRelease -Pversion=5.10.1 -PownerBuild=true
```

`tools/release.sh 5.11.0` documents the full module → npatch host → sign →
delivery flow; add `--owner` for the author package. The patched host is
**your** artifact; do not redistribute it.

## License

GPL-3.0. See `LICENSE` and `NOTICE.md`. Upstream Sesame attribution is kept
in `NOTICE.md` and `docs/licenses/`.

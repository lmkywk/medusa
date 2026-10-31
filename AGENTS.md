# AGENTS.md

This file is the bootstrap contract for a local AI agent maintaining Medusa.
Read it before changing code, configuration, or plans.

## What this repository is

- Source-only repository for the Medusa Alipay task module.
- The Alipay **host APK**, patched host outputs, signing keys, user data,
  logs, tokens, and AI provider keys are **not** included and must never be
  committed.
- The module package is `com.medusa.alipay`; the Xposed scope is
  `com.eg.android.AlipayGphone`.
- License: GPL-3.0. Keep upstream attribution in `NOTICE.md`.
- Only this repository and its release tags are the authoritative source.
  Forks and repackaged APKs are not official.

## Read order

1. `README.md` — distribution, update, and fingerprint rules.
2. `docs/AI-MAINTENANCE-MANUAL.md` — architecture, diagnosis, patch, test,
   rollback.
3. `docs/AI-HANDOFF.md` — compact local-AI → development-AI handoff packet.
4. `docs/TASK-RUN-EVENT.md` — leaf event schema.
5. `docs/P0-contracts.md` — RPC/task/ledger contracts.
6. `docs/DISTRIBUTION.md` — APK and framework-market release rules.
7. `PROVENANCE.md` — official tag, certificate, APK hash, fingerprint.

## Safety rules

- Never commit or upload the host APK, npatch output, signing keys,
  `keystore.properties`, `local.properties`, tokens, user accounts, logs, or
  ledger/config data.
- Keep user data local. The handoff packet is generated on-device and is the
  only diagnostic artifact intended to leave the device.
- Never bypass risk controls, 1009/1004 handling, or verification gates to
  make a task pass.
- Never fake payment, order, evaluation, donation, or external-app actions.
  Real actions stay manual.
- Prefer configuration/plan changes over code changes. A task that cannot be
  classified is recorded as a sample, not force-finished.
- Do not change the signing key unless the user explicitly asks; changing it
  breaks updates and invalidates the fingerprint.

## Build and test

```bash
# JDK 21 and Android SDK 37 are required.
./gradlew :app:testNormalDebugUnitTest --offline
# 公开版：API 101 / Medusa 目录 / 新装任务开关默认关闭
./gradlew :app:assembleNormalRelease --offline -Pversion=5.11.0
# 作者版：API 102 / sesame-M 目录 / 默认开关不变
./gradlew :app:assembleNormalRelease --offline -Pversion=5.10.1 -PownerBuild=true
```

Official release:

```bash
bash tools/release.sh 5.11.0            # public
bash tools/release.sh 5.10.1 --owner    # author only
```

`tools/release.sh` generates `META-INF/xposed/module.prop` from
`app/module.prop.template`, checks the version/API profile, builds the module,
patches the host with npatch, signs, and writes the delivery directory plus
SHA-256 sums.
The host APK and BKS keystore are local inputs and are never published.

## Update procedure

1. Read the latest release notes and `PROVENANCE.md`.
2. Check out the official tag; keep local data and configuration.
3. Run unit tests and a device canary on a small target.
4. Build the module; verify `moduleVersion` and `fingerprint` in
   `system_status.json` and `ai/handoff-<date>.json`.
5. Install the module (and a rebuilt host only if the host layer changed).
6. Keep a rollback path: previous APK, previous tag, and a data backup.
7. Report old/new tag, fingerprint, test target, and result.

## Reporting

Use `ai/handoff-<date>.json` as the primary evidence. Include the
`fingerprint`, `moduleVersion`, failing target/task, endpoint, outcome, and
counts. Do not paste raw logs, account IDs, or friend lists into public
issues.

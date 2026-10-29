# Provenance and fingerprint

This repository is the **only authoritative source** for Medusa. Forks,
repackaged APKs, and module-market copies that do not match the values below
are not official.

## Release identity

| Field | Value |
|---|---|
| Product | Medusa |
| Module package | `com.medusa.alipay` |
| Public version | `5.9.0` |
| `versionCode` | `59000` |
| Release tag | `v5.9.0` |
| Build commit | `0fda2ce` (source repository) |
| Build fingerprint | `medusa-5.9.0-0fda2ce` |
| Release certificate SHA-256 | `0699038ea17842a49eaec941360e33723d1a547ad0c82f36932322265c986016` |
| Module APK (arm64) SHA-256 | `ebba1fd0c30d9157311128f64365de85b62837aaed0d2538804e9a277db057ea` |
| Module APK (universal) SHA-256 | `3a84aec2164a2997de3b562b8a9025bff24db893923ba6fa9a42233bf2cd568a` |
| Host APK | **not published**; user-supplied |
| Signing key | **not published**; never commit it |

## What the fingerprint means

The official build embeds:

```text
MEDUSA_FINGERPRINT = medusa-5.9.0-0fda2ce
```

It is written to:

- `system_status.json` → `process.fingerprint`
- `ai/handoff-<date>.json` → `fingerprint`

A user or agent should compare the in-app value with this file before
installing or reporting a problem. If it does not match, treat the build as
unofficial.

## How to verify an APK

```bash
sha256sum Medusa-V5.9.0-module-arm64.apk
apksigner verify --print-certs Medusa-V5.9.0-module-arm64.apk
```

Compare the APK hash and certificate SHA-256 with the table above. Do not
install on a mismatch.

## Build reproducibility

The module is built from the official tag with JDK 21, Android SDK 37, and the
project release key. The host APK, keystore, BKS file, and signing passwords
are local inputs and are intentionally absent from this repository.

## Privacy

The repository contains source and documentation only. It contains no real
account UID, nickname, device serial, friend list, task ledger, logs, tokens,
or AI provider keys. Test fixtures use `2088000000000000` / `演示账号`.

# Provenance and fingerprint

This repository is the **only authoritative source** for Medusa. Forks,
repackaged APKs, and module-market copies that do not match the values below
are not official.

## Release identity

| Field | Value |
|---|---|
| Product | Medusa |
| Module package | `com.medusa.alipay` |
| Public version | `5.11.0` |
| `versionCode` | `60100` |
| Release tag | `v5.11.0` |
| Build commit | `465e3ed` (private build mirror) |
| Public snapshot | tag `v5.11.0` in this repository (authoritative source) |
| Build fingerprint | `medusa-5.11.0-465e3ed` |
| Release certificate SHA-256 | `0699038ea17842a49eaec941360e33723d1a547ad0c82f36932322265c986016` |
| Module APK (arm64) SHA-256 | `97ecf7a5f5c6c02e7016285d9467e5ddec0c8ca88f6ec2eca1042bf987bff0e8` |
| Module APK (universal) SHA-256 | `f52350b4cf4c19e8a664de8d2f8d664b6a35f543b084f6df1a2f1dedb50a5a21` |
| Host APK | **not published**; user-supplied |
| Signing key | **not published**; never commit it |

`465e3ed` is the private build-mirror commit used to produce the official
binary. The public `v5.11.0` tag is the authoritative source snapshot; its
module code is identical. Verify authenticity with the APK hash and signing
certificate above, not by guessing a private commit.

## What the fingerprint means

The official build embeds:

```text
MEDUSA_FINGERPRINT = medusa-5.11.0-465e3ed
```

It is written to:

- `system_status.json` → `process.fingerprint`
- `ai/handoff-<date>.json` → `fingerprint`

A user or agent should compare the in-app value with this file before
installing or reporting a problem. If it does not match, treat the build as
unofficial.

## How to verify an APK

```bash
sha256sum Medusa-V5.11.0-module-arm64.apk
apksigner verify --print-certs Medusa-V5.11.0-module-arm64.apk
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

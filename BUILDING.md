# Building Medusa

This repository contains only the module source. You must supply your own
Alipay host package and your own signing key.

## Requirements

- JDK 21
- Android SDK platform 37 and build-tools 37.0.0
- Gradle wrapper (included)
- NPatch 1.0.9 (GPL-3.0) if you want to generate a patched host APK
- Your own legally obtained Alipay host APK
- Your own keystore; never commit it

## Local files (not committed)

Create these locally; they are git-ignored:

`local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

`keystore.properties`:

```properties
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Place your host APK at the path expected by the release script, or set
`ORIGIN_APK` explicitly:

```bash
export ORIGIN_APK=/path/to/your/origin.apk
export NPATCH_JAR=/path/to/npatch-1.0.9.jar
```

## Build the module

```bash
./gradlew :app:testNormalDebugUnitTest
./gradlew :app:assembleNormalRelease -Pversion=4.1.22
```

Outputs are under `app/build/outputs/apk/normal/release/`.

## Build a patched host (optional)

`tools/release.sh` documents the full flow (module APK → npatch → sign →
delivery). It expects a host APK and a BKS keystore configured locally.
The patched host is **your** artifact; do not redistribute it.

```bash
bash tools/release.sh 4.1.22 --skip-tests
```

## Install / rollback

- Back up the host data directory before uninstalling.
- Uninstalling the host can delete its media/config directory.
- A new signing key means uninstall/reinstall; plan a data backup/restore.
- Keep the previous module APK and `config_v2.json` / `plans.json` for rollback.

## Licensing

The module is GPL-3.0. NPatch is GPL-3.0. Alipay is proprietary; this
repository does not distribute it.

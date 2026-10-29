# Distribution

Medusa has two supported delivery paths. Both ship the **module only**; the
Alipay host APK is never redistributed.

## 1. Download the module APK

Attach these assets to the official GitHub Release:

- `Medusa-V5.9.0-module-arm64.apk`
- `Medusa-V5.9.0-module-universal.apk`
- `SHA256SUMS.txt`

Release tag: `v5.9.0`. The APK is signed with the project's release key; its
certificate SHA-256 is recorded in `PROVENANCE.md`. The host APK and signing
key are not in the release.

Users install the module in their Xposed/LSPosed manager, patch their own
host, and verify the fingerprint before use.

## 2. Framework / module market

The module follows the standard Xposed/LSPosed layout:

```text
app/src/main/resources/META-INF/xposed/module.prop
app/src/main/resources/META-INF/xposed/scope.list
app/src/main/resources/META-INF/xposed/java_init.list
```

`module.prop` contains the fields scrapers and module managers read:

```properties
id=medusa
name=Medusa
version=5.9.0
versionCode=59000
author=Medusa Contributors
description=Decentralized, agent-maintained assistant for Alipay daily tasks. Source-only; the Alipay host APK is not included.
minApiVersion=102
targetApiVersion=102
staticScope=true
exceptionMode=protective
autoHotReload=false
```

Rules that keep repository/market scraping reliable:

- keep `version` and `versionCode` in `module.prop` identical to the release
  tag and APK `versionName`/`versionCode`;
- keep the release tag and asset names stable (`v5.9.0`,
  `Medusa-V5.9.0-module-*.apk`);
- keep `scope.list` limited to `com.eg.android.AlipayGphone`;
- keep `java_init.list` pointing to
  `io.github.aw1y2z.sesame.hook.ApplicationHook`;
- put the module APK, not the host, in the release;
- do not upload signing keys, `keystore.properties`, user data, logs, or
  tokens to any market;
- include the GPL-3.0 source link and a short privacy statement in the market
  listing.

For a module-market submission, use the signed module APK and the release
notes from `docs/release-notes-5.9.0.md`. The host layer remains the user's
responsibility.

## Versioning

- Public release version: `5.9.0`
- `versionCode`: `59000`
- Tag: `v5.9.0`
- Build fingerprint: `medusa-5.9.0-0fda2ce` (official build)
- Release certificate and APK hash: see `PROVENANCE.md`

All public descriptions, APK metadata, release notes, and the in-app
`system_status.json` must use `5.9.0`. Do not reuse an older version number
for a new binary.

## Privacy

The repository and release contain no user account, device serial, friend
list, logs, or ledger data. Do not add screenshots or sample data that reveal
a real account. Test fixtures use `2088000000000000` / `演示账号`.

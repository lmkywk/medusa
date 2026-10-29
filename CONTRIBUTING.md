# Contributing

Medusa is GPL-3.0. By contributing you agree to license your changes under
GPL-3.0 and to keep upstream copyright/license notices.

## Do not include

- host APKs, npatch output, signing keys, `keystore.properties`;
- user accounts, friend lists, tokens, raw logs/status/ledger files;
- AI provider keys.

## Before a PR

1. Run `./gradlew :app:testNormalDebugUnitTest`.
2. Keep changes scoped to a leaf target or a narrow bug.
3. Update `docs/TASK-RUN-EVENT.md` if event fields/enums change.
4. Add or update a test for the failure class, not only the instance.
5. Describe the observed event signature and the device/host version used.

## Event schema changes

`TaskRunEvent` is a public contract for agents. Add fields; do not silently
rename or repurpose existing fields. Bump `schemaVersion` in the event file
when the meaning changes.

## Release tags

Use `vMAJOR.MINOR.PATCH`. A tag must point to a commit that builds from a
clean tree, with the tested host/module versions recorded in the release notes.

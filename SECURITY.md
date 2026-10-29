# Security Policy

## Scope

This project runs inside a third-party host on the user's own device. Treat
all logs, status files, ledgers, and event streams as **local private data**.

## Never commit or upload

- host APKs or patched host outputs;
- keystores, signing keys, `keystore.properties`, `local.properties`;
- account IDs, nicknames, friend lists, tokens, cookies, raw RPC payloads;
- AI provider API keys;
- raw `tasks.*.jsonl`, `events.*.jsonl`, status/ledger files.

Use the sanitizer/tests in `app/src/test` before sharing any replay corpus.

## Agent safety rules

A local agent must:

1. start read-only (events, status, ledger, git diff);
2. propose a diff before editing;
3. never change risk controls, payment/donation switches, or signing/build
   secrets without explicit user approval;
4. run unit tests and a device canary before declaring success;
5. keep a rollback snapshot and tell the user how to restore it;
6. send only aggregated, redacted data to any remote model.

## Reporting

Open a private security advisory or contact the repository owner. Do not post
tokens, account data, or host APKs in public issues.

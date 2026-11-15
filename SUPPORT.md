# Support

## I need help using VaultZero

1. Read [docs/GETTING-STARTED.md](docs/GETTING-STARTED.md) — install, first vault, recovery kit,
   biometrics and backups.
2. Check [docs/FAQ.md](docs/FAQ.md) and [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md).
3. Search existing GitHub issues before opening a new one.

## I found a bug

Open a GitHub issue using the **Bug report** template with: version/build, device + Android
version, steps to reproduce, expected vs actual behavior, and a screenshot if it is safe
(remember the app blocks screenshots by design — describe instead).

## I found a security vulnerability

**Do not open a public issue.** Use
[GitHub private security advisories](https://github.com/cipherwell/Vault-Zero/security/advisories/new)
and follow [SECURITY.md](SECURITY.md).

## I want a feature

Open a **Feature request** issue describing the use case. Features that add network access,
cloud sync, telemetry or weaken cryptographic guarantees will be declined.

## I lost my master password / recovery phrase

There is no recovery mechanism beyond the 12-word recovery kit and the master password. If both
are lost, the vault data cannot be decrypted by anyone, including the maintainer. Restore from an
encrypted backup if you have the matching credential.

## Supported versions

Only the latest production release is supported (`1.1.x`). The debug-signed `1.0.0` preview is
superseded and cannot be updated in place.

This is a single-maintainer open-source project; support is best-effort.

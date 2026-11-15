# VaultZero documentation

Start here. Documents are grouped by intent; every file is self-contained and linked from
[README.md](../README.md) and [SECURITY.md](../SECURITY.md).

## Read by role

### I just want to install and use it
1. [GETTING-STARTED.md](GETTING-STARTED.md) — install, first vault, recovery kit, biometrics, backups.
2. [FEATURES.md](FEATURES.md) — full user guide, search syntax, settings reference.
3. [FAQ.md](FAQ.md) / [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

### I want to verify the security
1. [SECURITY.md](../SECURITY.md) — controls, evidence, limitations, disclosure.
2. [CRYPTO-SPEC.md](CRYPTO-SPEC.md) — algorithms, key hierarchy, record/backup formats.
3. [THREAT-MODEL.md](THREAT-MODEL.md) — assets, adversaries, residual risk.
4. [PENTEST-2026-11-15.md](PENTEST-2026-11-15.md) — self-directed adversarial assessment.

### I want to build, test, release or contribute
1. [BUILDING.md](BUILDING.md) — toolchain, dev flags, device tests.
2. [TESTING.md](TESTING.md) — test matrix, commands, QA checklist.
3. [ARCHITECTURE.md](ARCHITECTURE.md) / [REPO-MAP.md](REPO-MAP.md) — code layout and data flow.
4. [RELEASING.md](RELEASING.md) — signing identity, versioning, Play/F-Droid/GitHub.
5. [CONTRIBUTING.md](../CONTRIBUTING.md) — rules and PR checklist.

### I want design/branding assets
- [DESIGN.md](DESIGN.md) — One UI-inspired tokens, typography, screen inventory, icon.
- [SCREENSHOTS.md](SCREENSHOTS.md) — current screenshots and archive.
- [store/README.md](../store/README.md) — Play listing pack; [fastlane/](../fastlane) — store metadata.

## Complete index

| Document | Purpose |
| --- | --- |
| [GETTING-STARTED.md](GETTING-STARTED.md) | End-user quick start and first-run walkthrough |
| [FEATURES.md](FEATURES.md) | Every screen and feature, search syntax, settings |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Stack, modules, state/data flow, database schema |
| [CRYPTO-SPEC.md](CRYPTO-SPEC.md) | Crypto design and formats (source of truth for implementers) |
| [THREAT-MODEL.md](THREAT-MODEL.md) | Adversaries, assets, mitigations, out-of-scope |
| [BACKUP-FORMAT.md](BACKUP-FORMAT.md) | Envelope schema, import/export semantics, compatibility |
| [BUILDING.md](BUILDING.md) | Build prerequisites, commands, dev flags, device tests |
| [TESTING.md](TESTING.md) | JVM/device test inventory, commands, manual QA matrix |
| [RELEASING.md](RELEASING.md) | Versioning, signing, GitHub/Play/F-Droid release steps |
| [DESIGN.md](DESIGN.md) | Design language, tokens, typography, app icon |
| [FAQ.md](FAQ.md) | Common questions |
| [TROUBLESHOOTING.md](TROUBLESHOOTING.md) | Fixes for install/backup/biometric issues |
| [SCREENSHOTS.md](SCREENSHOTS.md) | Screenshot gallery and archive |
| [REPO-MAP.md](REPO-MAP.md) | Annotated repository tree |
| [PRIVACY.md](PRIVACY.md) | Privacy policy (also [privacy.html](privacy.html)) |
| [PENTEST-2026-11-15.md](PENTEST-2026-11-15.md) | Adversarial test log and findings |
| [AUDIT-2026-11-15-recovery-and-encryption.md](AUDIT-2026-11-15-recovery-and-encryption.md) | Recovery-phrase/encryption audit, fixes and residual risks |
| [index.html](index.html) | GitHub Pages landing page |

## Documentation conventions

- **Version:** documents describe release `1.3.0` unless noted.
- Commands are copy-pasteable from the repository root unless a path is shown.
- Security claims must be reproducible: link to a test, command or source file.
- When behavior changes, update the relevant doc in the same PR (see [CONTRIBUTING.md](../CONTRIBUTING.md)).
- Screenshots live in `screenshots/`; legacy images are under `screenshots/archive/`.

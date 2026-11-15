# Contributing to VaultZero

Thanks for helping improve an offline security tool. VaultZero is a small, security-first
project: correctness, privacy and reviewability matter more than feature volume.

By contributing you agree to the [Code of Conduct](CODE_OF_CONDUCT.md) and license your work
under the project's [MIT license](LICENSE).

## Ground rules (security invariants)

Changes must preserve these invariants. A PR that breaks one is rejected:

1. **No network capability.** No `INTERNET`/`ACCESS_NETWORK_STATE` permission, no networking
   libraries, no telemetry/crash SDKs, no WebView fetches, no cloud APIs.
2. **No plaintext secrets at rest or in logs.** Titles/categories/tags stay inside the encrypted
   record envelope; SQLite columns remain redacted; do not log key material, passwords, TOTP
   secrets or recovery phrases.
3. **Crypto changes require tests and documentation.** Any change to `crypto/`, KDF parameters,
   AAD construction or backup format must update [docs/CRYPTO-SPEC.md](docs/CRYPTO-SPEC.md) and
   [docs/BACKUP-FORMAT.md](docs/BACKUP-FORMAT.md) and add regression tests.
4. **Backups stay SAF-only.** Do not add share sheets, clipboard export, implicit cloud
   destinations, or persistable URI grants.
5. **Release signing.** Never add debug keystores or CI that signs releases with debug keys.
   Release signing uses the production upload key described in [docs/RELEASING.md](docs/RELEASING.md).
6. **Migration safety.** Room migrations must be non-destructive and validated with the
   export-schema/tests described in [docs/TESTING.md](docs/TESTING.md).

If you are unsure whether a change is security-sensitive, open a discussion/issue first.

## Development setup

Requirements: **JDK 17**, Android SDK **36**, Git, a device or emulator (API 24+).

```bash
git clone https://github.com/cipherwell/Vault-Zero.git
cd Vault-Zero
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug builds require a local debug keystore at the repository root (`debug.keystore`,
alias `androiddebugkey`, passwords `android`) because the project follows the AI Studio
signing layout. It is not committed. Generate one if missing:

```bash
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

Device instrumentation, emulator notes and the full QA checklist:
[docs/BUILDING.md](docs/BUILDING.md) and [docs/TESTING.md](docs/TESTING.md).

## Code style

- Kotlin + Jetpack Compose, Material 3 with the project's One UI-inspired token system
  ([docs/DESIGN.md](docs/DESIGN.md)). Do not hardcode colours/blur values; use `ui/theme` tokens.
- State flows in `VaultViewModel`; keep repository/crypto logic out of composables.
- Prefer small, testable pure helpers (`core/`, `crypto/`) and add JVM tests where possible.
- No new dependencies without justification; they must be open-source and offline-safe.
- Keep user-facing strings clear and factual; no unverifiable "military grade" marketing claims.
- Follow existing formatting (4-space indent, trailing commas, descriptive test names).

## Tests required

- **New crypto/backup/import logic:** JVM tests (Robolectric where Android APIs are involved).
- **New UI feature:** at least a screenshot/QA note in the PR; add Compose UI tests when feasible.
- **Security-sensitive change:** update [SECURITY.md](SECURITY.md) evidence table if applicable.
- Run `./gradlew :app:testDebugUnitTest :app:lintDebug` before requesting review; CI will run them
  when enabled.

## Commit & pull request style

- One logical change per PR. Commits: short imperative subject, body explains what/why and
  what was verified. Security fixes should include the threat/failure scenario.
- PR template asks for: summary, threat impact, test evidence, screenshots (if UI), and
  release impact (version bump/backup-format change).
- Do not bump `versionCode`/`versionName` or tag releases in normal PRs; maintainers handle
  release commits per [docs/RELEASING.md](docs/RELEASING.md).
- Do not include APK/AAB binaries or keystores in PRs.

## Documentation

Docs are part of the product. If behavior changes, update the relevant file under `docs/` and
`README.md` when user-facing. Keep instructions reproducible and commands copy-pasteable.

## Security reports

Do not open public issues for vulnerabilities. Use
[GitHub private advisories](https://github.com/cipherwell/Vault-Zero/security/advisories/new)
and see [SECURITY.md](SECURITY.md).

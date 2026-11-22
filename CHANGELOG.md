# Changelog

VaultZero is now maintained under the **Cipherwell** identity. Earlier release history was
intentionally removed during the identity migration.

## [1.7.20] — 2026-11-22

### Fixed
- **Interrupted setup no longer loses the Recovery Kit.** The “kit not yet confirmed” state is written
  atomically with the vault. If the app is killed, locked or restarted before the user confirms the
  kit, the next unlock shows an amber notice and **re-displays the same kit** before entering the
  vault. Confirming it clears the pending state.
- **Stale auto-lock can no longer lock the setup wizard.** Auto-lock is ignored unless the vault is
  unlocked, `clearVault()` cancels its timer, and a reset cannot leave a timer that fires into setup.
- **IME “Done” no longer creates a vault.** The confirm field only hides the keyboard; vault creation
  requires the explicit **Create Secure Vault** button.

### Tests
- 138 JVM/Robolectric tests / 0 failures, including a regression test that the pending kit is set at
  setup, decryptable with the active VMK, and cleared only after confirmation.

## [1.7.19] — 2026-11-22

### Changed
- **Filter chips are a single horizontally scrolling row again** (as originally intended), with a
  subtle circular chevron in a right-hand gutter that signals more filters and scrolls on tap. The
  chevron disappears at the end of the row. Chips use the same filled backgrounds as the Settings
  segmented buttons (surface container, tinted primary when selected).

### Notes
- Reverts the two-row wrap from 1.7.18. 137 JVM tests / 0 failures, lint 0.

## [1.7.18] — 2026-11-22

### Added
- **Reset vault from the lock screen.** A low-emphasis “Reset vault” link opens a destructive
  confirmation explaining that every record, key and biometric enrolment is erased permanently, and
  that it is only for the case where both the Master Password and the Recovery Kit are lost. Spanish
  and English.

### Changed (UI audit)
- Vault filter chips wrap into two even rows instead of scrolling off-screen; compact labels
  (Logins / Cards / APIs / Wallets / Notes / IDs · Accesos / Tarjetas / APIs / Billeteras / Notas / IDs).
- Vault and audit lists get bottom padding so the last row/card clears the navigation bar.
- 2FA row separates the timer with “·” for readability.

### Notes
- 137 JVM tests / 0 failures, lint 0 errors. UI audit report in `docs/design/UI-AUDIT-2026-11-22.md`.

## [1.7.17] — 2026-11-22

### Fixed
- **Spanish recovery-kit button clipped.** “Ya guardé mi kit de recuperación — Abrir bóveda” wrapped to
  two lines but the button was fixed at 48 dp, cutting off “Abrir bóveda”. The button now grows with
  its label (`heightIn(min = 48 dp)`), wraps to two lines with centred text, and keeps the full label.
- **12-word grid spacing.** Long words could break mid-word (“together” → “togethe”/“r”) on narrow
  screens or larger system fonts. The grid keeps three columns and now sizes each word to its cell
  (accounting for font scale), with single-line words and even spacing.

### Notes
- No cryptographic or data changes. 137 JVM tests / 0 failures, lint 0 errors.

## [1.7.16] — 2026-11-22

### Added
- **Biometric unlock can be enabled while creating the vault.** A switch on the setup screen (with a
  clear hint when the phone has no fingerprint/face enrolled) asks for the biometric prompt right
  after the recovery kit is saved and the vault opens.
- **Automatic biometric prompt on every open.** When biometrics are enabled, returning to the app (or
  an auto-lock while it is open) shows the system prompt again instead of waiting for a tap.

### Changed
- **Stronger save warning for the generated master password.** The dialog now requires checking
  "I saved this password in a safe place" before it can be dismissed, and an amber reminder stays on
  the setup screen until the vault is created.

### Notes
- No cryptographic changes. 137 JVM tests / 0 failures, lint 0 errors.

## [1.7.15] — 2026-11-22

### Added
- **Generate a master password during setup.** A sparkle button inside the Master Password field
  creates a 20-character password with the device CSPRNG, fills both fields, reveals it, and opens a
  "save it now" dialog with a copy button and the recovery-kit warning. Tap **I saved it** to hide the
  fields again. Available in English and Spanish.

### Notes
- No cryptographic changes: the generated password still derives the key exactly like a typed one.
- 137 JVM/Robolectric tests / 0 failures, lint 0 errors.

## [1.7.14] — 2026-11-22

### Documentation
- **Bilingual user guide** (`docs/guide/`): English and Spanish Markdown + A4 PDFs, covering every
  feature in plain language, how the security works, an honest comparison with cloud managers, what
  the app cannot protect against, backups/migration, installation and the audit summary. Includes a
  social share kit.
- **Corrected in-app cryptographic specifications.** The Settings dialog still described PBKDF2
  600,000 rounds as the current KDF; it now states Argon2id (64 MiB, t=3, p=1) with PBKDF2 described
  as the legacy envelope format, plus the per-record AAD binding and BIP-39 recovery phrase.

### Notes
- No functional or cryptographic changes; only the specification strings (EN/ES) and documentation.

## [1.7.13] — 2026-11-21

### Security
- **Bounded backup reads.** `BackupFileIo.readText` now stops at the 8 MB ceiling instead of calling
  `readBytes()` and checking afterwards; a malicious or oversized document can no longer OOM the app
  (`OutOfMemoryError` is not caught by `catch (Exception)`).
- **Guarded backup JSON parsing.** A 20k-deep backup threw `StackOverflowError` from `org.json`,
  which escaped the import's `catch (Exception)` and killed the process. `BackupJson.parse` now
  pre-scans nesting depth (64, string-aware) and maps any overflow to `VaultError.INVALID_JSON`.
- Added `security-audit/` (MASVS 2.1 kit: methodology, checklists, scripts, fuzz corpus generator,
  report renderer) and the full audit report at `docs/security/AUDIT-2026-11-21.md`.
- 6 new regression tests for the import attack surface.

### Notes
- No cryptographic or data-format changes. Both issues were DoS-only; no data exposure.

## [1.7.12] — 2026-11-21

### Fixed
- **Dark mode no longer casts card shadows.** On true black they were invisible at best; the white
  rim could also show through translucent cards and stamp the shadow layer's bounds as a grey
  rectangle. Dark-mode separation is now the lifted surface plus the hairline border only.
- **Card containers are always opaque.** `VaultCard` composites its container over the background,
  so a translucent card can no longer pick up a blue/navy tint or a shadow bleed from behind it.

### Notes
- Light mode keeps its soft 10 dp shadow. No functional or cryptographic changes.

## [1.7.12] — 2026-11-21

### Fixed
- **Dark cards are neutral grey (`#1C1C1E`) and opaque.** They no longer inherit a caller's
  near-black or translucent container, which is what produced the navy tint on the lock screen.
  `VaultCard` normalises every dark card to `surfaceContainerHigh`.
- **Dark mode casts no card shadows.** On true black they were invisible at best; the white rim
  could also show through translucent cards and stamp the shadow layer's bounds as a grey rectangle.

### Notes
- Light mode keeps its soft 10 dp shadow and its white/grey containers. No functional or
  cryptographic changes.

## [1.7.11] — 2026-11-21

### Changed
- **Subtle elevation across the app.** All 19 card containers (8 screens, including dialogs) now go
  through a single `VaultCard`: a 10 dp soft drop shadow in the light theme and a faint white rim in
  the dark theme, where a black shadow would vanish against true black. Hairline borders stay the
  primary edge, so nothing turns heavy.
- Light shadow: ambient black 10%, spot black 16%. Dark rim: white 12% / 18%, measured ≈6/255 at the
  card edge and fading over ~35 px — visible as separation, not as a glow.
- Buttons, chips and list rows deliberately cast no shadow.

### Notes
- No functional or cryptographic changes.

## [1.7.10] — 2026-11-21

### Fixed
- **Spanish biometric row clipped in Settings.** The Spanish description
  ("Desbloquea rápidamente con la biometría del dispositivo") pushed the biometric switch past the
  content edge (switch right edge 1048 px on a 1080 px screen, content ends at 1027 px). The text
  column is now weighted, wraps to two lines and the switch always stays inside the row.
- **Lock-screen biometric button hardened:** adaptive minimum height (48 dp), single line with
  centred ellipsis fallback, and shorter Spanish labels (`Huella / Biometría`, 19 chars, was 27;
  `Huella / Biometría (desactivada)`, 33, was 40).

### Notes
- No functional or cryptographic changes.

## [1.7.9] — 2026-11-21

### Changed
- **Samsung One UI-inspired icon.** Vertical gradient, a single white glyph and a **diffuse shadow**
  replace the flat offset shadow and sparkles. The V is now a clean background-coloured cutout, and
  the Z bit is smaller (13 × 14, 5.5 stroke) so it sits in proportion with the bow and shaft.
- Shadow is a pre-blurred 432 px bitmap under the vector key (`layer-list` foreground), so it stays
  soft at every density instead of scaling as a hard edge.
- The V cutout is backfilled with the exact background gradient, so the shadow cannot bleed through
  the hole and turn it muddy.

### Notes
- No functional or cryptographic changes.

## [1.7.7] — 2026-11-21

### Fixed
- **Key icon proportions.** The 1.7.6 bit was a 32 × 13.5 stripe; it is now a 16 × 18 Z with a longer
  shaft and a smaller bow, matching a real key (and a real capital Z). Everything still fits inside
  the 66 dp safe circle.

### Notes
- No functional or cryptographic changes.

## [1.7.6] — 2026-11-21

### Changed
- **The key icon gets personality.** Same V-bow / Z-tip structure, now treated like a product logo:
  a flat offset shadow for depth, a cyan **V** in the bow, two sparkles, and tighter proportions.
  All geometry is scaled into the 66 dp safe circle so no launcher mask clips the sparkles.

### Notes
- No functional or cryptographic changes.

## [1.7.5] — 2026-11-21

### Changed
- **New icon: a key whose bow is a V and whose tip is a Z.** One silhouette carries the initials —
  the bow (the part you hold) is a loop with a V-shaped opening, the bit is drawn as a Z. Same
  adaptive construction as before: 66 dp safe-zone foreground, separate monochrome layer, legacy
  squircle/round PNGs, regenerated 512 px icon and feature graphic.

### Notes
- No functional or cryptographic changes. Supersedes the withdrawn 1.7.3 fingerprint concept.

## [1.7.4] — 2026-11-21

### Fixed
- **Reverted the launcher icon to the v1.7.2 keyhole.** The 1.7.3 fingerprint/zero mark read as a
  spiral or target at launcher size, especially under circular masks. The icon is back to the
  zero-shaped keyhole while a better fingerprint direction is designed.

### Notes
- No functional or cryptographic changes; icon assets are byte-identical to 1.7.2.

## [1.7.3] — 2026-11-21

### Changed
- **New icon: a zero holding a fingerprint.** The ring is the zero (zero-knowledge); the whorl
  inside (two ridges plus a core, gaps aligned on a lower-left delta) is biometrics. Same adaptive
  construction as 1.7.2: safe-zone foreground, separate monochrome layer for themed icons, legacy
  squircle/round PNGs, regenerated 512 px store icon and feature graphic.
- Added a **wordmark lockup** (`design/icon/vaultzero-icon-lockup.svg`) for store and social
  artwork. The app name is deliberately not drawn inside the adaptive icon: text at launcher size
  is unreadable and would break the safe zone.

### Notes
- No functional or cryptographic changes.

## [1.7.2] — 2026-11-21

### Changed
- **New launcher icon.** A zero-shaped keyhole drawn as proper adaptive layers: full-bleed gradient
  background, a foreground glyph kept inside Android's 66 dp safe zone (so no launcher mask clips
  it), and a dedicated single-colour **monochrome layer** for Android 13+ themed icons. Legacy
  squircle/round PNGs at every density and a 512 px store icon were regenerated from the same
  vector source. The previous icon baked a squircle into the foreground and reused the coloured
  layer as its monochrome mask.
- Matching feature graphic, F-Droid repository icon and fastlane artwork.
- `design/icon/` holds the source geometry generator, SVGs and a preview sheet, so the mark can be
  regenerated deterministically.

### Notes
- No functional or cryptographic changes.

## [1.7.1] — 2026-11-21

### Added
- **Copy diagnostics** in Settings → About: app version, device, Android level, language, vault
  state, biometric state and the last error — never titles, secrets or key material. Gives beta
  testers a usable bug-report path while the app intentionally ships no telemetry and blocks
  screenshots.
- `docs/BETA.md` (tester guide + known limitations), `docs/SOCIAL-BETA-POST.md` (EN/ES announcement
  drafts) and `docs/PLAY-INTERNAL-TESTING.md` (maintainer checklist).

### Fixed
- Android biometric wording no longer says "Touch ID" (iOS branding): "Fingerprint / Biometrics",
  "Huella digital / Biometría".

### Security
- No cryptographic changes. The diagnostics payload is built from a pure, unit-tested function and
  is only generated when the user taps the button.

### Verified on a physical Pixel 9 Pro XL (Android 17)
- 15/15 instrumentation tests pass, including the four biometric key-binding tests that need an
  enrolled fingerprint (unavailable on the test emulator).
- Unlock cost: Argon2id 64 MiB/t=3 1.17-1.46 s vs PBKDF2 600k 2.25-2.31 s on the same device.

## [1.7.0] — 2026-11-21

### Added
- **Bilingual English (US) / Spanish (US)** interface. Every screen, dialog, empty state,
  accessibility description, status message and validation error is translated; 477 strings and
  19 plurals per language, tested for key/placeholder parity in CI.
- **Language switch on the lock/setup screen**, so a first-time user can read the setup flow in
  their language before any vault exists.
- **Language switch in Settings**, applied instantly through a localized composition — no activity
  recreation, so an unlocked vault is not dropped back to the lock screen.
- Languages are persisted outside the vault preferences: the choice survives app restarts and
  "Reset vault", and Android 13+ lists the app in the system per-app language picker
  (`android:localeConfig`).

### Changed
- `versionCode 9`, `versionName 1.7.0`.
- Data-layer failures now carry a typed error code (`VaultError`) so the UI can render them in the
  selected language while logs and tests keep the English message.
- Password-strength labels, record-type names, sort options and audit findings are resolved through
  resources instead of model-enum English strings.

### Security
- No change to the cryptographic design; Argon2id envelopes, backups and migrations are untouched.

## [1.6.0] — 2026-11-21

### Security
- **Argon2id key derivation** (RFC 9106, version 1.3) replaces PBKDF2-HMAC-SHA256 for every new
  password and recovery-phrase envelope: 64 MiB memory, 3 iterations, 1 lane, 256-bit tag, 128-bit
  random salt. Pure-Java Bouncy Castle implementation, pinned and verified against the RFC 9106
  known-answer vector in the test suite.
- Every envelope now records its own KDF descriptor (`argon2id:<mKiB>:<t>:<p>` or
  `pbkdf2-sha256:<iterations>`), so password and recovery envelopes can never drift apart when one
  of them rotates or is restored from an older backup.
- **Transparent migration:** a successful unlock of a PBKDF2 vault (or of a vault restored from a
  3.2-era backup) re-wraps both envelopes with fresh salts under Argon2id in one atomic commit. The
  vault master key and every encrypted record stay untouched.
- Backup format `3.3` carries both KDF descriptors. `3.2` and older backups still import and are
  treated as PBKDF2. Import validates algorithm and parameters against the supported ranges before
  any derivation, so a crafted file cannot force a 1 GiB allocation or long CPU burn.

### Changed
- `versionCode 8`, `versionName 1.6.0`; runtime dependency on `org.bouncycastle:bcprov-jdk18on:1.81`
  (Argon2id only; no provider is registered).

### Upgrade notes
- Existing vaults open with the same master password or recovery phrase and migrate in place.
- Downgrading to 1.5.0 or older is not supported once an envelope has migrated to Argon2id.

## [1.5.0] — 2026-11-15

### Changed
- Application ID moved from `com.aistudio.vaultzero.secvlt` to **`com.cipherwell.vaultzero`**,
  completing the Cipherwell identity migration. This is a new Android package; the old package can
  be uninstalled after migrating data through an encrypted backup.
- F-Droid metadata file renamed to `com.cipherwell.vaultzero.yml`; store/privacy/docs links updated.

### Security
- Same Cipherwell signing identity, AES-256-GCM vault format, BIP-39 recovery kits, verified
  backups and offline posture as 1.4.0.
- 105 JVM tests / 0 failures; lint clean.

### Upgrade notes
- `com.cipherwell.vaultzero` installs alongside the old package. Export an encrypted backup +
  recovery phrase from the old app, install v1.5.0, restore, then uninstall the old package.
- v1.4.0 and earlier GitHub releases were removed in favour of the new package identity.

## [1.4.0] — 2026-11-15

### Changed
- New **Cipherwell** project identity and a new production signing key (`CN=Cipherwell`).
  Existing installs must be uninstalled/reinstalled; export a backup first.
- Repository, documentation, F-Droid metadata and release assets rebuilt under the new identity.

### Security
- BIP-39 128-bit recovery kits with unique words; legacy 136-word kits still accepted and rotatable
  in Settings.
- AES-256-GCM records with AAD binding, PBKDF2-HMAC-SHA256 (600,000), hardware per-use biometrics.
- First-run restore from encrypted backup with the existing recovery phrase.
- Verified, duplicate-safe backup import; SAF-only backups; no network permission or telemetry.
- 105 JVM tests / 0 failures; lint clean.

### Upgrade notes
- v1.4.0 is signed with a new key; it cannot update v1.3.1 or older builds in place.
- Export an encrypted backup + recovery phrase, uninstall the old app, install v1.4.0, restore.
- Future releases continue under the Cipherwell signing identity.

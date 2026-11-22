# Threat model — VaultZero (offline Android vault)

Complete this before testing. Revisit when the app gains a backend, sync, widgets, or share targets.

## 1. Assets

| Asset | Where it lives | Impact if compromised |
| --- | --- | --- |
| Master password | user's head; transiently in Compose state | total vault compromise + offline brute force of backups |
| Vault Master Key (VMK) | memory while unlocked; wrapped in envelopes on disk | decryption of every item |
| Item ciphertext | Room DB (`vault.db`), prefs envelope | disclosure, integrity loss |
| Recovery phrase (BIP-39) | paper/user; transiently in UI | permanent vault takeover |
| Backup files | user-chosen SAF location | offline attack target, cross-device transfer |
| Biometric-wrapped key | Android Keystore + prefs | unlock without password on that device |
| Audit/diagnostics data | local, copy-to-clipboard | metadata leakage |
| Signing key | offline release env | malicious update distribution |

## 2. Trust boundaries

1. **App process ↔ OS**: sandbox, Keystore, biometric prompt, clipboard, intents.
2. **App ↔ other apps**: exported components, content providers, implicit intents, overlays,
   accessibility, share targets, clipboard listeners.
3. **App ↔ user-supplied files**: backup import, recovery phrase, any future import/share.
4. **App ↔ device storage**: private files, backups, screenshots, recents preview, auto-backup.
5. **Build ↔ release channel**: signing key, reproducible builds, update mechanism.

## 3. Attacker profiles

| Profile | Capabilities | Primary tests |
| --- | --- | --- |
| A1 Malicious co-resident app | intents, clipboard read, overlay windows, accessibility, notifications | exported components, tapjacking, clipboard lifetime, screenshots |
| A2 Device thief (offline) | physical possession, unlocked bootloader or root, offline envelope extraction | KDF strength, backup format, Keystore binding, auto-lock, memory |
| A3 Malicious backup author | crafts `.vaultzero` JSON, filenames, sizes | parser robustness, KDF downgrade, path traversal, DoS, UI spoofing |
| A4 Shoulder surfer / recorder | sees screen, recents, notifications | FLAG_SECURE, recents snapshot, notification content, clipboard toast |
| A5 Supply chain | dependency or build compromise | dependency versions, build reproducibility, signatures |
| A6 Insider/CI | signing key, release pipeline | key custody, checksum publication, release provenance |

## 4. STRIDE pass

| STRIDE | Concrete concern | Test |
| --- | --- | --- |
| Spoofing | fake biometric prompt / overlay captures password | overlay + tapjacking checks; biometric prompt via system only |
| Tampering | modify envelope KDF spec, ciphertext, AAD, DB rows | integrity checks, downgrade tests, bit-flip tests |
| Repudiation | no audit trail of sensitive actions | acceptable for offline vault; check diagnostics do not log secrets |
| Information disclosure | screenshots, recents, logs, clipboard, world-readable files | FLAG_SECURE, logcat, clipboard TTL, file modes |
| Denial of service | huge backup, deep JSON, zip bomb, memory exhaustion | fuzz corpus + limits |
| Elevation of privilege | exported components, intent redirection, pending intents | manifest inspection + `am start` attacks |

## 5. Abuse cases (must have a verdict)

1. Another app launches `MainActivity` with crafted extras — does anything leak or change state?
2. Another app reads the clipboard after copying a password and after the clear timeout.
3. A screen recorder captures the unlocked vault.
4. A crafted backup with `argon2id:8:1:1` KDF spec is imported — is it rejected or accepted?
5. A 10 GB / deeply nested / NaN-laden backup is imported — crash, ANR, or clean error?
6. Backup file name `../../databases/vault.db` is chosen on export — where does it land?
7. Wrong password 100× — timing, lockout, error differentiation.
8. Biometric prompt cancelled/failed — does it fall back to plaintext or stay locked?
9. Device re-enrols a fingerprint — is the Keystore key invalidated?
10. App is backgrounded during unlock — does the recents preview show secrets?
11. Rooted device with Frida — can the unlocked VMK be read from memory? (document as accepted
    physical-access risk if not mitigated)
12. Kill the process while saving an item — is the DB consistent (WAL) or is data lost?

## 6. Accepted risks (fill in during triage)

| Risk | Why accepted | Owner | Date |
| --- | --- | --- | --- |
| Root/Frida memory inspection of an unlocked process | physical access + no hardware attestation model | | |
| Offline brute force if master password is weak | user choice; KDF raises cost | | |

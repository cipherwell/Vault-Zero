# FAQ

**Is VaultZero free?**
Yes. MIT licensed, no accounts, no purchases, no ads. Source:
https://github.com/cipherwell/Vault-Zero.

**Does it need internet access?**
No. It declares no `INTERNET` permission and contains no networking code. It cannot phone home,
sync, or check for updates; download releases manually.

**Where is my data stored?**
In app-private storage: an encrypted SQLite database, plus wrapped key material/settings in
private preferences. Titles/categories/tags are ciphertext inside each record. Nothing is sent
anywhere unless you export a backup.

**Can I sync between devices?**
Not automatically. Export an encrypted backup and import it on the other device using the vault's
master password or 12-word recovery phrase. Cloud folders work only as file storage for the
encrypted file — VaultZero never uploads anything itself.

**I forgot my master password. What now?**
Use the 12-word **Emergency Recovery Kit** on the lock screen ("Use Emergency Kit") to set a new
master password. Without the phrase, the vault cannot be decrypted by anyone. There is no reset.

**How do backups work, and are they readable?**
Backups are AES-256-GCM encrypted JSON envelopes. Restoring needs the source vault's master
password **or** its 12-word phrase. The optional "12-word phrase only" mode removes the password
envelope, so only the phrase opens it. Out of the app, the file is ciphertext; titles/secrets are
inside encrypted payloads.

**Do backup files break when I change my master password?**
No. Standard backups remain openable with the old password or the unchanged recovery phrase.
Phrase-only backups open with the recovery phrase. Records are not re-encrypted by a password change.

**How do I move my vault to a new phone?**
Export an encrypted backup on the old phone. On the new phone, install VaultZero, tap **Restore
from Backup** on the first-run screen, pick the backup, and enter the old phone's 12-word recovery
phrase. Your existing kit and key fingerprint are preserved — no new seed phrase is created.
Standard backups keep the original master password unless you set a new one; phrase-only backups
require a new local password. Biometrics must be re-enabled on the new device.

**Can biometrics unlock my backup on another phone?**
No. Biometrics are device-local and cannot decrypt a foreign file; the phrase/password is the
portable credential. Biometrics simply unlock this device's vault key through a hardware gate.

**Why does the fingerprint prompt appear automatically?**
It is the opt-in convenience: when biometric unlock is enabled, the app asks once per lock on
launch/resume. Cancel it and use the password. Disable it in Settings.

**Why did biometrics stop working?**
Re-enrolling fingerprints/changing the lock screen invalidates the hardware key. Unlock with the
master password and re-enable biometric unlock.

**Why did my old recovery phrase contain repeated words?**
Kits generated before v1.3.0 used a custom 136-word list and sampled 11 words with replacement, so
repeats occurred in ~34% of phrases and entropy was only ≈78 bits. New kits use the standard
BIP-39 2048-word list (128-bit entropy) and never repeat a word. Existing phrases still work; you
can replace one with **Settings → Generate New Recovery Kit**.

**Why are screenshots black?**
`FLAG_SECURE` blocks screenshots/screen recording/recents previews by design. Dev builds can opt
out with a Gradle flag for automation only; never distribute those.

**Can I turn auto-lock or clipboard clearing off?**
Yes: Settings → Security Controls has Off for both. Clipboard Off still wipes the app's own cached
secret when the vault locks. Background lock can be Off/15/30/60 s; screen-off locks immediately
when enabled.

**Why did my import say "duplicates skipped"?**
Imports compare record ids and a content fingerprint (type/title/category/tags/payload). Re-imports
are skipped; same-id changed records update in place. New records from merged files are added.

**Can I import a damaged backup?**
Strict verification rejects the file and writes nothing. The app then offers "Import readable
records", which imports only authenticated records and reports skipped ones.

**Does it support 2FA/TOTP?**
Yes for logins: paste a base32 secret in the editor; live codes appear in the item/list and in the
2FA dashboard. No QR scanning, no network time sync.

**Is there a browser extension / desktop app?**
No. Android only.

**What happens on uninstall?**
Local data (DB, prefs, Keystore wrapping keys) is removed. Exported backups remain wherever you
saved them. Export before uninstalling/upgrading signatures.

**Why must I uninstall the v1.0.0 preview?**
It was debug-signed (non-production key). Android blocks updates with a different signature;
uninstall and install the current release, then restore your backup.

**Does it work on rooted devices?**
It runs, but root/OS compromise can read memory and bypass app protections. Rooted devices are
outside the threat model.

**Has it been audited?**
A self-directed pentest is documented (`docs/PENTEST-2026-11-15.md`); an independent audit is
still recommended. See [SECURITY.md](../SECURITY.md).

**Which Android versions?**
Android 7.0 (API 24) and newer. Biometric hardware features depend on the device.

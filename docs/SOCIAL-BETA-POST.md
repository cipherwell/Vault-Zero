# Social media beta announcement — drafts

Use the **soft beta** framing: small, invite-style, test data only, no overclaiming. Replace
`<VERSION>`, `<SHA256>` and the link at publish time. Keep the pinned comment with hashes.

Guardrails baked into these drafts (keep them if you edit):

- "beta", "test data only", "not audited yet" — honest, and it filters for better testers.
- Never say "secure your passwords with this" or "unhackable".
- Always link the GitHub release, not a file host.
- Always publish the SHA-256 + signing certificate fingerprint.

---

## English — short (X/Twitter, Mastodon, Threads)

> VaultZero is a fully offline, zero-knowledge password manager I've been building: no account, no
> cloud, no `INTERNET` permission, no telemetry. Argon2id vaults, AES-256-GCM, BIP-39 recovery kits,
> encrypted backups, English/Español.
>
> I'm opening a **small beta** on Android. Test-data only — this is pre-audit software.
> APK + hashes: https://github.com/cipherwell/Vault-Zero/releases/latest
> Not on Google Play, by design. Automatic updates via Obtainium, or manual APK installs.
> Guide: https://github.com/cipherwell/Vault-Zero/blob/main/docs/BETA.md
>
> Built in the open, MIT. Feedback very welcome. 🐾
>
> #Android #InfoSec #Privacy #PasswordManager #FOSS #OpenSource

---

## English — long (LinkedIn / Reddit r/androidapps / r/privacy)

> **VaultZero — an offline, zero-knowledge password manager. Looking for a few careful beta testers.**
>
> I built VaultZero around one constraint: the vault should never need a network. The APK has no
> `INTERNET` permission at all. No account, no sync, no analytics, no crash reporting.
>
> What's inside: Argon2id (RFC 9106, 64 MiB, t=3) password/recovery envelopes with transparent
> PBKDF2 migration, AES-256-GCM per record bound to id+type via AAD, BIP-39 128-bit recovery kits,
> hardware-backed biometrics, verified and duplicate-safe encrypted backups (SAF only), screenshot
> protection, an English/Español UI that switches instantly, reproducible release builds and 128
> tests.
>
> This is a **beta**: self-audited and self-pentested, not yet independently audited. Please use
> **test secrets only**, keep the 12-word recovery kit on paper, and don't migrate your real vault
> yet. **Not on Google Play, by design** — install from GitHub Releases or add the repo to
> [Obtainium](https://github.com/ImranR98/Obtainium) for automatic updates.
>
> Install: https://github.com/cipherwell/Vault-Zero/releases/latest
> Tester guide: https://github.com/cipherwell/Vault-Zero/blob/main/docs/BETA.md
> SHA-256: `<SHA256>` · cert `62:92:A1:…:07:E7`
>
> Bug reports: GitHub issues (there's a "Copy diagnostics" button in Settings → About that never
> includes secrets). MIT licensed. If you find something security-relevant, please use the private
> channel in SECURITY.md.

---

## Español — corto

> VaultZero es un gestor de contraseñas totalmente **offline y de conocimiento cero** que estoy
> construyendo: sin cuenta, sin nube, sin permiso de `INTERNET`, sin telemetría. Bóvedas Argon2id,
> AES-256-GCM, kit de recuperación BIP-39, copias cifradas e interfaz en inglés/español.
>
> Abro una **beta pequeña** en Android. Solo datos de prueba: es software previo a la auditoría.
> APK y hashes: https://github.com/cipherwell/Vault-Zero/releases/latest
> Guía: https://github.com/cipherwell/Vault-Zero/blob/main/docs/BETA.md
>
> Código abierto, licencia MIT. Se agradece todo el feedback. 🐾
>
> #Android #Seguridad #Privacidad #SoftwareLibre

---

## Pinned comment template

```
Version: v<VERSION> · https://github.com/cipherwell/Vault-Zero/releases/tag/v<VERSION>
APK SHA-256: <SHA256>
Signing cert SHA-256: 6292a1fc630c200e7ec5342248ba7a6ae30a893fd882b2ad4188f699bbb607e7
No INTERNET permission. No telemetry. Use test data only — pre-audit beta.
Report bugs: https://github.com/cipherwell/Vault-Zero/issues
Security issues (private): see SECURITY.md
```

## What to ask testers for (short list)

- Device model + Android version (Settings → Copy diagnostics includes both).
- The step sequence that broke, and whether it survives a force-stop/relaunch.
- Whether the recovery kit + backup restore worked on a second device if they have one.

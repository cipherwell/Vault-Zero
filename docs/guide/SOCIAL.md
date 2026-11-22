# VaultZero — social share kit

Short, copy-ready posts for the **public beta**. Keep the claims factual: no account, no cloud, no
internet permission, open source, **self-audited + pentested internally; independent audit pending**.
Always say it is a beta and to use test data.

The full launch pack (8-slide carousel, square image, posting notes, alt text and first-comment
links) is in [`social/FACEBOOK-POST.md`](social/FACEBOOK-POST.md).

**Links**

- Guide EN: `https://github.com/cipherwell/Vault-Zero/blob/main/docs/guide/VaultZero-Guide-EN.pdf`
- Guía ES: `https://github.com/cipherwell/Vault-Zero/blob/main/docs/guide/VaultZero-Guia-ES.pdf`
- Releases: `https://github.com/cipherwell/Vault-Zero/releases`
- F-Droid repo: `https://cipherwell.github.io/Vault-Zero/fdroid/repo`
- Beta rules: `https://github.com/cipherwell/Vault-Zero/blob/main/docs/BETA.md`
- Audit report: `https://github.com/cipherwell/Vault-Zero/blob/main/docs/security/AUDIT-2026-11-21.md`

## Short posts — English

**1. Launch**
```
VaultZero is in public beta: an offline Android vault for passwords, cards, 2FA codes, notes and
wallets — encrypted on your phone. No account, no cloud, no internet permission.

Open source, self-audited + pentested internally. Independent audit still ahead, so treat this as a
beta: test data, backups, and no real secrets you can't afford to lose. 🔐
```

**2. Why offline**
```
No account. No sync server. No INTERNET permission at all. Your vault is a local encrypted database,
and the key is derived on your phone from your Master Password.

The trade-off: backups are manual. That is the point. Public beta — start with test data.
```

**3. Security, honestly**
```
AES-256-GCM, Argon2id (64 MiB, deliberately slow), BIP-39 recovery kit, biometric unlock, FLAG_SECURE.
137 automated tests + 15 on-device tests, no Critical/High findings in our internal audit.

But we have not had an independent audit yet, and no app can protect a rooted phone or a keylogger.
VaultZero is a public beta — smaller attack surface, not magic.
```

## Publicaciones cortas — Español

**1. Lanzamiento**
```
VaultZero está en beta pública: una bóveda Android offline para contraseñas, tarjetas, códigos 2FA,
notas y billeteras, cifrada en tu teléfono. Sin cuenta, sin nube, sin permiso de internet.

Código abierto, autoauditada y con pentest interno. Falta la auditoría independiente, así que trátala
como beta: datos de prueba, copias de seguridad y nada que no puedas permitirte perder. 🔐
```

**2. Por qué offline**
```
Sin cuenta. Sin servidor de sincronización. Sin permiso de INTERNET. Tu bóveda es una base de datos
cifrada local y la clave se deriva en tu teléfono a partir de tu contraseña maestra.

La contrapartida: las copias son manuales. Ese es el punto. Beta pública: empieza con datos de prueba.
```

**3. Seguridad, con honestidad**
```
AES-256-GCM, Argon2id (64 MiB, lento a propósito), kit de recuperación BIP-39, biometría y FLAG_SECURE.
137 pruebas automatizadas + 15 en dispositivo, sin hallazgos Críticos/Altos en nuestra auditoría interna.

Pero todavía no hay auditoría independiente, y ninguna app protege un teléfono rooteado o un keylogger.
VaultZero es una beta pública: menos superficie de ataque, no magia.
```

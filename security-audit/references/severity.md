# Severity, triage and false positives

## 1. Scale

Use **CVSS 4.0 base** for exploitability/impact and adjust with **MASVS impact** for this app.

| Severity | CVSS | VaultZero examples |
| --- | --- | --- |
| Critical | 9.0–10.0 | plaintext vault key on disk; auth bypass without device unlock; remote code exec |
| High | 7.0–8.9 | backup KDF downgrade silently accepted; biometric bypass; exported component that unlocks or exports; secrets in logs; path traversal writing outside SAF |
| Medium | 4.0–6.9 | tapjacking on unlock; clipboard not cleared on lock; info leak in recents/preview; DoS via crafted backup (crash/ANR); weak KDF floor |
| Low | 0.1–3.9 | verbose error text; missing `FLAG_IMMUTABLE` with no exploit; minor metadata leak |
| Info | 0.0 | hardening gaps with no attack path (e.g., no certificate pinning for a no-network app) |

If CVSS and real-world impact disagree, state both and pick the higher for prioritization.

## 2. Triage rules

1. **Reproduce** from a clean install, twice, with commands recorded.
2. **Bound the impact** to this threat model: a "leak" that requires root on an unlocked device is
   usually Low/Informational (document the assumption).
3. **Distinguish intended behavior** (e.g., recovery phrase is portable by design) from a finding.
4. **No scanner-only findings.** A grep hit must be traced to a reachable path.
5. **Confidence** column: Confirmed / Likely / Suspected.
6. **Fix ownership**: code / docs / accepted risk.

## 3. Common false positives

| Claim | Why it can be wrong |
| --- | --- |
| "Secrets in SharedPreferences" | If they are ciphertext/KDF spec, not plaintext |
| "allowBackup missing" | Here it is explicitly false; verify merged manifest |
| "No certificate pinning" | App has no INTERNET permission and no network code |
| "No root detection" | Out of threat model unless physical access is in scope |
| "Debuggable" | Check merged release manifest, not debug builds |
| "AES-GCM IV static" | IV may be generated per envelope; trace the call site |
| "KDF too weak" | Compare against the *imported-envelope* floor, not the defaults |
| "Clipboard leaks" | TTL + clear-on-lock may mitigate; test the actual lifetime |

## 4. Finding template

Use `templates/finding.md`. Required fields: id, title, severity, CVSS vector, control (MASVS),
affected version, component, evidence, reproduction, impact, fix, retest.

## 5. Sign-off checklist

- [ ] All Critical/High fixed and retested on-device.
- [ ] Mediums fixed or accepted in writing with owner/date.
- [ ] Every MASVS row Pass/Fail/N/A(reason).
- [ ] Fuzz corpus green; seeds kept.
- [ ] Release APK: no debug, no backup, FLAG_SECURE on, no INTERNET, clean strings.
- [ ] Report reviewed by a second person (or documented limitation).

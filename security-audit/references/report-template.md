# Audit report template

Produce `REPORT.md` in the results directory. Keep it readable by a non-author.

```markdown
# VaultZero — Security Audit & Penetration Test

**Version tested:** 1.7.x (versionCode xx) · **Date:** YYYY-MM-DD
**Auditor:** <name/agent> · **Method:** OWASP MASVS 2.1 / MASTG, static + dynamic + fuzzing
**Scope:** release APK + source; local storage, crypto, backup, biometrics, UI/platform.
**Out of scope:** OS keystore internals, physical chip-off, third-party library internals.

## 1. Executive summary

- 2–3 sentences: overall posture, what was tested, whether beta is safe.
- Finding table:

| Severity | Open | Fixed | Accepted |
| --- | ---: | ---: | ---: |
| Critical | | | |
| High | | | |
| Medium | | | |
| Low | | | |
| Info | | | |

- Recommendation: **GO / NO-GO** for beta, with conditions.

## 2. What was tested

- Builds, devices/API levels, locales, tools, dates.
- Coverage: list MASVS families and major flows (create, unlock, biometrics, export, import, lock,
  clipboard, screenshots, intents).
- What was **not** tested and why (no rooted device for Frida, etc.).

## 3. Findings

For each: id, title, severity + CVSS, MASVS control, component, evidence, reproduction, impact,
fix, retest status.

### VZ-<AREA>-<nnn> — <title> (Severity)

- **Control:** MASVS-…
- **CVSS:** 4.0 vector + score
- **Confidence:** Confirmed / Likely / Suspected
- **Affected:** version/build
- **Evidence:** commands/output/`file:line`
- **Reproduction:** numbered steps
- **Impact:** what an attacker gains
- **Fix:** concrete change
- **Retest:** date + result

## 4. Passed checks (evidence of coverage)

Grouped by MASVS family, one line each: test id, what was verified, evidence reference.

## 5. Threat model reconciliation

Which abuse cases from `threat-model.md` are now covered, which remain accepted.

## 6. Residual risk and accepted findings

Table: risk, reason, owner, date, review date.

## 7. Beta sign-off

- Critical/High open: 0
- Medium accepted: n (owners/dates)
- MASVS checklist complete: yes/no
- Artifact hashes verified: yes/no
- Signature: auditor / product owner
```

Tone: factual, no marketing. Every claim links to evidence. Redact secrets.

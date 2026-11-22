#!/usr/bin/env python3
"""Cryptography heuristics for VaultZero.

Parses the crypto sources for KDF parameters, IV/tag sizes, AEAD usage, Keystore
configuration and dangerous patterns, then checks them against the audit
thresholds in references/crypto-review.md.

This is a *review accelerator*, not a proof: every finding must be traced to a
reachable call path in the source.

Usage:
  python3 crypto_review.py --project .. --json results/crypto.json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

THRESHOLDS = {
    "ARGON2_MEMORY_KIB": 65536,
    "ARGON2_ITERATIONS": 3,
    "ARGON2_PARALLELISM": 1,
    "GCM_IV_LENGTH_BYTES": 12,
    "GCM_TAG_LENGTH_BITS": 128,
    "DEFAULT_ITERATIONS": 600000,
    "MIN_ACCEPTED_ITERATIONS": 10000,
}

DANGEROUS = [
    ("ECB mode", r"AES/ECB", "critical"),
    ("Default AES (ECB)", r'Cipher\.getInstance\(\s*"AES"\s*\)', "critical"),
    ("Static IV", r"IvParameterSpec\(\s*(?:byteArrayOf|ByteArray\()", "high"),
    ("Non-crypto RNG", r"java\.util\.Random|Math\.random", "high"),
    ("MD5", r'MessageDigest\.getInstance\(\s*"MD5"', "medium"),
    ("SHA-1", r'MessageDigest\.getInstance\(\s*"SHA-?1"', "medium"),
    ("Hardcoded key", r"SecretKeySpec\(\s*(?:byteArrayOf|\"[A-Za-z0-9+/=]{16,}\")", "critical"),
    ("Auth not required", r"setUserAuthenticationRequired\(\s*false", "high"),
    ("Randomized encryption off", r"setRandomizedEncryptionRequired\(\s*false", "high"),
    ("Keystore not backed by StrongBox", r"setIsStrongBoxBacked", "info"),
]

REQUIRED = [
    ("SecureRandom", r"SecureRandom", "pass", "CSPRNG used"),
    ("AES-GCM", r"AES/GCM/NoPadding", "pass", "AEAD mode"),
    ("GCMParameterSpec", r"GCMParameterSpec", "pass", "Explicit tag length"),
    ("AAD binding", r"updateAAD", "pass", "AAD used (verify coverage)"),
    ("Keystore", r"AndroidKeyStore|KeyGenParameterSpec", "pass", "Keystore usage"),
    ("Biometric invalidation", r"setInvalidatedByBiometricEnrollment\(\s*true", "pass", "Re-enrol invalidates key"),
]


def constants(text: str) -> dict:
    out = {}
    for name in THRESHOLDS:
        m = re.search(rf"const val {name}\s*=\s*([0-9_]+)", text)
        if m:
            out[name] = int(m.group(1).replace("_", ""))
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="..")
    ap.add_argument("--json", default="results/crypto.json")
    args = ap.parse_args()
    project = Path(args.project).resolve()
    src = project / "app/src/main/java/com/example/crypto"
    files = sorted(src.glob("*.kt")) if src.exists() else []
    text = "\n".join(f.read_text(errors="replace") for f in files)

    found = constants(text)
    findings = []
    for name, minimum in THRESHOLDS.items():
        if name not in found:
            continue
        value = found[name]
        if value < minimum:
            findings.append({
                "area": "kdf", "severity": "high" if "MEMORY" in name or "ITERATIONS" in name else "medium",
                "title": f"{name} below audit floor",
                "detail": f"{name}={value}, expected >= {minimum}",
            })
        else:
            findings.append({
                "area": "kdf", "severity": "pass",
                "title": f"{name} meets floor",
                "detail": f"{name}={value} (floor {minimum})",
            })

    # Legacy PBKDF2 floor: min accepted is 10k; flag if it can be used for new envelopes
    if "MIN_ACCEPTED_ITERATIONS" in found and found["MIN_ACCEPTED_ITERATIONS"] < 600000:
        findings.append({
            "area": "kdf", "severity": "info",
            "title": "Legacy PBKDF2 floor below current default",
            "detail": "Ensure MIN_ACCEPTED_ITERATIONS is read-only legacy and never used to write new envelopes.",
        })

    for title, pattern, severity in DANGEROUS:
        for m in re.finditer(pattern, text):
            line = text.count("\n", 0, m.start()) + 1
            findings.append({"area": "pattern", "severity": severity, "title": title,
                             "detail": f"match at crypto bundle line {line}"})
    for title, pattern, severity, detail in REQUIRED:
        if not re.search(pattern, text):
            findings.append({"area": "coverage", "severity": "medium" if severity == "pass" else severity,
                             "title": f"expected control missing: {title}", "detail": detail})

    # Backup importer must validate KDF specs; look for the validation call sites.
    repo = (project / "app/src/main/java/com/example/data").glob("*.kt")
    repo_text = "\n".join(p.read_text(errors="replace") for p in repo)
    if "KdfSpec.parse" in repo_text or "KdfSpec.decode" in repo_text:
        findings.append({"area": "backup", "severity": "pass", "title": "KdfSpec.parse used in data layer",
                         "detail": "decode() bounds parameters; verify whether below-current specs are accepted on restore."})
    else:
        findings.append({"area": "backup", "severity": "medium", "title": "No visible KdfSpec.parse call",
                         "detail": "Backup import may not validate attacker-controlled KDF parameters."})

    result = {"constants": found, "findings": findings,
              "counts": {s: sum(1 for f in findings if f["severity"] == s)
                         for s in ("critical", "high", "medium", "pass", "info")}}
    out = Path(args.json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2))
    print("constants:", found)
    for f in findings:
        print(f"[{f['severity']:>8}] {f['area']}: {f['title']} — {f['detail']}")
    print("wrote", out)
    return 1 if result["counts"]["critical"] or result["counts"]["high"] else 0


if __name__ == "__main__":
    sys.exit(main())

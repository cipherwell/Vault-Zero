#!/usr/bin/env python3
"""Secrets and high-entropy material scan for source trees and APKs.

Usage:
  python3 secret_scan.py --path .. --json results/secrets.json
  python3 secret_scan.py --apk app-release.apk --json results/secrets-apk.json

Exit code 1 when a high-confidence secret is found.
"""
from __future__ import annotations

import argparse
import json
import math
import re
import subprocess
import sys
import zipfile
from pathlib import Path

SKIP_DIRS = {".git", "build", ".gradle", ".idea", "results", "node_modules", "__pycache__",
             "security-audit/results"}
SKIP_SUFFIXES = {".apk", ".aab", ".jar", ".jks", ".keystore", ".png", ".jpg", ".jpeg", ".webp",
                 ".ttf", ".otf", ".woff", ".woff2", ".zip", ".so", ".dex"}

RULES = [
    ("private_key", re.compile(r"-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----"), "critical"),
    ("aws_access_key", re.compile(r"\bAKIA[0-9A-Z]{16}\b"), "critical"),
    ("google_api_key", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}\b"), "critical"),
    ("github_pat", re.compile(r"\bghp_[0-9A-Za-z]{36}\b|\bgithub_pat_[0-9A-Za-z_]{50,}\b"), "critical"),
    ("slack_token", re.compile(r"\bxox[baprs]-[0-9A-Za-z\-]{10,}\b"), "high"),
    ("stripe_key", re.compile(r"\b(sk|pk)_(live|test)_[0-9A-Za-z]{16,}\b"), "high"),
    ("jwt", re.compile(r"\beyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b"), "medium"),
    ("basic_auth_url", re.compile(r"\bhttps?://[^/\s:@]+:[^/\s@]+@"), "high"),
    ("assigned_secret", re.compile(r"(?i)\b(api[_-]?key|secret|passwd|password|token)\s*[:=]\s*[\"'][^\"'\s]{8,}[\"']"), "medium"),
    ("bcrypt_hash", re.compile(r"\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}"), "high"),
    ("pem_cert", re.compile(r"-----BEGIN CERTIFICATE-----"), "info"),
]

ALLOWLIST_HINTS = ("example", "sample", "test", "dummy", "placeholder", "redacted", "xxxx",
                   "your_", "<", ">", "testImplementation", "assert", "expected", "TEST-PASSWORD")


def shannon(s: str) -> float:
    if not s:
        return 0.0
    counts = {}
    for ch in s:
        counts[ch] = counts.get(ch, 0) + 1
    return -sum((c / len(s)) * math.log2(c / len(s)) for c in counts.values())


def scan_text(text: str, origin: str, hits: list[dict]) -> None:
    for lineno, line in enumerate(text.splitlines(), 1):
        if len(line) > 4000:
            line = line[:4000]
        low = line.lower()
        if any(h in low for h in ALLOWLIST_HINTS):
            continue
        is_test = "/src/test/" in origin or "/src/androidTest/" in origin
        for name, rx, sev in RULES:
            for m in rx.finditer(line):
                value = m.group(0)
                effective = "info" if is_test and sev in ("critical", "high", "medium") else sev
                hits.append({
                    "rule": name, "severity": effective, "origin": origin, "line": lineno,
                    "match": (value[:12] + "…" + value[-4:] if len(value) > 20 else value)
                             + (" (test fixture)" if effective != sev else ""),
                })
        # high-entropy tokens (base64/hex), avoid long identifiers and normal prose
        for tok in re.findall(r"\b[A-Za-z0-9+/=_\-]{32,}\b", line):
            if re.fullmatch(r"[A-Fa-f0-9]{32,}", tok) or "/" in tok or "+" in tok or "=" in tok:
                if shannon(tok) > 4.3:
                    hits.append({"rule": "high_entropy", "severity": "low",
                                 "origin": origin, "line": lineno,
                                 "match": tok[:10] + "…(" + str(len(tok)) + ")"})


def scan_tree(root: Path, hits: list[dict]) -> None:
    for path in root.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(root)
        if any(part in SKIP_DIRS for part in rel.parts) or "security-audit" in rel.parts:
            continue
        if path.suffix.lower() in SKIP_SUFFIXES:
            continue
        if path.stat().st_size > 2_000_000:
            continue
        try:
            text = path.read_text(errors="replace")
        except OSError:
            continue
        scan_text(text, str(rel), hits)


def scan_apk(apk: Path, hits: list[dict]) -> None:
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not (name.endswith(".dex") or name.startswith("res/") or name.endswith(".xml")):
                continue
            data = z.read(name)
            if name.endswith(".dex"):
                text = data.decode("latin-1", errors="replace")
                scan_text(text, f"{apk.name}!{name}", hits)
        strings = sh_str(apk)
        if strings:
            scan_text(strings, f"{apk.name}!strings", hits)


def sh_str(apk: Path) -> str:
    try:
        return subprocess.run(["strings", "-n", "8", str(apk)], capture_output=True, text=True,
                              timeout=120).stdout
    except (OSError, subprocess.SubprocessError):
        return ""


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--path")
    ap.add_argument("--apk")
    ap.add_argument("--json", default="results/secrets.json")
    args = ap.parse_args()
    hits: list[dict] = []
    if args.path:
        scan_tree(Path(args.path).resolve(), hits)
    if args.apk:
        scan_apk(Path(args.apk).resolve(), hits)
    # de-duplicate identical rule/origin/line
    seen = set()
    unique = []
    for h in hits:
        key = (h["rule"], h["origin"], h["line"], h["match"])
        if key not in seen:
            seen.add(key)
            unique.append(h)
    out = Path(args.json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({"hits": unique, "count": len(unique)}, indent=2))
    for h in unique:
        print(f"[{h['severity']:>8}] {h['rule']:<16} {h['origin']}:{h['line']}  {h['match']}")
    print(f"{len(unique)} hit(s) -> {out}")
    return 1 if any(h["severity"] in ("critical", "high") for h in unique) else 0


if __name__ == "__main__":
    sys.exit(main())

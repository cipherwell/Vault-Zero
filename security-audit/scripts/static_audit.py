#!/usr/bin/env python3
"""Static security inventory for an Android app (VaultZero-oriented).

Reads the manifest, Gradle build files, Kotlin sources and dependency catalog,
then emits machine-readable findings plus a human summary. It is a *lead*
generator: every hit still needs a reachable code path before it becomes a
finding.

Usage:
  python3 static_audit.py --project .. --json out/static.json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

RISKY_PATTERNS = {
    "webview": (r"\bWebView\b", "info", "WebView present — review JS bridge, file access, URL loading"),
    "js_enabled": (r"javaScriptEnabled\s*=\s*true", "medium", "JavaScript enabled in WebView"),
    "js_bridge": (r"addJavascriptInterface", "high", "JS bridge exposes native objects"),
    "log": (r"\bLog\.[vdiew]\s*\(", "low", "Logging call — ensure no secrets"),
    "println": (r"\bprintln\s*\(", "low", "stdout logging — ensure no secrets"),
    "clipboard": (r"ClipboardManager|setPrimaryClip|ClipData", "info", "Clipboard use — verify TTL/clear on lock"),
    "screenshot_guard": (r"FLAG_SECURE", "pass", "FLAG_SECURE used"),
    "tapjacking": (r"filterTouchesWhenObscured|setFilterTouchesWhenObscured", "info", "Tapjacking guard"),
    "random": (r"java\.util\.Random|Math\.random|\bRandom\s*\(", "medium", "Non-crypto RNG — verify not used for secrets"),
    "secure_random": (r"SecureRandom", "pass", "SecureRandom used"),
    "ecb": (r"AES/ECB|Cipher\.getInstance\(\s*\"AES\"\s*\)", "high", "ECB/default AES mode"),
    "gcm": (r"AES/GCM/NoPadding", "pass", "AES-GCM used"),
    "static_iv": (r"IvParameterSpec\(\s*ByteArray|iv\s*=\s*byteArrayOf", "high", "Possible static IV"),
    "keystore": (r"KeyGenParameterSpec|AndroidKeyStore", "pass", "Android Keystore used"),
    "auth_required": (r"setUserAuthenticationRequired\s*\(\s*true", "pass", "Keystore key requires auth"),
    "biometric_invalidate": (r"setInvalidatedByBiometricEnrollment\s*\(\s*true", "pass", "Key invalidated on re-enrolment"),
    "mutable_pending": (r"PendingIntent\.get\w+\([^)]*FLAG_MUTABLE", "medium", "Mutable PendingIntent"),
    "world_mode": (r"MODE_WORLD_(READABLE|WRITABLE)", "high", "World-readable/writable file mode"),
    "sql_concat": (r"(rawQuery|execSQL)\s*\([^)]*\+", "medium", "Possible SQL string concatenation"),
    "exported_true": (r"android:exported=\"true\"", "info", "Exported component (verify necessity)"),
    "allow_backup_true": (r"allowBackup=\"true\"", "high", "Backups enabled"),
    "cleartext": (r"usesCleartextTraffic=\"true\"", "high", "Cleartext traffic enabled"),
    "hardcoded_secret": (r"(?i)(api[_-]?key|secret|password|token)\s*=\s*\"[^\"]{8,}\"", "high", "Possible hardcoded secret"),
}

DANGEROUS_PERMISSIONS = {
    "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.MANAGE_EXTERNAL_STORAGE", "android.permission.SYSTEM_ALERT_WINDOW",
    "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.READ_SMS",
    "android.permission.RECEIVE_SMS", "android.permission.READ_CONTACTS",
    "android.permission.ACCESS_FINE_LOCATION", "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO", "android.permission.READ_PHONE_STATE",
    "android.permission.QUERY_ALL_PACKAGES",
}


def find_project_root(start: Path) -> Path:
    for p in [start, *start.parents]:
        if (p / "settings.gradle.kts").exists() or (p / "settings.gradle").exists():
            return p
    return start


def find_manifest(project: Path) -> tuple[Path | None, str]:
    """Prefer the packaged/merged manifest: source manifests can carry
    tools:node="remove" permissions that never reach the APK."""
    candidates = list(project.glob("app/build/intermediates/packaged_manifests/*/*/AndroidManifest.xml"))
    candidates += list(project.glob("app/build/intermediates/merged_manifests/*/*/AndroidManifest.xml"))
    candidates += list(project.glob("app/build/intermediates/merged_manifest/*/*/AndroidManifest.xml"))
    release = [c for c in candidates if "release" in c.as_posix().lower()]
    pool = release or candidates
    for cand in sorted(pool, key=lambda p: p.stat().st_mtime, reverse=True):
        lowered = cand.as_posix().lower()
        if "/debug" in lowered or "test" in lowered:
            continue
        return cand, "merged"
    src = project / "app/src/main/AndroidManifest.xml"
    if src.exists():
        return src, "source"
    return None, "missing"


def parse_manifest(path: Path) -> dict:
    root = ET.fromstring(path.read_text())
    app = root.find("application")
    out: dict = {
        "package": root.get("package"),
        "permissions": [],
        "components": [],
        "application": {},
        "intent_filters": [],
    }
    for perm in root.findall("uses-permission"):
        if perm.get("{http://schemas.android.com/tools}node") == "remove":
            continue
        out["permissions"].append(perm.get(ANDROID + "name"))
    for perm in root.findall("uses-permission-sdk-23"):
        out["permissions"].append(perm.get(ANDROID + "name") + " (sdk23)")
    if app is not None:
        for attr in ("allowBackup", "debuggable", "usesCleartextTraffic", "networkSecurityConfig",
                     "dataExtractionRules", "fullBackupContent", "localeConfig", "testOnly"):
            out["application"][attr] = app.get(ANDROID + attr)
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for comp in app.findall(tag):
                name = comp.get(ANDROID + "name")
                exported = comp.get(ANDROID + "exported")
                perms = comp.get(ANDROID + "permission")
                filters = []
                for f in comp.findall("intent-filter"):
                    actions = [a.get(ANDROID + "name") for a in f.findall("action")]
                    cats = [c.get(ANDROID + "name") for c in f.findall("category")]
                    filters.append({"actions": actions, "categories": cats})
                out["components"].append({
                    "type": tag, "name": name, "exported": exported,
                    "permission": perms, "intent_filters": filters,
                })
                for f in filters:
                    out["intent_filters"].append({"component": name, "type": tag, **f})
    return out


def scan_sources(project: Path) -> list[dict]:
    hits = []
    src = project / "app/src/main/java"
    if not src.exists():
        return hits
    for path in sorted(src.rglob("*.kt")):
        text = path.read_text(errors="replace")
        for name, (pattern, severity, message) in RISKY_PATTERNS.items():
            for m in re.finditer(pattern, text):
                line = text.count("\n", 0, m.start()) + 1
                hits.append({
                    "pattern": name, "severity": severity, "message": message,
                    "file": str(path.relative_to(project)), "line": line,
                })
    return hits


def scan_build(project: Path) -> dict:
    info = {"minify": None, "shrink_resources": None, "debuggable": None,
            "min_sdk": None, "target_sdk": None, "compile_sdk": None,
            "dependencies": [], "signing_present": None}
    gradle = project / "app/build.gradle.kts"
    if gradle.exists():
        text = gradle.read_text(errors="replace")
        info["minify"] = bool(re.search(r"isMinifyEnabled\s*=\s*true", text))
        info["shrink_resources"] = bool(re.search(r"isShrinkResources\s*=\s*true", text))
        info["min_sdk"] = (re.search(r"minSdk\s*=\s*(\d+)", text) or [None, None])[1]
        info["target_sdk"] = (re.search(r"targetSdk\s*=\s*(\d+)", text) or [None, None])[1]
        info["compile_sdk"] = (re.search(r"compileSdk\s*=\s*(\d+)", text) or [None, None])[1]
        info["signing_present"] = ("signingConfig" in text) or ("keystore.env" in text)
        for m in re.finditer(r'implementation\((?:libs\.)?["\']?([^)"\']+)', text):
            info["dependencies"].append(m.group(1).strip())
    catalog = project / "gradle/libs.versions.toml"
    if catalog.exists():
        info["version_catalog"] = catalog.read_text(errors="replace")[:4000]
    return info


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--project", default="..")
    ap.add_argument("--json", default="results/static.json")
    args = ap.parse_args()

    project = find_project_root(Path(args.project).resolve())
    manifest_path, manifest_kind = find_manifest(project)
    manifest = parse_manifest(manifest_path) if manifest_path else {}
    sources = scan_sources(project)
    build = scan_build(project)

    leads = []
    if manifest:
        if manifest["application"].get("allowBackup") not in ("false", None):
            leads.append({"severity": "high", "area": "manifest", "message": "allowBackup not false"})
        if manifest["application"].get("debuggable") == "true":
            leads.append({"severity": "high", "area": "manifest", "message": "debuggable=true"})
        if manifest["application"].get("usesCleartextTraffic") == "true":
            leads.append({"severity": "high", "area": "manifest", "message": "cleartext traffic enabled"})
        for perm in manifest["permissions"]:
            base = perm.replace(" (sdk23)", "")
            if base in DANGEROUS_PERMISSIONS:
                leads.append({"severity": "info", "area": "manifest",
                              "message": f"dangerous permission requested: {base}"})
        for comp in manifest["components"]:
            if comp["exported"] == "true" and comp["type"] != "activity":
                leads.append({"severity": "high", "area": "manifest",
                              "message": f"exported {comp['type']}: {comp['name']}"})
            if comp["type"] == "provider" and comp["exported"] is None:
                leads.append({"severity": "medium", "area": "manifest",
                              "message": f"provider with default exported: {comp['name']}"})
        if not any(c["name"].endswith("MainActivity") for c in manifest["components"]):
            leads.append({"severity": "info", "area": "manifest", "message": "MainActivity not found"})

    for hit in sources:
        if hit["severity"] in ("high", "medium"):
            leads.append({"severity": hit["severity"], "area": "source",
                          "message": f"{hit['message']} ({hit['file']}:{hit['line']})"})
    if build.get("minify") is False:
        leads.append({"severity": "medium", "area": "build", "message": "R8 minify disabled"})
    if build.get("debuggable") is True:
        leads.append({"severity": "high", "area": "build", "message": "debuggable release build"})

    result = {
        "project": str(project),
        "manifest_path": str(manifest_path) if manifest_path else None,
        "manifest_kind": manifest_kind,
        "manifest": manifest,
        "build": build,
        "source_hits": sources,
        "leads": leads,
        "counts": {
            "files_scanned": len(list((project / "app/src/main/java").rglob("*.kt")))
            if (project / "app/src/main/java").exists() else 0,
            "leads": len(leads),
            "high": sum(1 for x in leads if x["severity"] == "high"),
            "medium": sum(1 for x in leads if x["severity"] == "medium"),
        },
    }
    out = Path(args.json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(result, indent=2))

    print(f"manifest: {manifest_kind} ({manifest_path})")
    print(f"kotlin files: {result['counts']['files_scanned']}  leads: {result['counts']['leads']}")
    for lead in leads:
        print(f"  [{lead['severity']:>6}] {lead['area']}: {lead['message']}")
    print(f"wrote {out}")
    return 1 if result["counts"]["high"] else 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Generate a malformed-backup corpus for VaultZero import fuzzing.

The importer expects a JSON envelope with:
  format="VaultZero_Encrypted_Envelope", items=[...], kdfPasswordSpec,
  kdfRecoverySpec, salt, encryptedVmkByPassword, recoverySalt,
  encryptedVmkByRecovery, verifier, version, protection.

Each generated file is classed and documented in manifest.json so a JVM harness
or a manual import pass can assert the expected outcome.

Usage:
  python3 fuzz_backup.py --out /tmp/vz-fuzz --count 120
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import random
import string
from pathlib import Path

FORMAT = "VaultZero_Encrypted_Envelope"


def b64(n: int) -> str:
    return base64.b64encode(bytes(random.getrandbits(8) for _ in range(n))).decode()


def envelope(**over) -> dict:
    base = {
        "version": "3.3",
        "format": FORMAT,
        "protection": "standard",
        "fingerprint": "VZ-FUZZ-0000-0000-0000",
        "exportedAt": 1760000000000,
        "kdfPasswordSpec": "argon2id:65536:3:1",
        "kdfRecoverySpec": "argon2id:65536:3:1",
        "recoverySalt": b64(16),
        "encryptedVmkByRecovery": b64(60),
        "salt": b64(16),
        "encryptedVmkByPassword": b64(60),
        "verifier": b64(32),
        "items": [{
            "id": "fuzz-item-1",
            "title": "[ENCRYPTED]",
            "type": "LOGIN",
            "category": "Encrypted",
            "encryptedPayload": b64(80),
            "isFavorite": False,
            "createdAt": 1760000000000,
            "updatedAt": 1760000000000,
            "lastUsedAt": 0,
            "tagsJson": "[]",
        }],
    }
    base.update(over)
    return base


def write(out: Path, name: str, cls: str, expected: str, content: str | bytes) -> dict:
    path = out / name
    if isinstance(content, bytes):
        path.write_bytes(content)
    else:
        path.write_text(content)
    return {"file": name, "class": cls, "expected": expected, "bytes": path.stat().st_size}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="/tmp/vz-fuzz")
    ap.add_argument("--count", type=int, default=120, help="minimum corpus size (more may be added)")
    ap.add_argument("--seed", type=int, default=1337)
    args = ap.parse_args()
    random.seed(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    manifest: list[dict] = []
    add = lambda n, c, e, content: manifest.append(write(out, n, c, e, content))

    # --- structure -------------------------------------------------------
    add("001_empty.json", "structure", "reject: not JSON", "")
    add("002_one_byte.json", "structure", "reject: not JSON", "{")
    add("003_null.json", "structure", "reject: not a backup", "null")
    add("004_array.json", "structure", "reject: not a backup", "[]")
    add("005_truncated.json", "structure", "reject: invalid JSON", json.dumps(envelope())[:120])
    add("006_trailing.json", "structure", "reject or ignore trailing", json.dumps(envelope()) + "GARBAGE")
    add("007_wrong_format.json", "structure", "reject: not a vault backup", json.dumps(envelope(format="Other")))
    add("008_no_items.json", "structure", "reject: no items", json.dumps({k: v for k, v in envelope().items() if k != "items"}))
    add("009_empty_items.json", "structure", "reject: empty", json.dumps(envelope(items=[])))
    add("010_items_not_array.json", "structure", "reject", json.dumps(envelope(items={})))
    add("011_item_not_object.json", "structure", "reject entry 1", json.dumps(envelope(items=[1, 2, 3])))
    add("012_item_missing_id.json", "structure", "reject entry 1", json.dumps(envelope(items=[{"type": "LOGIN", "encryptedPayload": b64(40)}])))
    add("013_item_unknown_type.json", "structure", "reject entry 1", json.dumps(envelope(items=[{**envelope()["items"][0], "type": "DROP TABLE"}])))
    add("014_item_empty_payload.json", "structure", "reject entry 1", json.dumps(envelope(items=[{**envelope()["items"][0], "encryptedPayload": ""}])))
    add("015_valid_shape_garbage_ciphertext.json", "crypto", "reject: auth failure, no partial import", json.dumps(envelope()))

    # --- KDF downgrade / parameter abuse ---------------------------------
    for i, spec in enumerate([
        "argon2id:8192:1:1", "argon2id:8192:3:1", "argon2id:65536:1:1",
        "pbkdf2-sha256:10000", "pbkdf2-sha256:60000", "pbkdf2-sha256:1",
        "argon2id:999999999:3:1", "argon2id:-1:3:1", "argon2id:65536:3:1:extra",
        "unknown:65536:3:1", "", "null",
    ]):
        add(f"kdf_{i:03d}.json", "kdf", f"reject weak/unsupported spec {spec!r}",
            json.dumps(envelope(kdfPasswordSpec=spec, kdfRecoverySpec=spec)))

    # --- sizes / DoS ------------------------------------------------------
    add("040_huge_salt.json", "size", "reject: salt too large", json.dumps(envelope(salt=b64(2000))))
    add("041_huge_envelope.json", "size", "reject: envelope too large", json.dumps(envelope(encryptedVmkByPassword=b64(200_000))))
    add("042_huge_payload.json", "size", "reject or bounded; no OOM", json.dumps(envelope(items=[{**envelope()["items"][0], "encryptedPayload": b64(5_000_000)}])))
    items = [{**envelope()["items"][0], "id": f"item-{i}"} for i in range(5000)]
    add("043_many_items.json", "size", "bounded time/memory; clean result", json.dumps(envelope(items=items)))
    add("044_deep_nesting.json", "size", "reject; no stack overflow", "[" * 2000 + "]" * 2000)
    add("045_duplicate_keys.json", "structure", "last-wins or reject; no crash",
        '{"format":"%s","items":[],"items":[%s]}' % (FORMAT, json.dumps(envelope()["items"][0])))

    # --- types / semantics ------------------------------------------------
    add("050_nan_number.json", "type", "reject or coerce safely", '{"format":"%s","exportedAt":NaN,"items":[]}' % FORMAT)
    add("051_float_items.json", "type", "reject", json.dumps(envelope(items=1.5)))
    add("052_negative_timestamps.json", "semantic", "accept but do not break UI",
        json.dumps(envelope(items=[{**envelope()["items"][0], "createdAt": -1, "updatedAt": 2**63 - 1}])))
    add("053_duplicate_ids.json", "semantic", "deduplicate or report, no crash",
        json.dumps(envelope(items=[envelope()["items"][0], envelope()["items"][0]])))
    add("054_huge_strings.json", "size", "bounded; no OOM",
        json.dumps(envelope(items=[{**envelope()["items"][0], "title": "A" * 1_000_000}])))
    add("055_control_chars.json", "semantic", "render safely; no crash",
        json.dumps(envelope(items=[{**envelope()["items"][0], "title": "\u202e\u0000\u0007"}])))
    add("056_rtl_unicode.json", "semantic", "render safely",
        json.dumps(envelope(items=[{**envelope()["items"][0], "title": "\u0627\u0644\u0628\u064a\u062a"}])))
    add("057_unknown_fields.json", "structure", "ignore unknown fields",
        json.dumps(envelope(extra="x", nested={"a": [1, 2, 3]})))

    # --- binary / encoding -------------------------------------------------
    add("060_invalid_utf8.json", "encoding", "reject", bytes([0xff, 0xfe, 0x00, 0x7b, 0x7d]))
    add("061_bom.json", "encoding", "reject or handle BOM", b"\xef\xbb\xbf" + json.dumps(envelope()).encode())
    add("062_garbage_base64.json", "encoding", "reject salt", json.dumps(envelope(salt="!!!!notbase64!!!!")))
    add("063_empty_salt.json", "encoding", "reject salt length", json.dumps(envelope(salt="")))
    add("064_short_salt.json", "encoding", "reject salt length", json.dumps(envelope(salt=b64(4))))

    # --- protection modes --------------------------------------------------
    add("070_phrase_only.json", "protection", "reject without phrase; accept with valid phrase only",
        json.dumps({**envelope(), "protection": "recovery_phrase_only", "salt": "", "encryptedVmkByPassword": ""}))
    add("071_unknown_protection.json", "protection", "reject or default safely", json.dumps(envelope(protection="weird")))

    # --- fill to requested count with random mutations --------------------
    seeds = [json.dumps(envelope()).encode()]
    while len(manifest) < args.count:
        data = bytearray(random.choice(seeds))
        for _ in range(random.randint(1, 6)):
            op = random.choice(["flip", "truncate", "insert", "dupe"])
            if not data:
                break
            i = random.randrange(len(data))
            if op == "flip":
                data[i] ^= 1 << random.randrange(8)
            elif op == "truncate":
                data = data[:i]
            elif op == "insert":
                data[i:i] = bytes([random.randrange(256)])
            else:
                data[i:i] = data[max(0, i - 10):i]
        name = f"mut_{len(manifest):03d}.json"
        manifest.append(write(out, name, "mutation", "no crash/ANR/data corruption; reject cleanly", bytes(data)))

    (out / "manifest.json").write_text(json.dumps({"generated": len(manifest), "seed": args.seed,
                                                   "cases": manifest}, indent=2))
    print(f"{len(manifest)} corpus files -> {out}")
    print("drive with: adb push", out, "/sdcard/Download/vz-fuzz/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

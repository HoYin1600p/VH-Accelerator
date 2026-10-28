#!/usr/bin/env python3
"""Compare complete, lossless BuildingGadgets inventories without opening a world."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import struct
import sys

SUPPORTED_JAR_SHA256 = "5eae7d0201adc89c4d6cdf80defb2807768a269090bb4ce7bc7c901cd208813a"
ORACLE_LIMIT = "Full registry-backed Template/BlockData decoding is a separate, unperformed gate."


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate JSON object key: " + key)
        result[key] = value
    return result


def invalid_constant(value):
    raise ValueError("non-finite JSON number: " + value)


def validate_inventory(value):
    if not isinstance(value, dict) or type(value.get("schemaVersion")) is not int or value["schemaVersion"] != 1:
        raise ValueError("unsupported or missing inventory schemaVersion")
    if value.get("status") != "complete":
        raise ValueError("inventory is incomplete or failed")
    provenance = value.get("provenance")
    if not isinstance(provenance, dict):
        raise ValueError("missing inventory provenance")
    if provenance.get("jarSha256") != SUPPORTED_JAR_SHA256:
        raise ValueError("inventory does not identify the supported original archive")
    if provenance.get("jarVersion") != "3.13.2-build.21+mc1.18.2":
        raise ValueError("unsupported original archive version")
    if not isinstance(provenance.get("sourceSha256"), str) or not re.fullmatch(r"[0-9a-f]{64}", provenance["sourceSha256"]):
        raise ValueError("missing source fingerprint")
    if type(provenance.get("sourceBytes")) is not int or provenance["sourceBytes"] < 0:
        raise ValueError("missing source byte count")
    for key in ("originalCoordinateCodecVerified", "originalPaletteCodecVerified"):
        if provenance.get(key) is not True:
            raise ValueError("unverified original codec: " + key)
    if provenance.get("fullOriginalTemplateDecode") is not False:
        raise ValueError("unsupported full-template oracle claim")
    semantic = value.get("semantic")
    if not isinstance(semantic, dict) or "rawSavedData" not in semantic or not isinstance(semantic.get("templates"), list):
        raise ValueError("missing complete semantic payload")
    if not isinstance(semantic.get("rawRootName"), str):
        raise ValueError("missing NBT document root name")
    raw = semantic["rawSavedData"]
    if not isinstance(raw, dict) or raw.get("type") != 10 or not isinstance(raw.get("value"), dict):
        raise ValueError("rawSavedData must preserve the complete typed compound root")
    return value


def load_inventory(path):
    # A supported NBT depth of 512 expands to more JSON levels because tags retain type envelopes.
    previous_limit = sys.getrecursionlimit()
    try:
        sys.setrecursionlimit(max(previous_limit, 4096))
        with Path(path).open("r", encoding="utf-8") as source:
            value = json.load(source, object_pairs_hook=unique_object, parse_constant=invalid_constant)
    finally:
        sys.setrecursionlimit(previous_limit)
    return validate_inventory(value)


def pointer(parent, key):
    return parent + "/" + str(key).replace("~", "~0").replace("/", "~1")


def object_children(path, left, right):
    return ((pointer(path, key), left[key], right[key]) for key in sorted(left))


def array_children(path, left, right):
    return ((pointer(path, index), a, b) for index, (a, b) in enumerate(zip(left, right)))


def first_difference(left, right):
    """Object key order is immaterial; list order and every JSON numeric type remain strict."""
    pending = [iter([("/semantic", left, right)])]
    while pending:
        try:
            path, a, b = next(pending[-1])
        except StopIteration:
            pending.pop()
            continue
        if type(a) is not type(b):
            return {"path": path, "reason": "type", "leftType": type(a).__name__, "rightType": type(b).__name__}
        if isinstance(a, dict):
            if set(a) != set(b):
                return {"path": path, "reason": "object keys", "leftOnly": sorted(set(a) - set(b)),
                        "rightOnly": sorted(set(b) - set(a))}
            pending.append(object_children(path, a, b))
        elif isinstance(a, list):
            if len(a) != len(b):
                return {"path": path, "reason": "list length", "leftLength": len(a), "rightLength": len(b)}
            pending.append(array_children(path, a, b))
        elif a != b or isinstance(a, float) and struct.pack(">d", a) != struct.pack(">d", b):
            return {"path": path, "reason": "value"}
    return None


def compare(left, right):
    validate_inventory(left)
    validate_inventory(right)
    difference = first_difference(left["semantic"], right["semantic"])
    return {"result": "EQUAL" if difference is None else "DIFFERENT", "difference": difference,
            "scope": "complete typed saved data and derived original-codec inventory",
            "limitation": ORACLE_LIMIT}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("left", type=Path)
    parser.add_argument("right", type=Path)
    parser.add_argument("--json", action="store_true", help="print a machine-readable result")
    args = parser.parse_args(argv)
    try:
        result = compare(load_inventory(args.left), load_inventory(args.right))
    except (OSError, ValueError, RecursionError) as error:
        result = {"result": "INVALID", "error": str(error), "limitation": ORACLE_LIMIT}
    if args.json:
        print(json.dumps(result, sort_keys=True, ensure_ascii=True))
    else:
        print(result["result"])
        if result.get("difference"):
            print(result["difference"]["path"] + ": " + result["difference"]["reason"])
        if result.get("error"):
            print(result["error"])
        print(result["limitation"])
    return {"EQUAL": 0, "DIFFERENT": 1, "INVALID": 2}[result["result"]]


if __name__ == "__main__":
    sys.exit(main())

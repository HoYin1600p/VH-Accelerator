"""Extract numeric startup CDF inputs without starting Java or Minecraft.

This tool records weights and source fingerprints, never complete loot tables.
Its parser contract was checked against the exact supported Vault loader. A
changed jar or an unproved input representation requires a fresh loader audit.
"""

import argparse
import collections
import hashlib
import json
import pathlib
import struct


VAULT_JAR_SHA256 = "898ad64c859b2533cafcc6105d1b6b179ce04a12a7d01319d0de8b4426b38c5c"


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON member: {key}")
        result[key] = value
    return result


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique_object)


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def extract(server_root):
    root = server_root.resolve(strict=True)
    jar = root / "mods/the_vault-1.18.2-3.21.62.jar"
    require(sha256(jar) == VAULT_JAR_SHA256,
            "Changed Vault jar: re-audit supports/addCache/parser/CDFKey first")
    registry = root / "config/the_vault/gen/loot_tables.json"
    keys = read_json(registry)["keys"]
    require(isinstance(keys, list), "Expected ordered registry keys")
    manifest = []
    vectors = {}
    for ordinal, key in enumerate(keys):
        versions = {name: value for name, value in key.items() if name not in ("id", "name")}
        require(set(versions) == {"1.0"}, "Unproved registry version layout")
        for version, relative in versions.items():
            source = (root / relative).resolve(strict=True)
            require(source.is_relative_to(root), "Registry source escapes selected server root")
            entries = read_json(source).get("entries", [])
            if len(entries) != 1:
                reason = "entry_count_not_1"
            elif len(entries[0].get("pool", [])) != 4:
                reason = "top_child_count_not_4"
            elif any("pool" not in child for child in entries[0]["pool"]):
                reason = "top_child_not_pool"
            else:
                reason = None
            record = dict(ordinal=ordinal, id=key["id"], version=version,
                          path=relative, sha256=sha256(source), supported=reason is None)
            if reason:
                record["reason"] = reason
            else:
                children = entries[0]["pool"]
                require(all(set(child) == {"weight", "pool"} for child in children),
                        "Unproved top-level pool representation")
                # For these small integers, JSON -> Java double is exact. Do
                # not silently assume Python and Gson parse arbitrary numbers identically.
                require(all(type(child["weight"]) is int and abs(child["weight"]) <= 2 ** 53
                            for child in children),
                        "Fractional/large top weights require a Java parsing audit")
                weights = [float(child["weight"]) for child in children]
                bits = tuple(struct.pack(">d", weight).hex() for weight in weights)
                fingerprint = hashlib.sha256(b"".join(bytes.fromhex(bit) for bit in bits)).hexdigest()
                vectors[bits] = dict(id=fingerprint, weights=weights, weightBits=list(bits))
                record["vectorId"] = fingerprint
            manifest.append(record)
    ordered = [vectors[key] for key in sorted(vectors)]
    supported = sum(record["supported"] for record in manifest)
    fixture = dict(schemaVersion=1,
                   contract="ModConfigs.registerGen -> supports -> addCache; exact raw CDFKey equality",
                   jarSha256=sha256(jar), registrySha256=sha256(registry),
                   supportedTableRefs=supported, samplesInclusive=[1, 53],
                   expectedUniqueConstructorKeys=len(ordered) * 53,
                   seedRangeInclusive=[0, 9999], vectors=ordered)
    coverage = dict(schemaVersion=1, jarSha256=sha256(jar), registrySha256=sha256(registry),
                    registeredTableRefs=len(manifest), supportedTableRefs=supported,
                    unsupportedReasons=dict(collections.Counter(record["reason"] for record in manifest
                                                               if not record["supported"])),
                    tables=manifest)
    return fixture, coverage


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-root", required=True, type=pathlib.Path)
    parser.add_argument("--out", required=True, type=pathlib.Path,
                        help="External report directory; existing named outputs are replaced")
    args = parser.parse_args()
    fixture, coverage = extract(args.server_root)
    args.out.mkdir(parents=True, exist_ok=True)
    for name, value in (("startup-cdf-weight-fixture.json", fixture),
                        ("startup-cdf-source-manifest.json", coverage)):
        target = args.out / name
        target.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
        print(f"{name}: {target.stat().st_size} bytes, SHA-256 {sha256(target)}")
    print(f"{fixture['supportedTableRefs']} supported tables, "
          f"{fixture['expectedUniqueConstructorKeys']} distinct startup keys")


if __name__ == "__main__":
    main()

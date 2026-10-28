#!/usr/bin/env python3
"""Compare recipe snapshots strictly, or normalize documented JVM-start ordering with --cross-start."""
import argparse
import json
import math
from pathlib import Path
import re
import sys


RESOURCE_ID = re.compile(r"[a-z0-9_.-]+:[a-z0-9/._-]+\Z")
HEX = re.compile(r"(?:[0-9a-f]{2})*\Z")
INTEGER_TEXT = re.compile(r"(?:0|-?[1-9][0-9]*)\Z")
TIMESTAMP = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,9})?Z\Z")


def require(condition, path, reason):
    if not condition:
        raise ValueError(f"{path}: {reason}")


def mapping(value, path, keys=None):
    require(type(value) is dict, path, "expected an object")
    if keys is not None:
        require(set(value) == set(keys), path, "unexpected/missing fields")


def sequence(value, path):
    require(type(value) is list, path, "expected an array")


def text(value, path, nonempty=False):
    require(type(value) is str and (not nonempty or bool(value)), path, "expected a string")


def integer(value, low, high, path):
    require(type(value) is int and low <= value <= high, path, "expected an integer in range")


def resource(value, path):
    require(type(value) is str and RESOURCE_ID.fullmatch(value) is not None,
            path, "expected a resource identifier")


def payload(value, path):
    require(type(value) is str and HEX.fullmatch(value) is not None,
            path, "expected complete lowercase hexadecimal bytes")


def integer_text(value, low, high, path):
    require(type(value) is str and INTEGER_TEXT.fullmatch(value) is not None,
            path, "expected canonical integer text")
    require(low <= int(value) <= high, path, "integer text is out of range")


def validate_json(value, path):
    """Ingredient JSON can be arbitrary JSON, but cannot contain non-finite numbers."""
    if type(value) is dict:
        for key, child in value.items():
            text(key, path)
            validate_json(child, f"{path}.{key}")
    elif type(value) is list:
        for index, child in enumerate(value):
            validate_json(child, f"{path}[{index}]")
    elif type(value) is float:
        require(math.isfinite(value), path, "non-finite JSON number")
    else:
        require(value is None or type(value) in (bool, int, str), path, "invalid JSON value")


def validate_nbt(value, path):
    mapping(value, path)
    require("type" in value, path, "missing NBT type")
    kind = value["type"]
    integer(kind, 0, 12, path + ".type")
    expected = {"type"} if kind == 0 else {"type", "bits"} if kind in (5, 6) else {"type", "value"}
    if kind == 9:
        expected.add("elementType")
    mapping(value, path, expected)
    if kind == 0:
        return
    child = value.get("value")
    if kind in (1, 2, 3, 4):
        bits = {1: 8, 2: 16, 3: 32, 4: 64}[kind]
        integer_text(child, -(1 << (bits - 1)), (1 << (bits - 1)) - 1, path + ".value")
    elif kind in (5, 6):
        width = 8 if kind == 5 else 16
        bits = value["bits"]
        require(type(bits) is str and re.fullmatch(r"0|[1-9a-f][0-9a-f]{0," + str(width - 1) + "}", bits) is not None,
                path + ".bits", "expected canonical raw floating-point bits")
    elif kind == 8:
        text(child, path + ".value")
    elif kind == 10:
        mapping(child, path + ".value")
        for key, tag in child.items():
            validate_nbt(tag, f"{path}.value.{key}")
    else:
        sequence(child, path + ".value")
        if kind == 9:
            element = value["elementType"]
            integer(element, 0, 12, path + ".elementType")
            require(element != 0 or not child, path, "nonempty NBT list cannot have END element type")
            for index, tag in enumerate(child):
                validate_nbt(tag, f"{path}.value[{index}]")
                require(tag["type"] == element, path, "NBT list element type mismatch")
        else:
            bits = {7: 8, 11: 32, 12: 64}[kind]
            for index, number in enumerate(child):
                validator = integer_text if kind == 12 else integer
                validator(number, -(1 << (bits - 1)), (1 << (bits - 1)) - 1,
                          f"{path}.value[{index}]")


def validate_stack(value, path):
    mapping(value, path, {"item", "count", "nbt"})
    resource(value["item"], path + ".item")
    integer(value["count"], -(1 << 31), (1 << 31) - 1, path + ".count")
    validate_nbt(value["nbt"], path + ".nbt")
    require(value["nbt"]["type"] == 10, path + ".nbt", "stack NBT must be a compound")


def validate_ingredient(value, path):
    mapping(value, path, {"class", "json", "network", "candidates"})
    text(value["class"], path + ".class", True)
    validate_json(value["json"], path + ".json")
    payload(value["network"], path + ".network")
    sequence(value["candidates"], path + ".candidates")
    for index, stack in enumerate(value["candidates"]):
        validate_stack(stack, f"{path}.candidates[{index}]")


def validate_snapshot(data, cross_start=False):
    mapping(data, "$", {"schema", "run", "coverage", "coverageNote", "scripts", "recipes",
                        "typeOrder", "tags", "errors", "uncoveredServerSemantics", "complete",
                        "hiddenServerStateProven"})
    integer(data["schema"], 1, 1, "$.schema")
    require(data["coverage"] == "NETWORK_AND_PUBLIC_VIEWS", "$.coverage", "unsupported coverage")
    text(data["coverageNote"], "$.coverageNote", True)
    require(type(data["complete"]) is bool, "$.complete", "expected a boolean")
    require(data["hiddenServerStateProven"] is False, "$.hiddenServerStateProven", "unsupported coverage claim")
    sequence(data["errors"], "$.errors")
    for index, error in enumerate(data["errors"]):
        text(error, f"$.errors[{index}]", True)
    if cross_start:
        require(data["complete"] is (not data["errors"]), "$.complete",
                "capture completion disagrees with errors")
    else:
        require(data["complete"] is True, "$.complete", "incomplete snapshot")
        require(not data["errors"], "$.errors", "capture errors")
    sequence(data["uncoveredServerSemantics"], "$.uncoveredServerSemantics")
    for index, note in enumerate(data["uncoveredServerSemantics"]):
        text(note, f"$.uncoveredServerSemantics[{index}]", True)
    run = data["run"]
    mapping(run, "$.run", {"timestamp", "compareMode", "indexedRemovalsConfigured"})
    require(type(run["timestamp"]) is str and TIMESTAMP.fullmatch(run["timestamp"]) is not None,
            "$.run.timestamp", "expected UTC Instant timestamp")
    for key in ("compareMode", "indexedRemovalsConfigured"):
        require(type(run[key]) is bool, f"$.run.{key}", "expected a boolean")
    mapping(data["scripts"], "$.scripts")
    for filename, digest in data["scripts"].items():
        text(filename, "$.scripts filename", True)
        require(not any(part in ("", ".", "..") for part in filename.split("/"))
                and "\\" not in filename and ":" not in filename,
                "$.scripts filename", "expected a normalized relative path")
        require(type(digest) is str and re.fullmatch(r"[0-9a-f]{64}", digest) is not None,
                f"$.scripts.{filename}", "expected SHA-256 hexadecimal fingerprint")
    sequence(data["recipes"], "$.recipes")
    ids, per_type, untyped_errors = set(), {}, set()
    recipe_fields = ("id", "serializer", "serializerClass", "class", "type", "group",
                     "special", "network", "displayResult", "ingredients")
    for index, entry in enumerate(data["recipes"]):
        path = f"$.recipes[{index}]"
        mapping(entry, path)
        partial = set(entry) != set(recipe_fields)
        if partial:
            require(cross_start and "id" in entry, path, "unexpected/missing recipe fields")
            require(any(set(entry) == set(recipe_fields[:end]) for end in range(1, len(recipe_fields))),
                    path, "partial recipe is not an emitted capture prefix")
            require(any(error.startswith(str(entry["id"]) + ": ") for error in data["errors"]),
                    path, "partial recipe has no matching capture error")
        for key in ("id", "serializer", "type"):
            if key in entry:
                resource(entry[key], f"{path}.{key}")
        for key in ("serializerClass", "class", "group"):
            if key in entry:
                text(entry[key], f"{path}.{key}", key != "group")
        if "special" in entry:
            require(type(entry["special"]) is bool, path + ".special", "expected a boolean")
        require(entry["id"] not in ids, path, "duplicate recipe id")
        ids.add(entry["id"])
        if "type" in entry:
            per_type.setdefault(entry["type"], []).append(entry["id"])
        else:
            untyped_errors.add(entry["id"])
        if "network" in entry:
            payload(entry["network"], path + ".network")
        if "displayResult" in entry:
            validate_stack(entry["displayResult"], path + ".displayResult")
        if "ingredients" in entry:
            sequence(entry["ingredients"], path + ".ingredients")
            for ingredient_index, ingredient in enumerate(entry["ingredients"]):
                validate_ingredient(ingredient, f"{path}.ingredients[{ingredient_index}]")
    mapping(data["typeOrder"], "$.typeOrder")
    ordered_membership = set()
    for kind, ordered_ids in data["typeOrder"].items():
        resource(kind, "$.typeOrder key")
        sequence(ordered_ids, f"$.typeOrder.{kind}")
        for recipe_id in ordered_ids:
            resource(recipe_id, f"$.typeOrder.{kind}")
            require(recipe_id in ids, f"$.typeOrder.{kind}", "unknown recipe id in type order")
        require(len(set(ordered_ids)) == len(ordered_ids), f"$.typeOrder.{kind}",
                "duplicate recipe id in type order")
        require(not ordered_membership.intersection(ordered_ids), f"$.typeOrder.{kind}",
                "recipe id belongs to multiple types")
        ordered_membership.update(ordered_ids)
        require(set(ordered_ids) - untyped_errors == set(per_type.get(kind, [])), f"$.typeOrder.{kind}",
                "recipe membership is inconsistent or truncated")
    require(set(per_type).issubset(data["typeOrder"]), "$.typeOrder",
            "recipe type membership is inconsistent or truncated")
    require(ids - ordered_membership <= untyped_errors, "$.typeOrder",
            "recipe membership is inconsistent or truncated")
    if not cross_start:
        require(data["typeOrder"].keys() == per_type.keys(), "$.typeOrder",
                "recipe type membership is inconsistent or truncated")
    mapping(data["tags"], "$.tags")
    for registry, tags in data["tags"].items():
        resource(registry, "$.tags registry")
        mapping(tags, f"$.tags.{registry}")
        for tag, values in tags.items():
            resource(tag, f"$.tags.{registry} key")
            sequence(values, f"$.tags.{registry}.{tag}")
            for value in values:
                resource(value, f"$.tags.{registry}.{tag} value")
            if not cross_start:
                require(len(set(values)) == len(values), f"$.tags.{registry}.{tag}", "duplicate tag member")


def read_snapshot(path, cross_start=False):
    def reject_duplicates(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, "$", f"duplicate object key {key!r}")
            result[key] = value
        return result

    def reject_constant(value):
        raise ValueError(f"non-finite JSON constant {value}")

    data = json.loads(Path(path).read_text(encoding="utf-8-sig"),
                      object_pairs_hook=reject_duplicates, parse_constant=reject_constant)
    validate_snapshot(data, cross_start=cross_start)
    # Only validated, documented run timestamp/feature fields differ between arms.
    del data["run"]
    return data


OBJECT_IDENTITY = re.compile(r"@[0-9a-fA-F]+(?![0-9A-Za-z_])")
VANILLA_INGREDIENT = "net.minecraft.world.item.crafting.Ingredient"
# Source-reviewed 1.18.2 vanilla writers; CT 9.1.213 interfaces and Create 0.5.1.i
# ProcessingRecipeSerializer.writeToBuffer. These offsets are parsed structurally, never searched.
NETWORK_WRITERS = {
    "net.minecraft.world.item.crafting.ShapedRecipe$Serializer": "shaped",
    "net.minecraft.world.item.crafting.ShapelessRecipe$Serializer": "shapeless",
    "net.minecraft.world.item.crafting.SimpleCookingSerializer": "single",
    "net.minecraft.world.item.crafting.SingleItemRecipe$Serializer": "single",
    "net.minecraft.world.item.crafting.UpgradeRecipe$Serializer": "smithing",
    "com.blamejared.crafttweaker.api.recipe.serializer.CTShapedRecipeSerializer": "ct_shaped",
    "com.blamejared.crafttweaker.api.recipe.serializer.CTShapelessRecipeSerializer": "counted",
    "com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer": "counted",
}

# Mekanism 10.2.5 NucleosynthesizingRecipe.write -> ItemStackChemicalToItemStackRecipe.write:
# item input first, then chemical input/output/duration. SawmillRecipe.write likewise emits
# item input first, then main/secondary output ItemStacks and the secondary chance double.
# Only the item-input record is rewritten; each complete suffix remains byte-exact.
MEKANISM_ITEM_PREFIX_WRITERS = {
    "mekanism.common.recipe.serializer.NucleosynthesizingRecipeSerializer",
    "mekanism.common.recipe.serializer.SawmillRecipeSerializer",
}

BOTANIA_MARIMORPHOSIS_SERIALIZER = "vazkii.botania.common.crafting.RecipeMarimorphosis$Serializer"
BOTANIA_MARIMORPHOSIS_CLASS = "vazkii.botania.common.crafting.RecipeMarimorphosis"



def exact_sort_key(value):
    """A total multiset order that preserves JSON type distinctions and signed zero."""
    if type(value) is dict:
        return ("object", tuple((key, exact_sort_key(child)) for key, child in sorted(value.items())))
    if type(value) is list:
        return ("array", tuple(map(exact_sort_key, value)))
    if type(value) is float:
        return ("float", value.hex())
    return (type(value).__name__, value)


class NetworkReader:
    """Bounds-checked framing only; retained bytes still carry every serialized value."""
    def __init__(self, data):
        self.data, self.offset = data, 0

    def take(self, count):
        if count < 0 or self.offset + count > len(self.data):
            raise ValueError("truncated network framing")
        start = self.offset
        self.offset += count
        return self.data[start:self.offset]

    def varint(self):
        start, value = self.offset, 0
        for shift in range(0, 35, 7):
            byte = self.take(1)[0]
            value |= (byte & 127) << shift
            if byte < 128:
                if value > 0x7fffffff or len(encode_varint(value)) != self.offset - start:
                    raise ValueError("noncanonical or negative collection framing")
                return value
        raise ValueError("oversized network VarInt")

    def utf(self):
        return self.take(self.varint()).decode("utf-8")

    def nbt_string(self):
        self.take(int.from_bytes(self.take(2), "big"))

    def nbt_payload(self, kind, depth=0):
        if depth > 512:
            raise ValueError("NBT depth limit")
        sizes = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}
        if kind in sizes:
            self.take(sizes[kind])
        elif kind == 8:
            self.nbt_string()
        elif kind in (7, 11, 12):
            count = int.from_bytes(self.take(4), "big", signed=True)
            self.take(count * {7: 1, 11: 4, 12: 8}[kind])
        elif kind == 9:
            element = self.take(1)[0]
            count = int.from_bytes(self.take(4), "big", signed=True)
            if count < 0 or count > len(self.data) or (element == 0 and count):
                raise ValueError("invalid NBT list framing")
            for _ in range(count):
                self.nbt_payload(element, depth + 1)
        elif kind == 10:
            while True:
                child = self.take(1)[0]
                if child == 0:
                    break
                self.nbt_string()
                self.nbt_payload(child, depth + 1)
        else:
            raise ValueError("unknown NBT wire type")

    def stack(self):
        start = self.offset
        present = self.take(1)[0]
        if present not in (0, 1):
            raise ValueError("invalid ItemStack presence flag")
        if present:
            self.varint()  # registry item ID
            self.take(1)  # count; preserve signed byte exactly
            kind = self.take(1)[0]
            if kind:
                if kind != 10:
                    raise ValueError("ItemStack wire NBT is not a compound")
                self.nbt_string()
                self.nbt_payload(kind)
        return self.data[start:self.offset]


def encode_varint(value):
    result = bytearray()
    while value > 127:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def canonical_vanilla_ingredient(reader, expected_count=None):
    count = reader.varint()
    if count > len(reader.data) or (expected_count is not None and count != expected_count):
        raise ValueError("ingredient candidate count disagrees with framing")
    stacks = [reader.stack() for _ in range(count)]
    # Full wire stacks are a multiset too: no count/share-NBT bytes are discarded.
    return encode_varint(count) + b"".join(sorted(stacks))


def canonical_ingredient_network(ingredient):
    original = bytes.fromhex(ingredient["network"])
    if ingredient["class"] != VANILLA_INGREDIENT:
        return original
    try:
        reader = NetworkReader(original)
        canonical = canonical_vanilla_ingredient(reader, len(ingredient["candidates"]))
        if reader.offset != len(original):
            return original
        return canonical
    except (ValueError, UnicodeError, RecursionError, OverflowError):
        return original  # custom/future framing remains byte-exact


def canonical_mekanism_item(reader, depth=0):
    if depth > 64:
        raise ValueError("Mekanism item ingredient depth limit")
    kind = reader.varint()  # IngredientType.SINGLE=0, MULTI=1, encoded via writeEnum
    if kind == 0:
        ingredient = canonical_vanilla_ingredient(reader)
        amount = reader.varint()
        return b"\x00" + ingredient + encode_varint(amount)
    if kind == 1:
        count = reader.varint()  # BasePacketHandler.writeArray
        if count > len(reader.data):
            raise ValueError("Mekanism item ingredient array length")
        # Preserve the declared alternatives' order; normalize only each vanilla candidate set.
        return b"\x01" + encode_varint(count) + b"".join(
            canonical_mekanism_item(reader, depth + 1) for _ in range(count))
    raise ValueError("unreviewed Mekanism item ingredient variant")


def canonical_marimorphosis_network(recipe, original):
    # Botania 1.18.2-435: Orechid.Serializer emits input block ID, StateIngredient,
    # base weight; Marimorphosis.Serializer appends count, biome Set UTF names, bonus.
    # Only discriminator 1 (one output block ID) has been reviewed here.
    if (recipe.get("serializer") != "botania:marimorphosis"
            or recipe.get("type") != "botania:marimorphosis"
            or recipe.get("class") != BOTANIA_MARIMORPHOSIS_CLASS
            or recipe.get("ingredients") != []):
        return original.hex()
    try:
        reader = NetworkReader(original)
        reader.varint()  # input block registry ID; retained exactly
        if reader.take(1) != b"\x01":
            return original.hex()
        reader.varint()  # single output block registry ID
        reader.varint()  # base weight
        count = reader.varint()
        if count > len(original):
            return original.hex()
        prefix = original[:reader.offset]  # includes the original count bytes
        biomes = []
        for _ in range(count):
            start = reader.offset
            if not reader.utf():
                return original.hex()
            biomes.append(original[start:reader.offset])
        bonus_start = reader.offset
        reader.varint()
        if reader.offset != len(original):
            return original.hex()
        # Set.copyOf plus Set.contains make order irrelevant; multiplicity still remains exact.
        return (prefix + b"".join(sorted(biomes)) + original[bonus_start:]).hex()
    except (ValueError, UnicodeError, RecursionError, OverflowError):
        return original.hex()


def canonical_recipe_network(recipe, canonical_ingredients):
    original = bytes.fromhex(recipe["network"])
    if recipe["serializerClass"] == BOTANIA_MARIMORPHOSIS_SERIALIZER:
        return canonical_marimorphosis_network(recipe, original)
    if recipe["serializerClass"] in MEKANISM_ITEM_PREFIX_WRITERS:
        try:
            reader = NetworkReader(original)
            canonical = canonical_mekanism_item(reader)
            return (canonical + original[reader.offset:]).hex()
        except (ValueError, UnicodeError, RecursionError, OverflowError):
            return original.hex()
    writer = NETWORK_WRITERS.get(recipe["serializerClass"])
    if writer is None:
        return original.hex()
    try:
        reader = NetworkReader(original)
        count = len(recipe["ingredients"])
        if writer in ("shaped", "ct_shaped"):
            width, height = reader.varint(), reader.varint()
            if width * height != count:
                return original.hex()
            if writer == "shaped" and reader.utf() != recipe["group"]:
                return original.hex()
        elif writer == "shapeless":
            if reader.utf() != recipe["group"] or reader.varint() != count:
                return original.hex()
        elif writer == "single":
            if count != 1 or reader.utf() != recipe["group"]:
                return original.hex()
        elif writer == "smithing":
            if count != 2:
                return original.hex()
        elif writer == "counted" and reader.varint() != count:
            return original.hex()
        prefix = original[:reader.offset]
        for ingredient in recipe["ingredients"]:
            # The reviewed writer emits exactly this ingredient at exactly this offset.
            # Never find/replace byte substrings: they may also occur in outputs or extra fields.
            emitted = bytes.fromhex(ingredient["network"])
            if reader.take(len(emitted)) != emitted:
                return original.hex()
        return (prefix + b"".join(canonical_ingredients) + original[reader.offset:]).hex()
    except (ValueError, UnicodeError, RecursionError, OverflowError):
        return original.hex()  # layout changes are compared unchanged


def cross_start_view(data):
    """Called only after mode-specific schema validation; normalize narrowly documented views."""
    data["errors"] = sorted(OBJECT_IDENTITY.sub("", error) for error in data["errors"])
    data["uncoveredServerSemantics"].sort()  # emitter appends these in global recipe traversal order
    for tags in data["tags"].values():
        for values in tags.values():
            values.sort()
    for recipe in data["recipes"]:
        if "ingredients" not in recipe:
            continue
        canonical = [canonical_ingredient_network(ingredient) for ingredient in recipe["ingredients"]]
        recipe["network"] = canonical_recipe_network(recipe, canonical)
        for ingredient, wire in zip(recipe["ingredients"], canonical):
            ingredient["network"] = wire.hex()
            ingredient["candidates"].sort(key=exact_sort_key)
    data["recipes"].sort(key=lambda recipe: recipe["id"])
    return data


def first_difference(a, b, path="$"):
    """Ignore object-key insertion order only; preserve JSON types and float sign."""
    if type(a) is not type(b):
        return path + " (type)"
    if type(a) is dict:
        if a.keys() != b.keys():
            return path + " (object keys)"
        for key in sorted(a):
            difference = first_difference(a[key], b[key], f"{path}.{key}")
            if difference:
                return difference
    elif type(a) is list:
        if len(a) != len(b):
            return path + " (array length)"
        for index, (left, right) in enumerate(zip(a, b)):
            difference = first_difference(left, right, f"{path}[{index}]")
            if difference:
                return difference
    elif type(a) is float:
        if a.hex() != b.hex():
            return path
    elif a != b:
        return path
    return None


def canonical_dump(path, cross_start=False):
    data = read_snapshot(path, cross_start=cross_start)
    return cross_start_view(data) if cross_start else data


def difference_context(data, difference):
    match = re.match(r"^\$\.recipes\[([0-9]+)\](?:\.|$)", difference)
    if match and int(match[1]) < len(data["recipes"]):
        recipe = data["recipes"][int(match[1])]
        return f" (recipe={recipe['id']!r}, serializer={recipe.get('serializer', 'capture failed')!r})"
    return ""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cross-start", action="store_true",
                        help="compare documented cross-JVM ordering as multisets; permit matching capture errors")
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args(argv)
    try:
        left = canonical_dump(args.baseline, cross_start=args.cross_start)
        right = canonical_dump(args.candidate, cross_start=args.cross_start)
        difference = first_difference(left, right)
    except (OSError, ValueError, TypeError, KeyError, RecursionError, OverflowError) as error:
        print(f"INVALID: {error}", file=sys.stderr)
        return 2
    if difference:
        print(f"DIFFERENT: {difference}{difference_context(left, difference)}")
        return 1
    if args.cross_start:
        print(f"EQUAL (cross-start): {len(left['recipes'])} recipe IDs; per-type order, normalized views, scripts and scoped network payloads match.")
        print(f"Capture errors: {len(left['errors'])} matching normalized entries; failed capture fields remain unproven.")
        print("Network: only candidate ItemStack records at reviewed vanilla/CT/Create offsets and the Mekanism nucleosynthesizing/sawmill item-input prefix normalize; reviewed single-block Botania marimorphosis biome records compare as a multiset. All other bytes and unknown writers remain exact.")
    else:
        print(f"EQUAL: {len(left['recipes'])} ordered recipes; all declared views, payloads, scripts and tags match.")
    print("Coverage: NETWORK_AND_PUBLIC_VIEWS; arbitrary custom server-only state is not proven.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

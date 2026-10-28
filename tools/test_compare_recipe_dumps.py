"""Offline snapshot validation/equality contracts; no game actions."""
import contextlib
import copy
import io
import json
from pathlib import Path
import tempfile
import unittest
import compare_recipe_dumps as compare


def stack(item="minecraft:stone", count=2):
    return {"item": item, "count": count, "nbt": {"type": 10, "value": {
        "id": {"type": 8, "value": item}, "Count": {"type": 1, "value": str(count)},
        "tag": {"type": 10, "value": {
            "byte": {"type": 1, "value": "-128"},
            "short": {"type": 2, "value": "32767"},
            "integer": {"type": 3, "value": "2147483647"},
            "long": {"type": 4, "value": "9223372036854775807"},
            "float": {"type": 5, "bits": "80000000"},
            "double": {"type": 6, "bits": "3ff0000000000000"},
            "bytes": {"type": 7, "value": [-128, 127]},
            "list": {"type": 9, "elementType": 8, "value": [
                {"type": 8, "value": "first"}, {"type": 8, "value": "second"}]},
            "ints": {"type": 11, "value": [-2147483648, 2147483647]},
            "longs": {"type": 12, "value": ["-9223372036854775808", "9223372036854775807"]}
        }}
    }}}


def fixture():
    def recipe(name):
        return {"id": name, "serializer": "create:mixing",
                "serializerClass": "com.simibubi.create.content.recipe.Serializer",
                "class": "com.simibubi.create.content.recipe.MixingRecipe", "type": "create:mixing",
                "group": "", "special": False, "network": "010200ff", "displayResult": stack(),
                "ingredients": [{"class": "net.minecraft.world.item.crafting.Ingredient",
                                 "json": {"item": "minecraft:stone"}, "network": "00",
                                 "candidates": [stack(), stack("minecraft:dirt", 1)]}]}
    return {"schema": 1, "coverage": "NETWORK_AND_PUBLIC_VIEWS", "coverageNote": "limited",
            "run": {"timestamp": "2026-10-23T17:19:26.123456789Z", "compareMode": False,
                    "indexedRemovalsConfigured": True}, "scripts": {"nested/test.zs": "0" * 64},
            "recipes": [recipe("example:first"), recipe("example:second")],
            "typeOrder": {"create:mixing": ["example:first", "example:second"]},
            "tags": {"minecraft:item": {"forge:stones": ["minecraft:stone", "minecraft:dirt"]}},
            "errors": [], "uncoveredServerSemantics": ["example:first (create:mixing)"],
            "complete": True, "hiddenServerStateProven": False}


def tag_fields(value):
    return value["recipes"][0]["displayResult"]["nbt"]["value"]["tag"]["value"]


class ComparatorTest(unittest.TestCase):
    def load_text(self, content):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "snapshot.json"
            path.write_text(content, encoding="utf-8")
            return compare.read_snapshot(path)

    def load(self, value):
        return self.load_text(json.dumps(value))

    def assert_invalid_pair(self, value):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "snapshot.json"
            path.write_text(json.dumps(value), encoding="utf-8")
            with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(2, compare.main([str(path), str(path)]))

    def test_actual_emitted_fixture_is_valid_and_hidden_state_limit_is_informational(self):
        value = fixture()
        self.assertIsNone(compare.first_difference(self.load(value), self.load(value)))
        value["uncoveredServerSemantics"].append("arbitrary custom server-only fields")
        self.load(value)

    def test_only_documented_valid_run_metadata_is_ignored(self):
        first = fixture()
        second = copy.deepcopy(first)
        second["run"] = {"timestamp": "2026-10-24T01:00:00Z", "compareMode": True,
                         "indexedRemovalsConfigured": False}
        self.assertIsNone(compare.first_difference(self.load(first), self.load(second)))
        second["scripts"]["nested/test.zs"] = "1" * 64
        self.assertIsNotNone(compare.first_difference(self.load(first), self.load(second)))

    def test_recipe_order_and_per_type_order_are_preserved_together(self):
        first = fixture()
        changed = copy.deepcopy(first)
        changed["recipes"].reverse()
        changed["typeOrder"]["create:mixing"].reverse()
        self.assertIsNotNone(compare.first_difference(self.load(first), self.load(changed)))

    def test_global_and_per_type_orders_are_independent_but_both_compared(self):
        baseline = fixture()
        baseline["typeOrder"]["create:mixing"].reverse()
        self.assertIsNone(compare.first_difference(self.load(baseline), self.load(baseline)))
        candidate = copy.deepcopy(baseline)
        candidate["typeOrder"]["create:mixing"].reverse()
        self.assertIsNotNone(compare.first_difference(self.load(baseline), self.load(candidate)))
        candidate = copy.deepcopy(baseline)
        candidate["recipes"].reverse()
        self.assertIsNotNone(compare.first_difference(self.load(baseline), self.load(candidate)))

    def test_tag_candidate_ingredient_and_nbt_list_order_are_significant(self):
        first = fixture()
        for mutate in [
            lambda value: value["tags"]["minecraft:item"]["forge:stones"].reverse(),
            lambda value: value["recipes"][0]["ingredients"][0]["candidates"].reverse(),
            lambda value: tag_fields(value)["list"]["value"].reverse(),
            lambda value: tag_fields(value)["longs"]["value"].reverse(),
        ]:
            changed = copy.deepcopy(first)
            mutate(changed)
            self.assertIsNotNone(compare.first_difference(self.load(first), self.load(changed)))
        first["recipes"][0]["ingredients"].append(copy.deepcopy(first["recipes"][1]["ingredients"][0]))
        first["recipes"][0]["ingredients"][1]["json"] = {"item": "minecraft:dirt"}
        changed = copy.deepcopy(first)
        changed["recipes"][0]["ingredients"].reverse()
        self.assertIsNotNone(compare.first_difference(self.load(first), self.load(changed)))

    def test_typed_nbt_counts_and_serializer_extra_output_payloads_are_significant(self):
        first = fixture()
        for mutate in [
            lambda value: value["recipes"][0]["displayResult"].update(count=258),
            lambda value: tag_fields(value)["byte"].update(type=2),
            lambda value: tag_fields(value)["float"].update(bits="0"),
            lambda value: value["recipes"][0].update(network="010201ff"),
            lambda value: value["recipes"][0]["ingredients"][0].update(network="01"),
        ]:
            changed = copy.deepcopy(first)
            mutate(changed)
            self.assertIsNotNone(compare.first_difference(self.load(first), self.load(changed)))

    def test_paired_identical_malformed_snapshot_fields_are_invalid(self):
        mutations = [
            lambda value: value.update(schema=True), lambda value: value.update(schema=1.0),
            lambda value: value.update(run=["timestamp", "compareMode", "indexedRemovalsConfigured"]),
            lambda value: value["run"].update(compareMode=1),
            lambda value: value["run"].update(timestamp=True),
            lambda value: value["run"].update(unknown="must not be discarded"),
            lambda value: value.update(complete=False), lambda value: value.update(complete=1),
            lambda value: value.update(errors=["serializer failure"]),
            lambda value: value.update(errors={}), lambda value: value.update(recipes={}),
            lambda value: value["recipes"][0].update(special=0),
            lambda value: value["recipes"][0].update(serializer=False),
            lambda value: value["recipes"][0].update(serializerClass=""),
            lambda value: value["recipes"][0].pop("network"),
            lambda value: value["recipes"][0].update(network="f"),
            lambda value: value["recipes"][0].update(network="ff ff"),
            lambda value: value["recipes"][0].update(displayResult={"type": 10, "value": {}}),
            lambda value: value["recipes"][0]["displayResult"].update(count=True),
            lambda value: value["recipes"][0]["displayResult"].update(count=2.0),
            lambda value: value["recipes"][0]["displayResult"].update(count=1 << 31),
            lambda value: value["recipes"][0]["displayResult"].update(item=[]),
            lambda value: value["recipes"][0].update(ingredients={}),
            lambda value: value["recipes"][0]["ingredients"][0].pop("json"),
            lambda value: value["recipes"][0]["ingredients"][0].update(network="xz"),
            lambda value: value["recipes"][0]["ingredients"][0].update(candidates={}),
            lambda value: value["typeOrder"]["create:mixing"].pop(),
            lambda value: value["typeOrder"]["create:mixing"].append("example:first"),
            lambda value: value.update(typeOrder={}),
            lambda value: value.update(tags=[]),
            lambda value: value["tags"].update({"minecraft:item": []}),
            lambda value: value["tags"]["minecraft:item"].update({"forge:stones": "minecraft:stone"}),
            lambda value: value["tags"]["minecraft:item"]["forge:stones"].append(True),
            lambda value: value["scripts"].update({"nested/test.zs": "0" * 63}),
            lambda value: value["scripts"].update({"../escape.zs": "0" * 64}),
            lambda value: value.update(scripts=[]),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(case=index):
                value = fixture()
                mutate(value)
                self.assert_invalid_pair(value)

    def test_paired_malformed_nbt_types_ranges_and_containers_are_invalid(self):
        malformed = [
            {"type": True}, {"type": 10.0, "value": {}}, {"type": 13},
            {"type": 1, "value": "128"}, {"type": 2, "value": "-32769"},
            {"type": 3, "value": "2147483648"}, {"type": 4, "value": "9223372036854775808"},
            {"type": 1, "value": 1}, {"type": 1, "value": True}, {"type": 1, "value": "01"},
            {"type": 1, "value": "-0"}, {"type": 5, "bits": "100000000"},
            {"type": 6, "bits": "10000000000000000"}, {"type": 5, "bits": "00"},
            {"type": 7, "value": [128]}, {"type": 7, "value": [True]},
            {"type": 8, "value": 5}, {"type": 10, "value": []},
            {"type": 9, "elementType": True, "value": []},
            {"type": 9, "elementType": 8, "value": [{"type": 3, "value": "1"}]},
            {"type": 9, "elementType": 0, "value": [{"type": 0}]},
            {"type": 11, "value": [2147483648]}, {"type": 11, "value": [1.0]},
            {"type": 12, "value": [1]}, {"type": 12, "value": ["9223372036854775808"]},
            {"type": 1, "value": "1", "extra": "truncation guard"},
        ]
        for malformed_tag in malformed:
            with self.subTest(tag=malformed_tag):
                value = fixture()
                tag_fields(value)["custom"] = malformed_tag
                self.assert_invalid_pair(value)

    def test_json_nonfinite_duplicates_and_truncation_are_rejected(self):
        for number in (float("nan"), float("inf"), -float("inf")):
            value = fixture()
            value["recipes"][0]["ingredients"][0]["json"] = {"number": number}
            with self.assertRaises(ValueError): self.load(value)
        valid = json.dumps(fixture())
        for invalid in (valid[:-1], valid.replace('"schema": 1', '"schema": 1, "schema": 1'),
                        valid.replace('"special": false', '"special": 1e999', 1)):
            with self.assertRaises(ValueError): self.load_text(invalid)

    def test_json_types_and_signed_zero_are_not_coerced_during_comparison(self):
        for left, right in [(1, 1.0), (1, True), (0.0, -0.0)]:
            first, second = fixture(), fixture()
            first["recipes"][0]["ingredients"][0]["json"] = {"value": left}
            second["recipes"][0]["ingredients"][0]["json"] = {"value": right}
            self.assertIsNotNone(compare.first_difference(self.load(first), self.load(second)))

    def test_only_object_key_insertion_order_is_ignored(self):
        first = fixture()
        second = json.loads(json.dumps(first, sort_keys=True))
        self.assertIsNone(compare.first_difference(self.load(first), self.load(second)))
        self.assertIsNotNone(compare.first_difference({"a": [1, 2]}, {"a": [2, 1]}))


def wire_stack(item_id, count=1, nbt=b"\x00"):
    return b"\x01" + compare.encode_varint(item_id) + bytes([count]) + nbt


def cross_fixture(writer="net.minecraft.world.item.crafting.ShapelessRecipe$Serializer"):
    value = fixture()
    for recipe in value["recipes"]:
        recipe["serializerClass"] = writer
        recipe["serializer"] = "minecraft:crafting_shapeless"
        recipe["class"] = "net.minecraft.world.item.crafting.ShapelessRecipe"
        recipe["type"] = "minecraft:crafting"
        ingredient = recipe["ingredients"][0]
        ingredient["json"] = {"tag": "forge:stones"}
        ingredient["network"] = (b"\x02" + wire_stack(1, 2) + wire_stack(2)).hex()
        recipe["network"] = (b"\x00\x01" + bytes.fromhex(ingredient["network"]) + wire_stack(3)).hex()
    value["typeOrder"] = {"minecraft:crafting": [recipe["id"] for recipe in value["recipes"]]}
    return value


def permute_candidate_wire(value):
    for recipe in value["recipes"]:
        ingredient = recipe["ingredients"][0]
        old = ingredient["network"]
        ingredient["candidates"].reverse()
        ingredient["network"] = (b"\x02" + wire_stack(2) + wire_stack(1, 2)).hex()
        recipe["network"] = recipe["network"].replace(old, ingredient["network"])


class CrossStartComparatorTest(unittest.TestCase):
    def compare_pair(self, left, right, cross_start=True):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("left.json", "right.json")]
            for path, value in zip(paths, (left, right)):
                path.write_text(json.dumps(value), encoding="utf-8")
            args = (["--cross-start"] if cross_start else []) + list(map(str, paths))
            with contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
                return compare.main(args)

    def test_cross_start_normalizes_only_global_tag_and_candidate_order(self):
        first, second = cross_fixture(), cross_fixture()
        second["recipes"].reverse()
        second["tags"]["minecraft:item"]["forge:stones"].reverse()
        permute_candidate_wire(second)
        self.assertEqual(0, self.compare_pair(first, second))
        self.assertEqual(1, self.compare_pair(first, second, cross_start=False))

    def test_per_type_order_still_exact(self):
        first, second = cross_fixture(), cross_fixture()
        second["typeOrder"]["minecraft:crafting"].reverse()
        self.assertEqual(1, self.compare_pair(first, second))

    def test_tag_and_candidate_multiplicity_is_preserved(self):
        first = cross_fixture()
        for mutate in [
            lambda value: value["tags"]["minecraft:item"]["forge:stones"].append("minecraft:stone"),
            lambda value: value["recipes"][0]["ingredients"][0]["candidates"].append(stack()),
        ]:
            second = copy.deepcopy(first)
            mutate(second)
            self.assertEqual(1, self.compare_pair(first, second))
        duplicated = copy.deepcopy(first)
        duplicated["tags"]["minecraft:item"]["forge:stones"].append("minecraft:stone")
        reordered = copy.deepcopy(duplicated)
        reordered["tags"]["minecraft:item"]["forge:stones"].reverse()
        self.assertEqual(0, self.compare_pair(duplicated, reordered))

    def test_global_and_type_membership_duplicates_remain_invalid(self):
        for mutate in [
            lambda value: value["recipes"].append(copy.deepcopy(value["recipes"][0])),
            lambda value: value["typeOrder"]["minecraft:crafting"].append("example:first"),
            lambda value: value["typeOrder"]["minecraft:crafting"].pop(),
            lambda value: value["typeOrder"].update({"example:unknown": ["example:first"]}),
        ]:
            value = cross_fixture()
            mutate(value)
            self.assertEqual(2, self.compare_pair(value, value))

    def test_matching_errors_only_normalize_identity_hashes_and_order(self):
        first, second = cross_fixture(), cross_fixture()
        first["errors"] = ["example:first: E: Thing@1abc missing", "example:second: E: unchanged"]
        second["errors"] = ["example:second: E: unchanged", "example:first: E: Thing@DEADbeef missing"]
        first["complete"] = second["complete"] = False
        self.assertEqual(0, self.compare_pair(first, second))
        self.assertEqual(2, self.compare_pair(first, second, cross_start=False))
        second["errors"][1] = "example:first: E: Thing@DEADbeef changed"
        self.assertEqual(1, self.compare_pair(first, second))
        second["errors"] = copy.deepcopy(first["errors"]) + [first["errors"][0]]
        self.assertEqual(1, self.compare_pair(first, second))

    def test_identity_normalization_does_not_touch_other_data_or_partial_hex_words(self):
        first, second = cross_fixture(), cross_fixture()
        first["errors"] = ["example:first: E: Thing@faceword"]
        second["errors"] = ["example:first: E: Thing@deadword"]
        first["complete"] = second["complete"] = False
        self.assertEqual(1, self.compare_pair(first, second))
        second["errors"] = copy.deepcopy(first["errors"])
        second["recipes"][0]["group"] = "literal@abc"
        self.assertEqual(1, self.compare_pair(first, second))

    def test_valid_emitted_partial_prefixes_require_matching_capture_errors(self):
        for fields in [1, 4, 9]:
            value = cross_fixture()
            recipe = value["recipes"][0]
            ordered = ("id", "serializer", "serializerClass", "class", "type", "group", "special", "network", "displayResult", "ingredients")
            value["recipes"][0] = {key: recipe[key] for key in ordered[:fields]}
            value["errors"] = ["example:first: java.lang.SomeException: capture failed"]
            value["complete"] = False
            if fields == 4:  # actual createaddition failure occurs before type and has no per-type entry
                value["typeOrder"]["minecraft:crafting"].remove("example:first")
            self.assertEqual(0, self.compare_pair(value, value))
            value["errors"] = ["example:other: java.lang.SomeException: unrelated"]
            self.assertEqual(2, self.compare_pair(value, value))

    def test_capture_errors_never_excuse_malformed_or_truncated_fields(self):
        mutations = [
            lambda value: value["recipes"][0].pop("network"),
            lambda value: value["recipes"][0]["ingredients"][0].pop("json"),
            lambda value: value["recipes"][0].update(network="f"),
            lambda value: value["recipes"][0]["displayResult"].update(count=True),
            lambda value: value.update(errors=[{}]),
            lambda value: value.update(complete=True),
            lambda value: value.update(complete=0),
        ]
        for mutate in mutations:
            value = cross_fixture()
            value["errors"] = ["example:first: E: known error"]
            value["complete"] = False
            mutate(value)
            self.assertEqual(2, self.compare_pair(value, value))
        value = cross_fixture()
        value["complete"] = False
        self.assertEqual(2, self.compare_pair(value, value))

    def test_recipe_extra_payload_bytes_are_retained_with_permuted_ingredients(self):
        first, second = cross_fixture(), cross_fixture()
        permute_candidate_wire(second)
        second["recipes"][0]["network"] += "01"  # serializer-specific extra data cannot disappear
        self.assertEqual(1, self.compare_pair(first, second))
        first, second = cross_fixture(), cross_fixture()
        second["recipes"][0]["network"] = second["recipes"][0]["network"][:-2] + "01"
        self.assertEqual(1, self.compare_pair(first, second))

    def test_unknown_serializer_and_ambiguous_layout_keep_exact_recipe_bytes(self):
        first = cross_fixture("example.UnknownSerializer")
        second = copy.deepcopy(first)
        permute_candidate_wire(second)
        self.assertEqual(1, self.compare_pair(first, second))
        first, second = cross_fixture(), cross_fixture()
        # An ingredient payload also appears in output data, but its real structural offset is wrong.
        for value in (first, second):
            recipe = value["recipes"][0]
            recipe["network"] = "0002" + recipe["network"][4:]  # declared ingredient count is wrong
        permute_candidate_wire(second)
        self.assertEqual(1, self.compare_pair(first, second))

    def test_custom_ingredient_wire_bytes_never_normalize(self):
        first = cross_fixture()
        for recipe in first["recipes"]:
            recipe["ingredients"][0]["class"] = "example.CustomIngredient"
        second = copy.deepcopy(first)
        permute_candidate_wire(second)
        self.assertEqual(1, self.compare_pair(first, second))

    def test_full_typed_nbt_counts_json_types_and_nbt_array_order_remain_exact(self):
        first = cross_fixture()
        for mutate in [
            lambda value: value["recipes"][0]["displayResult"].update(count=3),
            lambda value: tag_fields(value)["byte"].update(type=2),
            lambda value: tag_fields(value)["list"]["value"].reverse(),
            lambda value: tag_fields(value)["longs"]["value"].reverse(),
            lambda value: tag_fields(value)["float"].update(bits="0"),
            lambda value: value["scripts"].update({"nested/test.zs": "1" * 64}),
            lambda value: value["recipes"][0]["ingredients"][0].update(json={"tag": "forge:other"}),
        ]:
            second = copy.deepcopy(first)
            mutate(second)
            self.assertEqual(1, self.compare_pair(first, second))

    def test_wire_stack_nbt_payloads_are_preserved_while_records_permute(self):
        ingredient = cross_fixture()["recipes"][0]["ingredients"][0]
        tag = b"\x0a\x00\x00\x03\x00\x01x\x00\x00\x00\x01\x00"
        one, two = wire_stack(1, 2, tag), wire_stack(2)
        ingredient["network"] = (b"\x02" + two + one).hex()
        canonical = compare.canonical_ingredient_network(ingredient)
        self.assertEqual(b"\x02" + b"".join(sorted([one, two])), canonical)
        changed = copy.deepcopy(ingredient)
        changed["network"] = changed["network"].replace("00000001", "00000002")
        self.assertNotEqual(canonical, compare.canonical_ingredient_network(changed))

    def test_mekanism_hidden_item_input_normalizes_candidates_and_retains_the_suffix(self):
        first = cross_fixture("mekanism.common.recipe.serializer.NucleosynthesizingRecipeSerializer")
        old = b"\x02" + wire_stack(1, 2) + wire_stack(2)
        new = b"\x02" + wire_stack(2) + wire_stack(1, 2)
        suffix = b"gas-input-output-duration"
        for recipe in first["recipes"]:
            recipe["serializer"] = "mekanism:nucleosynthesizing"
            recipe["ingredients"] = []  # Mekanism does not expose this through Recipe.getIngredients
            recipe["network"] = (b"\x00" + old + b"\x03" + suffix).hex()
        second = copy.deepcopy(first)
        for recipe in second["recipes"]:
            recipe["network"] = (b"\x00" + new + b"\x03" + suffix).hex()
        self.assertEqual(0, self.compare_pair(first, second))
        second["recipes"][0]["network"] = (b"\x00" + new + b"\x04" + suffix).hex()
        self.assertEqual(1, self.compare_pair(first, second))
        second["recipes"][0]["network"] = (b"\x00" + new + b"\x03" + suffix + b"changed").hex()
        self.assertEqual(1, self.compare_pair(first, second))

    def test_mekanism_sawmill_keeps_both_outputs_counts_and_secondary_chance(self):
        first = cross_fixture("mekanism.common.recipe.serializer.SawmillRecipeSerializer")
        old = b"\x00\x02" + wire_stack(1, 2) + wire_stack(2) + b"\x01"
        new = b"\x00\x02" + wire_stack(2) + wire_stack(1, 2) + b"\x01"
        chance = bytes.fromhex("3fd0000000000000")  # writeDouble(0.25)
        suffix = wire_stack(3) + wire_stack(4, 2) + chance
        for recipe in first["recipes"]:
            recipe["serializer"] = "mekanism:sawing"
            recipe["ingredients"] = []
            recipe["network"] = (old + suffix).hex()
        second = copy.deepcopy(first)
        for recipe in second["recipes"]:
            recipe["network"] = (new + suffix).hex()
        self.assertEqual(0, self.compare_pair(first, second))
        for changed_suffix in [
            wire_stack(5) + wire_stack(4, 2) + chance,
            wire_stack(3) + wire_stack(5, 2) + chance,
            wire_stack(3) + wire_stack(4, 3) + chance,
            wire_stack(3) + wire_stack(4, 2) + bytes.fromhex("3fe0000000000000"),
        ]:
            second["recipes"][0]["network"] = (new + changed_suffix).hex()
            self.assertEqual(1, self.compare_pair(first, second))

    def test_mekanism_alternative_order_and_unknown_variants_remain_exact(self):
        one = b"\x00\x01" + wire_stack(1) + b"\x01"
        two = b"\x00\x01" + wire_stack(2) + b"\x02"
        left = compare.NetworkReader(b"\x01\x02" + one + two)
        right = compare.NetworkReader(b"\x01\x02" + two + one)
        self.assertNotEqual(compare.canonical_mekanism_item(left), compare.canonical_mekanism_item(right))
        with self.assertRaises(ValueError):
            compare.canonical_mekanism_item(compare.NetworkReader(b"\x02\x00"))

    def test_recipe_difference_message_includes_id_and_serializer(self):
        value = cross_fixture()
        self.assertIn("example:first", compare.difference_context(value, "$.recipes[0].network"))
        self.assertIn("minecraft:crafting_shapeless", compare.difference_context(value, "$.recipes[0].network"))
        self.assertEqual("", compare.difference_context(value, "$.tags"))

    def test_reviewed_writer_prefixes_retain_dimensions_group_and_tail(self):
        layouts = [
            ("net.minecraft.world.item.crafting.ShapedRecipe$Serializer", b"\x01\x01\x00"),
            ("com.blamejared.crafttweaker.api.recipe.serializer.CTShapedRecipeSerializer", b"\x01\x01"),
            ("com.blamejared.crafttweaker.api.recipe.serializer.CTShapelessRecipeSerializer", b"\x01"),
            ("com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer", b"\x01"),
            ("net.minecraft.world.item.crafting.SimpleCookingSerializer", b"\x00"),
            ("net.minecraft.world.item.crafting.SingleItemRecipe$Serializer", b"\x00"),
        ]
        for writer, prefix in layouts:
            first = cross_fixture(writer)
            for recipe in first["recipes"]:
                recipe["network"] = (prefix + bytes.fromhex(recipe["ingredients"][0]["network"]) + b"\x12\x34").hex()
            second = copy.deepcopy(first)
            permute_candidate_wire(second)
            self.assertEqual(0, self.compare_pair(first, second), writer)
            second["recipes"][0]["network"] += "56"
            self.assertEqual(1, self.compare_pair(first, second), writer)


BOTANIA_OFF = "01018a10010205746169676106666f726573740b"
BOTANIA_ON = "01018a10010206666f726573740574616967610b"


def botania_fixture(network=BOTANIA_OFF):
    value = fixture()
    for recipe in value["recipes"]:
        recipe.update(serializer="botania:marimorphosis", type="botania:marimorphosis",
                      serializerClass=compare.BOTANIA_MARIMORPHOSIS_SERIALIZER,
                      **{"class": compare.BOTANIA_MARIMORPHOSIS_CLASS}, ingredients=[], network=network)
    value["typeOrder"] = {"botania:marimorphosis": [recipe["id"] for recipe in value["recipes"]]}
    return value


class BotaniaComparatorTest(unittest.TestCase):
    compare_pair = CrossStartComparatorTest.compare_pair
    def test_actual_marimorphosis_payloads_normalize_only_in_cross_start_mode(self):
        first, second = botania_fixture(BOTANIA_OFF), botania_fixture(BOTANIA_ON)
        self.assertEqual(0, self.compare_pair(first, second))
        self.assertEqual(1, self.compare_pair(first, second, cross_start=False))

    def test_marimorphosis_input_output_weights_bonus_and_biome_names_stay_exact(self):
        first = botania_fixture()
        changed_payloads = [
            "02" + BOTANIA_ON[2:],                         # input block ID
            BOTANIA_ON[:4] + "8b10" + BOTANIA_ON[8:],       # output block ID
            BOTANIA_ON[:8] + "02" + BOTANIA_ON[10:],       # base weight
            BOTANIA_ON[:-2] + "0c",                      # biome bonus
            BOTANIA_ON.replace("666f72657374", "646573657274"),  # forest -> desert
            BOTANIA_ON[:10] + "01" + BOTANIA_ON[12:],     # biome record count
        ]
        for payload in changed_payloads:
            with self.subTest(payload=payload):
                self.assertEqual(1, self.compare_pair(first, botania_fixture(payload)))

    def test_marimorphosis_multiplicity_and_display_count_nbt_stay_exact(self):
        first = botania_fixture()
        # Add a repeated taiga record; sorting must not silently deduplicate it.
        duplicate = "01018a10010305746169676106666f726573740574616967610b"
        self.assertEqual(1, self.compare_pair(first, botania_fixture(duplicate)))
        for mutate in [
            lambda value: value["recipes"][0]["displayResult"].update(count=3),
            lambda value: tag_fields(value)["byte"].update(type=2),
            lambda value: tag_fields(value)["float"].update(bits="0"),
            lambda value: tag_fields(value)["list"]["value"].reverse(),
        ]:
            second = botania_fixture(BOTANIA_ON)
            mutate(second)
            self.assertEqual(1, self.compare_pair(first, second))

    def test_marimorphosis_unknown_layouts_and_truncated_payloads_remain_byte_exact(self):
        for left, right in [
            (BOTANIA_OFF[:2] + "02" + BOTANIA_OFF[4:], BOTANIA_ON[:2] + "02" + BOTANIA_ON[4:]),
            (BOTANIA_OFF[:-2], BOTANIA_ON[:-2]),             # missing bonus
            (BOTANIA_OFF + "00", BOTANIA_ON + "00"),       # unreviewed trailing field
            (BOTANIA_OFF[:10] + "8200" + BOTANIA_OFF[12:], BOTANIA_ON[:10] + "8200" + BOTANIA_ON[12:]),
            (BOTANIA_OFF.replace("7461696761", "ff61696761"), BOTANIA_ON.replace("7461696761", "ff61696761")),
        ]:
            with self.subTest(payload=left):
                first, second = botania_fixture(left), botania_fixture(right)
                self.assertEqual(left, compare.canonical_recipe_network(first["recipes"][0], []))
                self.assertEqual(right, compare.canonical_recipe_network(second["recipes"][0], []))
                self.assertEqual(1, self.compare_pair(first, second))

    def test_marimorphosis_unknown_class_type_or_ingredient_layout_does_not_normalize(self):
        for key, value in [
            ("serializerClass", "example.OtherSerializer"),
            ("class", "example.OtherRecipe"),
            ("serializer", "example:other"),
            ("type", "example:other"),
            ("ingredients", [copy.deepcopy(fixture()["recipes"][0]["ingredients"][0])]),
        ]:
            first, second = botania_fixture(), botania_fixture(BOTANIA_ON)
            for data in (first, second):
                for recipe in data["recipes"]:
                    recipe[key] = copy.deepcopy(value)
                if key == "type":
                    data["typeOrder"] = {value: [recipe["id"] for recipe in data["recipes"]]}
            self.assertEqual(1, self.compare_pair(first, second))


if __name__ == "__main__":
    unittest.main()

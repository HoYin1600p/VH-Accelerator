"""Lossless comparison contracts using synthetic inventories only."""
from copy import deepcopy
import contextlib
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest

from compare_inventories import (SUPPORTED_JAR_SHA256, compare, load_inventory, main,
                                 validate_inventory)


def fixture():
    return {"schemaVersion": 1, "status": "complete", "provenance": {
        "jarSha256": SUPPORTED_JAR_SHA256, "jarVersion": "3.13.2-build.21+mc1.18.2",
        "sourceSha256": "a" * 64, "sourceBytes": 32,
        "originalCoordinateCodecVerified": True, "originalPaletteCodecVerified": True,
        "fullOriginalTemplateDecode": False}, "semantic": {"rawRootName": "",
            "rawSavedData": {"type": 10, "value": {"unknown": {"type": 1, "value": 1}}},
            "templates": [{"id": "fixture", "name": "Empty", "blocks": [], "header": {
                "type": 10, "value": {"min": {"type": 11, "value": [-2147483648, -1, 2147483647]}}}}]}}


class ComparisonTests(unittest.TestCase):
    def test_equal_empty_template_and_boundary_metadata(self):
        self.assertEqual("EQUAL", compare(fixture(), deepcopy(fixture()))["result"])

    def test_source_bytes_and_paths_are_provenance_only(self):
        a, b = fixture(), fixture()
        b["provenance"].update(sourceSha256="b" * 64, sourceBytes=40, sourcePath="copy-elsewhere")
        self.assertEqual("EQUAL", compare(a, b)["result"])

    def test_object_order_is_ignored(self):
        a, b = fixture(), fixture()
        b["semantic"] = dict(reversed(list(b["semantic"].items())))
        b["semantic"]["rawSavedData"]["value"]["unknown"] = {"value": 1, "type": 1}
        self.assertEqual("EQUAL", compare(a, b)["result"])

    def test_unknown_fields_numeric_types_and_values_are_semantic(self):
        for change in (lambda s: s.update(extra=True),
                       lambda s: s["rawSavedData"]["value"]["unknown"].update(type=3),
                       lambda s: s["rawSavedData"]["value"]["unknown"].update(value=1.0),
                       lambda s: s["rawSavedData"]["value"]["unknown"].update(value=True),
                       lambda s: s["rawSavedData"]["value"]["unknown"].update(value=2)):
            a, b = fixture(), fixture()
            change(b["semantic"])
            with self.subTest(change=change):
                self.assertEqual("DIFFERENT", compare(a, b)["result"])

    def test_coordinate_state_palette_tile_data_and_names_are_semantic(self):
        base = fixture()
        base["semantic"]["templates"][0].update(blocks=[{"coordinate": [-1, 0, 65535], "paletteIndex": 0}],
                                               blockPalette=[{"state": "fixture:block", "tile": {"value": 3}}])
        changes = (lambda t: t.update(name="Changed"),
                   lambda t: t["blocks"][0]["coordinate"].__setitem__(0, 0),
                   lambda t: t["blocks"][0].update(paletteIndex=1),
                   lambda t: t["blockPalette"][0].update(state="fixture:other"),
                   lambda t: t["blockPalette"][0]["tile"].update(value=4))
        for change in changes:
            other = deepcopy(base)
            change(other["semantic"]["templates"][0])
            self.assertEqual("DIFFERENT", compare(base, other)["result"])

    def test_all_list_order_and_lengths_are_retained(self):
        a, b = fixture(), fixture()
        a["semantic"]["ordered"] = [1, 2]
        b["semantic"]["ordered"] = [2, 1]
        self.assertEqual("DIFFERENT", compare(a, b)["result"])
        b["semantic"]["ordered"] = [1]
        self.assertEqual("list length", compare(a, b)["difference"]["reason"])

    def test_signed_zero_is_not_silently_normalized(self):
        a, b = fixture(), fixture()
        a["semantic"]["floating"] = -0.0
        b["semantic"]["floating"] = 0.0
        self.assertEqual("DIFFERENT", compare(a, b)["result"])

    def test_document_root_name_and_unicode_are_semantic(self):
        a, b = fixture(), fixture()
        a["semantic"]["rawRootName"] = "\ud800"
        b["semantic"]["rawRootName"] = "?"
        self.assertEqual("DIFFERENT", compare(a, b)["result"])
        b["semantic"]["rawRootName"] = "\ud800"
        self.assertEqual("EQUAL", compare(a, b)["result"])

    def test_supported_nbt_depth_survives_typed_json_envelopes(self):
        inventory = fixture()
        nested = '{"type":10,"value":' * 512 + '{}' + '}' * 512
        text = json.dumps(inventory).replace('"rawRootName": ""', '"rawRootName": "", "nested": ' + nested)
        previous_limit = sys.getrecursionlimit()
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "deep.json"
            path.write_text(text, encoding="utf-8")
            first = load_inventory(path)
            second = load_inventory(path)
            self.assertEqual("EQUAL", compare(first, second)["result"])
        self.assertEqual(previous_limit, sys.getrecursionlimit())

    def test_incomplete_wrong_archive_and_unverified_codecs_are_rejected(self):
        changes = (lambda a: a.update(status="incomplete"), lambda a: a.update(schemaVersion=True),
                   lambda a: a["provenance"].update(jarSha256="wrong"),
                   lambda a: a["provenance"].update(originalCoordinateCodecVerified=False),
                   lambda a: a["provenance"].update(originalPaletteCodecVerified=False),
                   lambda a: a["provenance"].update(fullOriginalTemplateDecode=True),
                   lambda a: a["semantic"].pop("rawSavedData"))
        for change in changes:
            a = fixture()
            change(a)
            with self.assertRaises(ValueError):
                validate_inventory(a)

    def test_duplicate_keys_nonfinite_and_truncated_json_fail(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "inventory.json"
            for text in ('{"a":1,"a":2}', '{"a":NaN}', '{"a":Infinity}', '{"a":'):
                path.write_text(text, encoding="utf-8")
                with self.subTest(text=text), self.assertRaises(ValueError):
                    load_inventory(path)

    def test_cli_exit_codes_and_no_input_writes(self):
        with tempfile.TemporaryDirectory() as folder:
            left, right = Path(folder) / "a.json", Path(folder) / "b.json"
            left.write_text(json.dumps(fixture()), encoding="utf-8")
            right.write_bytes(left.read_bytes())
            before = left.read_bytes()
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, main([str(left), str(right), "--json"]))
                changed = fixture()
                changed["semantic"]["templates"] = []
                right.write_text(json.dumps(changed), encoding="utf-8")
                self.assertEqual(1, main([str(left), str(right)]))
                right.write_text("{}", encoding="utf-8")
                self.assertEqual(2, main([str(left), str(right)]))
            self.assertEqual(before, left.read_bytes())


if __name__ == "__main__":
    unittest.main()

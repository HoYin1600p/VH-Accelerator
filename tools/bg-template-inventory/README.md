# BuildingGadgets offline template inventory

This discovery tool preserves the saved data of BuildingGadgets
`3.13.2-build.21+mc1.18.2`. It changes no runtime representation. Run it against
an independent, stopped-world **copy** of `data/buildinggadgets_template_save.dat`,
never a live world or the immutable recovery source. Keep its output outside the
copied world. No server is started and no saved-data load/save method is called.

Supply the legally obtained original archive explicitly. Its required SHA-256 is
`5eae7d0201adc89c4d6cdf80defb2807768a269090bb4ce7bc7c901cd208813a`.
There is no default local dependency or bundled BuildingGadgets implementation.
The inventory classes are test tools and are excluded from release jars.

From the repository root, with JDK 17 and the development dependencies available:

```sh
./gradlew bgadgetsInventory --no-daemon --max-workers=16 \
  -Pbgadgets_jar=../references/buildinggadgets.jar \
  -Pbgadgets_input=../disposable-copy/data/buildinggadgets_template_save.dat \
  -Pbgadgets_output=../inventories/original.json
```

On Windows use `gradlew.bat`. Quote property arguments containing spaces. Follow
the project's single-heavy-job lock procedure for every Gradle invocation.
The output must be a new file; do not direct it to an existing inventory, source
file, symlink, or world directory. Retain the raw input and its fingerprint.

The tool invokes unchanged original `MathUtils.posFromLong/readStateId` and
`DataDecompressor` code after checking the archive identity and method behavior.
It does **not** invoke `TemplateSave.getTemplate`: that getter marks the save
dirty and refreshes timestamps. It does not bootstrap incomplete block or tile
serializer registries, which could otherwise replace unknown modded states with
air. Complete raw state names, properties, serializer tags and associated tile
data remain in the typed NBT payload and palettes.

The JSON envelope contains:

- `schemaVersion` and a successful `status`;
- `provenance`, including input/archive fingerprints and original-codec checks;
- `semantic.rawSavedData`, the entire typed NBT root, including unknown fields;
- `semantic.templates`, identifiers, header/name/author metadata, timestamps,
  palettes and ordered coordinate/palette references.

Compound keys are canonicalized. Lists, palette order, template entry order,
numeric tag types and floating-point bits remain meaningful. Packed coordinates
are decoded with the original unsigned field behavior, not reinterpreted as
signed world coordinates; signed header bounds remain intact. Invalid palette
references and malformed input fail rather than producing an empty inventory.
`fullOriginalTemplateDecode` is deliberately false: equality here does not pass
the later registry-backed template load/save, placement or rollback gates.

Compare two inventories with Python 3.10 or later:

```sh
python tools/bg-template-inventory/compare_inventories.py original.json reopened.json
```

Exit codes are `0` for EQUAL, `1` for DIFFERENT and `2` for invalid input. `--json`
prints a machine-readable result and the first differing semantic path. Only
provenance is excluded from equality, allowing source compression or location to
differ. All semantic fields, including unknown fields, are compared. Unsupported
archives, incomplete results and missing original-codec verification are refused.
The comparer reads both JSON inventories into memory; run large comparisons as
offline jobs with sufficient free memory. It never opens the source world.

Local tests:

```sh
./gradlew test --no-daemon --max-workers=16 -Pbgadgets_jar=../references/buildinggadgets.jar
python -m unittest discover -s tools/bg-template-inventory -v
```

Without the opt-in archive, original-codec tests report an explicit assumption
skip. Acceptance verification must supply the pinned archive so those checks run.
Synthetic fixtures cover empty templates, packed coordinate boundaries, signed
header coordinates, all typed NBT, unknown metadata and malformed input. Real
backup counts, complete pack registry round trips and server operations remain
separate manager checks.

The format research and original comparison methods come from
[BuildingGadgets](https://github.com/Direwolf20-MC/BuildingGadgets), by Direwolf20
and contributors. The inspected release declares MIT; its
[release license](https://github.com/Direwolf20-MC/BuildingGadgets/blob/daf87c2/LICENSE.md)
provides the upstream terms. No upstream source or archive is redistributed.

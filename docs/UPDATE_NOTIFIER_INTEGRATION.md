# Reusable update notifier integration

VH Accelerator contains the canonical Forge 1.18.2 implementation under
`client/update`. It fetches the small GitHub manifest asynchronously, adds a
coordinated main-menu notice, and persists a rate-limited chat reminder. The
notifier remains active when a modpack disables Forge's global version checker.

The implementation is intended to be copied into another public mod rather
than added as a runtime library dependency. Relocate every copied class into
the destination mod's own package so multiple HoYin1600p mods never ship the
same fully qualified class.

## Required integration values

Each destination mod supplies only:

1. its mod ID;
2. the short display name used by notices;
3. its HTTPS CurseForge project URL;
4. the raw GitHub URL for its update manifest.

Register the unit from client-only initialization:

```java
UpdateNoticeService.initialize(
        MOD_ID,
        "Short Display Name",
        "https://raw.githubusercontent.com/HoYin1600p/REPOSITORY/master/update.json",
        "https://www.curseforge.com/minecraft/mc-mods/project-slug"
);
```

Keep this call behind the destination mod's physical-client gate. The copied
unit must never be referenced by a dedicated-server classloading path.

## `mods.toml`

Add the standard Forge update URL to the mod entry:

```toml
updateJSONURL = "https://raw.githubusercontent.com/HoYin1600p/REPOSITORY/master/update.json"
```

Add the coordination marker outside the `[[mods]]` table:

```toml
[modproperties."mod_id_here"]
hoyinUpdateNotifier = true
hoyinUpdateName = "Short Display Name"
```

The marker lets independently packaged copies calculate deterministic,
non-overlapping rows when several supported mods are installed. An up-to-date
integrated mod can leave an unused row, but notices never overlap.

## Manifest

Place `update.json` at the repository root:

```json
{
  "homepage": "https://www.curseforge.com/minecraft/mc-mods/project-slug",
  "1.18.2": {
    "1.2.3": "Performance Improvement"
  },
  "promos": {
    "1.18.2-latest": "1.2.3",
    "1.18.2-recommended": "1.2.3"
  }
}
```

Prefix the target version's message with `[CRITICAL]` for a critical notice:

```json
"1.2.3": "[CRITICAL] Critical Bug Fix"
```

The prefix is removed before display. A critical reminder appears on the first
successful fresh world join and again after every five additional successful
fresh joins. A normal reminder appears on the first successful fresh join and
again after every ten. Server transfers, dimension changes, and failed joins
do not count.

## Release order

1. Publish and verify the CurseForge file.
2. Publish the GitHub release.
3. Update the version, message, and promotion values in `update.json`.
4. Push the manifest last.

This order prevents installed clients from advertising an update before its
download is available. The notifier is independent of Forge's global update
preference so it remains reliable across modpacks. Its HTTPS request has strict
timeouts and size limits; network failures never block launch or world entry.

## Mapping variants

The canonical implementation uses Mojang/Parchment names and can be copied
directly into VH Accelerator, ArcaneBeam, VRO, and Sophisticated VH Compat.
Re-Forgematica uses Loom/Yarn and therefore needs only its Minecraft-facing
screen, text, and render-event adapter translated; the manifest, reminder
state, severity parsing, cadence, and release workflow stay unchanged.

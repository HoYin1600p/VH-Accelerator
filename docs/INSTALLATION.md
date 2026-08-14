# Installation

## Supported environment

VH Accelerator 1.0.9 targets Minecraft 1.18.2 and Forge 40.3.11 or newer in
the 40.x line. It produces Java 17-compatible bytecode.

The same jar contains guarded support for:

| Profile | Vault version | JEI version |
| --- | --- | --- |
| Remastered previous | `20.0.3-remastered.6872` | `10.2.1.1009` |
| Remastered current baseline | `20.0.3-remastered.6883` | `10.2.1.1009` |
| Remastered additional baseline | `20.0.3-remastered` | `10.2.1.1009` |
| Official previous | `3.21.5.6882` | `9.7.2.1001` |
| Official current baseline | `3.21.6.6884` | `9.7.2.1001` |
| Wolds Vaults 0.32.2 | `3.21.5.6573` | `10.2.1.1006` |
| Wolds Vaults 0.33.0 | `3.21.6.6884` | `10.2.1.1006` |
| Custom MVP | `3.21.62` | `9.7.2.1001` |

The entries above are compile and test baselines, not hard dependencies.
Unsupported Vault or JEI layouts leave their optional integration disabled
instead of making those mods mandatory.

The Wolds Vaults profile has completed repeated launch and texture-safety
runtime testing.

## Client installation

1. Stop the game and launcher-managed Java process.
2. Back up the instance.
3. Remove or disable any older VH Accelerator jar.
4. Remove or disable LaunchFaster, Lightspeed, and VHClientOptimize.
5. Copy `VH-Accelerator-1.0.7.jar` into the instance's `mods` directory.
6. Start the client and allow the first launch to build its caches.
7. Confirm the Mods screen reports version `1.0.7`.

VH Accelerator works client-side when connecting to a server that does not
have the mod. This is the expected deployment for public or otherwise
uncontrolled servers.

## Dedicated-server status

The public release is currently supported as a client mod. Dedicated-server
testing has not yet been completed, so server installation is not part of the
published support list. A separate server pass and deployment guide will be
published after that work is verified.

## Overlap with other performance mods

| Mod | Recommendation |
| --- | --- |
| LaunchFaster | Disable. It targets the same launch and model classes. |
| VHClientOptimize | Disable. Its Vault and JEI patches overlap VH Accelerator. |
| Lightspeed | Disable for an apples-to-apples test and to avoid duplicate resource/model caches. |
| ModernFix | Supported and optional. VH Accelerator yields overlapping work automatically. |
| LazyDFU | Its rule warm-up suppression is built into VH Accelerator's physical-client path; a separate copy is unnecessary there. |
| FerriteCore, Fastload, Smooth Boot, Starlight, Embeddium | Not replaced by VH Accelerator; retain only if appropriate for the pack. |

When diagnosing a crash, test the smallest relevant combination rather than
assuming every performance mod is composable.

## First launch and warm launches

The first launch performs original parsing and scanning before writing
fingerprinted cache data. A warm launch can reuse only the deterministic
portions that validate.

A cache rebuild is expected after changes to relevant inputs, including:

- mod versions or jar contents;
- resource packs or pack order;
- model-affecting configuration;
- item registry contents;
- synchronized server tags, recipes, or Forge server configuration;
- the server address for server-scoped caches;
- VH Accelerator's cache schema.

The log states why a cache missed when detailed diagnostics are enabled.

## Configuration migration

Development builds used the previous mod name and config filenames. When the
new config does not yet exist, VH Accelerator imports those settings once and
leaves the old files untouched for rollback.

The active files are:

- `config/vhaccelerator-common.toml`
- `config/vhaccelerator-client.toml`

Review [Configuration and commands](CONFIGURATION.md) after upgrading because
new settings receive release defaults.

## Updating

1. Stop the client or server.
2. Replace the old VH Accelerator jar; do not leave multiple versions active.
3. Read the version's release notes for cache-schema or config changes.
4. Launch once and inspect the log for mixin or compatibility fallbacks.
5. Validate models, JEI search, recipes, and one server transfer before broad
   deployment.

Persistent caches are versioned and self-invalidating. Manual deletion should
not normally be required.

## Removing the mod

1. Stop the game.
2. Remove the VH Accelerator jar.
3. Optionally remove `config/vhaccelerator-common.toml`,
   `config/vhaccelerator-client.toml`, and `cache/vhaccelerator/`.

The cache directory contains derived data only and is safe to regenerate.
Removing it makes the next VH Accelerator launch a cold-cache run.

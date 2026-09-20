# VH Accelerator 1.0.14

This is a major performance and stability pass for Minecraft 1.18.2 Vault Hunters clients.

## Highlights

- Added a broad set of guarded ModernFix-derived improvements for Forge registries, resource packs, recipes, language data, world generation, memory use, and runtime allocations.
- Improved repeated launch and login cache safety, including Forge mod resources, embedded mods, config changes, synchronized tags, and JEI runtime restarts.
- Reduced normal-play overhead by leaving diagnostic-only mixins unloaded when debug mode is off at startup.
- Improved background work scheduling so model preparation, disk reads, and optional network checks interfere less with one another.
- Added a version-guarded startup fix for Target Dummy `1.18-1.5.2`.
- Moved render-focused ModernFix backports to Vault Render Optimization so each mod retains a clear scope.
- Relicensed new development after 1.0.13 under LGPL-3.0-or-later, with complete upstream credits and provenance included in the JAR and repository.

Existing configs remain compatible. Some cache formats are refreshed automatically on the first launch after updating.

[Read the full technical changelog on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.14)

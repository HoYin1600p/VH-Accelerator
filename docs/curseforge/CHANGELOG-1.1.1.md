# VH Accelerator 1.1.1 — critical fix

Fixes two problems in 1.1.0. Everyone on 1.1.0 should update.

- Vault portals could fail to take you in: you stood in the portal and were
  never teleported, and relogging could bounce you between stuck vaults.
- Some blocks could render invisible, such as sideways logs, pillars and some
  slabs. If you set `deferBlockStateModelBaking = false` to work around it,
  you can set it back to `true` after updating.

[Read the detailed changelog on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.1.1)

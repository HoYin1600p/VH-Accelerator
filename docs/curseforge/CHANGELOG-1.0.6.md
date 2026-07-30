## 1.0.6 — Wold's Compatibility Pass

**Added**

- Added Wold's Vaults 0.32.2 compatibility for The Vault `3.21.5.6573` and
  JEI `10.2.1.1006`.
- Expanded Remastered compatibility to include `20.0.3-remastered`, `.6872`,
  and `.6883`.

**Improved**

- Reduced repeated CTM, model-registry, and complex voxel-shape work during
  large-pack client launches.
- Made persistent model-cache reuse more reliable when UI or renderer state
  files are rewritten between launches.
- Skipped EveryCompat's optional on-disk debug resource mirror while keeping
  all live generated resources enabled.

**Fixed**

- Prevented unsafe JER menu preloading in KubeJS packs; JER keeps its normal
  login-time initialization in that combination.

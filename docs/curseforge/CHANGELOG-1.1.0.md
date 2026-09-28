# VH Accelerator 1.1.0 — critical update

This update focuses on faster warm starts, lower memory use and safer recipe
and model loading in large Vault Hunters clients.

- Eligible block and item models can finish loading when needed, while
  dynamic and custom models retain compatibility safeguards.
- Improved CTM, Decocraft, Every Compat and large-pack texture preparation.
- Reduced JEI menu memory and fixed recipe-cache cases that could cause
  missing or repeatedly rebuilt recipes.
- Improved world-join preparation and cleanup after leaving singleplayer
  worlds.
- Added guarded fixes for several optional mods and Vault cake-vault slowdowns.
- Updated the Wolds Vaults compatibility baseline to 0.34.1.

The first launch after updating may refresh caches. VH Accelerator is intended
for clients and dedicated servers, but dedicated-server behavior has not yet
been tested. Test before using it on a production server.

[Read the detailed changelog on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.1.0)

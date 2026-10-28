# VH Accelerator 1.2.0

The big server update. VH Accelerator now speeds up dedicated servers as well
as clients, and the same jar works on both. Your settings carry over.

**Faster, lighter servers**

- In our paired tests, server startup was 24% faster on a large production
  pack and 32% faster on Wolds Vaults 0.34.3, and the server used 39% and 23%
  less memory after starting.
- Before release, 1.2.0 ran on five live servers through many
  restarts with no crashes, using far less memory than the day before.
- Big savings come from Vault loot tables, Building Gadgets templates and
  WorldZones, which now take much less memory. Recipe scripts (KubeJS,
  CraftTweaker, LootJS) and other startup work also load faster.

**Stability fixes**

- Fixes rare startup crashes and hangs caused by mods loading at the same
  time, including the long-reported EventBus crash.
- Fixes a reload race that could leave a server stuck while starting.
- Budding crystals no longer load neighbouring chunks while the world loads.

**New: Server settings tab**

- Server operators (permission level 4) can change the server's VH
  Accelerator settings from the in-game settings screen. Nobody else can.
  Needs Cloth Config, like the rest of the settings screen.

**Client fixes**

- Framed drawers no longer show the missing texture.
- The Vault cascade filter works again on Vault Hunters 21.6.

**Upgrade notes**

- Wolds Vaults players: update Wolds to a version with its own Rampage fix.
  VH Accelerator's old workaround for it has been removed.
- Java 24 or newer may print one harmless `sun.misc.Unsafe` warning at startup.

[Read the detailed changelog on GitHub](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.2.0)

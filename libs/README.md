# Local compile dependencies

Binary mod dependencies are intentionally excluded from Git. Put the pinned
files below in this directory before compiling:

- `the_vault-1.18.2-20.0.3-remastered.6872.jar`
- `the_vault-1.18.2-3.21.62.jar`
- `the_vault-1.18.2-3.21.5.6882.jar`
- `the_vault-1.18.2-3.21.6.6884.jar`
- `jei-1.18.2-forge-10.2.1.1009.jar`
- `jei-1.18.2-9.7.2.1001.jar`
- `Powah-3.0.8.jar`
- `JEITweaker-1.18.2-3.0.0.9.jar`
- `CraftTweaker-forge-1.18.2-9.1.213.jar`
- `thermal_foundation-1.18.2-9.2.2.58.jar`
- `JustEnoughResources-1.18.2-0.14.2.206.jar`
- `ironfurnaces-1.18.2-3.3.3.jar`
- `industrial-foregoing-1.18.2-3.3.1.7-11.jar`
- `Xaeros_Minimap_25.2.10_Forge_1.18.2.jar`
- `XaerosWorldMap_1.39.12_Forge_1.18.2.jar`
- `sophisticatedcore-1.18.2-0.6.4.604.jar`

The Remastered Vault jar is CurseForge project `458203`, file `8502584`.
The official 3.21.6 jar is project `458203`, file `8508967`; its immediately
previous standard release is file `8508674`. The 3.21.62 jar is the custom
MVP target. These files are used only for compile-time compatibility checks
and are not redistributed.

JEI 10 is the Remastered target. JEI 9 is shared by the custom MVP and
official 3.21.6 profiles. Both are compile-only and are not bundled.

The Powah, JEITweaker, CraftTweaker, Thermal Foundation, Just Enough
Resources, Iron Furnaces, Industrial Foregoing, and Sophisticated Core jars
are optional compile-time compatibility targets and are never bundled.

The Xaero's Minimap 25.2.10 and World Map 1.39.12 jars pin the exact
client-startup hooks used to move their online checks beyond the launch
barrier. They are compile-only and are never bundled.

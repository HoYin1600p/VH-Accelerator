# Local compile dependencies

Binary mod dependencies are intentionally excluded from Git. Put the pinned
files below in this directory before compiling:

- `the_vault-1.18.2-20.0.3-remastered.6872.jar`
- `the_vault-1.18.2-3.21.62.jar`
- `the_vault-1.18.2-3.21.6.6884.jar`
- `jei-1.18.2-forge-10.2.1.1009.jar`
- `jei-1.18.2-9.7.2.1001.jar`
- `Powah-3.0.8.jar`
- `JEITweaker-1.18.2-3.0.0.9.jar`
- `CraftTweaker-forge-1.18.2-9.1.213.jar`

The Remastered Vault jar is CurseForge project `458203`, file `8502584`.
The official 3.21.6 jar is project `458203`, file `8508967`. The 3.21.62 jar
is the custom MVP target. These files are used only for compile-time
compatibility checks and are not redistributed.

JEI 10 is the Remastered target. JEI 9 is shared by the custom MVP and
official 3.21.6 profiles. Both are compile-only and are not bundled.

The Powah, JEITweaker, and CraftTweaker jars are optional compile-time
compatibility targets and are never bundled.

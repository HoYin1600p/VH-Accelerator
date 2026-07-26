# Local compile dependencies

Binary mod dependencies are intentionally excluded from Git. Put the pinned
files below in this directory before compiling:

- `the_vault-1.18.2-20.0.3-remastered.6872.jar`
- `jei-1.18.2-forge-10.2.1.1009.jar`
- `Powah-3.0.8.jar`
- `JEITweaker-1.18.2-3.0.0.9.jar`
- `CraftTweaker-forge-1.18.2-9.1.213.jar`

The Vault jar is CurseForge project `458203`, file `8502584`. It is used only
as a compile-time compatibility target and is not redistributed.

The JEI version matches the read-only VaultCrafters instance used for
compatibility comparison.

The Powah, JEITweaker, and CraftTweaker versions also match that instance.
They are optional compile-time compatibility targets and are never bundled.

# Local compile dependencies

Binary mod dependencies are intentionally excluded from Git. Put the pinned
files below in this directory before compiling:

- `the_vault-1.18.2-3.21.62.jar`
- `jei-1.18.2-9.7.2.1001.jar`
- `Powah-3.0.8.jar`
- `JEITweaker-1.18.2-3.0.0.9.jar`
- `CraftTweaker-forge-1.18.2-9.1.213.jar`

The Vault and JEI jars match the read-only VaultersParadise mod-pack
repository used by the ten-server cluster. They are compile-time compatibility
targets and are not redistributed.

The Powah, JEITweaker, and CraftTweaker versions also match that instance.
They are optional compile-time compatibility targets and are never bundled.

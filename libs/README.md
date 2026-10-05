# Local dependency overrides (optional)

VH Accelerator downloads every mod it compiles against from CurseForge through
[CurseMaven](https://cursemaven.com), so a clean clone builds with `./gradlew build` and no
extra files. The CurseForge file ids are listed in `gradle.properties` (main build) and in
`compatibilityProfiles` in `build.gradle` (compatibility checks).

This folder is not needed. To build against a jar on your own disk while developing, pass its
path as a property:

```
./gradlew build -Pvault_jar=path/to/the_vault.jar
```

or put the same line (`vault_jar=path/to/the_vault.jar`) in your user-level
`~/.gradle/gradle.properties`. Never commit an override to the repository's `gradle.properties`.

Override names for the main build: `vault_jar`, `jei10_jar`, `jei9_jar`, `powah_jar`,
`jeitweaker_jar`, `crafttweaker_jar`, `thermal_foundation_jar`, `jer_jar`,
`ironfurnaces_jar`, `industrial_foregoing_jar`, `xaeros_minimap_jar`, `xaeros_world_map_jar`,
`sophisticatedcore_jar`, `ferritecore_jar`, `kubejs_jar`, `rhino_jar`, `create_jar`.
Each compatibility profile also accepts `<profile>_vault_jar` and `<profile>_jei_jar`, for
example `asgard_vault_jar`.

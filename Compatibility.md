# Model bakery compatibility API

VHA exposes a client-only Java API for mods that supply native models and
blockstates, including mods that keep their JSON in binary resource bundles.
Providers contribute through VHA's loading path without competing for the same
Mixin redirect. Bundled resources can stay bundled.

This guide targets Minecraft 1.18.2, Forge, Java 17, and API version `1`, which
ships in VHA 1.1.5 and later. VHA 1.1.4 and earlier do not include the API.

## 1. Make the integration optional

Compile your integration against a VHA build containing
`dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents`. Do not package VHA's
API classes inside your mod. Use a development dependency supplied by your build
or a published Maven artifact when one is available; this guide does not assume
a published API coordinate exists.

Keep direct API references in a separate client integration class. Load that
class only after checking that VHA and the API class are present. A mod-ID check
alone is insufficient because older VHA jars do not contain this API. Reflection
is another option when you want to avoid a compile-time VHA dependency; Buildscape
uses that approach.

Keep your existing loader available when the API is absent, incompatible, or
registration fails. Do not load this client API on a dedicated server.

## 2. Implement a provider

The following adapter is a complete abstract example. Implement its three
protected methods using your mod's existing resource storage and conversion code:

```java
import dev.hoyin1600p.vhaccelerator.api.client.ModelBakeryEvents;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

public abstract class BundledModelProvider implements ModelBakeryEvents.Listener {
    protected abstract String sourcePack(ResourceManager resources);

    protected abstract BlockModel findModel(ResourceLocation location);

    protected abstract BlockModelDefinition findBlockState(
            ResourceLocation location,
            StateDefinition<Block, BlockState> states);

    @Override
    public ModelBakeryEvents.Model model(
            ResourceManager resources, ResourceLocation location) {
        BlockModel value = findModel(location);
        return value == null ? null
                : new ModelBakeryEvents.Model(sourcePack(resources), value);
    }

    @Override
    public ModelBakeryEvents.BlockStateDefinition blockState(
            ResourceManager resources,
            ResourceLocation location,
            StateDefinition<Block, BlockState> states) {
        BlockModelDefinition value = findBlockState(location, states);
        return value == null ? null
                : new ModelBakeryEvents.BlockStateDefinition(
                        sourcePack(resources), value);
    }
}
```

- Model IDs look like `your_mod:block/example`, without `models/` or `.json`.
- Blockstate IDs look like `your_mod:example`, without `blockstates/` or `.json`.
- Return `null` when your provider does not supply a resource.
- `sourcePack` must return the exact `PackResources.getName()` of the active pack
  owning that contribution. Inspect `resources.listPacks()` to locate it; do not
  guess a display name or simply choose the highest pack.
- Return your own pack's blockstate definition even when another pack overrides
  it. VHA merges definitions in pack order and preserves higher-priority overrides.
- Do not perform OpenGL work, world mutations, or recursive model lookups inside
  a provider callback. Keep provider state safe for model-loading worker threads.

## 3. Register before the first resource reload

From your guarded client integration setup, register one listener per namespace:

```java
ModelBakeryEvents.Registration result = ModelBakeryEvents.register(
        "your_mod", ModelBakeryEvents.API_VERSION, provider);

if (result != ModelBakeryEvents.Registration.REGISTERED) {
    // Report the result and retain your normal loader.
}
```

Here, `provider` is your implementation of the adapter above. Register once,
before the first model preparation begins. Registrations are snapshotted for
each reload, so registering during a reload does not affect that reload.

Results are `REGISTERED`, `DUPLICATE_NAMESPACE`, `UNSUPPORTED_VERSION`, or
`INVALID_ARGUMENT`. A duplicate does not replace the existing provider. Namespace
ownership alone does not prove a particular resource was supplied successfully.

Optional listener callbacks run in this order:

1. `beforePreparation(ResourceManager)` — initialize reload-specific resources.
2. `model(...)` and `blockState(...)` — supply requested contributions.
3. `afterPreparation(ResourceManager)` — finish preparation.
4. `beforeBaking(ResourceManager)` — prepare for baking.
5. `afterBaking(ResourceManager)` — clear reload-specific caches.

VHA owns the session lifecycle. Providers should not call `begin()` or close a
session themselves. Callbacks may declare `throws Exception`.

## 4. Cooperate with existing loading hooks

A new provider normally only needs registration. If your mod already redirects
model-bakery resource lookup, registration alone does not remove that conflict.
Adapt the existing hook to cooperate, as Buildscape does:

- Forward blockstate resource lookup through
  `ModelBakeryEvents.resources(bakery, resourceManager, location)`. Allow lookup
  failures to reach your existing fallback. With no VHA access on the bakery,
  the helper uses ordinary resource lookup.
- Query `ModelBakeryEvents.model(bakery, location)` before your native model
  fallback. A `null` result means you must continue through the existing loader.
- Use `ModelBakeryEvents.hasBlockState(bakery, location)` at your existing
  blockstate insertion point. Skip your duplicate contribution only when this
  returns `true`; VHA has then accepted the definition for that reload.

These helpers accept the actual `ModelBakery` instance as `Object`. They do not
require your mixin to implement `ModelBakeryEvents.Access`; VHA supplies that
interface. Do not introduce another required redirect at the same instruction.
Exact injection points still need auditing for your mod and Minecraft version.

## 5. Check fallback and resource-pack behavior

Test your mod alone, with the supported VHA build, and with an older VHA build
that lacks this API. Check a cold launch, a resource reload, models with parent
references, multipart and variant blockstates, nested resource paths, and a
higher-priority resource pack overriding both models and blockstates.

Also test invalid registrations and a provider callback that throws. VHA logs a
recoverable callback failure once and disables that provider for the current
reload; it retries on the next reload and attempts cleanup after baking. This
contains provider errors, but it cannot reconstruct bundled-only resources when
their only provider fails. Retain a working fallback and test it explicitly.
Fatal VM failures and errors outside the guarded callbacks are not covered.

Source: [ModelBakeryEvents.java](src/main/java/dev/hoyin1600p/vhaccelerator/api/client/ModelBakeryEvents.java).

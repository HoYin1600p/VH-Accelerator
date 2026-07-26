# Dedicated-server safety and startup scope

## Physical-side boundary

LaunchFasterToo is intentionally installed on both sides, but the dedicated
server does not initialize or transform its client compatibility code.

- Client initialization is behind Forge's physical-side executor.
- Client mixins are in the mixin configuration's `client` list.
- The mixin plugin independently rejects every `.client.` and `.compat.`
  mixin when the physical distribution is a dedicated server.
- JEI, JEITweaker, Powah, Vault tooltip/group, GUI, model, texture, and client
  launch-timer classes are therefore not resolved on a dedicated server.
- Optional Vault and recipe-viewer jars are compile-only and are not bundled
  or required by the server.
- The dedicated-server main timer is rejected on a physical client and applies
  only to the dedicated-server entry point.

A Forge `40.3.11` dedicated-server userdev launch reached the EULA boundary
with the server timer mixed in and no client-class or optional-mod loading
failure.

## Server paths in LaunchFasterToo

| Path | Default without ModernFix | With ModernFix |
| --- | --- | --- |
| Dedicated-server launch timer | Active | Active |
| Resource-list result cache | Active | Disabled in favor of ModernFix |
| Instrumented reload replacement | Disabled | Disabled in favor of ModernFix |
| Registry validation skipping | Disabled | Disabled in favor of ModernFix |
| Registry dump skipping | Disabled | Disabled in favor of ModernFix |
| Parallel BlockState cache initialization | Disabled | Disabled in favor of ModernFix |
| Lazy BlockState caches | Disabled | Disabled in favor of ModernFix |

The aggressive compatibility switches remain available for controlled
experiments but are not safe defaults:

- registry validation must not be probabilistically skipped;
- registry diagnostic output should not be suppressed without a measured
  benefit;
- modded BlockState cache initialization is not assumed to be thread-safe;
- the replacement reload coordinator currently adds timing visibility but
  does not reduce the work already overlapped by Minecraft 1.18.2.

## Optimization ownership with ModernFix

The inspected compatibility instance already contains ModernFix, LazyDFU,
Lightspeed, Fastload Reforged, FerriteCore, Smooth Boot, and Starlight.
LaunchFasterToo yields overlapping transformations to ModernFix. This keeps
the primary build deliberately conservative: it measures total
dedicated-server startup and does not stack another implementation over the
pack's existing registry, resource, reload, or BlockState optimizations.

The next useful server work should be driven by timings from the real cluster.
Likely investigation points are Vault configuration loading and individual
server data-pack reload listeners. They must be profiled separately before
parallelization because both publish large mutable registries and config
graphs.

## Deployment requirement

Remove `launchfaster-1.0.jar` from both the client and server test
environments before adding LaunchFasterToo. The two mods target several of the
same generic startup classes.

# Contributing to VH Accelerator

Contributions are welcome when they preserve correctness, visual
compatibility, and a playable first world frame.

## Development setup

Requirements:

- JDK 17
- Git
- the compile-only jars listed in [`libs/README.md`](libs/README.md)

Build:

```powershell
.\gradlew.bat clean build
```

The `check` lifecycle compiles the universal source against all four Vault/JEI
profiles and verifies the packaged compatibility layout.

## Repository boundaries

Do not commit:

- original or decompiled mod jars;
- the ignored `reference/` directory;
- compile-only jars under `libs/`;
- instance configs, caches, logs, crash reports, tokens, server addresses, or
  local absolute paths.

Reference material must remain local and legally obtained. A tracked document
may record behavioral findings without reproducing third-party source.

## Optimization design rules

1. Measure a repeatable bottleneck before changing it.
2. Keep worker input immutable or privately owned.
3. Publish complete results at one defined barrier.
4. Preserve encounter order when the original API exposes order.
5. Keep OpenGL, live entities, Forge ordered callbacks, and unknown mod code on
   their required thread.
6. Invalidate persistent data against every input that can change its result.
7. Reject stale client-session work after transfer or disconnect.
8. Provide an original sequential fallback for worker or compatibility
   failure.
9. Keep optional integrations presence- and layout-gated.
10. Do not lower a timer by hiding required work after the first playable
    frame.

Dynamic models must be treated as unsafe until proven otherwise. A namespace
allowlist is not a substitute for understanding a custom loader's behavior.

## Testing

Follow [Testing and benchmarking](docs/TESTING.md). At minimum:

- run `clean build`;
- test a cold and warm client launch;
- test repeated login, transfer, and disconnect;
- inspect JEI search and recipes;
- inspect Vault gear and GUI atlases;
- inspect dynamic/custom models in-world and in JEI;
- compare with ModernFix present when changing a shared loading path;
- start a dedicated server when changing common code.

Performance claims should include individual samples and a median, not one
best run.

## Documentation and credit

New user-facing settings require updates to:

- `README.md` when the feature is significant;
- `docs/CONFIGURATION.md`;
- `CHANGELOG.md`;
- the current release-note draft.

If another project materially informed the work, update `CREDITS.md` with its
author, source link, and the specific discovery relationship. Do this even
when no code was copied.

## Pull requests

Keep changes focused and commits descriptive. Explain:

- the measured bottleneck;
- the thread-safety and publication boundary;
- cache dependencies and invalidation, if applicable;
- fallback behavior;
- supported mod/version layouts;
- tests and observed timing results.

Do not combine broad formatting changes with optimization code.

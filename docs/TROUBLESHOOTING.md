# Troubleshooting

## Start with safe isolation

1. Back up the instance.
2. Make sure only one VH Accelerator jar is active.
3. Disable LaunchFaster, Lightspeed, and VHClientOptimize.
4. Restore VH Accelerator's generated defaults.
5. Run:

   ```text
   /vha compare on
   /vha timers on
   /vha debug on
   ```

6. Restart and reproduce the problem once.

If Compare Mode still reproduces the failure, the optimization code is
disabled but instrumentation and mixin presence remain. That distinction is
useful when identifying an incompatibility.

## Startup crash

Collect:

- the complete launcher output;
- `logs/latest.log`;
- the newest crash report;
- Minecraft, Forge, Vault, JEI, ModernFix, and VH Accelerator versions;
- whether the previous launch completed successfully.

Do not diagnose only from the final exception line. Mixin failures and the
first `Caused by` entry earlier in the log are usually more useful.

## Missing or broken models

1. Turn on detailed diagnostics and restart.
2. Check whether the affected model uses Forge custom geometry, a runtime
   loader, or dynamic upgrades.
3. Confirm `protectDynamicModels = true`.
4. Test Compare Mode.
5. Stop the game and remove `cache/vhaccelerator/client-assets/`, then retest.

Deleting the derived cache is safe. Do not force a dynamic model onto a
parallel path merely to improve the launch timer.

When reporting the issue, include the affected item/block IDs and whether they
are broken in-world, in JEI, or both.

## A recipe or ingredient is missing only from JEI

First confirm the recipe still works in a crafting grid or another recipe
viewer. Without disconnecting, run:

```text
/vha reload_jei
```

This rebuilds JEI from the live recipe and tag state and bypasses VHA's core
JEI caches and parallel index paths for that recovery pass. If the entry is
still absent, retain `logs/latest.log` before restarting so the failed session
can be diagnosed.

## JEI appears late or the first world frames lag

The post-login timer should finish without a large untracked gameplay stall.
Record the server-login and post-login values, then test these settings one at
a time:

- `asyncJeiSearchIndex`
- `parallelJeiSearchPrefixes`
- `parallelJeiIngredientSorting`
- `parallelVanillaRecipeValidation`
- the relevant plugin-specific cache

Do not enable `persistentVanillaRecipeValidationCache` as a general fix; its
large accepted-ID manifest can take longer to resolve than the default bounded
parallel validation pass.

## Cache misses every launch

Enable debug diagnostics and look for the changed dependency. Common causes
include:

- a config file rewritten with meaningful content changes;
- a resource pack or pack-order change;
- mod jars touched or replaced by a launcher;
- switching server addresses;
- changed synchronized tags, recipes, or Forge server config;
- a cache-schema update.

Volatile map, voice, shader, and renderer UI files are intentionally excluded
where they cannot affect the cached result.

## ModernFix interaction

VH Accelerator asks ModernFix for its effective dynamic-resource setting. If
that cannot be determined safely, VH Accelerator assumes the overlapping
ModernFix path is active and leaves those model transformations disabled.

Include ModernFix's configuration and the ownership messages from the startup
log when reporting a model-loading problem.

## Server transfer or disconnect issue

Reproduce:

1. one normal initial login;
2. one backend/world transfer;
3. a second rapid transfer;
4. disconnect while post-login work is incomplete.

Enable debug diagnostics before connecting. The log should show session
generation changes and reject late work from an old world. For disconnect
problems, include the network-close, world-teardown, Forge logout-listener, and
menu-transition timings.

## Resetting VH Accelerator

With the game stopped, remove:

- `config/vhaccelerator-common.toml`
- `config/vhaccelerator-client.toml`
- `cache/vhaccelerator/`

The next launch recreates defaults and performs cold-cache work.

## Filing a report

Use the repository's
[bug report form](https://github.com/HoYin1600p/VH-Accelerator/issues/new/choose).
Attach logs as files rather than pasting thousands of lines into the issue
body.

Redact access tokens, session IDs, private server addresses, and local
profile-directory names. Do not redact mod IDs, versions, stack traces, timing
lines, or the affected resource identifiers.

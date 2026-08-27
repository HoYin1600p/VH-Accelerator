## 1.0.11 - Targeted JEI Repair Pass

**Fixed**

- Fixed another intermittent case where a synchronized crafting recipe could
  be missing from JEI until the recipe list was manually reloaded.
- Cached JEI recipes are now checked against the current category and output
  identity, and only affected recipes are rebuilt.
- Added safe handling for Sophisticated Storage recipes whose wood identity
  changes between launches.

**Added**

- Added `/vha jei_audit on|off|status` for opt-in recipe-cache troubleshooting.
  It is disabled by default and does not enable the full debug profiler.

Older JEI recipe-index caches are refreshed automatically. No manual cache or
configuration reset is required.

[Read the detailed GitHub changelog](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.11)

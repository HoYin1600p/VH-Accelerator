## 1.0.10 — JEI Cache Correctness Pass

**Fixed**

- Fixed an issue where normal crafting-table recipes could occasionally be
  missing from JEI after connecting.
- Prevented smaller late recipe batches from replacing the complete cached JEI
  recipe list.
- JEI now checks recipe categories against the live game state on every login
  instead of trusting an older category result.

**Changed**

- Improved JEI cache validation so real recipe, tag, server configuration, or
  mod changes rebuild the affected cache automatically.
- Kept the existing JEI loading optimizations while making recipe and category
  refreshes safer.

No configuration reset or manual cache deletion is required when updating.

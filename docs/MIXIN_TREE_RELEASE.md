# Mixin class-tree release

Common `[optimizations]` `releaseMixinClassTrees` (default `true`, dedicated servers
only, restart required).

## Default and upgrade

Enabled by default after paired dedicated-server and reload checks. Defaults
revision 4 moves the old false to true once for older revision markers; a
missing key uses true without rewriting the file. Subsequent opt-outs and
choices covered by revisions 1-3 are not replayed. As with earlier default
upgrades, an explicit old-default false cannot be distinguished from a generated
false before the upgrade; set it again afterwards to keep it. The Experimental
button leaves this option alone, so it preserves that opt-out.

The production-pack paired run released 1,362 of 1,539 trees after start in about 85 ms
(3,250 to 1,311 KiB of stored class bytes), then 27 more in about 4 ms at five
minutes. Every sweep had zero failed or non-byte-stable entries. Reload and
a late armor-stand class check passed; all rows held 20 TPS. One of three
performance pairs flagged a p95 increase, with the other two flat, below the
two-pair trigger. Earlier eight off/on simultaneous soak pairs had no failures.
The roughly 25-40 MB live-heap saving is indicative, not a guaranteed pack
saving or startup-speed improvement. Runtime eligibility and fallbacks below
are unchanged.

## What it frees

Mixin 0.8.5 keeps one ASM class tree per mixin in `MixinInfo.state.classNode` for the whole run.
On the Wolds server pack (2,830 `MixinInfo`, 2,536 `MixinClassNode`) those trees retain about 41 MB.
MixinSquared 0.3.7 (bundled in `cable_facades`) replaces each applied mixin's tree with a fresh
`MixinClassNode` copy on every application, which is why Wolds has far more of them than a pack
without MixinSquared (a plain Vault Hunters server: 154).

## What reads the tree

Only `MixinInfo.State.createClassNode`, which copies the stored tree into a new working node with
`ClassNode.accept`. It is called through `MixinInfo.getClassNode(int)` when a mixin is applied to a
target (`createContextFor`), and by extensions that ask the mixin for its code at the same moment
(MixinSquared's `ExtensionAnnotationAdjust.preApply`). Nothing else reads it: mixin inheritance, the
debug export, hot-swap reload and `@Pseudo` handling use the mixin's `ClassInfo` or build a new
state. So a tree is needed again only if the mixin is applied to another class.

## What the release does

At server start, and once more five minutes later, for each mixin of every active config (inside
Mixin's own `MixinProcessor` monitor, 32 mixins at a time):

1. skip it when it is pseudo, has no resolved target, or has no validated state;
2. skip it unless Mixin's class tracker (`IClassTracker.isClassLoaded`) reports every target loaded;
3. write the tree to class bytes, read them back and write again; skip it unless the two byte
   arrays are equal (the tree must survive the round trip exactly);
4. replace `State.classNode` with a `ReleasedMixinTree` that keeps the bytes deflated and answers
   `accept(ClassVisitor)` by reading them with `EXPAND_FRAMES`, which is how the original was built.

Nothing force-loads a class. If a loaded target is ever re-transformed (a `ClassInfo` cache miss,
another mod asking Mixin for a transformed class), Mixin gets an equal tree rebuilt from the bytes,
so the worst case is a little extra work and, with MixinSquared, a fresh tree that the next sweep
releases again. There is no state in which Mixin sees a missing or empty tree.

## Guards

- The active Mixin version must be exactly `0.8.5` and the hot-swap option must be off.
- The active transformer must be Mixin's `MixinTransformer`, and the fields
  `MixinTransformer.processor`, `MixinProcessor.configs`, `MixinConfig.mixins`, `MixinInfo.state`,
  `MixinInfo.virtual` and `MixinInfo$State.classNode` must exist with the expected types.
- Any failure leaves Mixin exactly as it was and logs one warning.
- A failure on one mixin keeps that mixin's tree; the sweep continues.
- Off in Compare Mode and when the common master switch is off.

## Expected saving

The tree objects of the 2,536 applied Wolds mixins are about 35-41 MB. A stored class is roughly a
tenth of that. The saving is the share of mixins whose targets are all loaded when the sweep runs.
At +90 s after the start, 2,536 of 2,830 mixins (90%) had already been applied at least once, and
most mixins have one target, so about 80-90% qualify: an estimate of **25-33 MB** on Wolds. The
second sweep adds what loads in the first five minutes. Packs without MixinSquared keep only the raw
trees, so the saving there is smaller (the 154 `MixinClassNode` of a plain Vault Hunters server
are about 2.5 MB; its raw `ClassNode` trees add a few MB more).

## Server check

The log line `Mixin class-tree release (...)` appears twice. A passing check shows:

- `released` close to `mixins` minus the `kept` counts, `failed` 0, `not byte-stable` 0, a time of
  well under a second, and a stored size about a tenth of the class size;
- a class histogram (`jcmd <pid> GC.class_histogram`) after a full GC, before and after the first
  sweep: `MixinInfo$MixinClassNode` and `org.objectweb.asm.tree.*` instance counts drop sharply and
  live heap drops by the estimated amount;
- a player joining, a vault run, a `/reload` and a late-loading feature (a rarely used block or
  entity) work without a Mixin error, and the log has no `MixinApplyError` or
  `InvalidMixinException`;
- the same server with the option off behaves identically and shows no release lines.

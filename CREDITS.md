# Credits and research attribution

VH Accelerator exists because other mod authors made large-pack loading
performance visible, measurable, and worth investigating. Credit is given here
for discovery and inspiration even when no code was reused.

VH Accelerator's tracked source was independently written for this project and
is released under the MIT License. No third-party mod jar, decompiled output,
or source tree is included in the repository or release jar. References were
used to understand behavior, locate bottlenecks, study APIs, compare safety
boundaries, and design independent Forge 1.18.2 implementations.

## Primary discovery inspiration

### LaunchFaster — DogV2

- Author: [DogV2](https://github.com/DogV2)
- Artifact studied: LaunchFaster 1.0 for Forge 1.18.2
- Declared artifact license: MIT

LaunchFaster was the starting point for the project. Its behavior led to the
initial investigation of:

- launch timing and title-screen reporting;
- parallel model JSON reads;
- independent atlas preparation and top-level model baking;
- model-material memoization;
- resource-list caching;
- reload and BlockState startup work;
- asynchronous online profile-service creation.

VH Accelerator reimplemented these behaviors independently, rejected unsafe
registry-validation and BlockState defaults, retained the completed profile
service, added dynamic-model protection, and introduced explicit failure
recovery and cache invalidation. The detailed behavior comparison is in
[the LaunchFaster behavior map](docs/ORIGINAL_BEHAVIOR.md).

### VHClientOptimize — JustAHuman

- Author: [JustAHuman](https://github.com/JustAHuman-xD)
- Source: [JustAHuman-xD/VHClientOptimize](https://github.com/JustAHuman-xD/VHClientOptimize)
- Public branch studied: `u18`
- Later local artifact studied: `1.0.4-u19`
- Declared source license: GPL-3.0

VHClientOptimize exposed the amount of login time spent above Minecraft's
generic loading layer, especially in Vault Hunters, JEI, and their plugins. It
inspired investigation of:

- JEI search-index construction and mutation ordering;
- JEITweaker hidden-ingredient matching;
- Vault group construction and tooltip lookups;
- Powah's repeated wiki recipe scans;
- repeated work in compatibility plugins;
- the difference between a lower login timer and a genuinely playable first
  frame.

No GPL source was copied into VH Accelerator. Each retained idea was redesigned
around worker-owned state, completion barriers, main-thread publication,
session invalidation, and sequential fallback. See
[the VHClientOptimize analysis](docs/VH_CLIENT_OPTIMIZE_ANALYSIS.md).

## Methods adapted through later research

### Just Enough Threads

- Author: [Tonywww2](https://github.com/Tonywww2)
- Source: [Tonywww2/JEI-Optimize](https://github.com/Tonywww2/JEI-Optimize)
- Project page:
  [Just Enough Threads](https://www.curseforge.com/minecraft/mc-mods/just-enough-threads)

Just Enough Threads identified two valuable JEI costs in newer Forge/NeoForge
versions: ingredient search-index construction and serial vanilla recipe
validation. VH Accelerator independently backported those concepts to JEI 9
and JEI 10 for Minecraft 1.18.2.

VHA builds a private unpublished index, journals live mutations, publishes on
the client thread, rejects stale connection generations, and falls back to
JEI's original path. Recipe validation uses ordered result slots and retries
sequentially on failure.

### Lightspeed — CCr4ft3r

- Author: [CCr4ft3r](https://github.com/CCr4ft3r)
- Source: [CCr4ft3r/lightspeed](https://github.com/CCr4ft3r/lightspeed)
- Project page:
  [Lightspeed](https://www.curseforge.com/minecraft/mc-mods/lightspeedmod)

Lightspeed's work on reducing file-system access, caching model/material
relationships, improving data structures, and measuring title-screen launch
time informed the resource-pack and model-cache research. VH Accelerator uses
independent immutable-jar indexes and fingerprinted raw-data caches with
mutable-pack fallbacks rather than reusing Lightspeed code or serialized
objects.

### ModernFix — embeddedt and contributors

- Source: [embeddedt/ModernFix](https://github.com/embeddedt/ModernFix)

ModernFix informed resource-pack indexing, BlockState cache analysis, dynamic
resource compatibility, and the principle that two mods should not transform
the same loading path simultaneously. VH Accelerator queries ModernFix's
effective dynamic-resource choice and yields overlapping mixins.

### LazyDFU — astei

- Source: [astei/lazydfu](https://github.com/astei/lazydfu)

LazyDFU established that DataFixerUpper's executor-backed all-rules warm-up is
optional: migrations remain correct when individual rules compile on demand.
VH Accelerator independently applies that boundary to its physical-client
launch path so the warm-up cannot compete with mod and resource loading.

### DashLoader — alphaqu and contributors

- Source: [alphaqu/DashLoader](https://github.com/alphaqu/DashLoader)

DashLoader demonstrated the repeat-launch value of persistent asset caches.
It also made the risks of whole baked-model serialization clear for a Forge
pack with live custom geometry. VH Accelerator adopted the cache/fingerprint
problem as a research direction but stores only deterministic raw resources
and identifiers, rebuilding runtime models every launch.

### Newer Minecraft and NeoForge model pipelines

- Reference:
  [NeoForge 1.21.5 model-system primer](https://github.com/neoforged/.github/blob/main/primers/1.21.5/index.md)
- Source platform:
  [NeoForge](https://github.com/neoforged/NeoForge)

Newer model systems separate model, blockstate, and atlas preparation into
executor-backed stages. That architecture informed VHA's overlapping private
preparation pipelines, single bakery barrier, canonical model-key cache, and
worker-owned final render lookup.

### BuildScape startup work

- Upstream: [kingodogo/BuildScape](https://github.com/kingodogo/BuildScape)
- Maintained fork:
  [HoYin1600p/Buildscape](https://github.com/HoYin1600p/Buildscape)

The sibling BuildScape optimization work informed dynamic worker sizing,
custom-loader exclusion, eager-versus-lazy BlockState tradeoffs, and the
requirement to inspect dynamic models in-world and in JEI. BuildScape remains
an explicit protected namespace where its custom behavior requires the
established path.

### Loading Profiler

- Source:
  [Minecraft-LightLand/LoadingProfilerCore](https://github.com/Minecraft-LightLand/LoadingProfilerCore)

Loading Profiler reinforced phase attribution before optimization. VH
Accelerator's profiler is independently implemented and scopes measurements
to Forge client-loading phases, reload listeners, model subphases, connection
work, and disconnect listeners.

### Lithium and Canary

- Original project:
  [CaffeineMC/Lithium](https://github.com/CaffeineMC/lithium)
- Forge port studied:
  [AbdElAziz333/Canary](https://www.curseforge.com/minecraft/mc-mods/canary)

Lithium's shape-merging research identified Minecraft's general coordinate
merger as a worthwhile optimization boundary. VH Accelerator independently
implements and randomized-tests an equivalent flat-array merger for Forge
1.18.2, and yields that patch when Lithium or Canary is present.

## Projects reviewed for boundaries or future work

These projects influenced design decisions, rejected approaches, coexistence
rules, or future server research. They are credited even where no equivalent
feature was shipped:

- [FerriteCore](https://github.com/malte0811/FerriteCore) — compact data
  structures and memory-focused optimization boundaries.
- [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast) — distinction
  between runtime rendering wins and launch/login critical-path work.
- [C2ME](https://github.com/RelativityMC/C2ME-fabric) — worker ownership,
  scalable parallelism, and server/world-generation scope.
- [Krypton](https://github.com/astei/krypton) — networking-stack performance
  research and the limits of client-only connection optimization.
- [Fastload](https://github.com/BumbleSoftware/Fastload) — world initialization
  and early-frame techniques.
- [Ksyxis](https://www.curseforge.com/minecraft/mc-mods/ksyxis) — spawn-region
  loading tradeoffs.

Fastload and Ksyxis helped define an intentional non-goal: VH Accelerator does
not report a deceptively early world frame while moving large required work
into active gameplay. C2ME's server-owned work remains a future dedicated
server research area, not a client claim.

## Foundational projects

VH Accelerator is built on and interoperates with:

- [Minecraft Forge](https://github.com/MinecraftForge/MinecraftForge)
- [SpongePowered Mixin](https://github.com/SpongePowered/Mixin)
- [Just Enough Items](https://github.com/mezz/JustEnoughItems)

Their APIs and implementation behavior make this mod possible. Their own
licenses and copyrights remain with their authors.

## Compatibility behavior studied

The following projects were inspected through public source or locally owned
binary APIs to implement version guards, identify repeated work, or verify
safety. They are not bundled:

- [Vault Hunters](https://www.curseforge.com/minecraft/mc-mods/vault-hunters-official-mod)
- [CraftTweaker](https://github.com/CraftTweaker/CraftTweaker)
- [JEITweaker](https://github.com/CraftTweaker/JEITweaker)
- [Just Enough Resources](https://github.com/way2muchnoise/JustEnoughResources)
- [Powah](https://github.com/owmii/Powah)
- [Thermal Foundation](https://github.com/CoFH/ThermalFoundation)
- [Industrial Foregoing](https://github.com/InnovativeOnlineIndustries/Industrial-Foregoing)
- [Iron Furnaces](https://www.curseforge.com/minecraft/mc-mods/iron-furnaces)
- [Sophisticated Storage](https://github.com/P3pp3rF1y/SophisticatedStorage)
- [Every Compat](https://www.curseforge.com/minecraft/mc-mods/every-compat)
- [Xaero's Minimap](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap)
- [Xaero's World Map](https://www.curseforge.com/minecraft/mc-mods/xaeros-world-map)

Thanks to every maintainer and contributor whose work made a bottleneck,
compatibility constraint, or safer implementation strategy discoverable.

## Attribution policy

When a future change is materially informed by another project, add that
project here and describe the relationship in the same commit. Credit is not
limited to copied code. Discovery, profiling, rejected designs, test methods,
and compatibility knowledge all deserve attribution.

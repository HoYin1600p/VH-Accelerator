# VH Accelerator

**Spend less time waiting for a large Vault Hunters client to start and become
playable.**

VH Accelerator is a performance mod for Minecraft 1.18.2 Forge. It reduces
work on the client-launch and multiplayer-login critical paths, with a focus on
large Vault Hunters Third Edition and Remastered packs.

It does not remove recipes, models, or gameplay content. Independent work is
prepared in parallel, deterministic results are cached behind strict
fingerprints, and completed data is published back to the game on the correct
thread. Dynamic models and other unsafe work stay on their normal path.

## What it improves

- Client startup, including model, blockstate, texture-atlas, and resource
  preparation.
- Multiplayer login and server/world transfers, including JEI search and
  recipe preparation.
- Repeated launches and connections through validated caches.
- Post-login responsiveness by keeping unfinished background work away from
  the first playable world frame.
- Testing and troubleshooting through built-in launch, login, transfer,
  post-login, and disconnect timers.

The mod includes targeted compatibility for Vault Hunters, JEI, JEITweaker,
CraftTweaker, JER, Powah, Thermal, Iron Furnaces, Industrial Foregoing, Xaero's
maps, Sophisticated Storage, Every Compat, and ModernFix. Optional integrations
activate only when the matching mod and supported class layout are present.

## Compatibility

- **Minecraft:** 1.18.2
- **Mod loader:** Forge 40.3.11 or newer in the Forge 40.x line
- **Vault Hunters:** Remastered `20.0.3-remastered`, `.6872`, and `.6883`;
  official `3.21.5.6882` and
  `3.21.6.6884`; Wold's Vaults 0.32.2 (`3.21.5.6573`, runtime-verified);
  custom MVP `3.21.62`
- **JEI:** 9.7.2.1001, 10.2.1.1006, and 10.2.1.1009

The Vault and JEI versions above are tested compatibility baselines, not hard
dependencies. One VH Accelerator jar contains guarded support for both JEI
generations and all listed Vault layouts.

VH Accelerator works as a **client-only mod** when connecting to a server that
does not have it. The same jar is safe to place on a dedicated server, although
version 1.0.0 provides only conservative server launch/resource improvements.

## Installation

1. Stop Minecraft.
2. Disable or remove older VH Accelerator jars.
3. Disable **LaunchFaster**, **Lightspeed**, and **VHClientOptimize** because
   their loading changes overlap VH Accelerator.
4. Put `VH-Accelerator-1.0.0.jar` in the instance's `mods` folder.
5. Launch once to create the configuration and cold caches.
6. Use later launches and connections when judging warm-cache performance.

ModernFix is optional. If it is installed, VH Accelerator detects the features
ModernFix already owns and avoids applying overlapping work.

## Timers and commands

Visible timers are enabled by default. Detailed debug profiling is disabled by
default.

| Command | Purpose |
| --- | --- |
| `/vha` | Show Compare Mode, timer, and debug status. |
| `/vha compare on` | Disable all optimizations while keeping selected measurement tools active. Restart before comparing times. |
| `/vha compare off` | Restore configured optimizations. Restart before measuring launch time. |
| `/vha timers on` | Show visible timers and routine timing logs. |
| `/vha timers off` | Hide visible timers and routine timing logs. |
| `/vha debug on` | Enable detailed diagnostic profiling. |
| `/vha debug off` | Stop new detailed diagnostic profiling. |

Each setting also accepts `status`, and `/vha compare`, `/vha timers`, or
`/vha debug` reports that setting without changing it.

These are client commands in multiplayer and do not require the mod on the
server. On a dedicated server, the commands are available from the console or
to operators with permission level 2 or higher.

## First-run expectations

The first launch or first connection after a relevant mod, resource-pack,
configuration, registry, recipe, or server change may be slower because VH
Accelerator must validate or rebuild affected caches. Invalid cache data is
never trusted; the original loading path is used and the cache is replaced.

For a useful comparison, run at least three warm launches or connections and
compare the median. Login time also includes network delay and server tick
load.

## Credits and source

VH Accelerator was independently implemented by
[HoYin1600p](https://github.com/HoYin1600p). Early discovery was inspired by
LaunchFaster by [DogV2](https://github.com/DogV2) and
[VHClientOptimize](https://github.com/JustAHuman-xD/VHClientOptimize) by
JustAHuman.

Just Enough Threads, Lightspeed, ModernFix, DashLoader, newer Minecraft model
pipelines, BuildScape, Loading Profiler, FerriteCore, ImmediatelyFast, C2ME,
Krypton, Fastload, Ksyxis, Forge, Mixin, JEI, and compatibility-target projects
also informed research and testing. The
[complete credits](https://github.com/HoYin1600p/VH-Accelerator/blob/master/CREDITS.md)
explain each influence.

- [Source code and issue tracker](https://github.com/HoYin1600p/VH-Accelerator)
- [Full installation guide](https://github.com/HoYin1600p/VH-Accelerator/blob/master/docs/INSTALLATION.md)
- [Configuration reference](https://github.com/HoYin1600p/VH-Accelerator/blob/master/docs/CONFIGURATION.md)
- License: MIT

No third-party mod jar or source is bundled. Minecraft is a trademark of
Microsoft. Vault Hunters belongs to its respective authors. This independent
project is not affiliated with Mojang, Microsoft, Forge, Iskallia, JEI, or the
credited projects.

The project icon is original AI-assisted branding and is not an in-game
screenshot or a modified official Vault Hunters logo.

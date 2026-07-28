# Testing and benchmarking

Performance results are noisy. Use a repeatable protocol and treat crashes,
missing models, incomplete JEI state, or post-frame stutter as regressions
even when a timer is lower.

## Metrics

VH Accelerator records:

| Metric | Start | End |
| --- | --- | --- |
| Client launch | JVM process start | Initial loading overlay completes |
| Server login | Connect screen begins connection | First playable world frame |
| Server/world transfer | Respawn/receiving-level transfer signal | First playable world frame |
| Post-login work | Login work session begins | All tracked post-login work completes |
| Disconnect | Synchronous disconnect begins | Multiplayer, title, or Realms menu opens |
| Dedicated-server launch | JVM process start | Server startup completion |

The login timer intentionally includes time until a playable frame. Moving
heavy work after that frame is not considered a valid gain.

Beginning with 1.0.2, launch measurements include ModLauncher and early JVM
bootstrap time. The timer attaches at Minecraft's entry point and reconstructs
the earlier process start from monotonic JVM uptime.

## Baseline with Compare Mode

1. Use the same instance, JVM arguments, memory allocation, server, and network.
2. Enable timers and disable detailed diagnostics:

   ```text
   /vha timers on
   /vha debug off
   /vha compare on
   ```

3. Restart the client.
4. Perform one unrecorded warm-up launch.
5. Record at least three launches and three logins; six is preferred.
6. Use the median, and retain the individual samples.
7. Run `/vha compare off`, restart, and repeat the same sequence.

Compare Mode preserves timers but disables cache prewarming, worker pipelines,
and optional-mod optimizations. It does not simulate removing the jar's mixin
configuration or class-loading cost, which keeps the comparison focused on
the optimizations themselves.

Beginning with 1.0.1, Compare Mode is captured from disk before early mixins
can run and remains stable for the complete launch. The log reports both the
bootstrap capture and the skipped client optimization startup groups.

## Cold and warm caches

Label results as:

- **cold:** no compatible cache exists or the input fingerprint changed;
- **warm:** the same validated inputs were seen on a previous completed run.

Do not compare a cold baseline with a warm optimized result without saying so.
The first login to a server may need to capture its synchronized tags,
recipes, and config fingerprint before later launches can use server-scoped
caches.

## Client validation checklist

After each optimization change:

1. Reach the title screen and verify the launch timer.
2. Browse every JEI page and search by name, namespace, tooltip, and tag.
3. Open several recipes and verify recipe transfer.
4. Inspect Vault gear, Vault GUI atlases, Sophisticated Storage upgrades,
   Every Compat blocks, and BuildScape custom models.
5. Connect twice to the same server.
6. Switch backend/world at least twice when testing a cluster.
7. Disconnect while post-login work is active.
8. Reconnect after a forced client crash or cold restart.
9. Trigger a resource reload when relevant.
10. Search the log for mixin failures, missing textures, worker recovery, and
    stale-session rejection.

## Server validation checklist

1. Back up the server and world.
2. Test startup once without VH Accelerator and once in Compare Mode.
3. Test normal startup with default settings.
4. Verify registry freeze, datapack reload, recipe/tag loading, and world load.
5. Join with a client that does and does not have VH Accelerator.
6. Stop the server cleanly and inspect shutdown logs.

The experimental common registry and BlockState switches must be tested
individually and are not release defaults.

## Reporting results

Include:

- VH Accelerator, Minecraft, Forge, Vault, and JEI versions;
- whether ModernFix and its dynamic-resource option are active;
- cold or warm cache state;
- Compare Mode state;
- individual samples plus mean and median;
- the relevant `latest.log`;
- what was visually and functionally checked.

Redact authentication tokens, server addresses that are not public, and local
profile-directory names before attaching logs.

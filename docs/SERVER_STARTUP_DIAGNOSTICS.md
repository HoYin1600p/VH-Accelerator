# Dedicated startup diagnostics

Enable common `diagnostics.timers` for routine totals and a JSON report, or
`diagnostics.debug` for detailed records. Both default to false. Restart after
changing debug: diagnostic mixins and callback observers are selected during
bootstrap. Compare Mode retains selected diagnostics. The physical-client
launch timer and its profiling setting remain independent.

The single `ServerLaunchTimer` estimates JVM monotonic start from runtime uptime.
Its clock mapping includes JVM start epoch milliseconds, attachment UTC wall
clock, attachment monotonic time and sampled uptime. Uptime has millisecond
resolution; compare the mapping with external JFR start time before making a
cross-clock timing claim. Early discovery is outside attachment coverage.

At `ServerStartedEvent`, the existing summary is retained and one report is
written to `logs/vhaccelerator/server-startup-<runId>.json`. The same JSON is
logged with `VHA_SERVER_STARTUP`. If writing fails, the log remains available.
Failed launches that never reach the event do not yet produce a final file;
the harness must retain their logs and classify them as invalid runs.

## Schema version 1

The root contains `schemaVersion`, `runId`, `physicalSide`, `clockMapping`,
`manifest`, `detailedDiagnostics`, `endpointOrder`, `completionState`, `errorState`,
`diagnosticFailures`,
`missingCheckpoints`, `unfinishedSpans`, `failedSpans`, `droppedRecords`,
`observations`, `timingSummary`, `hookCoverage`, `compatibility` and `records`.
Each record repeats the schema/run/side and contains an `event` with:

- ID, kind, name, hook/proxy marker and explicit parent ID;
- monotonic elapsed nanoseconds from the estimated JVM start and UTC wall clock;
- thread ID/name, optional reload generation and feature-group/owner identifier;
- completion/error state and optional start/end/duration/CPU nanoseconds;
- optional checkpoint occupancy: heap used/committed/max, metaspace
  used/committed/max, GC count/time, and loaded/total/unloaded class counts.

Null means unavailable or inapplicable. JVM counters may report `-1` for an
unsupported metric or unlimited maximum. Occupancy never forces collection and
is not a live-set measurement. CPU is recorded only when the JVM already
supports/enables it and the span completes on its originating thread. It is not
enabled by this instrumentation. Error records retain exception class names,
not exception objects or messages. Diagnostic failures separately retain bounded
stage, exception-class and reason strings so an invalid measurement can be
explained without retaining a throwable.

The manifest repeats the run ID, process ID and Java identity. The external
harness must supply cohort/pair/order, archive/configuration/script/world
fingerprints, cache policy, JFR reconciliation, contention and clean-stop
results. A report alone does not constitute an accepted benchmark manifest.

## Observed boundaries on Forge 40.3.11

| Observation | Attachment and interpretation |
| --- | --- |
| Attachment | Existing `ServerMainMixin`, dedicated Main HEAD; uptime estimate includes earlier unobserved work. |
| Construction | VHA constructor proxy; other constructors are not individually measured. |
| Registry start/end | Before/after `ModLoader.gatherAndInitializeMods`; proxy interval also includes construction and gathering work. |
| Common/sided/load-complete callbacks | VHA callback proxies, not global barriers. |
| Setup/load-completion barriers | Return from `ModLoader.loadMods` / `finishMods`; phase spans include synchronous queues and waits without attributing individual callbacks. |
| Reload | Listener aggregate; names unwrap Forge's state-aware listener wrappers. Prepare/apply intervals, queued tasks and barrier waits have explicit generation/parent IDs. Recipe/tag/mod listener ownership follows the listener class. |
| Tag update | `ReloadableServerResources.updateRegistryTags`, including Forge's tag event. |
| Dedicated initialization | `DedicatedServer.initServer` HEAD/RETURN. |
| Level/spawn | `MinecraftServer.loadLevel`, `createLevels`, `prepareLevels` HEAD/RETURN. |
| Done | Unique literal immediately before the actual dedicated logger invocation. It occurs before `ServerStartingEvent`. |
| Starting/started | Forge lifecycle hook entry, before event dispatch. Started remains distinct from Done and from VHA's reporting callback. |
| Mixin application | This configuration's preApply/postApply after timer attachment; early applications and other configs are not covered. |

The interval union, inclusive duration sums and waits are separate. Inclusive
sums can exceed elapsed startup time. Per-thread task totals contain observed
executor tasks, not all work performed by the thread. Parent CPU can include
nested loading/transformation work; it is not exclusive callback CPU.

Pack metadata enumerates only already-selected packs, once per reload, retaining
names, implementation classes and manager iteration order. It performs no extra
resource scan. The list is capped at 512 with an explicit truncation flag.
Mutability remains unclassified: a class name does not prove an archive is
immutable. File counts, bytes, independent parse/allocation evidence, duplicate
indexes and effective precedence beyond manager order remain unavailable.

Core FML/ModLauncher classes precede attachment. Individual constructor/event
callbacks, synchronous queue ownership, class definition and transformation
chains need external recording or separately verified hooks. `hookCoverage`
states these limits explicitly.

## Script, generated-resource and index observations

Dedicated debug launches select separate observational compatibility groups:

| Exact mod version | Observed methods |
| --- | --- |
| CraftTweaker 9.1.213 | `ExecutingScriptRunner.executeRunAction(SemanticModule)` and `ScriptRunManager.applyAction(IAction)`; action implementation class is retained as the owner name. |
| KubeJS 1802.5.5-build.569 | `ScriptManager.loadFromDirectory/load`, `RecipeEventJS.post` and synchronous `forEachRecipe`; filter implementation class identifies filtering work. Discovery and execution are distinct operations; the ScriptType and individual source filenames are not captured. |
| Selene 1.18.2-1.17.17 | DynamicResourcePack listener/finder registration, protected `addBytes(ResourceLocation,byte[])` and `addRootResource`; generated payload counts and supplied byte-array lengths include overwrites and are not unique retained resources. |

These groups require known discovery, exact versions, dedicated side and common
debug. They do not depend on optimization master/Compare Mode or claim optimization
ownership. Unknown members and unsupported versions fail closed. Whole-group
preflight checks literal descriptors even with optional injectors; postApply also
checks merged handlers and their call edges. Client replay/optimization helpers
remain separate. Existing client startup timing is unchanged.

`operationMetrics` holds at most 256 owner/thread/parent/generation rows and 256
open method calls, separately from the event buffer. Each row contains entered
and normally completed counts, inclusive wall/available CPU time, maximum wall
duration, optional payload byte and item counts, thread identity and nested
operation name. Operations inside observed reload executor tasks retain the task
span ID and reload generation. Calls outside those tasks have null linkage;
unobserved asynchronous work is not attributed to an inferred parent.

HEAD/RETURN hooks preserve original calls and exceptions. An exception that
escapes without a return leaves unfinished work; unmatched returns, overflow or
overlong metadata invalidate operation measurements and the root report. No
exception object, script, recipe, byte array or pack is retained. Bounds can be
reached on large packs and must be checked before drawing conclusions.

VHA's existing path-index build records its already-counted files. Its ZIP index
records construction, central-directory read-or-map calls and tree parsing
separately, including logical directory buffer sizes. Mapping a directory is
not evidence that all its bytes were physically read. No additional directory
walk, stream read, cache or scan is introduced. Per-resource physical read
bytes, separable JSON parsing, unique generated-resource counts, ZipFileSystem
duplicate scans, retained index sizes and allocation evidence remain unavailable;
use external JFR/heap evidence. Aggregate method durations overlap and are not
exclusive critical-path contributions.

## Offline execution-sample classification

`StartupSampleClassifier` accepts an external, clock-reconciled JSON export and
writes its result to stdout. It does not collect samples inside Minecraft.
The startup report embeds its versioned contract. An executable input and
rendered output live in `src/test/resources/dev/hoyin1600p/vhaccelerator/startup/`.
Run the main class with the normal runtime dependencies on the Java classpath
and one argument pointing to the exported JSON. The export fields are:

```json
{"schemaVersion":1,"startInclusiveNanos":0,"doneExclusiveNanos":100,
 "samples":[{"timestampNanos":50,"classNamesLeafToRoot":["java.util.HashMap","iskallia.vault.Example"],
             "truncated":false,"phase":"reload"}]}
```

The filter includes JVM start and excludes Done. Class names use dotted binary
notation, from leaf toward root. Nearest-owner depth is 32 and inclusive depth
is 48. `packageOwners` and `excludedPlatformFrameworkPrefixes` in the output
are the exact rules; no broad inference about an unknown mod is made. The first
non-excluded unmapped frame becomes `other_mod_or_library`; an all-excluded stack
becomes `platform_or_framework`. Missing stacks remain in the denominator as
`missing_stack`. Truncated and depth-limited stacks remain classified with
separate counters, without inferred frames. Each inclusive owner counts at most
once per sample. Nearest counts partition selected samples; inclusive counts
overlap. Phase labels are supplied externally, not inferred from stacks.

The fixture is synthetic. The original recording/export and its timestamp
mapping were not supplied to this implementation, so the reference 1,514 samples
and phase/owner totals remain explicitly unreconciled. Do not use this new
attribution to schedule optimization work until that reconciliation is performed.
Neither table converts sample shares into exclusive CPU or critical-path seconds.
Regenerate the sample output through `StartupSampleClassifierTest` with
`VHA_REGENERATE_SAMPLE_SNAPSHOT=1`; regenerate the Selene layout fixture through
`StartupAdapterLayoutsTest` with that flag and `VHA_SELENE_FIXTURE_JAR` pointing
to the exact archive. The fixture records archive version and SHA-256.

## Compatibility audit records

Every configured compatibility group, including physical-client compatibility
packages, has a decision with physical side, discovered mod versions, bootstrap
feature facts, common master, Compare Mode, debug, selected owner, required
target pairs and preflight result/reason. Unknown discovery and unreadable own
mixin bytecode cannot authorize a compatibility group. Selected members are
checked as a whole before selection; one layout failure rejects the group.
Both physical sides log one `VHA_COMPAT_CONTEXT` record and per-group
`VHA_COMPAT_DECISION` records linked by context ID, avoiding repetition of the
entire mod/version list. `VHA_COMPAT_APPLICATION` records update transformation
state; debug `VHA_COMPAT_FIRST_USE` records distinguish callback entry from the
first successful guarded operation. These records also cover client launches
where the dedicated startup timer does not run. They are evidence of application
and observed use, not measured performance savings.

The jar packages `dev/hoyin1600p/vhaccelerator/compatibility-inventory.json`.
Each decision references its group in this versioned inventory. Its renderer
reads compiled mixin declarations, target descriptors, injection annotations,
field declarations and method-reachable VHA helpers, including static
initializers and lambda handles. It records configuration accessors, external
member dependencies, registration sides, mod/version policies and declared
competing owners. Generation is bounded to 512 helper classes and 8,192 methods
per group; a missing helper or exceeded bound fails validation. It does not
initialize helper/client/optional classes. Regenerate through
`CompatibilityInventoryTest` with `VHA_REGENERATE_COMPAT_INVENTORY=1`.
Normalized source hashes tie the declared policies to the current gate,
discovery and live-input implementation; policy changes require regeneration
and review rather than silently retaining a stale inventory.

An existing presence-only version policy remains explicitly unpinned; this
inventory does not invent exact-version support or change legacy eligibility.
Per-boot installed versions and whole-group preflight remain authoritative.
Registration on both sides also does not authorize a dedicated exception.
Unknown external callbacks and reflection are not established by the static
inventory; the stricter common-side safety audit remains separate.

`configurationSnapshots` contains corrected raw values captured from Forge
Loading/Reloading config events, without resolving a client configuration class
from common code. It copies scalar values only, with at most 1,024 keys per type,
eight nesting levels, 512-character keys and 4,096-character string values.
Omissions, revisions and capture timestamps are explicit. Dedicated servers do
not acquire CLIENT configuration. Debug logs expose the same evidence through
`VHA_COMPAT_CONFIGURATION` on either physical side. Per-group runtime evidence
links these snapshots to inventory accessors and bootstrap gate facts. Raw
settings and callback entry cannot prove a runtime guard succeeded; unmeasured
successful-use counters remain null and receive no performance credit.

`fallback` can mean either no members were selected or application is still
unconfirmed; use the accompanying preflight result and reason. `applied` means
all selected target transformations have postApply confirmation. The common
Decocraft/FerriteCore ports also verify actual injector call edges. It does not
prove the optimization passed its runtime guards. Partial transformation is
marked invalid for benchmarking. Application progress is logged on both sides.
Any selected group with unconfirmed targets, including zero confirmations,
invalidates the report's compatibility measurement. Selection alone is never a
valid applied-feature result. Rejected layouts also remain visible as invalid
benchmark evidence while their original code path is retained.

With bootstrap debug enabled, merged callback methods record their first
invocation per mixin. These counters are bounded by configured mixins and are
not optimization-use counters. Decocraft texture clearing/model sharing and
FerriteCore view removal additionally count successful guarded operations with
debug on. Other runtime feature-use counts remain null, so no
performance credit may be assigned from callback invocation alone. Accessor-only
paths and methods without merge metadata may have no observable invocation.

The broad dedicated `.compat.` rejection remains after three optimization and
five observational exact-name common exceptions. Before selection on a dedicated server, a bounded static bytecode
audit follows handlers, static initializers, helper calls and lambda handles
through VHA and FerriteCore code. Descriptors, generic signatures and exception
types are checked for client references. Required reflective fields and factory
methods receive separate descriptor checks. Limits are 256 classes and 2,048
methods per audit; missing bytecode or overflow rejects the group. Inventory
records retain names/counts, not ClassNodes. This is a static linkage audit;
it does not establish safety of unknown external callbacks or reflection.

Common Decocraft/FerriteCore mutations wait until every selected target and
injector is confirmed on either physical side. Otherwise their guards keep
the original representation. Optional Decocraft injectors have `require=0`
alongside mandatory whole-group preflight and call-edge verification; optional
requirements alone never authorize activation. A contradictory application
after activation fails visibly because safe fallback cannot be assumed.

If a legacy optimization group's callback executes while application is
incomplete, its diagnostic startup is invalid. Observational startup groups
instead require the callback's own target transformations to be confirmed:
independent observer classes may load at different times. Every selected target
must still be confirmed by the final report, and a callback without its own
confirmation remains invalid. These observations grant no optimization credit.

Startup diagnostic verification failures produce an incomplete measurement with
`errorState` set to `startup_diagnostics_failed`. The `diagnosticFailures` array
retains up to 64 distinct stage/exception-class/reason combinations, with stage
and reason strings limited to 96 and 384 characters. Each distinct combination
produces one WARN per run; repeated failures do not flood the log. They do not escape a game event or
observer callback. Verification runs before the final report is serialized so
its failure is retained in the report. Diagnostic failures do not change the
failure behavior of the original game work or optimization correctness guards.

In particular:

| Candidate | Current dedicated status and audit dependency |
| --- | --- |
| Decocraft 3.0.4-1.18.2 | Only common-list BBModel/JsonParser registration hooks cross the server gate. Require known discovery, exact version, common master, feature on and Compare Mode off. Layout checks require public load/parse methods, `BBModel.textures:List` and the three public String texture fields. Contract fixtures cover accepted/changed layouts; the exact Decocraft archive is not in the repository fixtures and is verified at runtime before selection. |
| FerriteCore 4.2.2 | Only common-list StateHolder view compaction crosses the server gate, with the same discovery/master/Compare requirements. Tests inspect the 4.2.2 archive's duck/FastMap/factory descriptors and the transitive static helper closure. FerriteCore retains FastMap ownership. |
| CraftTweaker removal | Client list and client config dependency remain; no server port is implied by diagnostics. Replay helpers are client-specific. |
| Vault CDF/group hooks | Client list/config/helpers remain. GroupUtils directly reaches Minecraft/ClientLevel; it must not receive a common-side exception. |

Target Dummy and Crystal Budding retain their independent correction gates.
No optimization algorithm, persistence representation or client-list placement
changes as part of these diagnostics. The existing Decocraft and FerriteCore
implementations gain narrow dedicated eligibility after the audit; no broad
package exception is added. Compiled inventories and settings evidence cover
the remaining legacy groups without expanding their dedicated eligibility.
Real physical-client, integrated-world and dedicated launches, paired overhead
measurements and original-recording reconciliation remain external validation
work; unit/layout fixtures do not claim those launch results.

## Lifecycle listener ledger and startup JFR

`diagnostics.timers` also enables a per-listener ledger of server lifecycle
and loading events. EventBus 5.0.3 is not reachable by mixins, so VHA wraps
each existing listener in an `IEventListener` through the public `ListenerList`
registration API, immediately before the events are posted, and restores the
originals when `ServerStartedEvent` has finished. Listener order and priority
are preserved. Each invocation costs two clock reads and a few atomic adds;
only invocations slower than the 64th slowest take a lock. Owner mod and
method are resolved once at the end.

The report section `lifecycleListenerLedger` (schema version 1) holds
`wrappedListeners`, `refusedListeners` (8,192 slot budget), `invocations`,
`totalMillis`, up to 64 `slowest` rows (`event`, `mod`, `listener`, `thread`,
`startOffsetMillis` from JVM start, `durationMillis`) and up to 64 `perMod`
totals. With the ledger active the JSON report is written after the last
started-event listener returns, not inside VHA's own listener, so a slow
listener that runs later is included. Durations are wall-clock and include
blocking; they are not CPU time.

`diagnostics.startupJfr` (default off) starts a JDK Flight Recorder recording
when VHA first runs and writes `startup-<runId>.jfr` beside the JSON report 30
seconds after the started event. Use it to see what a thread did inside a slow
listener the ledger has named.

## Bounds and overhead

The startup buffer holds at most 4,096 events, plus 64 metadata observations
of at most 65,536 serialized characters each and the operation limits above.
Operation names, owners and thread names are limited to 512 characters.
It is released after the started
event. Overflow and missing/unfinished/failed observations remain visible.
There are no polling threads, stack walks, forced collections or retained
world/resource/mod instances. Detailed task wrappers, CPU queries, JSON and
debug callback observers add overhead; measure diagnostics-on/off in separate
paired runs. No overhead claim has been validated by a game launch yet.

package dev.hoyin1600p.vhaccelerator.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.startup.StartupDiagnostics;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;

/**
 * Applies third-party compatibility mixins all-or-nothing per group.
 *
 * <p>Mixins in one compatibility package cooperate (for example the JEI
 * search-index mixins share indexing state), so a group is applied only when
 * every member that would otherwise apply passes {@link MixinTargetPreflight}.
 * A group whose target mod changed is skipped with one warning, leaving that
 * mod's original behavior, instead of failing startup on a required injector.
 * Model-bake mixins each target a different mod and form their own groups.
 */
public final class CompatMixinGroups {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final com.google.gson.Gson DIAGNOSTIC_JSON = new com.google.gson.GsonBuilder().serializeNulls().create();
    private static final String COMPAT_PACKAGE = ".mixin.compat.";
    private static final String MODEL_BAKE_PACKAGE = ".mixin.compat.modelbake.";

    private final List<String> configuredMixins;
    private final BiPredicate<String, String> gate;
    private final MixinTargetPreflight.ClassLookup lookup;
    private final Map<String, Boolean> decisions = new LinkedHashMap<>();
    private final Map<String, String> reasons = new LinkedHashMap<>();
    private final Map<String, Set<String>> expected = new LinkedHashMap<>();
    private final Set<String> applied = new LinkedHashSet<>();
    private final Map<String, Object> context = new LinkedHashMap<>();
    private static volatile CompatMixinGroups published;
    private final Map<String, AtomicBoolean> firstUses = new ConcurrentHashMap<>();
    private final Map<String, CommonCompatibilityAudit.Result> sideAudits = new LinkedHashMap<>();
    private final Map<String, LongAdder> optimizationUses = new ConcurrentHashMap<>();
    private final Set<String> successfulUseLogged = ConcurrentHashMap.newKeySet();
    private final Set<String> unsafePartialUse = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cdfRejectionLogged = new AtomicBoolean();
    private static volatile boolean usageDiagnostics;
    private final Map<String, AtomicBoolean> activated = new ConcurrentHashMap<>();
    private static final Set<String> COUNTED_MIXINS = Set.of(
            dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.MIXIN,
            VaultLootCdfLayouts.MIXIN,
            VaultLootCdfLayouts.LAZY_MIXIN,
            "dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets.TemplateLookupMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things.StorageManagerLoadMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.lootjs.LootJsItemFilterMixin",
            dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.STIRLING_MIXIN,
            dev.hoyin1600p.vhaccelerator.compat.thermalfuels.ThermalConvertedFuels.GOURMAND_MIXIN,
            dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.MIXIN,
            "dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft.BBModelTextureDataMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.decocraft.JsonParserModelShareMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.ferritecore.StateHolderPropertyMapMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals.ScriptRunIndexSessionMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals.ScriptRunManagerOutputContextMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals.RecipeListIndexedOutputMixin");

    /**
     * @param configuredMixins fully qualified names of every configured mixin
     * @param gate the plugin's existing decision for (target, mixin), with
     *             dotted class names, evaluated without this preflight
     * @param lookup class lookup by internal name; returns null when absent
     */
    public CompatMixinGroups(
            List<String> configuredMixins,
            BiPredicate<String, String> gate,
            MixinTargetPreflight.ClassLookup lookup
    ) {
        this.configuredMixins = List.copyOf(configuredMixins);
        this.gate = gate;
        this.lookup = lookup;
        configuredMixins.forEach(mixin -> firstUses.put(mixin, new AtomicBoolean()));
        configuredMixins.stream().filter(COUNTED_MIXINS::contains)
                .forEach(mixin -> {
                    optimizationUses.put(mixin, new LongAdder());
                    activated.put(mixin, new AtomicBoolean());
                });
    }

    /** Whether the preflight allows {@code mixinClassName} to apply. */
    public synchronized boolean allows(String mixinClassName) {
        String group = groupOf(mixinClassName);
        if (group == null) {
            return true;
        }
        Boolean decision = decisions.get(group);
        if (decision == null) {
            decision = evaluate(group);
            decisions.put(group, decision);
        }
        return decision;
    }

    static String groupOf(String mixinClassName) {
        if (!mixinClassName.contains(COMPAT_PACKAGE)
                && !mixinClassName.contains(".mixin.client.compat.")) {
            return null;
        }
        if (mixinClassName.contains(MODEL_BAKE_PACKAGE)) {
            return mixinClassName;
        }
        int end = mixinClassName.lastIndexOf('.');
        return end < 0 ? mixinClassName : mixinClassName.substring(0, end);
    }

    private boolean evaluate(String group) {
        List<String> problems = new ArrayList<>();
        Set<String> members = new LinkedHashSet<>();
        List<String> roots = new ArrayList<>();
        expected.put(group, members);
        for (String mixin : configuredMixins) {
            if (!group.equals(groupOf(mixin)) || !gate.test("", mixin)) {
                continue;
            }
            roots.add(mixin);
            ClassNode node;
            try {
                node = lookup.find(mixin.replace('.', '/'));
            } catch (Exception exception) {
                node = null;
            }
            if (node == null) {
                problems.add(simpleName(mixin) + ": own mixin bytecode unavailable");
                continue;
            }
            List<String> targets = MixinTargetPreflight.targets(node);
            problems.addAll(StartupAdapterLayouts.problems(mixin, node, lookup));
            if (targets.isEmpty()) {
                problems.add(simpleName(mixin) + ": no target metadata");
            }
            for (String target : targets) {
                if (!gate.test(target.replace('/', '.'), mixin)) {
                    continue;
                }
                members.add(mixin + " -> " + target.replace('/', '.'));
                for (String problem : MixinTargetPreflight.problems(node, target, lookup)) {
                    problems.add(simpleName(mixin) + ": " + problem);
                }
            }
        }
        if (!roots.isEmpty() && "DEDICATED_SERVER".equals(context.get("physicalSide"))) {
            CommonCompatibilityAudit.Result audit = CommonCompatibilityAudit.inspect(roots, lookup);
            sideAudits.put(group, audit);
            problems.addAll(audit.problems());
        }
        if (!roots.isEmpty() && CommonCompatibilityLayouts.isCommonGroup(group)) {
            problems.addAll(CommonCompatibilityLayouts.problems(group, lookup));
        }
        if (problems.isEmpty()) {
            reasons.put(group, members.isEmpty()
                    ? "no members selected by side/mod/version/configuration/ownership gates"
                    : "whole-group target preflight passed; application not yet confirmed");
            return true;
        }
        reasons.put(group, String.join("; ", problems));
        StartupDiagnostics.run("compatibility preflight warning", () -> LOGGER.warn(
                "Skipping VH Accelerator compatibility group {} because the "
                        + "installed mod no longer matches it; that mod keeps its "
                        + "original behavior. Details: {}",
                group,
                String.join("; ", problems)
        ));
        return false;
    }

    /** Evaluate even groups for absent mods or the other side, so silence cannot imply activation. */
    public synchronized void publishDiagnostics(Map<String, Object> evidence) {
        context.putAll(evidence);
        context.put("contextId", java.util.UUID.randomUUID().toString());
        configuredMixins.stream().map(CompatMixinGroups::groupOf).filter(group -> group != null)
                .distinct().forEach(group -> {
                    if (!decisions.containsKey(group)) {
                        decisions.put(group, evaluate(group));
                    }
                });
        published = this;
        usageDiagnostics = Boolean.TRUE.equals(context.get("debug"));
        StartupDiagnostics.run("compatibility decision evidence", () -> {
            LOGGER.info("VHA_COMPAT_CONTEXT {}", DIAGNOSTIC_JSON.toJson(context));
            for (Map<String, Object> decision : snapshot()) {
                Map<String, Object> compact = new LinkedHashMap<>(decision);
                context.keySet().stream().filter(key -> !key.equals("contextId") && !key.equals("physicalSide"))
                        .forEach(compact::remove);
                LOGGER.info("VHA_COMPAT_DECISION {}", DIAGNOSTIC_JSON.toJson(compact));
                LOGGER.info("Compatibility decision: group={}, side={}, state={}, preflight={}, reason={}",
                        decision.get("group"), decision.get("physicalSide"), decision.get("state"),
                        decision.get("preflight"), decision.get("reason"));
            }
        });
    }

    /** This confirms transformation, not execution of an optimization. */
    public synchronized void applied(String mixin, String target) {
        String group = groupOf(mixin);
        if (group != null) {
            if (applied.add(mixin + " -> " + target)) {
                Set<String> required = expected.getOrDefault(group, Set.of());
                long confirmed = required.stream().filter(applied::contains).count();
                if (Boolean.TRUE.equals(decisions.get(group)) && !required.isEmpty() && confirmed == required.size()) {
                    configuredMixins.stream().filter(member -> group.equals(groupOf(member)))
                            .filter(activated::containsKey).forEach(member -> activated.get(member).set(true));
                }
                StartupDiagnostics.run("compatibility application evidence", () -> {
                    LOGGER.info("Compatibility application: group={}, confirmed={}/{}, complete={}, optimization use unproven",
                            group, confirmed, required.size(), !required.isEmpty() && confirmed == required.size());
                    LOGGER.info("VHA_COMPAT_APPLICATION contextId={}, group={}, state={}, confirmed={}, required={}",
                            context.get("contextId"), group,
                            Boolean.TRUE.equals(decisions.get(group)) && !required.isEmpty() && confirmed == required.size()
                                    ? "applied" : "fallback", confirmed, required.size());
                });
            }
        }
    }

    public synchronized void applicationRejected(String mixin, String target, List<String> problems) {
        String group = groupOf(mixin);
        if (group == null) return;
        boolean previouslyActive = configuredMixins.stream().filter(member -> group.equals(groupOf(member)))
                .filter(activated::containsKey).anyMatch(member -> activated.get(member).get());
        configuredMixins.stream().filter(member -> group.equals(groupOf(member)))
                .filter(activated::containsKey).forEach(member -> activated.get(member).set(false));
        decisions.put(group, false);
        reasons.put(group, "application verification failed: " + String.join("; ", problems));
        StartupDiagnostics.run("compatibility rejection evidence", () -> {
            if (isCdfGroup(group)) {
                if (cdfRejectionLogged.compareAndSet(false, true)) LOGGER.warn(
                        "Vault loot CDF optimization deferred to original/external implementation: {}", problems);
                return;
            }
            // The state codec group's rejection is an expected, safe fallback to the eager codecs.
            boolean expected = dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.GROUP.equals(group)
                    || dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP.equals(group);
            if (expected) LOGGER.warn("Rejected compatibility application {} -> {}: {}", mixin, target, problems);
            else LOGGER.error("Rejected compatibility application {} -> {}: {}", mixin, target, problems);
            if (expected) LOGGER.warn("VHA_COMPAT_APPLICATION contextId={}, group={}, state=rejected, reason={}",
                    context.get("contextId"), group, reasons.get(group));
            else LOGGER.error("VHA_COMPAT_APPLICATION contextId={}, group={}, state=rejected, reason={}",
                    context.get("contextId"), group, reasons.get(group));
        });
        if (previouslyActive) {
            unsafePartialUse.add(group);
            // The eager builder owns no shared mutation, and the lazy hooks keep serving the distributions
            // already deferred while refusing new ones: revocation leaves both inert, never inconsistent.
            if (isCdfGroup(group)) return;
            // Existing private WorldZones adapters still implement the reviewed original operations;
            // revocation refuses future conversions without corrupting or discarding any zone data.
            if (dev.hoyin1600p.vhaccelerator.compat.worldzones.WorldZonesLayouts.GROUP.equals(group)) return;
            throw new IllegalStateException("Compatibility application failed after activation; safe fallback unavailable: " + group);
        }
    }

    public synchronized List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var entry : decisions.entrySet()) {
            String group = entry.getKey();
            Set<String> required = expected.getOrDefault(group, Set.of());
            List<String> confirmed = required.stream().filter(applied::contains).toList();
            String state = !entry.getValue() ? "rejected"
                    : !required.isEmpty() && confirmed.size() == required.size() ? "applied" : "fallback";
            Map<String, Object> record = new LinkedHashMap<>(context);
            record.put("group", group);
            record.put("state", state);
            record.put("selectedOwner", required.isEmpty() || !entry.getValue() ? "original_or_external" : "VHA");
            record.put("preflight", !entry.getValue() ? "failed" : required.isEmpty() ? "not_selected" : "passed");
            record.put("reason", state.equals("applied") ? "all selected target transformations confirmed"
                    : reasons.get(group));
            record.put("requiredTargets", List.copyOf(required));
            record.put("appliedTargets", confirmed);
            record.put("commonSideAudit", sideAudits.get(group));
            record.put("contract", CommonCompatibilityLayouts.contract(group));
            record.put("inventory", CompatibilityInventory.reference(group));
            record.put("applicationComplete", !required.isEmpty() && confirmed.size() == required.size());
            boolean measuredUse = Boolean.TRUE.equals(context.get("debug"));
            record.put("firstCallbackInvocationCount", measuredUse ? configuredMixins.stream()
                    .filter(mixin -> group.equals(groupOf(mixin)))
                    .filter(mixin -> firstUses.get(mixin).get()).count() : null);
            List<String> counted = configuredMixins.stream().filter(mixin -> group.equals(groupOf(mixin)))
                    .filter(COUNTED_MIXINS::contains).toList();
            Long uses = measuredUse && !counted.isEmpty()
                    ? counted.stream().mapToLong(mixin -> optimizationUses.get(mixin).sum()).sum() : null;
            record.put("optimizationUseCount", uses);
            record.put("unsafePartialUse", unsafePartialUse.contains(group));
            record.put("activationPolicy", withholdsActivation(group)
                    ? "common optimization waits for all target/injector confirmations"
                    : StartupAdapterLayouts.isGroup(group)
                            ? "independent observer callbacks wait for their own target confirmations; final measurement requires the whole group"
                            : "legacy; partial observed use invalidates diagnostic startup");
            record.put("benchmarkValid", !state.equals("rejected") && !unsafePartialUse.contains(group)
                    && (required.isEmpty() || confirmed.size() == required.size()));
            record.put("eligibleForPerformanceCredit", state.equals("applied") && uses != null && uses > 0
                    && !unsafePartialUse.contains(group));
            record.put("useCoverage", measuredUse
                    ? "first invocation of merged callback methods per mixin; this does not prove an optimization passed its runtime guards"
                    : "disabled with debug; transformation alone earns no performance credit");
            record.put("runtimeSettingEvidence", Map.of(
                    "source", "configurationSnapshots plus bootstrap commonSettings/featureFacts/compareMode/commonMaster",
                    "dependencies", "inventory configurationAccessors and gatePolicy",
                    "guardOutcome", uses == null ? "unavailable: callback entry and raw settings do not prove successful use"
                            : "successful guarded operations counted separately; zero does not identify which guard prevented use"));
            result.add(record);
        }
        return result;
    }

    public static List<Map<String, Object>> startupSnapshot() {
        return StartupDiagnostics.get("compatibility snapshot", () -> {
            CompatMixinGroups current = published;
            return current == null ? List.of() : current.snapshot();
        }, List.of());
    }

    /** Invoked only by debug-instrumented merged methods; no game objects or throwables retained. */
    public static void recordFirstUse(String mixin) {
        StartupDiagnostics.run("compatibility callback observation", () -> {
            CompatMixinGroups current = published;
            if (current != null) {
                AtomicBoolean used = current.firstUses.get(mixin);
                if (used != null && used.compareAndSet(false, true)) {
                    current.notePartialInvocation(mixin);
                    if (usageDiagnostics) LOGGER.info("VHA_COMPAT_FIRST_USE contextId={}, group={}, mixin={}, count=1, kind=callback_only",
                            current.context.get("contextId"), groupOf(mixin), mixin);
                }
            }
        });
    }

    private synchronized void notePartialInvocation(String mixin) {
        String group = groupOf(mixin);
        if (withholdsActivation(group)) return;
        Set<String> required = expected.getOrDefault(group, Set.of());
        if (StartupAdapterLayouts.isGroup(group)) {
            // Observer targets load independently. A transformed ScriptManager can run
            // before RecipeEventJS loads, without relying on the latter's observer.
            List<String> ownTargets = required.stream()
                    .filter(target -> target.startsWith(mixin + " -> ")).toList();
            if (ownTargets.isEmpty() || !applied.containsAll(ownTargets)) {
                unsafePartialUse.add(group);
            }
        } else if (!required.isEmpty() && !applied.containsAll(required)) {
            unsafePartialUse.add(group);
        }
    }

    private static boolean isCdfGroup(String group) {
        return VaultLootCdfLayouts.GROUP.equals(group) || VaultLootCdfLayouts.LAZY_GROUP.equals(group);
    }

    private boolean withholdsActivation(String group) {
        return CommonCompatibilityLayouts.isCommonGroup(group);
    }

    /** A common-side port cannot mutate state while only part of its group is transformed. */
    public static boolean activationAllowed(String mixin) {
        CompatMixinGroups current = published;
        if (VaultLootCdfLayouts.MIXIN.equals(mixin) || VaultLootCdfLayouts.LAZY_MIXIN.equals(mixin)) {
            if (current == null) return false;
            AtomicBoolean cdf = current.activated.get(mixin);
            return cdf != null && cdf.get();
        }
        // The state codec hook runs inside every StateDefinition constructor, so it needs the confirmation
        // outright: an unpublished or unconfirmed context keeps the eager codec.
        if (dev.hoyin1600p.vhaccelerator.compat.statecodec.StateCodecLayouts.MIXIN.equals(mixin)) {
            AtomicBoolean codec = current == null ? null : current.activated.get(mixin);
            return codec != null && codec.get();
        }
        if (current == null) return true;
        AtomicBoolean activation = current.activated.get(mixin);
        return activation == null || activation.get();
    }

    /**
     * Exact-version output contexts must be available before legacy client
     * parallel matching is safe. A rejected/incomplete group keeps CT's loop.
     * This query deliberately does not initialize any optional CT helper.
     */
    public static boolean legacyCraftTweakerParallelAllowed() {
        CompatMixinGroups current = published;
        if (current == null) return false;
        synchronized (current) {
            Object versions = current.context.get("installedVersions");
            Object version = versions instanceof Map<?, ?> map ? map.get("crafttweaker") : null;
            String group = "dev.hoyin1600p.vhaccelerator.mixin.compat.crafttweaker.removals";
            Set<String> required = current.expected.getOrDefault(group, Set.of());
            boolean complete = Boolean.TRUE.equals(current.decisions.get(group))
                    && !required.isEmpty() && current.applied.containsAll(required);
            return legacyCraftTweakerParallelAllowed(version instanceof String text ? text : null, complete);
        }
    }

    static boolean legacyCraftTweakerParallelAllowed(String version, boolean contextsComplete) {
        return version != null && (!version.equals("9.1.213") || contextsComplete);
    }

    /** Count only a successful guarded optimization, never merely entry into its handler. */
    public static void recordOptimizationUse(String mixin) {
        if (!usageDiagnostics) return;
        StartupDiagnostics.run("compatibility optimization observation", () -> {
            if (!activationAllowed(mixin)) return;
            CompatMixinGroups current = published;
            if (current != null) {
                LongAdder count = current.optimizationUses.get(mixin);
                if (count != null) {
                    count.increment();
                    if (current.successfulUseLogged.add(mixin)) LOGGER.info(
                            "VHA_COMPAT_FIRST_USE contextId={}, group={}, mixin={}, count=1, kind=successful_guarded_operation",
                            current.context.get("contextId"), groupOf(mixin), mixin);
                }
            }
        });
    }

    /** Verify before report serialization; diagnostic failure invalidates measurement without stopping startup. */
    public static void verifyStartupApplication() {
        StartupDiagnostics.run("compatibility application verification", () -> {
            CompatMixinGroups current = published;
            if (usageDiagnostics && current != null && !current.unsafePartialUse.isEmpty()) {
                throw new IllegalStateException("Invalid startup measurement: compatibility callback executed before required target application: "
                        + String.join(", ", current.unsafePartialUse));
            }
        });
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Reads the fully qualified mixin names from a mixin configuration JSON
     * resource. Returns an empty list if it cannot be read.
     */
    public static List<String> readConfiguredMixins(
            ClassLoader loader,
            String resource
    ) {
        List<String> names = new ArrayList<>();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return names;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                String mixinPackage = config.get("package").getAsString();
                for (String section : List.of("mixins", "client", "server")) {
                    JsonElement element = config.get(section);
                    if (element instanceof JsonArray array) {
                        for (JsonElement entry : array) {
                            names.add(mixinPackage + "." + entry.getAsString());
                        }
                    }
                }
            }
        } catch (Exception exception) {
            StartupDiagnostics.run("compatibility configuration warning", () ->
                    LOGGER.debug("Unable to read mixin configuration {}", resource, exception));
            names.clear();
        }
        return names;
    }
}

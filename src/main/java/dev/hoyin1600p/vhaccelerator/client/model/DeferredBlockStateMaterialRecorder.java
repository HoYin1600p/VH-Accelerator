package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.MaterialId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Pure core of the opt-in block-state material recorder. It builds a
 * {@link PersistentDeferredBlockStateManifest} from already-loaded graphs
 * for later experiments; nothing reads that manifest yet.
 *
 * <p>Holds no Minecraft types, so tests can drive it directly. The bakery
 * adapter lives in {@link DeferredBlockStateBaking}.
 */
public final class DeferredBlockStateMaterialRecorder {
    static final int MAX_DETAILS = 8;

    private DeferredBlockStateMaterialRecorder() {
    }

    /**
     * Initial launch only, flag on, optimizations on, Compare Mode off, and
     * no CTM. Short-circuits in that order, so the off path reads only the
     * launch state and the flag.
     */
    static boolean shouldRecord(
            boolean launchFinished,
            BooleanSupplier flagEnabled,
            BooleanSupplier optimizationsEnabled,
            BooleanSupplier compareMode,
            BooleanSupplier compatible
    ) {
        return !launchFinished
                && flagEnabled.getAsBoolean()
                && optimizationsEnabled.getAsBoolean()
                && !compareMode.getAsBoolean()
                && compatible.getAsBoolean();
    }

    /**
     * A candidate's block ID, that block's full possible-state count, and
     * the bakery's raw model-group ID of the candidate's state
     * ({@code -1} ungrouped, {@code 0} non-{@code MODEL}, else positive).
     */
    record StateGroup(String block, int blockStates, int group) {
    }

    /**
     * Records every candidate whose key, model group, and complete material
     * list are valid. An ineligible or throwing candidate is skipped before
     * the sticky {@code Builder#add}, so it never closes the builder and is
     * never partially recorded. A null or inconsistent group is uncertain
     * and skipped. Every candidate of a block is skipped when that block's
     * candidates disagree on its state count, outnumber its states, or
     * share a positive raw group ID with another block. A null fingerprint
     * records nothing and never reads the candidates. A failure outside one
     * candidate propagates, and the caller writes nothing.
     */
    static <K> Result record(
            String fingerprint,
            Supplier<? extends Iterable<? extends K>> candidates,
            Function<? super K, StateGroup> groups,
            Function<? super K, ? extends Collection<MaterialId>> materials
    ) {
        if (fingerprint == null) {
            return new Result(null, false, 0, 0, 0, 0, List.of());
        }
        List<String> details = new ArrayList<>();
        List<Pending> pending = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        int seen = 0;
        int skipped = 0;
        int failed = 0;
        for (K candidate : candidates.get()) {
            seen++;
            String key = null;
            StateGroup group;
            List<MaterialId> ids;
            try {
                key = String.valueOf(candidate);
                if (!PersistentDeferredBlockStateManifest.validModelKey(key)) {
                    skipped++;
                    detail(details, key, "not a plain minecraft variant");
                    continue;
                }
                if (!keys.add(key)) {
                    skipped++;
                    detail(details, key, "duplicate key");
                    continue;
                }
                group = groups.apply(candidate);
                if (!certainGroup(key, group)) {
                    skipped++;
                    detail(details, key, "uncertain model group");
                    continue;
                }
                Collection<MaterialId> collected = materials.apply(candidate);
                // One snapshot for both the check and the add.
                ids = collected == null ? null : new ArrayList<>(collected);
            } catch (RuntimeException | LinkageError failure) {
                failed++;
                detail(details, key, failure.getClass().getSimpleName());
                continue;
            }
            pending.add(new Pending(key, group, ids));
        }
        Set<String> inconsistent = inconsistentBlocks(pending);
        // Canonical key order keeps the builder independent of input order.
        pending.sort(Comparator.comparing(Pending::key));
        PersistentDeferredBlockStateManifest.Builder builder =
                new PersistentDeferredBlockStateManifest.Builder();
        for (Pending entry : pending) {
            StateGroup group = entry.group();
            if (inconsistent.contains(group.block())) {
                skipped++;
                detail(details, entry.key(), "inconsistent block model groups");
                continue;
            }
            if (!builder.accepts(
                    entry.key(), group.blockStates(), group.group(), entry.ids())) {
                skipped++;
                detail(details, entry.key(),
                        "incomplete, invalid, or over-limit materials");
                continue;
            }
            builder.add(
                    entry.key(), group.blockStates(), group.group(), entry.ids());
        }
        return new Result(
                builder.build(fingerprint),
                true,
                seen,
                builder.size(),
                skipped,
                failed,
                List.copyOf(details)
        );
    }

    private record Pending(String key, StateGroup group, List<MaterialId> ids) {
    }

    /** The group belongs to the key's own block and is in range. */
    static boolean certainGroup(String key, StateGroup group) {
        return group != null
                && group.block() != null
                && group.block().equals(
                        PersistentDeferredBlockStateManifest.blockOf(key))
                && group.blockStates() >= 1
                && group.group()
                        >= PersistentDeferredBlockStateManifest.UNGROUPED;
    }

    /**
     * Blocks whose candidates disagree on the state count, outnumber the
     * states, or share a positive raw group ID with another block.
     */
    private static Set<String> inconsistentBlocks(List<Pending> pending) {
        Map<String, Integer> states = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<Integer, String> owners = new HashMap<>();
        Set<String> inconsistent = new HashSet<>();
        for (Pending entry : pending) {
            StateGroup group = entry.group();
            String block = group.block();
            Integer known = states.putIfAbsent(block, group.blockStates());
            if (known != null && known != group.blockStates()) {
                inconsistent.add(block);
            }
            if (counts.merge(block, 1, Integer::sum) > group.blockStates()) {
                inconsistent.add(block);
            }
            if (group.group()
                    > PersistentDeferredBlockStateManifest.NON_MODEL_GROUP) {
                String owner = owners.putIfAbsent(group.group(), block);
                if (owner != null && !owner.equals(block)) {
                    inconsistent.add(owner);
                    inconsistent.add(block);
                }
            }
        }
        return inconsistent;
    }

    private static void detail(List<String> details, String key, String reason) {
        if (details.size() < MAX_DETAILS) {
            details.add((key == null ? "<unnamed>" : key) + " (" + reason + ")");
        }
    }

    /**
     * {@code manifest} is null when nothing may be written: no fingerprint,
     * nothing recorded, or an unusable fingerprint. {@code details} holds at
     * most {@link #MAX_DETAILS} skipped or failed keys.
     */
    record Result(
            PersistentDeferredBlockStateManifest.Manifest manifest,
            boolean fingerprinted,
            int candidates,
            int recorded,
            int skipped,
            int failed,
            List<String> details
    ) {
    }
}

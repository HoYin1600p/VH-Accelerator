package dev.hoyin1600p.vhaccelerator.client.model;

import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest;
import dev.hoyin1600p.vhaccelerator.client.cache.PersistentDeferredBlockStateManifest.MaterialId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
     * Records every candidate whose key and complete material list are
     * valid. An ineligible or throwing candidate is skipped before the
     * sticky {@code Builder#add}, so it never closes the builder and is
     * never partially recorded. A null fingerprint records nothing and
     * never reads the candidates. A failure outside one candidate
     * propagates, and the caller writes nothing.
     */
    static <K> Result record(
            String fingerprint,
            Supplier<? extends Iterable<? extends K>> candidates,
            Function<? super K, ? extends Collection<MaterialId>> materials
    ) {
        if (fingerprint == null) {
            return new Result(null, false, 0, 0, 0, 0, List.of());
        }
        PersistentDeferredBlockStateManifest.Builder builder =
                new PersistentDeferredBlockStateManifest.Builder();
        List<String> details = new ArrayList<>();
        int seen = 0;
        int skipped = 0;
        int failed = 0;
        for (K candidate : candidates.get()) {
            seen++;
            String key = null;
            List<MaterialId> ids;
            try {
                key = String.valueOf(candidate);
                if (!PersistentDeferredBlockStateManifest.validModelKey(key)) {
                    skipped++;
                    detail(details, key, "not a plain minecraft variant");
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
            if (!builder.accepts(key, ids)) {
                skipped++;
                detail(details, key, "incomplete, invalid, or over-limit materials");
                continue;
            }
            builder.add(key, ids);
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

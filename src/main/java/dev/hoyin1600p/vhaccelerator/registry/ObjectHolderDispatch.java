package dev.hoyin1600p.vhaccelerator.registry;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ObjectHolderRegistry;

/**
 * Entry points for the mixins that index Forge's object-holder dispatch.
 *
 * <p>{@code GameData.applyHolderLookups(name)} announces the registry with
 * {@link #announce}; the next {@code ObjectHolderRegistry.applyObjectHolders(filter)}
 * on the same thread checks that its filter really is "equals that name"
 * ({@link #enter}) and, if so, replaces its full loop by {@link #forEach}, which
 * passes only the handlers able to act for that registry to Forge's own loop
 * body. Every other call, and every failure, runs Forge's original loop.</p>
 */
public final class ObjectHolderDispatch {
    private static final int MAX_REBUILDS = 8;
    private static final ResourceLocation PROBE =
            new ResourceLocation("vhaccelerator", "object_holder_probe_never_registered");
    private static final ThreadLocal<ResourceLocation> ANNOUNCED = new ThreadLocal<>();
    private static final ThreadLocal<ResourceLocation> ACTIVE = new ThreadLocal<>();

    private static volatile HolderDispatch<Object> dispatch;
    private static volatile ForgeHolderClassifier classifier;

    private ObjectHolderDispatch() {
    }

    /** Called when Forge starts applying holder lookups for one registry. */
    public static void announce(ResourceLocation registryName) {
        ANNOUNCED.set(registryName);
    }

    /** Called when {@code GameData.applyHolderLookups} returns: an unconsumed announcement must not linger. */
    public static void clearAnnouncement() {
        ANNOUNCED.remove();
    }

    /** The class-name prefix of the method-reference filter {@code GameData.applyHolderLookups} passes. */
    static final String ANNOUNCED_FILTER_PREFIX = "net.minecraftforge.registries.GameData$$Lambda";

    /** Called at the start of {@code applyObjectHolders(filter)}. */
    public static void enter(Predicate<ResourceLocation> filter) {
        enter(filter, ANNOUNCED_FILTER_PREFIX);
    }

    static void enter(Predicate<ResourceLocation> filter, String filterClassPrefix) {
        ResourceLocation name = ANNOUNCED.get();
        ANNOUNCED.remove();
        ACTIVE.remove();
        if (name == null || filter == null || !filter.getClass().getName().startsWith(filterClassPrefix)) {
            return;
        }
        try {
            // The announced call passes "name::equals"; refuse anything that does not behave like it.
            if (filter.test(name) && !filter.test(PROBE)) {
                ACTIVE.set(name);
            }
        } catch (RuntimeException ignored) {
            // leave the original loop
        }
    }

    /** The registry the current thread's announced pass is for, or null (test hook). */
    static ResourceLocation activeKey() {
        return ACTIVE.get();
    }

    /**
     * Replaces {@code holders.forEach(body)} for an announced single-registry call.
     *
     * @return false when Forge's original loop must run; true only after every selected handler ran
     */
    public static boolean forEach(Set<?> holders, Consumer<?> body) {
        ResourceLocation key = ACTIVE.get();
        if (key == null) {
            return false;
        }
        ACTIVE.remove();
        HolderDispatch<Object> engine;
        HolderDispatchIndex<Object> snapshot;
        Consumer<Object> loopBody;
        try {
            engine = engine();
            if (!classifier.registryClassReviewed()) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Set<Object> source = (Set<Object>) holders;
            @SuppressWarnings("unchecked")
            Consumer<Object> cast = (Consumer<Object>) body;
            loopBody = cast;
            snapshot = engine.prepare(source, ObjectHolderRegistry.class);
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
        if (snapshot == null) {
            return false;
        }
        // Outside the try: once handlers run, nothing may be swallowed or replayed.
        snapshot.visit(key, loopBody);
        engine.record(snapshot, key);
        return true;
    }

    /**
     * Dedicated servers call this when loading ends: holder passes are over, so the index is only memory.
     * A later pass (there is none in normal operation) rebuilds it on demand.
     */
    public static void releaseIndex() {
        HolderDispatch<Object> engine = dispatch;
        if (engine != null) {
            engine.release();
        }
    }

    /** Reports an add, remove or prune of the handler set. */
    public static void mutated() {
        HolderDispatch<Object> engine = dispatch;
        if (engine != null) {
            engine.mutated();
        }
    }

    private static HolderDispatch<Object> engine() {
        HolderDispatch<Object> engine = dispatch;
        if (engine == null) {
            synchronized (ObjectHolderDispatch.class) {
                engine = dispatch;
                if (engine == null) {
                    ForgeHolderClassifier resolved = ForgeHolderClassifier.resolve(
                            ObjectHolderDispatch.class.getClassLoader());
                    classifier = resolved;
                    engine = new HolderDispatch<>(resolved, MAX_REBUILDS);
                    // Mutations seen before the engine existed cannot be older than its first build.
                    dispatch = engine;
                }
            }
        }
        return engine;
    }

    /** One diagnostic line, or null when the index never ran. */
    public static String summaryLine() {
        HolderDispatch<Object> engine = dispatch;
        ForgeHolderClassifier resolved = classifier;
        if (engine == null || resolved == null) {
            return null;
        }
        if (!resolved.registryClassReviewed()) {
            return "Object-holder dispatch index: not used (ObjectHolderRegistry is not a reviewed Forge build)";
        }
        HolderDispatch.Summary s = engine.summary();
        if (s.events() == 0 && !s.disabled()) {
            return null;
        }
        return "Object-holder dispatch index: " + s.events() + " registry events visited " + s.visited()
                + " handlers instead of " + s.fullPassEquivalent() + " (" + s.handlers() + " handlers, "
                + s.buckets() + " registries, " + s.alwaysVisited() + " always visited, " + s.builds()
                + " builds" + (s.disabled() ? ", switched off after repeated changes" : "") + "; ObjectHolderRef "
                + (resolved.indexesObjectHolderRefs() ? "indexed" : "layout not reviewed") + ", RegistryObject "
                + (resolved.indexesRegistryKeyHandlers() ? "indexed" : "layout not reviewed") + ")";
    }
}

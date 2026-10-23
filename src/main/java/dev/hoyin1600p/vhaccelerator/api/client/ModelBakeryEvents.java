package dev.hoyin1600p.vhaccelerator.api.client;

import com.mojang.datafixers.util.Pair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Client-only integration for native or bundled model providers.
 * Providers register by namespace before the first resource reload. Each reload
 * snapshots the listeners into a session that closes after baking.
 * Callback failures quarantine that provider for the session; ordinary loading
 * remains the fallback, but bundled-only resources need a working provider.
 */
public final class ModelBakeryEvents {
    /** Version accepted by {@link #register}; pass this value when registering. */
    public static final int API_VERSION = 1;
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator Model API");
    private static final Registry REGISTRY = new Registry();

    private ModelBakeryEvents() {
    }

    /**
     * Registers one listener per namespace during client setup, before the first reload.
     * Does not load the provider's mod. Later registrations affect future sessions.
     *
     * @return the registration result; an existing listener is never replaced
     */
    public static Registration register(String namespace, int version, Listener listener) {
        return REGISTRY.register(namespace, version, listener);
    }

    /** Reports registration, even if the provider is quarantined in the current session. */
    public static boolean isRegistered(String namespace) {
        return namespace != null && REGISTRY.listeners.containsKey(namespace);
    }

    /** Bridge implemented by the bakery mixin for reload-scoped provider access. */
    public interface Access {
        /** Returns this bakery's session, or null outside its reload lifecycle. */
        Session vhaccelerator$modelSession();
        /** Looks up resources through the bakery and prepares native block-state data when available. */
        List<Resource> vhaccelerator$resources(ResourceManager resources, ResourceLocation location) throws IOException;
    }

    /**
     * Uses the bakery bridge when present, otherwise reads ordinary resources.
     * The bridge may reuse parsed resources or return an empty list for a native
     * block state prepared in the open session; its definition is merged separately.
     *
     * @throws IOException if resource lookup fails
     */
    public static List<Resource> resources(Object bakery, ResourceManager resources, ResourceLocation location)
            throws IOException {
        return bakery instanceof Access access ? access.vhaccelerator$resources(resources, location)
                : resources.getResources(location);
    }

    /**
     * Queries this bakery's open session, or returns null if none exists.
     * A null result also means the provider declined, failed, or lost pack precedence.
     */
    public static BlockModel model(Object bakery, ResourceLocation location) {
        Session session = bakery instanceof Access access ? access.vhaccelerator$modelSession() : null;
        return session == null ? null : session.model(location);
    }

    /** Reports a prepared provider block state only while this bakery's session is open. */
    public static boolean hasBlockState(Object bakery, ResourceLocation location) {
        Session session = bakery instanceof Access access ? access.vhaccelerator$modelSession() : null;
        return session != null && !session.closed && session.states.containsKey(
                new ResourceLocation(location.getNamespace(), location.getPath()));
    }

    /**
     * Registration outcomes: accepted, namespace already taken, unsupported API version,
     * or invalid namespace/listener. The version is checked before the arguments.
     */
    public enum Registration {
        REGISTERED, DUPLICATE_NAMESPACE, UNSUPPORTED_VERSION, INVALID_ARGUMENT
    }

    /**
     * Namespace provider callbacks, serialized per provider within each session.
     * Query callbacks return null to leave ordinary loading in charge.
     * Exceptions quarantine the provider for that reload; cleanup is still attempted.
     */
    public interface Listener {
        /** Opens provider preparation when the reload session begins. */
        default void beforePreparation(ResourceManager resources) throws Exception { }
        /** Supplies a model by logical location, or null to use ordinary resources. */
        default Model model(ResourceManager resources, ResourceLocation location) throws Exception { return null; }
        /** Supplies a block-state definition, or null to use ordinary resources. */
        default BlockStateDefinition blockState(ResourceManager resources, ResourceLocation location,
                StateDefinition<Block, BlockState> states) throws Exception { return null; }
        /** Finishes preparation after the bakery has loaded its model graphs. */
        default void afterPreparation(ResourceManager resources) throws Exception { }
        /** Runs before the bakery begins baking the prepared models. */
        default void beforeBaking(ResourceManager resources) throws Exception { }
        /** Releases reload data after baking, including for quarantined providers. */
        default void afterBaking(ResourceManager resources) throws Exception { }
    }

    /**
     * A model contribution whose source pack must be active in this reload.
     * @param sourcePack the source pack name used to preserve resource-pack precedence
     * @param value the non-null model
     */
    public record Model(String sourcePack, BlockModel value) { }
    /**
     * A block-state contribution whose source pack must be active in this reload.
     * @param sourcePack the source pack name used to preserve resource-pack precedence
     * @param value the non-null definition
     */
    public record BlockStateDefinition(String sourcePack, BlockModelDefinition value) { }

    /** Snapshots registered providers and calls their preparation hooks for one reload. */
    public static Session begin(ResourceManager resources) {
        return REGISTRY.begin(resources);
    }

    static final class Registry {
        private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
        private final Map<String, Listener> listeners = new ConcurrentHashMap<>();

        Registration register(String namespace, int version, Listener listener) {
            if (version != API_VERSION) {
                return Registration.UNSUPPORTED_VERSION;
            }
            if (namespace == null || !NAMESPACE.matcher(namespace).matches() || listener == null) {
                return Registration.INVALID_ARGUMENT;
            }
            return listeners.putIfAbsent(namespace, listener) == null
                    ? Registration.REGISTERED : Registration.DUPLICATE_NAMESPACE;
        }

        Session begin(ResourceManager resources) {
            Session session = new Session(resources, new TreeMap<>(listeners));
            session.lifecycle(listener -> listener.beforePreparation(resources));
            return session;
        }
    }

    /**
     * Provider state for one resource reload, from preparation through baking.
     * Registrations added later are absent from this snapshot. Call {@link #afterBaking}
     * to close the session and release prepared block-state data.
     */
    public static final class Session {
        private final ResourceManager resources;
        private final List<String> packs;
        private final Map<String, Entry> listeners;
        private final Map<ResourceLocation, BlockStateDefinition> states = new ConcurrentHashMap<>();
        private final Set<ResourceLocation> stateMisses = ConcurrentHashMap.newKeySet();
        private volatile boolean closed;

        private Session(ResourceManager resources, Map<String, Listener> snapshot) {
            this.resources = resources;
            this.packs = resources.listPacks().map(PackResources::getName).toList();
            Map<String, Entry> entries = new TreeMap<>();
            snapshot.forEach((namespace, listener) -> entries.put(namespace, new Entry(namespace, listener)));
            this.listeners = Collections.unmodifiableMap(entries);
        }

        /**
         * Returns a valid provider model while open, unless an ordinary resource wins.
         * Returns null for missing, declined, quarantined, or builtin model queries.
         */
        public BlockModel model(ResourceLocation location) {
            Entry entry = listeners.get(location.getNamespace());
            if (entry == null || closed || location.getPath().startsWith("builtin/")) {
                return null;
            }
            Model contribution = entry.call(() -> entry.listener.model(resources, location));
            if (contribution == null) {
                return null;
            }
            if (contribution.value() == null) {
                entry.fail(new IllegalArgumentException("Provider returned a null model"));
                return null;
            }
            if (!validPack(entry, contribution.sourcePack())) {
                return null;
            }
            ResourceLocation file = new ResourceLocation(location.getNamespace(), "models/" + location.getPath() + ".json");
            if (resources.hasResource(file)) {
                try (Resource resource = resources.getResource(file)) {
                    int index = packs.indexOf(resource.getSourceName());
                    if (index < 0 || index > packs.indexOf(contribution.sourcePack())) {
                        return null;
                    }
                } catch (java.io.IOException | RuntimeException failure) {
                    entry.fail(failure);
                    return null;
                }
            }
            contribution.value().name = location.toString();
            return contribution.value();
        }

        /** Caches a valid provider definition while open; returns false when unavailable. */
        public boolean prepareBlockState(ResourceLocation location, StateDefinition<Block, BlockState> definition) {
            Entry entry = listeners.get(location.getNamespace());
            if (entry == null || closed || stateMisses.contains(location)) {
                return false;
            }
            synchronized (entry) {
                if (states.containsKey(location)) {
                    return true;
                }
                BlockStateDefinition contribution = entry.call(() -> entry.listener.blockState(resources, location, definition));
                if (contribution != null && contribution.value() == null) {
                    entry.fail(new IllegalArgumentException("Provider returned a null blockstate definition"));
                }
                if (contribution == null || contribution.value() == null || !validPack(entry, contribution.sourcePack())) {
                    stateMisses.add(location);
                    return false;
                }
                states.put(location, contribution);
                return true;
            }
        }

        /**
         * Merges a prepared definition in source-pack order, replacing that pack's entry.
         * Returns the original list when no definition exists or this session is closed.
         */
        public List<Pair<String, BlockModelDefinition>> mergeBlockState(ResourceLocation location,
                List<Pair<String, BlockModelDefinition>> original) {
            BlockStateDefinition contribution = states.get(location);
            if (contribution == null || closed) {
                return original;
            }
            List<Pair<String, BlockModelDefinition>> merged = new ArrayList<>(original);
            // One definition per source pack; external packs retain their original precedence.
            merged.removeIf(pair -> contribution.sourcePack().equals(pair.getFirst()));
            int index = packs.indexOf(contribution.sourcePack());
            int insertion = 0;
            while (insertion < merged.size()) {
                int other = packs.indexOf(merged.get(insertion).getFirst());
                if (other < 0 || other > index) break;
                insertion++;
            }
            merged.add(insertion, Pair.of(contribution.sourcePack(), contribution.value()));
            return merged;
        }

        /** Calls preparation-complete hooks for active providers while the session is open. */
        public void afterPreparation() {
            lifecycle(listener -> listener.afterPreparation(resources));
        }

        /** Calls pre-bake hooks for active providers while the session is open. */
        public void beforeBaking() {
            lifecycle(listener -> listener.beforeBaking(resources));
        }

        /** Closes once, attempts cleanup for every provider, and clears prepared state. */
        public synchronized void afterBaking() {
            if (closed) {
                return;
            }
            closed = true;
            // Give quarantined providers a guarded cleanup attempt too.
            listeners.values().forEach(entry -> entry.call(() -> {
                entry.listener.afterBaking(resources);
                return null;
            }, true));
            states.clear();
            stateMisses.clear();
        }

        private boolean validPack(Entry entry, String name) {
            if (name != null && packs.contains(name)) {
                return true;
            }
            entry.fail(new IllegalArgumentException("Provider returned an inactive source pack"));
            return false;
        }

        private void lifecycle(Callback callback) {
            if (closed) {
                return;
            }
            listeners.values().forEach(entry -> entry.call(() -> { callback.accept(entry.listener); return null; }));
        }
    }

    @FunctionalInterface
    private interface Callback {
        void accept(Listener listener) throws Exception;
    }

    @FunctionalInterface
    private interface Query<T> {
        T get() throws Exception;
    }

    private static final class Entry {
        private final String namespace;
        private final Listener listener;
        private boolean disabled;
        private boolean calling;

        Entry(String namespace, Listener listener) {
            this.namespace = namespace;
            this.listener = listener;
        }

        synchronized <T> T call(Query<T> query) {
            return call(query, false);
        }

        synchronized <T> T call(Query<T> query, boolean cleanup) {
            if (disabled && !cleanup) {
                return null;
            }
            if (calling) {
                fail(new IllegalStateException("Recursive model provider callback"));
                return null;
            }
            calling = true;
            try {
                T result = query.get();
                return disabled ? null : result;
            } catch (Exception | LinkageError | AssertionError failure) {
                fail(failure);
                return null;
            } finally {
                calling = false;
            }
        }

        synchronized void fail(Throwable failure) {
            if (disabled) {
                return;
            }
            disabled = true;
            LOGGER.error("Model provider {} failed; quarantined for this reload. Using the existing loader; "
                    + "bundled-only resources require the provider to recover on the next reload.", namespace, failure);
        }
    }
}

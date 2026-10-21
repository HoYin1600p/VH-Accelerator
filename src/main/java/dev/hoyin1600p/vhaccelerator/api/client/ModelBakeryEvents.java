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

// Client-only, optional integration point for native/bundled model providers.
public final class ModelBakeryEvents {
    public static final int API_VERSION = 1;
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator Model API");
    private static final Registry REGISTRY = new Registry();

    private ModelBakeryEvents() {
    }

    // Register during client setup, before the first resource reload. Never loads the provider's mod itself.
    public static Registration register(String namespace, int version, Listener listener) {
        return REGISTRY.register(namespace, version, listener);
    }

    public static boolean isRegistered(String namespace) {
        return namespace != null && REGISTRY.listeners.containsKey(namespace);
    }

    public interface Access {
        Session vhaccelerator$modelSession();
        List<Resource> vhaccelerator$resources(ResourceManager resources, ResourceLocation location) throws IOException;
    }

    public static List<Resource> resources(Object bakery, ResourceManager resources, ResourceLocation location)
            throws IOException {
        return bakery instanceof Access access ? access.vhaccelerator$resources(resources, location)
                : resources.getResources(location);
    }

    public static BlockModel model(Object bakery, ResourceLocation location) {
        Session session = bakery instanceof Access access ? access.vhaccelerator$modelSession() : null;
        return session == null ? null : session.model(location);
    }

    public static boolean hasBlockState(Object bakery, ResourceLocation location) {
        Session session = bakery instanceof Access access ? access.vhaccelerator$modelSession() : null;
        return session != null && !session.closed && session.states.containsKey(
                new ResourceLocation(location.getNamespace(), location.getPath()));
    }

    public enum Registration {
        REGISTERED, DUPLICATE_NAMESPACE, UNSUPPORTED_VERSION, INVALID_ARGUMENT
    }

    public interface Listener {
        default void beforePreparation(ResourceManager resources) throws Exception { }
        default Model model(ResourceManager resources, ResourceLocation location) throws Exception { return null; }
        default BlockStateDefinition blockState(ResourceManager resources, ResourceLocation location,
                StateDefinition<Block, BlockState> states) throws Exception { return null; }
        default void afterPreparation(ResourceManager resources) throws Exception { }
        default void beforeBaking(ResourceManager resources) throws Exception { }
        default void afterBaking(ResourceManager resources) throws Exception { }
    }

    public record Model(String sourcePack, BlockModel value) { }
    public record BlockStateDefinition(String sourcePack, BlockModelDefinition value) { }

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

        public void afterPreparation() {
            lifecycle(listener -> listener.afterPreparation(resources));
        }

        public void beforeBaking() {
            lifecycle(listener -> listener.beforeBaking(resources));
        }

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

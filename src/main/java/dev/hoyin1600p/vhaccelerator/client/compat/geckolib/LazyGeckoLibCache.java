package dev.hoyin1600p.vhaccelerator.client.compat.geckolib;

import com.mojang.logging.LogUtils;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapDebugDiagnostics;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;

/**
 * GeckoLib 3 parses and builds every animation and geo model json in every
 * mod during each resource reload and keeps all of them, although a session
 * only renders the entities, blocks and items it meets (Wolds: 464 files, two
 * copies of the cache including Ars Nouveau's shaded one). The reload now
 * only lists the files; each one is loaded by GeckoLib's own loader the first
 * time it is asked for and then kept, so a model or animation is the same
 * instance on every later lookup, as before. GeckoLib itself only reads the
 * maps with {@code get}; iteration loads everything.
 */
public final class LazyGeckoLibCache {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();

    private LazyGeckoLibCache() {
    }

    /** Returns null when the cache class has an unexpected shape or the option is off. */
    public static CompletableFuture<Void> reload(
            Object cache,
            PreparableReloadListener.PreparationBarrier barrier,
            ResourceManager manager,
            Executor background,
            Executor game
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.lazyGeckoLibResources)) {
            return null;
        }
        Access access = ACCESS.computeIfAbsent(cache.getClass(), Access::find);
        if (!access.valid()) {
            return null;
        }
        return CompletableFuture.supplyAsync(() -> {
                    LazyResourceMap<Object> animations = new LazyResourceMap<>(
                            access.keys(manager.listResources("animations", name -> name.endsWith(".json"))),
                            (location, resources) -> access.loadAnimation(cache, location, resources));
                    LazyResourceMap<Object> geoModels = new LazyResourceMap<>(
                            access.keys(manager.listResources("geo", name -> name.endsWith(".json"))),
                            (location, resources) -> access.loadModel(cache, location, resources));
                    // The first file of each kind loads GeckoLib's parser and builder
                    // classes (160-260 ms in Wolds); do that here, not on first render.
                    animations.warmUp(manager);
                    geoModels.warmUp(manager);
                    return new Listing(animations, geoModels);
                }, background)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(listing -> {
                    access.set(access.animations, cache, listing.animations);
                    access.set(access.geoModels, cache, listing.geoModels);
                }, game);
    }

    private record Listing(Map<ResourceLocation, Object> animations, Map<ResourceLocation, Object> geoModels) {
    }

    private static final class Access {
        private final Field animationLoader;
        private final Field modelLoader;
        private final Field parser;
        private final Field animations;
        private final Field geoModels;
        private final Set<?> excludedNamespaces;
        private final Method loadAllAnimations;
        private final Method loadModel;

        private Access(
                Field animationLoader,
                Field modelLoader,
                Field parser,
                Field animations,
                Field geoModels,
                Set<?> excludedNamespaces,
                Method loadAllAnimations,
                Method loadModel
        ) {
            this.animationLoader = animationLoader;
            this.modelLoader = modelLoader;
            this.parser = parser;
            this.animations = animations;
            this.geoModels = geoModels;
            this.excludedNamespaces = excludedNamespaces;
            this.loadAllAnimations = loadAllAnimations;
            this.loadModel = loadModel;
        }

        static Access find(Class<?> type) {
            try {
                Field animationLoader = field(type, "animationLoader");
                Field modelLoader = field(type, "modelLoader");
                Field parser = field(type, "parser");
                Field animations = field(type, "animations");
                Field geoModels = field(type, "geoModels");
                // Ars Nouveau's older copy has no excluded namespaces.
                Set<?> excluded = Set.of();
                try {
                    excluded = (Set<?>) field(type, "excludedNamespaces").get(null);
                } catch (NoSuchFieldException absent) {
                    // keep the empty set
                }
                Method loadAllAnimations = animationLoader.getType().getMethod(
                        "loadAllAnimations", parser.getType(), ResourceLocation.class, ResourceManager.class);
                Method loadModel = modelLoader.getType().getMethod(
                        "loadModel", ResourceManager.class, ResourceLocation.class);
                if (animations.getType() != Map.class || geoModels.getType() != Map.class) {
                    throw new NoSuchFieldException("cache maps");
                }
                return new Access(animationLoader, modelLoader, parser, animations, geoModels,
                        excluded, loadAllAnimations, loadModel);
            } catch (ReflectiveOperationException | RuntimeException failure) {
                LOGGER.warn("GeckoLib cache {} has an unexpected shape; loading it eagerly", type.getName(), failure);
                return new Access(null, null, null, null, null, null, null, null);
            }
        }

        boolean valid() {
            return loadModel != null;
        }

        private static Field field(Class<?> type, String name) throws NoSuchFieldException {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        /** GeckoLib skips these namespaces: other mods use the same folder names. */
        Set<ResourceLocation> keys(Collection<ResourceLocation> listed) {
            Set<ResourceLocation> keys = ConcurrentHashMap.newKeySet(listed.size());
            for (ResourceLocation location : listed) {
                if (!excludedNamespaces.contains(location.getNamespace().toLowerCase(Locale.ROOT))) {
                    keys.add(location);
                }
            }
            return keys;
        }

        void set(Field field, Object cache, Map<ResourceLocation, ?> value) {
            try {
                field.set(cache, value);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }

        Object loadAnimation(Object cache, ResourceLocation location, ResourceManager resources) {
            try {
                return loadAllAnimations.invoke(animationLoader.get(cache), parser.get(cache),
                        location, resources);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not load GeckoLib animation " + location, failure);
            }
        }

        Object loadModel(Object cache, ResourceLocation location, ResourceManager resources) {
            try {
                return loadModel.invoke(modelLoader.get(cache), resources, location);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not load GeckoLib model " + location, failure);
            }
        }
    }

    /**
     * Keys are the files found at reload plus anything put later. A file that
     * fails to load is logged once and dropped, which GeckoLib reports as a
     * missing file where it is used.
     */
    static final class LazyResourceMap<V> extends AbstractMap<ResourceLocation, V> {
        private final Set<ResourceLocation> keys;
        private final Map<ResourceLocation, V> loaded = new ConcurrentHashMap<>();
        private final BiFunction<ResourceLocation, ResourceManager, ?> loader;

        LazyResourceMap(Set<ResourceLocation> keys, BiFunction<ResourceLocation, ResourceManager, ?> loader) {
            this.keys = keys;
            this.loader = loader;
        }

        /** Loads the first listed file with the reload's own resources. */
        void warmUp(ResourceManager resources) {
            for (ResourceLocation location : keys) {
                try {
                    loaded.put(location, load(location, resources));
                } catch (RuntimeException failure) {
                    // Left to the first get, which logs it as usual.
                }
                return;
            }
        }

        @Override
        public V get(Object key) {
            V value = loaded.get(key);
            if (value != null || !(key instanceof ResourceLocation location) || !keys.contains(location)) {
                return value;
            }
            try {
                return loaded.computeIfAbsent(location,
                        missing -> load(missing, Minecraft.getInstance().getResourceManager()));
            } catch (RuntimeException failure) {
                LOGGER.error("GeckoLib could not load {}", location, failure);
                keys.remove(location);
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private V load(ResourceLocation location, ResourceManager resources) {
            if (!BootstrapDebugDiagnostics.enabled()) {
                return (V) loader.apply(location, resources);
            }
            long start = System.nanoTime();
            V value = (V) loader.apply(location, resources);
            LOGGER.info("[debug] GeckoLib loaded {} on first use in {} ms", location,
                    String.format(Locale.ROOT, "%.2f", (System.nanoTime() - start) / 1_000_000.0));
            return value;
        }

        @Override
        public boolean containsKey(Object key) {
            return keys.contains(key);
        }

        @Override
        public V put(ResourceLocation key, V value) {
            V previous = get(key);
            loaded.put(key, value);
            keys.add(key);
            return previous;
        }

        @Override
        public V remove(Object key) {
            V previous = get(key);
            keys.remove(key);
            loaded.remove(key);
            return previous;
        }

        @Override
        public int size() {
            return keys.size();
        }

        @Override
        public Set<Entry<ResourceLocation, V>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public Iterator<Entry<ResourceLocation, V>> iterator() {
                    Iterator<ResourceLocation> source = Set.copyOf(keys).iterator();
                    return new Iterator<>() {
                        private Entry<ResourceLocation, V> next;
                        private ResourceLocation last;

                        @Override
                        public boolean hasNext() {
                            while (next == null && source.hasNext()) {
                                ResourceLocation key = source.next();
                                V value = LazyResourceMap.this.get(key);
                                if (value != null) {
                                    next = new SimpleImmutableEntry<>(key, value);
                                }
                            }
                            return next != null;
                        }

                        @Override
                        public Entry<ResourceLocation, V> next() {
                            if (!hasNext()) {
                                throw new NoSuchElementException();
                            }
                            Entry<ResourceLocation, V> entry = next;
                            next = null;
                            last = entry.getKey();
                            return entry;
                        }

                        @Override
                        public void remove() {
                            if (last == null) {
                                throw new IllegalStateException();
                            }
                            LazyResourceMap.this.remove(last);
                            last = null;
                        }
                    };
                }

                @Override
                public int size() {
                    return keys.size();
                }
            };
        }
    }
}

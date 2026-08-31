/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/resources/PackResourcesCacheEngine.java
 * Upstream commit: 4cde23f4fe2c3c8422ea21195a6f6f9125ef7dc8
 * Original copyright: Copyright (c) 2025 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the tree index to Forge 40's PathResourcePack API,
 * restricted it to immutable filesystems, and added fail-closed scan limits and
 * Minecraft 1.18.2's server-language namespace fallback.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/**
 * Lazy, immutable tree of the {@code assets/} and {@code data/} roots in a
 * jar-backed Forge path pack.
 *
 * <p>The entire pack is accepted atomically. Any failed or suspicious scan
 * permanently returns control to Forge's original implementation, so callers
 * can never observe a partial index.</p>
 */
public final class ImmutablePathPackIndex {
    private static final int MAX_FILES_PER_PACK = 1_000_000;
    private static final List<PackType> INDEXED_TYPES = List.of(
            PackType.CLIENT_RESOURCES,
            PackType.SERVER_DATA
    );

    private final PathResolver resolver;
    private final String debugName;
    private volatile Snapshot snapshot;
    private volatile boolean rejected;

    private ImmutablePathPackIndex(
            PathResolver resolver,
            String debugName
    ) {
        this.resolver = resolver;
        this.debugName = debugName;
    }

    @Nullable
    public static ImmutablePathPackIndex create(
            Path source,
            PathResolver resolver
    ) {
        try {
            String scheme = source.getFileSystem().provider().getScheme();
            if (!"jar".equalsIgnoreCase(scheme)
                    && !"union".equalsIgnoreCase(scheme)) {
                return null;
            }
            return new ImmutablePathPackIndex(
                    resolver,
                    source.toAbsolutePath().toString()
            );
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /** Creates an index for vanilla's already-resolved immutable type roots. */
    @Nullable
    public static ImmutablePathPackIndex createVanilla(
            Map<PackType, Path> roots
    ) {
        try {
            for (PackType type : INDEXED_TYPES) {
                Path root = roots.get(type);
                if (root == null || !immutableFileSystem(root)) {
                    return null;
                }
            }
            Path debugRoot = roots.get(PackType.CLIENT_RESOURCES);
            return new ImmutablePathPackIndex(
                    paths -> {
                        if (paths.length != 1) {
                            throw new IllegalArgumentException(
                                    "expected one vanilla pack root"
                            );
                        }
                        for (PackType type : INDEXED_TYPES) {
                            if (type.getDirectory().equals(paths[0])) {
                                return roots.get(type);
                            }
                        }
                        throw new IllegalArgumentException(
                                "unsupported pack root " + paths[0]
                        );
                    },
                    debugRoot.toAbsolutePath().toString()
            );
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    /**
     * Returns null until another indexed operation has built the snapshot.
     * Namespace discovery therefore never turns one cheap query into a full
     * pack scan.
     */
    @Nullable
    public Set<String> cachedNamespaces(PackType type) {
        Snapshot current = this.snapshot;
        if (current == null || this.rejected) {
            return null;
        }
        return current.namespaces(type);
    }

    /** Returns null when this path cannot be answered safely by the index. */
    @Nullable
    public Boolean hasResource(String name) {
        ParsedPath parsed = ParsedPath.parse(name);
        if (parsed == null) {
            return null;
        }
        Snapshot current = snapshot();
        if (current == null) {
            return null;
        }
        Node node = current.root(parsed.type());
        if (node == null) {
            return false;
        }
        node = node.find(parsed.components());
        return node != null && node.file;
    }

    /** Returns null when Forge must execute its original resource walk. */
    @Nullable
    public Collection<ResourceLocation> resources(
            PackType type,
            String namespace,
            String prefix,
            int maxDepth,
            Predicate<String> filter
    ) {
        if (!INDEXED_TYPES.contains(type)
                || namespace == null
                || namespace.isEmpty()
                || prefix == null
                || prefix.startsWith("/")
                || prefix.indexOf('\\') >= 0) {
            return null;
        }
        if (maxDepth < 0) {
            return new ArrayList<>();
        }
        Snapshot current = snapshot();
        if (current == null) {
            return null;
        }

        Node typeRoot = current.root(type);
        if (typeRoot == null) {
            return new ArrayList<>();
        }
        Node namespaceRoot = typeRoot.children.get(namespace);
        if (namespaceRoot == null) {
            return new ArrayList<>();
        }

        String[] components = split(prefix);
        Node requestedRoot = namespaceRoot.find(components);
        if (requestedRoot == null) {
            return new ArrayList<>();
        }

        String normalizedPrefix = String.join("/", components);
        List<ResourceLocation> matches = new ArrayList<>();
        requestedRoot.collect(
                namespace,
                normalizedPrefix,
                components.length,
                maxDepth,
                filter,
                matches
        );
        // Minecraft 1.18.2's DefaultClientPackResources appends generated
        // resources directly to the collection returned by this method.
        // Return a fresh mutable list even though the backing index is
        // immutable; returning List.of/List.copyOf crashes vanilla callers.
        return matches;
    }

    @Nullable
    private Snapshot snapshot() {
        Snapshot current = this.snapshot;
        if (current != null) {
            return current;
        }
        if (this.rejected) {
            return null;
        }
        synchronized (this) {
            current = this.snapshot;
            if (current != null) {
                return current;
            }
            if (this.rejected) {
                return null;
            }
            try {
                current = build();
                this.snapshot = current;
                return current;
            } catch (IOException | RuntimeException | LinkageError failure) {
                this.rejected = true;
                VHAccelerator.LOGGER.debug(
                        "Immutable resource tree deferred {} to Forge's original pack path",
                        this.debugName,
                        failure
                );
                return null;
            }
        }
    }

    private Snapshot build() throws IOException {
        long started = System.nanoTime();
        EnumMap<PackType, Node> roots = new EnumMap<>(PackType.class);
        EnumMap<PackType, Boolean> rootPresence =
                new EnumMap<>(PackType.class);
        int files = 0;

        for (PackType type : INDEXED_TYPES) {
            Path typePath = this.resolver.resolve(type.getDirectory())
                    .toAbsolutePath();
            boolean present = Files.isDirectory(typePath);
            rootPresence.put(type, present);
            Node root = new Node();
            roots.put(type, root);
            if (!present) {
                continue;
            }

            try (Stream<Path> stream = Files.find(
                    typePath,
                    Integer.MAX_VALUE,
                    (path, attributes) -> attributes.isRegularFile()
            )) {
                java.util.Iterator<Path> iterator = stream.iterator();
                while (iterator.hasNext()) {
                    if (++files > MAX_FILES_PER_PACK) {
                        throw new IOException(
                                "resource pack exceeds the file safety limit"
                        );
                    }
                    Path relative = typePath.relativize(
                            iterator.next().toAbsolutePath()
                    );
                    String[] components = components(relative);
                    if (components.length < 2) {
                        continue;
                    }
                    root.insert(components, 0);
                }
            }
        }

        roots.values().forEach(Node::freeze);
        Snapshot complete = new Snapshot(
                Collections.unmodifiableMap(roots),
                Collections.unmodifiableMap(rootPresence)
        );
        VHAccelerator.LOGGER.debug(
                "Indexed {} immutable resources for {} in {} ms",
                files,
                this.debugName,
                (System.nanoTime() - started) / 1_000_000L
        );
        return complete;
    }

    private static boolean immutableFileSystem(Path path) {
        String scheme = path.getFileSystem().provider().getScheme();
        return "jar".equalsIgnoreCase(scheme)
                || "union".equalsIgnoreCase(scheme);
    }

    private static String[] components(Path relative) {
        List<String> components = new ArrayList<>(relative.getNameCount());
        for (Path part : relative) {
            String component = part.toString();
            if (!component.isEmpty()) {
                components.add(component);
            }
        }
        return components.toArray(String[]::new);
    }

    private static String[] split(String path) {
        if (path.isEmpty()) {
            return new String[0];
        }
        return Stream.of(path.split("/"))
                .filter(component -> !component.isEmpty())
                .toArray(String[]::new);
    }

    private static final class Node {
        private Map<String, Node> children = new LinkedHashMap<>();
        private boolean file;

        private void insert(String[] components, int index) {
            if (index == components.length) {
                this.file = true;
                return;
            }
            this.children.computeIfAbsent(
                    components[index],
                    ignored -> new Node()
            ).insert(components, index + 1);
        }

        @Nullable
        private Node find(String[] components) {
            Node node = this;
            for (String component : components) {
                if (component.isEmpty()) {
                    continue;
                }
                node = node.children.get(component);
                if (node == null) {
                    return null;
                }
            }
            return node;
        }

        private void collect(
                String namespace,
                String path,
                int depth,
                int maxDepth,
                Predicate<String> filter,
                List<ResourceLocation> output
        ) {
            if (this.file
                    && depth <= maxDepth
                    && !path.endsWith(".mcmeta")) {
                int slash = path.lastIndexOf('/');
                String fileName = slash < 0
                        ? path
                        : path.substring(slash + 1);
                if (filter.test(fileName)) {
                    try {
                        output.add(ResourceLocation.fromNamespaceAndPath(
                                namespace,
                                path
                        ));
                    } catch (ResourceLocationException ignored) {
                        // Forge's original list path also rejects invalid IDs.
                    }
                }
            }
            if (depth >= maxDepth) {
                return;
            }
            for (Map.Entry<String, Node> child : this.children.entrySet()) {
                String childPath = path.isEmpty()
                        ? child.getKey()
                        : path + "/" + child.getKey();
                child.getValue().collect(
                        namespace,
                        childPath,
                        depth + 1,
                        maxDepth,
                        filter,
                        output
                );
            }
        }

        private void freeze() {
            for (Node child : this.children.values()) {
                child.freeze();
            }
            this.children = Collections.unmodifiableMap(
                    new LinkedHashMap<>(this.children)
            );
        }
    }

    private record Snapshot(
            Map<PackType, Node> roots,
            Map<PackType, Boolean> rootPresence
    ) {
        @Nullable
        private Node root(PackType type) {
            return this.roots.get(type);
        }

        private Set<String> namespaces(PackType type) {
            Node root = root(type);
            if (root == null) {
                return Set.of();
            }
            if (type == PackType.SERVER_DATA
                    && !this.rootPresence.getOrDefault(type, false)) {
                root = root(PackType.CLIENT_RESOURCES);
                if (root == null) {
                    return Set.of();
                }
            }
            Set<String> namespaces = new LinkedHashSet<>();
            for (Map.Entry<String, Node> entry : root.children.entrySet()) {
                if (!entry.getValue().children.isEmpty()) {
                    namespaces.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(namespaces);
        }
    }

    private record ParsedPath(PackType type, String[] components) {
        @Nullable
        private static ParsedPath parse(String name) {
            if (name == null || name.indexOf('\\') >= 0) {
                return null;
            }
            String[] components = split(name);
            if (components.length < 3) {
                return null;
            }
            PackType type;
            if (PackType.CLIENT_RESOURCES.getDirectory().equals(
                    components[0]
            )) {
                type = PackType.CLIENT_RESOURCES;
            } else if (PackType.SERVER_DATA.getDirectory().equals(
                    components[0]
            )) {
                type = PackType.SERVER_DATA;
            } else {
                return null;
            }
            String[] relative = new String[components.length - 1];
            System.arraycopy(
                    components,
                    1,
                    relative,
                    0,
                    relative.length
            );
            return new ParsedPath(type, relative);
        }
    }

    @FunctionalInterface
    public interface PathResolver {
        Path resolve(String... paths);
    }
}

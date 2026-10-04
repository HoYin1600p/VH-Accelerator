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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<PackType, Path> resolvedRoots;
    private final String debugName;
    private volatile Snapshot snapshot;
    private volatile boolean rejected;

    private ImmutablePathPackIndex(
            Map<PackType, Path> resolvedRoots,
            String debugName
    ) {
        this.resolvedRoots = Map.copyOf(resolvedRoots);
        this.debugName = debugName;
    }

    @Nullable
    public static ImmutablePathPackIndex create(
            Path source,
            PathResolver resolver
    ) {
        try {
            // Preserve the exclusion for exploded/development mod folders.
            // A virtual resolver alone must not authorize caching a disk folder.
            if (!immutableFileSystem(source) && !Files.isRegularFile(source)) {
                return null;
            }
            // Forge's mod packs expose the outer archive as getSource(), but
            // override resolve() to use IModFile.findResource (also for JarJar).
            // Validate the actual resource roots, never the archive's disk path.
            EnumMap<PackType, Path> roots = new EnumMap<>(PackType.class);
            for (PackType type : INDEXED_TYPES) {
                Path root = resolver.resolve(type.getDirectory());
                if (root == null || !immutableFileSystem(root)) {
                    return null;
                }
                roots.put(type, root.toAbsolutePath());
            }
            return new ImmutablePathPackIndex(
                    roots,
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
                    roots,
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
                || prefix.indexOf('\\') >= 0) {
            return null;
        }
        Snapshot current = snapshot();
        if (current == null) {
            return null;
        }
        if (prefix.startsWith("/")) {
            // Forge walks every file of the namespace and keeps the relative
            // paths that start with the requested one. A leading slash makes
            // the requested path absolute, which no relative path starts
            // with, so the walk always returns nothing. vhapi lists six GUI
            // texture folders this way on every blocks atlas stitch: 5.7 s of
            // walking in Wolds for empty results.
            return new ArrayList<>();
        }
        if (maxDepth < 0) {
            return new ArrayList<>();
        }

        Node typeRoot = current.root(type);
        if (typeRoot == null) {
            return new ArrayList<>();
        }
        Node namespaceRoot = typeRoot.child(namespace);
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
            Path typePath = this.resolvedRoots.get(type).toAbsolutePath();
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
                Iterator<Path> iterator = stream.iterator();
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

    /**
     * One path component. Children are kept in insertion order, which is the
     * order listings return, in parallel arrays. Directories with more than
     * {@link #LINEAR_LIMIT} children add an open-addressed table of slot
     * indices keyed by the name's cached hash, so lookups stay hash-speed
     * without a map entry per child. Every plain file shares the immutable
     * {@link #FILE} node, so a file costs one array slot and its name.
     */
    private static final class Node {
        private static final int LINEAR_LIMIT = 8;
        private static final String[] NO_NAMES = new String[0];
        private static final Node[] NO_NODES = new Node[0];
        private static final Node FILE = new Node(true);
        /** Directory names repeat across every pack; file names rarely do. */
        private static final ConcurrentHashMap<String, String>
                DIRECTORY_NAMES = new ConcurrentHashMap<>();

        private String[] names = NO_NAMES;
        private Node[] nodes = NO_NODES;
        private int size;
        /** Slot + 1 per bucket (0 = empty); present only for large nodes. */
        private int[] table;
        private boolean file;

        private Node() {
        }

        private Node(boolean file) {
            this.file = file;
        }

        private static String directoryName(String name) {
            String existing = DIRECTORY_NAMES.putIfAbsent(name, name);
            return existing == null ? name : existing;
        }

        private void insert(String[] components, int index) {
            if (index == components.length) {
                this.file = true;
                return;
            }
            Node node = this;
            for (int position = index; position < components.length; position++) {
                boolean last = position == components.length - 1;
                String name = last ? components[position] : directoryName(components[position]);
                int slot = node.slot(name);
                Node child;
                if (slot < 0) {
                    child = last ? FILE : new Node();
                    node.add(name, child);
                } else {
                    child = node.nodes[slot];
                    if (child == FILE && !last) {
                        // Never mutate the shared leaf; split it into its own node.
                        child = new Node(true);
                        node.nodes[slot] = child;
                    } else if (last && child != FILE) {
                        child.file = true;
                    }
                }
                node = child;
            }
        }

        private int slot(String name) {
            int[] buckets = this.table;
            if (buckets != null) {
                int mask = buckets.length - 1;
                for (int bucket = spread(name.hashCode()) & mask; ; bucket = (bucket + 1) & mask) {
                    int entry = buckets[bucket];
                    if (entry == 0) {
                        return -1;
                    }
                    if (this.names[entry - 1].equals(name)) {
                        return entry - 1;
                    }
                }
            }
            for (int slot = 0; slot < this.size; slot++) {
                if (this.names[slot].equals(name)) {
                    return slot;
                }
            }
            return -1;
        }

        private void add(String name, Node child) {
            if (this.size == this.names.length) {
                int capacity = Math.max(4, this.size * 2);
                this.names = Arrays.copyOf(this.names, capacity);
                this.nodes = Arrays.copyOf(this.nodes, capacity);
            }
            this.names[this.size] = name;
            this.nodes[this.size] = child;
            this.size++;
            if (this.size <= LINEAR_LIMIT) {
                return;
            }
            if (this.table == null || this.size * 2 > this.table.length) {
                rebuildTable(this.size * 4);
            } else {
                place(this.table, this.size - 1);
            }
        }

        private void rebuildTable(int minimumBuckets) {
            int[] buckets = new int[Integer.highestOneBit(minimumBuckets - 1) << 1];
            for (int slot = 0; slot < this.size; slot++) {
                place(buckets, slot);
            }
            this.table = buckets;
        }

        private void place(int[] buckets, int slot) {
            int mask = buckets.length - 1;
            int bucket = spread(this.names[slot].hashCode()) & mask;
            while (buckets[bucket] != 0) {
                bucket = (bucket + 1) & mask;
            }
            buckets[bucket] = slot + 1;
        }

        private static int spread(int hash) {
            return hash ^ (hash >>> 16);
        }

        @Nullable
        private Node child(String name) {
            int slot = slot(name);
            return slot < 0 ? null : this.nodes[slot];
        }

        private boolean hasChildren() {
            return this.size > 0;
        }

        @Nullable
        private Node find(String[] components) {
            Node node = this;
            for (String component : components) {
                if (component.isEmpty()) {
                    continue;
                }
                node = node.child(component);
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
            for (int slot = 0; slot < this.size; slot++) {
                String childPath = path.isEmpty()
                        ? this.names[slot]
                        : path + "/" + this.names[slot];
                this.nodes[slot].collect(
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
            if (this == FILE) {
                return;
            }
            for (int slot = 0; slot < this.size; slot++) {
                this.nodes[slot].freeze();
            }
            if (this.size == 0) {
                this.names = NO_NAMES;
                this.nodes = NO_NODES;
            } else if (this.size < this.names.length) {
                this.names = Arrays.copyOf(this.names, this.size);
                this.nodes = Arrays.copyOf(this.nodes, this.size);
            }
            if (this.table != null && this.table.length > Integer.highestOneBit(this.size * 2 - 1) << 1) {
                // Trim growth slack; keep the load factor at or below one half.
                rebuildTable(this.size * 2);
            }
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
            for (int slot = 0; slot < root.size; slot++) {
                if (root.nodes[slot].hasChildren()) {
                    namespaces.add(root.names[slot]);
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

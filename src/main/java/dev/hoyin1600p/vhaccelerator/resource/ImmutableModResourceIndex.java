package dev.hoyin1600p.vhaccelerator.resource;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/**
 * Indexes immutable jar-backed mod resources once per namespace.
 *
 * <p>Mutable folders and generated/in-memory packs are deliberately rejected.
 * A failed or suspicious namespace scan is permanently delegated back to the
 * pack's original implementation.</p>
 */
public final class ImmutableModResourceIndex {
    private static final int MAX_PATHS_PER_NAMESPACE = 500_000;

    private final PathResolver resolver;
    private final Map<NamespaceKey, NamespaceIndex> namespaces =
            new HashMap<>();
    private final Set<NamespaceKey> rejected = new HashSet<>();

    private ImmutableModResourceIndex(PathResolver resolver) {
        this.resolver = resolver;
    }

    @Nullable
    public static ImmutableModResourceIndex create(
            Path source,
            PathResolver resolver
    ) {
        try {
            String scheme = source.getFileSystem()
                    .provider()
                    .getScheme();
            if (!"jar".equalsIgnoreCase(scheme)
                    && !"union".equalsIgnoreCase(scheme)) {
                return null;
            }
            return new ImmutableModResourceIndex(resolver);
        } catch (RuntimeException | LinkageError failure) {
            return null;
        }
    }

    @Nullable
    public synchronized Collection<ResourceLocation> resources(
            PackType type,
            String namespace,
            String prefix,
            int maxDepth,
            Predicate<String> filter
    ) {
        if (prefix == null
                || prefix.isEmpty()
                || maxDepth < 0) {
            return null;
        }
        NamespaceIndex index = index(type, namespace);
        if (index == null) {
            return null;
        }

        String normalizedPrefix = stripTrailingSlash(prefix);
        List<ResourceLocation> matches = new ArrayList<>();
        for (IndexedPath path : index.paths) {
            if (path.depth > maxDepth
                    || !within(path.path, normalizedPrefix)
                    || !filter.test(path.fileName)) {
                continue;
            }
            matches.add(path.location);
        }
        return matches;
    }

    /**
     * Returns null when a namespace has not already been indexed. A single
     * existence check must never pay the cost of walking a complete jar.
     */
    @Nullable
    public synchronized Boolean existingResource(String name) {
        ParsedPath parsed = parse(name);
        if (parsed == null) {
            return null;
        }
        NamespaceIndex index = namespaces.get(parsed.key);
        if (index == null) {
            return null;
        }
        return index.containedPaths.contains(parsed.relativePath);
    }

    @Nullable
    private NamespaceIndex index(
            PackType type,
            String namespace
    ) {
        NamespaceKey key = new NamespaceKey(type, namespace);
        NamespaceIndex existing = namespaces.get(key);
        if (existing != null) {
            return existing;
        }
        if (rejected.contains(key)) {
            return null;
        }

        long started = System.nanoTime();
        Path root;
        try {
            root = resolver.resolve(
                    type.getDirectory(),
                    namespace
            ).toAbsolutePath();
        } catch (RuntimeException | LinkageError failure) {
            rejected.add(key);
            return null;
        }
        if (!Files.isDirectory(root)) {
            NamespaceIndex empty = new NamespaceIndex(
                    List.of(),
                    Set.of()
            );
            namespaces.put(key, empty);
            return empty;
        }

        List<IndexedPath> paths = new ArrayList<>();
        Set<String> contained = new HashSet<>();
        try (Stream<Path> stream = Files.walk(root)) {
            java.util.Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path relative = root.relativize(
                        iterator.next().toAbsolutePath()
                );
                String encoded = encode(relative);
                if (encoded.isEmpty()) {
                    continue;
                }
                if (contained.size() >= MAX_PATHS_PER_NAMESPACE) {
                    throw new IOException(
                            "resource namespace exceeds safety limit"
                    );
                }
                contained.add(encoded);
                String fileName =
                        relative.getFileName().toString();
                if (fileName.endsWith(".mcmeta")) {
                    continue;
                }
                ResourceLocation location;
                try {
                    location = ResourceLocation.fromNamespaceAndPath(
                            namespace,
                            encoded
                    );
                } catch (ResourceLocationException invalid) {
                    throw new IOException(
                            "invalid resource path " + encoded,
                            invalid
                    );
                }
                paths.add(new IndexedPath(
                        encoded,
                        fileName,
                        relative.getNameCount(),
                        location
                ));
            }
        } catch (IOException | RuntimeException failure) {
            rejected.add(key);
            VHAccelerator.LOGGER.debug(
                    "Immutable resource indexing deferred {}:{} to "
                            + "Forge's original pack path",
                    type,
                    namespace,
                    failure
            );
            return null;
        }

        NamespaceIndex complete = new NamespaceIndex(
                List.copyOf(paths),
                Set.copyOf(contained)
        );
        namespaces.put(key, complete);
        VHAccelerator.LOGGER.debug(
                "Indexed {} immutable {} resources for namespace {} "
                        + "in {} ms",
                paths.size(),
                type,
                namespace,
                (System.nanoTime() - started) / 1_000_000L
        );
        return complete;
    }

    private static String encode(Path relative) {
        StringBuilder encoded = new StringBuilder();
        for (Path part : relative) {
            if (!encoded.isEmpty()) {
                encoded.append('/');
            }
            encoded.append(part);
        }
        return encoded.toString();
    }

    private static String stripTrailingSlash(String prefix) {
        int end = prefix.length();
        while (end > 0 && prefix.charAt(end - 1) == '/') {
            end--;
        }
        return prefix.substring(0, end);
    }

    private static boolean within(
            String path,
            String prefix
    ) {
        return path.equals(prefix)
                || path.startsWith(prefix + "/");
    }

    @Nullable
    private static ParsedPath parse(String name) {
        int first = name.indexOf('/');
        int second = first < 0
                ? -1
                : name.indexOf('/', first + 1);
        if (first <= 0
                || second <= first + 1
                || second >= name.length() - 1) {
            return null;
        }
        PackType type;
        String root = name.substring(0, first);
        if (PackType.CLIENT_RESOURCES.getDirectory().equals(root)) {
            type = PackType.CLIENT_RESOURCES;
        } else if (PackType.SERVER_DATA.getDirectory().equals(root)) {
            type = PackType.SERVER_DATA;
        } else {
            return null;
        }
        String namespace = name.substring(first + 1, second);
        return new ParsedPath(
                new NamespaceKey(type, namespace),
                name.substring(second + 1)
        );
    }

    @FunctionalInterface
    public interface PathResolver {
        Path resolve(String... paths);
    }

    private record NamespaceKey(
            PackType type,
            String namespace
    ) {
    }

    private record ParsedPath(
            NamespaceKey key,
            String relativePath
    ) {
    }

    private record IndexedPath(
            String path,
            String fileName,
            int depth,
            ResourceLocation location
    ) {
    }

    private record NamespaceIndex(
            List<IndexedPath> paths,
            Set<String> containedPaths
    ) {
    }
}

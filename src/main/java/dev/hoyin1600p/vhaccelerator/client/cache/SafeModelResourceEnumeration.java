package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.resource.DelegatingResourcePack;
import net.minecraftforge.resource.PathResourcePack;

/**
 * Recovers model-resource enumeration when one malformed loose-pack path
 * causes Forge's all-or-nothing path stream to throw. Invalid paths are not
 * valid Minecraft resources, so skipping only those entries preserves the
 * same resource view that Minecraft can actually address.
 */
final class SafeModelResourceEnumeration {
    private static final String MODEL_ROOT = "models";
    private static final String JSON_SUFFIX = ".json";
    private static final int DETAIL_LIMIT = 8;
    @Nullable
    private static final Field DELEGATES_FIELD = findDelegatesField();

    private SafeModelResourceEnumeration() {
    }

    @Nullable
    static Collection<ResourceLocation> recover(
            ResourceManager resourceManager
    ) {
        Set<ResourceLocation> recovered = new LinkedHashSet<>();
        Set<PackResources> visited = Collections.newSetFromMap(
                new IdentityHashMap<>()
        );
        AtomicInteger invalidPaths = new AtomicInteger();
        AtomicInteger failedPacks = new AtomicInteger();
        try (Stream<PackResources> packs = resourceManager.listPacks()) {
            packs.forEach(pack -> collect(
                    pack,
                    recovered,
                    visited,
                    invalidPaths,
                    failedPacks
            ));
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not recover model JSON resource enumeration; "
                            + "using normal model loading",
                    failure
            );
            return null;
        }

        if (failedPacks.get() > 0) {
            VHAccelerator.LOGGER.warn(
                    "Could not safely enumerate {} client resource pack(s); "
                            + "using normal model loading",
                    failedPacks.get()
            );
            return null;
        }

        List<ResourceLocation> sorted = new ArrayList<>(recovered);
        Collections.sort(sorted);
        VHAccelerator.LOGGER.info(
                "Recovered {} model JSON resources while skipping {} "
                        + "malformed loose-pack path(s)",
                sorted.size(),
                invalidPaths.get()
        );
        return sorted;
    }

    private static void collect(
            PackResources pack,
            Set<ResourceLocation> recovered,
            Set<PackResources> visited,
            AtomicInteger invalidPaths,
            AtomicInteger failedPacks
    ) {
        if (!visited.add(pack)) {
            return;
        }

        if (pack instanceof DelegatingResourcePack) {
            List<PackResources> delegates = delegates(pack);
            if (delegates == null) {
                failedPacks.incrementAndGet();
                return;
            }
            for (PackResources delegate : delegates) {
                collect(
                        delegate,
                        recovered,
                        visited,
                        invalidPaths,
                        failedPacks
                );
            }
            return;
        }

        Set<String> namespaces;
        try {
            namespaces = pack.getNamespaces(
                    PackType.CLIENT_RESOURCES
            );
        } catch (RuntimeException | LinkageError failure) {
            failedPacks.incrementAndGet();
            VHAccelerator.LOGGER.debug(
                    "Could not enumerate namespaces from client resource "
                            + "pack {}",
                    pack.getName(),
                    failure
            );
            return;
        }

        for (String namespace : namespaces) {
            if (pack instanceof PathResourcePack pathPack) {
                collectPathPack(
                        pathPack,
                        namespace,
                        recovered,
                        invalidPaths,
                        failedPacks
                );
                continue;
            }
            try {
                recovered.addAll(pack.getResources(
                        PackType.CLIENT_RESOURCES,
                        namespace,
                        MODEL_ROOT,
                        Integer.MAX_VALUE,
                        fileName -> fileName.endsWith(JSON_SUFFIX)
                ));
            } catch (RuntimeException | LinkageError failure) {
                failedPacks.incrementAndGet();
                VHAccelerator.LOGGER.debug(
                        "Could not enumerate model JSON resources from "
                                + "client resource pack {} namespace {}",
                        pack.getName(),
                        namespace,
                        failure
                );
            }
        }
    }

    private static void collectPathPack(
            PathResourcePack pack,
            String namespace,
            Set<ResourceLocation> recovered,
            AtomicInteger invalidPaths,
            AtomicInteger failedPacks
    ) {
        Path root = pack.getSource()
                .resolve(PackType.CLIENT_RESOURCES.getDirectory())
                .resolve(namespace)
                .toAbsolutePath();
        if (!Files.isDirectory(root)) {
            return;
        }

        Path modelRoot = root.getFileSystem().getPath(MODEL_ROOT);
        Predicate<Path> modelJson = relative ->
                relative.startsWith(modelRoot)
                        && relative.getFileName() != null
                        && relative.getFileName().toString()
                        .endsWith(JSON_SUFFIX)
                        && !relative.toString().endsWith(".mcmeta");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.map(root::relativize)
                    .filter(modelJson)
                    .forEach(relative -> {
                        String resourcePath = forwardSlashPath(relative);
                        ResourceLocation location =
                                ResourceLocation.tryParse(
                                        namespace + ":" + resourcePath
                                );
                        if (location != null) {
                            recovered.add(location);
                            return;
                        }
                        int skipped = invalidPaths.incrementAndGet();
                        if (skipped <= DETAIL_LIMIT) {
                            VHAccelerator.LOGGER.warn(
                                    "Skipping malformed client resource path "
                                            + "{}:{} from {}",
                                    namespace,
                                    resourcePath,
                                    pack.getName()
                            );
                        }
                    });
        } catch (IOException | RuntimeException failure) {
            failedPacks.incrementAndGet();
            VHAccelerator.LOGGER.debug(
                    "Could not safely enumerate model JSON resources from "
                            + "path pack {} namespace {}",
                    pack.getName(),
                    namespace,
                    failure
            );
        }
    }

    private static String forwardSlashPath(Path path) {
        StringBuilder result = new StringBuilder();
        for (Path segment : path) {
            if (!result.isEmpty()) {
                result.append('/');
            }
            result.append(segment);
        }
        return result.toString();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static List<PackResources> delegates(PackResources pack) {
        Field field = DELEGATES_FIELD;
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(pack);
            return value instanceof List<?>
                    ? (List<PackResources>) value
                    : null;
        } catch (IllegalAccessException | RuntimeException failure) {
            VHAccelerator.LOGGER.debug(
                    "Could not inspect delegated client resource packs",
                    failure
            );
            return null;
        }
    }

    @Nullable
    private static Field findDelegatesField() {
        try {
            Field field = DelegatingResourcePack.class
                    .getDeclaredField("delegates");
            return field.trySetAccessible() ? field : null;
        } catch (NoSuchFieldException | RuntimeException failure) {
            return null;
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/dynamiclanguages/DynamicLanguageMap.java
 * Upstream commit: d749205427d714a4865155f03c16a48f8e564117
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; adapted dynamic language values to Minecraft 1.18.2's
 * single-use Resource streams by retaining descriptors and reopening fresh,
 * fully closed resources through the active ResourceManager on cache misses.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.language;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DynamicLanguageStorage {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final ThreadLocal<LoadContext> LOAD_CONTEXT =
            new ThreadLocal<>();

    private DynamicLanguageStorage() {
    }

    public static void begin(ResourceManager resourceManager) {
        LOAD_CONTEXT.set(new LoadContext(resourceManager));
    }

    public static void record(ResourceLocation location) {
        LoadContext context = LOAD_CONTEXT.get();
        if (context != null) {
            context.languageLocations.add(location);
        }
    }

    public static BuildResult finish(Map<String, String> rawLanguageContents) {
        LoadContext context = LOAD_CONTEXT.get();
        LOAD_CONTEXT.remove();
        if (context == null || context.languageLocations.isEmpty()) {
            return new BuildResult(
                    rawLanguageContents,
                    0,
                    rawLanguageContents.size(),
                    0,
                    0L
            );
        }
        return createStorage(
                context.resourceManager,
                rawLanguageContents,
                context.languageLocations
        );
    }

    public static void abort() {
        LOAD_CONTEXT.remove();
    }

    static BuildResult createStorage(
            ResourceManager resourceManager,
            Map<String, String> rawLanguageContents,
            Iterable<ResourceLocation> languageLocations
    ) {
        Map<String, Object> storage = new HashMap<>(rawLanguageContents);
        Set<LanguageResourceDescriptor> usedSources = new LinkedHashSet<>();

        for (ResourceLocation location : languageLocations) {
            List<Resource> resources = openAll(resourceManager, location);
            try {
                for (int resourceIndex = 0;
                     resourceIndex < resources.size();
                     resourceIndex++) {
                    Resource resource = resources.get(resourceIndex);
                    LanguageResourceDescriptor descriptor =
                            new LanguageResourceDescriptor(
                                    location,
                                    resource.getSourceName(),
                                    resourceIndex
                            );
                    try {
                        Language.loadFromJson(
                                resource.getInputStream(),
                                (key, value) -> {
                                    if (value != null
                                            && value.equals(storage.get(key))) {
                                        storage.put(key, descriptor);
                                        usedSources.add(descriptor);
                                    }
                                }
                        );
                    } catch (Exception failure) {
                        LOGGER.debug(
                                "Could not inspect language resource {} from {}",
                                location,
                                resource.getSourceName(),
                                failure
                        );
                    }
                }
            } finally {
                closeAll(resources);
            }
        }

        Map<String, Object> immutableStorage = Map.copyOf(storage);
        LoadingCache<LanguageResourceDescriptor, Map<String, String>>
                languageFileContents = CacheBuilder.newBuilder()
                        .softValues()
                        .build(new CacheLoader<>() {
                            @Override
                            public Map<String, String> load(
                                    LanguageResourceDescriptor descriptor
                            ) {
                                return loadSource(resourceManager, descriptor);
                            }
                        });

        int dynamicEntries = 0;
        long dynamicCharacters = 0L;
        for (Map.Entry<String, Object> entry : immutableStorage.entrySet()) {
            if (entry.getValue() instanceof LanguageResourceDescriptor) {
                dynamicEntries++;
                String rawValue = rawLanguageContents.get(entry.getKey());
                if (rawValue != null) {
                    dynamicCharacters += rawValue.length();
                }
            }
        }

        Map<String, String> dynamicMap = Maps.asMap(
                immutableStorage.keySet(),
                key -> resolveValue(
                        key,
                        immutableStorage.get(key),
                        languageFileContents
                )
        );
        return new BuildResult(
                dynamicMap,
                dynamicEntries,
                immutableStorage.size() - dynamicEntries,
                usedSources.size(),
                dynamicCharacters
        );
    }

    private static String resolveValue(
            String key,
            Object value,
            LoadingCache<LanguageResourceDescriptor, Map<String, String>> cache
    ) {
        if (value instanceof LanguageResourceDescriptor descriptor) {
            return cache.getUnchecked(descriptor).getOrDefault(key, "");
        }
        return value instanceof String string ? string : "";
    }

    private static Map<String, String> loadSource(
            ResourceManager resourceManager,
            LanguageResourceDescriptor descriptor
    ) {
        List<Resource> resources = openAll(
                resourceManager,
                descriptor.location()
        );
        try {
            Resource resource = selectResource(resources, descriptor);
            if (resource != null) {
                Map<String, String> data = new Object2ObjectOpenHashMap<>();
                try {
                    Language.loadFromJson(resource.getInputStream(), data::put);
                } catch (Exception failure) {
                    LOGGER.error(
                            "Error reopening language data from {} in {}",
                            descriptor.location(),
                            descriptor.sourceName(),
                            failure
                    );
                }
                return data;
            }
        } finally {
            closeAll(resources);
        }
        LOGGER.warn(
                "Language source {} no longer provides {}",
                descriptor.sourceName(),
                descriptor.location()
        );
        return Map.of();
    }

    private static Resource selectResource(
            List<Resource> resources,
            LanguageResourceDescriptor descriptor
    ) {
        int resourceIndex = descriptor.resourceIndex();
        if (resourceIndex >= 0 && resourceIndex < resources.size()) {
            Resource indexed = resources.get(resourceIndex);
            if (descriptor.sourceName().equals(indexed.getSourceName())) {
                return indexed;
            }
        }
        for (Resource resource : resources) {
            if (descriptor.sourceName().equals(resource.getSourceName())) {
                return resource;
            }
        }
        return null;
    }

    private static List<Resource> openAll(
            ResourceManager resourceManager,
            ResourceLocation location
    ) {
        try {
            return new ArrayList<>(resourceManager.getResources(location));
        } catch (IOException failure) {
            LOGGER.debug(
                    "Could not reopen language resource {}",
                    location,
                    failure
            );
            return List.of();
        }
    }

    private static void closeAll(List<Resource> resources) {
        for (Resource resource : resources) {
            try {
                resource.close();
            } catch (IOException failure) {
                LOGGER.debug(
                        "Could not close language resource {} from {}",
                        resource.getLocation(),
                        resource.getSourceName(),
                        failure
                );
            }
        }
    }

    public static void log(BuildResult result) {
        LOGGER.info(
                "Prepared dynamic language storage: {} releasable entries "
                        + "across {} source file(s), {} literal entries "
                        + "retained, {} translation characters releasable",
                result.dynamicEntries(),
                result.sourceCount(),
                result.literalEntries(),
                result.dynamicCharacters()
        );
    }

    public record BuildResult(
            Map<String, String> storage,
            int dynamicEntries,
            int literalEntries,
            int sourceCount,
            long dynamicCharacters
    ) {
    }

    record LanguageResourceDescriptor(
            ResourceLocation location,
            String sourceName,
            int resourceIndex
    ) {
    }

    private static final class LoadContext {
        private final ResourceManager resourceManager;
        private final Set<ResourceLocation> languageLocations =
                new LinkedHashSet<>();

        private LoadContext(ResourceManager resourceManager) {
            this.resourceManager = resourceManager;
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_loot_loading/LootDataManagerMixin.java
 * Upstream commit: 0a68e874e98a1476bc36cedfbf2f1e3ee64bcbcb
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; adapted resource-origin capture to Minecraft 1.18.2,
 * retained exact Forge source names, avoided JSON markers, and added a bounded
 * reload-local metadata resource plus focused test seams.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.loot;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;

public final class LootResourceOriginCache {
    private final Map<ResourceLocation, String> sourceNames =
            new ConcurrentHashMap<>();

    public void record(Resource resource) {
        sourceNames.put(resource.getLocation(), resource.getSourceName());
    }

    @Nullable
    public Resource replay(ResourceLocation location) {
        String sourceName = sourceNames.get(location);
        return sourceName == null
                ? null
                : new OriginOnlyResource(location, sourceName);
    }

    public void clear() {
        sourceNames.clear();
    }

    int sizeForTesting() {
        return sourceNames.size();
    }

    private record OriginOnlyResource(
            ResourceLocation location,
            String sourceName
    ) implements Resource {
        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T> T getMetadata(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }

        @Override
        public void close() {
        }
    }
}

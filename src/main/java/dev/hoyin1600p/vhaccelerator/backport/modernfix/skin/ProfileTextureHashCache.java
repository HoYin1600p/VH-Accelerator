/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/cache_profile_texture_url/SkinManagerMixin.java
 * Upstream commit: e859ce8eb6b7b05c79179becf67df32e3efc4ad5
 * Original copyright: Copyright (c) 2023 embeddedt, Fury_Phoenix, and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; extracted a bounded URL-keyed cache with observable
 * hit/miss statistics and null-safe vanilla fallback behavior.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.skin;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class ProfileTextureHashCache {
    static final long DEFAULT_MAXIMUM_SIZE = 2_048L;
    static final long DEFAULT_EXPIRY_SECONDS = 60L;

    private final Cache<String, String> hashes;

    public ProfileTextureHashCache() {
        this(DEFAULT_MAXIMUM_SIZE, DEFAULT_EXPIRY_SECONDS);
    }

    ProfileTextureHashCache(long maximumSize, long expirySeconds) {
        hashes = CacheBuilder.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(expirySeconds, TimeUnit.SECONDS)
                .concurrencyLevel(1)
                .recordStats()
                .build();
    }

    public String resolve(MinecraftProfileTexture texture) {
        Objects.requireNonNull(texture, "texture");
        String url = texture.getUrl();
        if (url == null) {
            return texture.getHash();
        }

        String cached = hashes.getIfPresent(url);
        if (cached != null) {
            return cached;
        }

        String computed = texture.getHash();
        if (computed != null) {
            hashes.put(url, computed);
        }
        return computed;
    }

    CacheStats stats() {
        return hashes.stats();
    }

    long estimatedSize() {
        hashes.cleanUp();
        return hashes.size();
    }
}

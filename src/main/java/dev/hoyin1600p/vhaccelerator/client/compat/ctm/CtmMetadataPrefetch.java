package dev.hoyin1600p.vhaccelerator.client.compat.ctm;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * CTM 1.1.5+5 reads the CTM metadata of every sprite of every atlas one at a
 * time in its TextureStitchEvent.Pre listener (1.3 s of the Wolds launch for
 * the blocks atlas). Its {@code ResourceUtil} caches each result in a plain
 * map, and the listener only asks that cache. The same reads now run on
 * VHA's workers first and fill the cache on the listener's thread; CTM's own
 * loop then finds every entry. Missing files are cached as absent exactly as
 * CTM does; anything else that fails (bad JSON, I/O errors) is left out so
 * CTM repeats it and reports it itself.
 */
public final class CtmMetadataPrefetch {
    private static final int MIN_SPRITES = 64;
    private static volatile Access access;

    private CtmMetadataPrefetch() {
    }

    public static void prefetch(TextureStitchEvent.Pre event) {
        Access fields = access();
        if (fields == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Collection<ResourceLocation> sprites = (Collection<ResourceLocation>)
                    ObfuscationReflectionHelper.getPrivateValue(TextureStitchEvent.Pre.class, event, "sprites");
            @SuppressWarnings("unchecked")
            Map<ResourceLocation, Object> cache = (Map<ResourceLocation, Object>) fields.cache.get(null);
            MetadataSectionSerializer<?> serializer = (MetadataSectionSerializer<?>) fields.serializer.get(null);
            if (sprites == null || cache == null || serializer == null) {
                return;
            }
            Set<ResourceLocation> wanted = new LinkedHashSet<>();
            for (ResourceLocation sprite : sprites) {
                ResourceLocation location = new ResourceLocation(
                        sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
                if (!cache.containsKey(location)) {
                    wanted.add(location);
                }
            }
            if (wanted.size() < MIN_SPRITES) {
                return;
            }
            ResourceManager resources = Minecraft.getInstance().getResourceManager();
            Map<ResourceLocation, Optional<Object>> found = new ConcurrentHashMap<>();
            List<ResourceLocation> work = new ArrayList<>(wanted);
            SharedWorkers.forEach(work, location -> {
                try (Resource resource = resources.getResource(location)) {
                    found.put(location, Optional.ofNullable(resource.getMetadata(serializer)));
                } catch (FileNotFoundException missing) {
                    found.put(location, Optional.empty());
                } catch (IOException | RuntimeException failure) {
                    // CTM reads it again and handles or reports it as before.
                }
            });
            for (ResourceLocation location : work) {
                Optional<Object> metadata = found.get(location);
                if (metadata != null && !cache.containsKey(location)) {
                    cache.put(location, metadata.orElse(null));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("CTM metadata prefetch skipped; CTM reads its metadata itself", failure);
        }
    }

    private static Access access() {
        Access current = access;
        if (current == null) {
            synchronized (CtmMetadataPrefetch.class) {
                current = access;
                if (current == null) {
                    current = Access.create();
                    access = current;
                }
            }
        }
        return current.cache == null ? null : current;
    }

    private record Access(Field cache, Field serializer) {
        static Access create() {
            try {
                Class<?> util = Class.forName("team.chisel.ctm.client.util.ResourceUtil",
                        false, CtmMetadataPrefetch.class.getClassLoader());
                Field cache = util.getDeclaredField("metadataCache");
                Field serializer = util.getDeclaredField("SERIALIZER");
                cache.setAccessible(true);
                serializer.setAccessible(true);
                return new Access(cache, serializer);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return new Access(null, null);
            }
        }
    }
}

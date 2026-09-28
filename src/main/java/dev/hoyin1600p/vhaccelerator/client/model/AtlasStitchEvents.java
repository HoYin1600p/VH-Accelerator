package dev.hoyin1600p.vhaccelerator.client.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.TextureAtlas;

/**
 * Atlases whose {@code TextureStitchEvent.Pre} was already fired, in vanilla
 * order on the loading thread, before their preparation runs on a worker.
 * Mods' stitch listeners are written for one thread; firing every event
 * sequentially first and then preparing the atlases in parallel keeps them
 * on one thread, one atlas at a time, in the original order. Preparation
 * then starts from the texture set the event produced and skips firing it
 * again.
 */
public final class AtlasStitchEvents {
    private static final Map<TextureAtlas, Boolean> PREFIRED = new ConcurrentHashMap<>();

    private AtlasStitchEvents() {
    }

    public static void markPrefired(TextureAtlas atlas) {
        PREFIRED.put(atlas, Boolean.TRUE);
    }

    /** True once for an atlas whose event was already fired. */
    public static boolean consume(TextureAtlas atlas) {
        return PREFIRED.remove(atlas) != null;
    }
}

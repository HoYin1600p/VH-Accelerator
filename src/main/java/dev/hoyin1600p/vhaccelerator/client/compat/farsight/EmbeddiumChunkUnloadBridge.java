package dev.hoyin1600p.vhaccelerator.client.compat.farsight;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraftforge.fml.ModList;

/**
 * Forwards VHA-initiated chunk forgets to Embeddium's world renderer.
 *
 * <p>Embeddium 0.3 (and Rubidium, which shares the Sodium 0.4 package layout)
 * learns about chunk unloads from an injection at RETURN of
 * {@code ClientPacketListener.handleForgetLevelChunk} that calls
 * {@code SodiumWorldRenderer.instance().onChunkRemoved(x, z)}. That call
 * removes the chunk from Sodium's {@code ChunkTracker} and asks the
 * {@code RenderSectionManager} to delete its render sections. Because
 * Farsight cancels the handler at HEAD, the injection never runs, so
 * {@link FarsightChunkBound} issues the same call through this bridge.
 *
 * <p>The renderer class is not on VHA's compile classpath, so it is resolved
 * once through method handles. Any failure disables the bridge for the rest
 * of the session and leaves vanilla forget behaviour intact.
 */
final class EmbeddiumChunkUnloadBridge {
    private static final String RENDERER_CLASS =
            "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer";

    private static final MethodHandle INSTANCE_NULLABLE;
    private static final MethodHandle ON_CHUNK_REMOVED;
    private static volatile boolean available;

    static {
        MethodHandle instanceHandle = null;
        MethodHandle removedHandle = null;
        if (sodiumRendererInstalled()) {
            try {
                Class<?> rendererClass = Class.forName(
                        RENDERER_CLASS,
                        false,
                        EmbeddiumChunkUnloadBridge.class.getClassLoader()
                );
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                instanceHandle = lookup.findStatic(
                        rendererClass,
                        "instanceNullable",
                        MethodType.methodType(rendererClass)
                );
                removedHandle = lookup.findVirtual(
                        rendererClass,
                        "onChunkRemoved",
                        MethodType.methodType(void.class, int.class, int.class)
                );
            } catch (ReflectiveOperationException
                     | RuntimeException
                     | LinkageError failure) {
                VHAccelerator.LOGGER.warn(
                        "Could not bind Embeddium's chunk-unload hook; "
                                + "Farsight-bounded chunks will not release "
                                + "their render sections",
                        failure
                );
                instanceHandle = null;
                removedHandle = null;
            }
        }
        INSTANCE_NULLABLE = instanceHandle;
        ON_CHUNK_REMOVED = removedHandle;
        available = instanceHandle != null && removedHandle != null;
        if (available) {
            VHAccelerator.LOGGER.info(
                    "Bound Embeddium's chunk-unload hook for Farsight-bounded chunks"
            );
        }
    }

    private EmbeddiumChunkUnloadBridge() {
    }

    static boolean available() {
        return available;
    }

    static void onChunkRemoved(int chunkX, int chunkZ) {
        if (!available) {
            return;
        }
        try {
            Object renderer = INSTANCE_NULLABLE.invoke();
            if (renderer != null) {
                ON_CHUNK_REMOVED.invoke(renderer, chunkX, chunkZ);
            }
        } catch (Throwable failure) {
            available = false;
            VHAccelerator.LOGGER.warn(
                    "Embeddium rejected a Farsight-bounded chunk unload; "
                            + "disabling the render-section notification",
                    failure
            );
        }
    }

    private static boolean sodiumRendererInstalled() {
        try {
            ModList modList = ModList.get();
            return modList != null
                    && (modList.isLoaded("embeddium")
                    || modList.isLoaded("rubidium"));
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.debug(
                    "Could not query the mod list for Embeddium",
                    failure
            );
            return false;
        }
    }
}

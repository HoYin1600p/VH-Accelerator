package dev.hoyin1600p.vhaccelerator.compat.worldzones;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;

/** Builds a complete replacement before publishing it to the just-loaded private owner. */
public final class WorldZonesCompaction {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final ClassValue<Field> FIELD = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            if (!type.getName().equals(WorldZonesLayouts.TARGET.replace('/', '.'))) throw new IllegalArgumentException("Unexpected WorldZones owner");
            try {
                Field field = type.getDeclaredField("chunkToZones");
                if (!Modifier.isPrivate(field.getModifiers()) || Modifier.isStatic(field.getModifiers())
                        || Modifier.isFinal(field.getModifiers()) || field.getType() != Long2ObjectMap.class
                        || !field.trySetAccessible()) throw new IllegalStateException("WorldZones field layout changed");
                return field;
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
    };
    private WorldZonesCompaction() { }

    /** Optional RETURN handler: a rejected or failed conversion leaves the loaded owner intact. */
    public static void compact(Object owner) {
        try {
            if (!CompatMixinGroups.activationAllowed(WorldZonesLayouts.MIXIN)
                    || !VHAcceleratorConfig.commonOptimizationsEnabled()
                    || !VHAcceleratorConfig.COMMON.compactWorldZonesMemberships.get()) return;
            long start = System.nanoTime();
            CompactChunkMembershipMap result = compactOwned(owner);
            if (result != null) {
                CompatMixinGroups.recordOptimizationUse(WorldZonesLayouts.MIXIN);
                LogManager.getLogger("VH Accelerator").debug("WorldZones memberships: {} inline, {} original in {} us",
                        result.inlineCount(), result.originalCount(), (System.nanoTime() - start) / 1000);
            }
        } catch (RuntimeException | ReflectiveOperationException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) LogManager.getLogger("VH Accelerator").warn(
                    "WorldZones membership compaction skipped; original loaded lookup retained", failure);
        }
    }

    /** Shared by the offline original-class oracle, after the runtime selection gate has been tested. */
    static CompactChunkMembershipMap compactOwned(Object owner) throws ReflectiveOperationException {
        if (owner == null) return null;
        // A later foreign mixin can run after our postApply callback: reject its final runtime markers too.
        for (var method : owner.getClass().getDeclaredMethods()) for (var annotation : method.getDeclaredAnnotations()) {
            if (annotation.annotationType().getName().equals("org.spongepowered.asm.mixin.transformer.meta.MixinMerged")) {
                Object source = annotation.annotationType().getMethod("mixin").invoke(annotation);
                if (!WorldZonesLayouts.MIXIN.equals(source)) return null;
            }
        }
        Field field = FIELD.get(owner.getClass());
        @SuppressWarnings("unchecked") Long2ObjectMap<IntSet> source = (Long2ObjectMap<IntSet>) field.get(owner);
        CompactChunkMembershipMap result = CompactChunkMembershipMap.copyOf(source);
        if (result != null && field.get(owner) == source) field.set(owner, result);
        else return null;
        return result;
    }
}

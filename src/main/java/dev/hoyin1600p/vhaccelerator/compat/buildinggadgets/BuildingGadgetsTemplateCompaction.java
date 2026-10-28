package dev.hoyin1600p.vhaccelerator.compat.buildinggadgets;

import com.google.common.collect.ImmutableMap;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;

/** Policy and fresh-owner boundary; never obtains templates through their mutating getter. */
public final class BuildingGadgetsTemplateCompaction {
    public static final String MIXIN = "dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets.TemplateLookupMixin";
    private static final String TEMPLATE_CLASS = BuildingGadgetsTemplateLayouts.TEMPLATE.replace('/', '.');
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final ClassValue<Field> MAP_FIELDS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            if (!type.getName().equals(BuildingGadgetsTemplateLayouts.TEMPLATE.replace('/', '.'))) {
                throw new IllegalArgumentException("Unexpected template owner");
            }
            try {
                Field field = type.getDeclaredField("map");
                int modifiers = field.getModifiers();
                if (field.getType() != ImmutableMap.class || !Modifier.isPrivate(modifiers)
                        || !Modifier.isFinal(modifiers) || !field.trySetAccessible()) {
                    throw new IllegalStateException("Template map field is not accessible with its reviewed layout");
                }
                return field;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Template map field is unavailable", failure);
            }
        }
    };

    private BuildingGadgetsTemplateCompaction() { }

    /**
     * Result of {@code normalize()}. When BG's own {@code serialize} calls it, the copy is discarded right after
     * writing, so compacting it would only burn time and memory on the saving thread.
     */
    public static void compactNormalizedResult(Object template) {
        // Every save normalizes every template (59 thousand on a large server); the stack walk below is far too
        // dear for the empty and tiny ones, which are never compacted anyway.
        if (template == null || tooSmallToCompact(template)) return;
        compactNormalizedResult(template, TEMPLATE_CLASS, "serialize", BuildingGadgetsTemplateCompaction::compactFreshResult);
    }

    private static boolean tooSmallToCompact(Object template) {
        try {
            return ((ImmutableMap<?, ?>) MAP_FIELDS.get(template.getClass()).get(template)).size() < PackedTemplatePositions.MINIMUM_SIZE;
        } catch (RuntimeException | ReflectiveOperationException | LinkageError unknown) {
            return false;
        }
    }

    static void compactNormalizedResult(Object template, String ownerClass, String temporaryCaller,
                                        java.util.function.Consumer<Object> compactor) {
        if (template == null || calledFrom(ownerClass, temporaryCaller)) return;
        compactor.accept(template);
    }

    /** True when the frame that called the nearest enclosing {@code normalize} is {@code owner.method}. */
    static boolean calledFrom(String owner, String method) {
        return StackWalker.getInstance().walk(frames -> frames
                .dropWhile(frame -> !frame.getMethodName().equals("normalize"))
                .skip(1).findFirst()
                .map(frame -> frame.getClassName().equals(owner) && frame.getMethodName().equals(method))
                .orElse(false));
    }

    public static void compactFreshResult(Object template) {
        if (template == null || !CompatMixinGroups.activationAllowed(MIXIN)
                || !VHAcceleratorConfig.buildingGadgetsTemplateCompactionEnabled()) return;
        try {
            if (!BuildingGadgetsTemplateLayouts.runtimeDependenciesSafe()) {
                warnOnce("the loaded coordinate classes do not have the reviewed lookup semantics");
                return;
            }
            Field field = MAP_FIELDS.get(template.getClass());
            @SuppressWarnings("unchecked")
            ImmutableMap<BlockPos, ?> map = (ImmutableMap<BlockPos, ?>) field.get(template);
            if (map == null || map.isEmpty() || PackedTemplatePositions.isMarker(map)) return;
            Runnable used = () -> CompatMixinGroups.recordOptimizationUse(MIXIN);
            // Packed arrays first: they replace the entries, keys and lookup table. Maps that cannot be packed
            // (and the rare packing failure) fall back to the lookup-only compaction.
            if (map.size() >= PackedTemplatePositions.MINIMUM_SIZE) PackedTemplatePositions.installDeferred(template, field, map, used);
            else CompactTemplateLookup.installDeferred(map, used);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError failure) {
            warnOnce(failure.toString());
        }
    }

    private static void warnOnce(String reason) {
        if (WARNED.compareAndSet(false, true)) {
            LogManager.getLogger("VH Accelerator").warn(
                    "Building Gadgets template lookup compaction is unavailable; retaining its original lookup: {}", reason);
        }
    }
}

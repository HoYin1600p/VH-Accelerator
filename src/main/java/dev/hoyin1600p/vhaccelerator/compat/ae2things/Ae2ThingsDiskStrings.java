package dev.hoyin1600p.vhaccelerator.compat.ae2things;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.apache.logging.log4j.LogManager;

/**
 * Shares equal {@code StringTag} values across the item keys AE2Things keeps for every DISK cell.
 *
 * <p>The manager holds each disk's complete key NBT as live tag objects; on a large server that is
 * about 1.5 M {@code StringTag}s (and their strings) for a few thousand distinct values. A
 * {@code StringTag} is immutable and {@code copy()} returns itself, so replacing equal instances
 * with one is invisible to every reader and to the saved bytes. This runs once on the freshly built
 * manager, before it is published, with a throwaway lookup that dies with the walk. Only values that
 * already exist are re-put, so compound key order, list order and every other tag stay as they were.
 * A failed walk leaves a valid tree, because each replacement is already value-equal.
 */
public final class Ae2ThingsDiskStrings {
    public static final String MIXIN = "dev.hoyin1600p.vhaccelerator.mixin.compat.ae2things.StorageManagerLoadMixin";
    /** Vanilla's NBT reader refuses deeper trees, so a deeper one here is not real data. */
    private static final int MAX_DEPTH = 512;
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static final ClassValue<Field> DISKS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            return accessible(type, "disks", Map.class, true);
        }
    };
    private static final ClassValue<Field> STACK_KEYS = new ClassValue<>() {
        @Override protected Field computeValue(Class<?> type) {
            return accessible(type, "stackKeys", ListTag.class, false);
        }
    };

    private Ae2ThingsDiskStrings() { }

    /** What a finished walk did: disks visited, string values seen, instances replaced, distinct strings. */
    public record Result(int disks, long stringSlots, long replaced, int distinct) { }

    /** Mixin entry: never throws, and leaves the manager as AE2Things built it on any problem. */
    public static void shareLoaded(Object manager) {
        if (manager == null || !CompatMixinGroups.activationAllowed(MIXIN)) return;
        long started = System.nanoTime();
        try {
            Result result = share(manager);
            CompatMixinGroups.recordOptimizationUse(MIXIN);
            LogManager.getLogger("VH Accelerator").info(
                    "AE2Things disks: shared {} of {} string values across {} disks ({} distinct) in {} ms",
                    result.replaced(), result.stringSlots(), result.disks(), result.distinct(),
                    (System.nanoTime() - started) / 1_000_000L);
        } catch (RuntimeException | ReflectiveOperationException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) {
                LogManager.getLogger("VH Accelerator").warn(
                        "AE2Things disk string sharing stopped early; the part already shared is value-equal"
                                + " and the disk data is otherwise unchanged", failure);
            }
        }
    }

    /** Walks every disk's key list. Throws on any problem; what was replaced so far stays valid. */
    static Result share(Object manager) throws ReflectiveOperationException {
        Object disks = DISKS.get(manager.getClass()).get(manager);
        if (!(disks instanceof Map<?, ?> map)) throw new IllegalStateException("disk map is not a Map");
        Sharer sharer = new Sharer();
        int count = 0;
        for (Object storage : map.values().toArray()) {
            if (storage == null) continue;
            Object keys = STACK_KEYS.get(storage.getClass()).get(storage);
            if (keys instanceof ListTag list) sharer.list(list, 0);
            count++;
        }
        return new Result(count, sharer.slots, sharer.replaced, sharer.canonical.size());
    }

    private static Field accessible(Class<?> type, String name, Class<?> fieldType, boolean privateFinal) {
        try {
            Field field = type.getDeclaredField(name);
            int modifiers = field.getModifiers();
            if (field.getType() != fieldType || Modifier.isStatic(modifiers)
                    || (privateFinal && !(Modifier.isPrivate(modifiers) && Modifier.isFinal(modifiers)))
                    || (!privateFinal && !Modifier.isPublic(modifiers)) || !field.trySetAccessible()) {
                throw new IllegalStateException(type.getName() + "." + name + " does not have the reviewed layout");
            }
            return field;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(type.getName() + "." + name + " is unavailable", failure);
        }
    }

    private static final class Sharer {
        final Map<String, StringTag> canonical = new HashMap<>();
        long slots;
        long replaced;

        StringTag share(StringTag tag) {
            slots++;
            StringTag first = canonical.putIfAbsent(tag.getAsString(), tag);
            if (first == null || first == tag) return tag;
            replaced++;
            return first;
        }

        void list(ListTag list, int depth) {
            if (depth > MAX_DEPTH) throw new IllegalStateException("NBT nesting is deeper than vanilla allows");
            for (int i = 0, size = list.size(); i < size; i++) {
                Tag element = list.get(i);
                if (element instanceof StringTag string) {
                    StringTag shared = share(string);
                    if (shared != string) list.set(i, shared);
                } else if (element instanceof CompoundTag compound) {
                    compound(compound, depth + 1);
                } else if (element instanceof ListTag nested) {
                    list(nested, depth + 1);
                }
            }
        }

        void compound(CompoundTag compound, int depth) {
            if (depth > MAX_DEPTH) throw new IllegalStateException("NBT nesting is deeper than vanilla allows");
            for (String key : compound.getAllKeys().toArray(new String[0])) {
                Tag value = compound.get(key);
                if (value instanceof StringTag string) {
                    StringTag shared = share(string);
                    if (shared != string) compound.put(key, shared);
                } else if (value instanceof CompoundTag nested) {
                    compound(nested, depth + 1);
                } else if (value instanceof ListTag nested) {
                    list(nested, depth + 1);
                }
            }
        }
    }
}

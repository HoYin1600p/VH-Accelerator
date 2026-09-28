package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import iskallia.vault.core.vault.Modifiers;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.modifier.spi.ModifierContext;
import iskallia.vault.core.vault.modifier.spi.VaultModifier;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Vault Hunters' cascade modifiers (the cake vault's chest, coin and ore
 * cascades) register one generation listener per stack, and every listener
 * asks {@code DecoratorCascadeModifier.getCascadeRun}, which scans every
 * modifier entry of the vault to find the stack count and whether its stack
 * is the first ("primary") one. A cake vault adds about 15 entries per cake,
 * so each generated chunk cost stacks x entries lookups: after 420 cakes the
 * server thread spent 92% of its time there and each cake took 14 s instead
 * of 2.7 s. The same answer is now read from an index built once per change
 * of the entry list: per modifier, the first entry's context UUID, the stack
 * count and the set of context UUIDs, in the same order the original loop
 * walks. Newer Vault builds (Asgard 3.21.62) group modifiers themselves and
 * keep their own code; this is applied only where the loop still exists.
 */
public final class VaultCascadeIndex {
    private static final Map<Modifiers, Index> INDEXES = new WeakHashMap<>();
    private static volatile Factory factory;

    private VaultCascadeIndex() {
    }

    /** The CascadeRun the original method would return, or null to run it. */
    public static Object cascadeRun(VaultModifier<?> modifier, Vault vault, ModifierContext context) {
        Factory runs = factory();
        if (runs == null || !vault.has(Vault.MODIFIERS)) {
            return null;
        }
        Modifiers modifiers = vault.get(Vault.MODIFIERS);
        Group group = index(modifiers).groups.get(modifier);
        UUID id = context.getUUID();
        if (group == null || !group.uuids.contains(id)) {
            return runs.single();
        }
        return runs.of(group.first.equals(id), group.count);
    }

    private static Index index(Modifiers modifiers) {
        List<Modifiers.Entry> entries = modifiers.getEntries();
        int size = entries.size();
        Object first = size == 0 ? null : entries.get(0);
        Object last = size == 0 ? null : entries.get(size - 1);
        synchronized (INDEXES) {
            Index index = INDEXES.get(modifiers);
            if (index != null && index.entries == entries && index.size == size
                    && index.firstEntry == first && index.lastEntry == last) {
                return index;
            }
            index = build(entries, size, first, last);
            INDEXES.put(modifiers, index);
            return index;
        }
    }

    private static Index build(List<Modifiers.Entry> entries, int size, Object first, Object last) {
        Map<VaultModifier<?>, Group> groups = new IdentityHashMap<>();
        for (Modifiers.Entry entry : entries) {
            VaultModifier<?> modifier = entry.getModifier().orElse(null);
            if (modifier == null) {
                continue;
            }
            UUID id = entry.getContext().getUUID();
            Group group = groups.get(modifier);
            if (group == null) {
                group = new Group(id);
                groups.put(modifier, group);
            }
            group.count++;
            group.uuids.add(id);
        }
        return new Index(entries, size, first, last, groups);
    }

    private static Factory factory() {
        Factory current = factory;
        if (current == null) {
            synchronized (VaultCascadeIndex.class) {
                current = factory;
                if (current == null) {
                    current = Factory.create();
                    factory = current;
                }
            }
        }
        return current.available ? current : null;
    }

    private static final class Group {
        final UUID first;
        final Set<UUID> uuids = new HashSet<>();
        int count;

        Group(UUID first) {
            this.first = first;
        }
    }

    private record Index(List<?> entries, int size, Object firstEntry, Object lastEntry,
                         Map<VaultModifier<?>, Group> groups) {
    }

    /** Builds Vault's private CascadeRun record; unavailable leaves the original method in charge. */
    private record Factory(boolean available, Constructor<?> constructor, Method singleMethod) {
        static Factory create() {
            try {
                Class<?> type = Class.forName(
                        "iskallia.vault.core.vault.modifier.modifier.DecoratorCascadeModifier$CascadeRun",
                        false, VaultCascadeIndex.class.getClassLoader());
                Constructor<?> constructor = type.getDeclaredConstructor(boolean.class, int.class);
                constructor.setAccessible(true);
                Method single = type.getDeclaredMethod("single");
                single.setAccessible(true);
                return new Factory(true, constructor, single);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return new Factory(false, null, null);
            }
        }

        Object of(boolean primary, int stackCount) {
            try {
                return constructor.newInstance(primary, stackCount);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }

        Object single() {
            try {
                return singleMethod.invoke(null);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        }
    }
}

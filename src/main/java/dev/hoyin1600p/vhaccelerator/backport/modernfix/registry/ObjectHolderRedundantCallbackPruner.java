/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/forge/registry/ObjectHolderClearer.java
 * Upstream commit: f23348c6cbf68bb44f4fdd95f900f7106278cb11
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; restricted removal to exact Forge ObjectHolderRef
 * callbacks whose key has no registered override candidates, verified the
 * current field/value identity, retained dummied and unresolved entries, and
 * added fail-closed reflection plus per-handler statistics for Forge 40.
 * Modified: 2026-09-09; short-circuit retained holders and share override-owner
 * snapshots only within the synchronous load-complete cleanup pass.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;

/**
 * Removes Forge {@code ObjectHolderRef} callbacks that can never select a
 * different registered object during a registry snapshot remap.
 *
 * <p>Forge 40 reapplies object-holder callbacks after registry injection and
 * restoration. A callback therefore remains installed whenever its registry
 * key has override candidates, is unresolved or dummied, or its target field
 * does not already reference the registry's current value. Only exact Forge
 * callbacks for single-owner keys are removed; arbitrary mod handlers are
 * never inspected or changed.</p>
 */
public final class ObjectHolderRedundantCallbackPruner {
    private static final String HOLDER_CLASS_NAME =
            "net.minecraftforge.registries.ObjectHolderRef";

    private ObjectHolderRedundantCallbackPruner() {
    }

    public static Statistics pruneForgeHolders(boolean streamlined) {
        try {
            ClassLoader loader =
                    ObjectHolderRedundantCallbackPruner.class.getClassLoader();
            Class<?> registryClass = Class.forName(
                    "net.minecraftforge.registries.ObjectHolderRegistry",
                    false,
                    loader
            );
            Class<?> holderClass = Class.forName(
                    HOLDER_CLASS_NAME,
                    false,
                    loader
            );
            Field holdersField = accessibleField(
                    registryClass,
                    "objectHolders"
            );
            Field holderRegistryField = accessibleField(
                    holderClass,
                    "registry"
            );
            Field holderKeyField = accessibleField(
                    holderClass,
                    "injectedObject"
            );
            Field holderTargetField = accessibleField(
                    holderClass,
                    "field"
            );
            Field holderValidField = accessibleField(
                    holderClass,
                    "isValid"
            );
            Method overrideOwners = ForgeRegistry.class.getDeclaredMethod(
                    "getOverrideOwners"
            );
            Method isDummied = ForgeRegistry.class.getDeclaredMethod(
                    "isDummied",
                    ResourceLocation.class
            );
            if (!overrideOwners.trySetAccessible()
                    || !isDummied.trySetAccessible()) {
                return Statistics.unavailable();
            }

            synchronized (registryClass) {
                Object value = holdersField.get(null);
                if (!(value instanceof Set<?> holders)) {
                    return Statistics.unavailable();
                }
                return pruneHandlers(
                        holders,
                        holderClass,
                        holderRegistryField,
                        holderKeyField,
                        holderTargetField,
                        holderValidField,
                        overrideOwners,
                        isDummied,
                        streamlined
                );
            }
        } catch (LinkageError | ReflectiveOperationException exception) {
            return Statistics.unavailable();
        }
    }

    static Statistics pruneHandlers(
            Set<?> holders,
            Class<?> holderClass,
            Field holderRegistryField,
            Field holderKeyField,
            Field holderTargetField,
            Field holderValidField,
            Method overrideOwners,
            Method isDummied,
            boolean streamlined
    ) {
        int holdersVisited = 0;
        int forgeHoldersVisited = 0;
        int redundantCallbacksRemoved = 0;
        int overrideCallbacksRetained = 0;
        int safetyCallbacksRetained = 0;
        int failures = 0;
        // This synchronous pass removes callbacks, never mutates registries.
        // Do not retain these snapshots across registry remaps or world joins.
        OverrideOwnerLookup ownerLookup = new OverrideOwnerLookup(overrideOwners, streamlined);

        Iterator<?> iterator = holders.iterator();
        while (iterator.hasNext()) {
            Object handler = iterator.next();
            holdersVisited++;
            if (handler == null || handler.getClass() != holderClass) {
                continue;
            }
            forgeHoldersVisited++;
            try {
                boolean valid = holderValidField.getBoolean(handler);
                if (streamlined && !valid) {
                    safetyCallbacksRetained++;
                    continue;
                }
                ForgeRegistry<?> registry = (ForgeRegistry<?>)
                        holderRegistryField.get(handler);
                ResourceLocation key = (ResourceLocation)
                        holderKeyField.get(handler);
                if (streamlined && (registry == null || key == null)) {
                    safetyCallbacksRetained++;
                    continue;
                }
                Map<?, ?> owners = ownerLookup.get(registry);
                boolean hasOverrideCandidates = owners != null
                        && owners.containsKey(key);
                if (streamlined && hasOverrideCandidates) {
                    overrideCallbacksRetained++;
                    continue;
                }
                boolean containsKey = registry != null
                        && key != null
                        && registry.containsKey(key);
                boolean dummied = containsKey
                        && Boolean.TRUE.equals(isDummied.invoke(registry, key));
                if (streamlined && (!containsKey || dummied)) {
                    safetyCallbacksRetained++;
                    continue;
                }
                Field target = (Field) holderTargetField.get(handler);
                boolean targetMatches = registry != null
                        && key != null
                        && target != null
                        && target.trySetAccessible()
                        && target.get(null) == registry.getValue(key);
                Eligibility eligibility = new Eligibility(
                        true,
                        valid,
                        registry != null,
                        key != null,
                        target != null,
                        hasOverrideCandidates,
                        containsKey,
                        dummied,
                        targetMatches
                );
                if (safeToRemove(eligibility)) {
                    iterator.remove();
                    redundantCallbacksRemoved++;
                } else if (hasOverrideCandidates) {
                    overrideCallbacksRetained++;
                } else {
                    safetyCallbacksRetained++;
                }
            } catch (RuntimeException | ReflectiveOperationException exception) {
                failures++;
            }
        }

        return new Statistics(
                true,
                holdersVisited,
                forgeHoldersVisited,
                redundantCallbacksRemoved,
                overrideCallbacksRetained,
                safetyCallbacksRetained,
                failures
        );
    }

    /** Pass-local, identity keyed, and failed lookups never become empty snapshots. */
    static final class OverrideOwnerLookup {
        private final Method method;
        private final boolean reuse;
        private final Map<Object, Map<?, ?>> snapshots = new IdentityHashMap<>();

        OverrideOwnerLookup(Method method, boolean reuse) {
            this.method = method;
            this.reuse = reuse;
        }

        Map<?, ?> get(Object registry) throws ReflectiveOperationException {
            if (registry == null) return null;
            Map<?, ?> cached = reuse ? snapshots.get(registry) : null;
            if (cached != null) return cached;
            Object value = method.invoke(registry);
            if (!(value instanceof Map<?, ?> owners)) {
                throw new ReflectiveOperationException(
                        "ForgeRegistry#getOverrideOwners did not return a map");
            }
            if (reuse) snapshots.put(registry, owners);
            return owners;
        }
    }

    static boolean safeToRemove(Eligibility eligibility) {
        return eligibility.exactForgeHolder()
                && eligibility.valid()
                && eligibility.registryPresent()
                && eligibility.keyPresent()
                && eligibility.targetPresent()
                && !eligibility.hasOverrideCandidates()
                && eligibility.registryContainsKey()
                && !eligibility.dummied()
                && eligibility.targetMatchesRegistryValue();
    }

    private static Field accessibleField(Class<?> owner, String name)
            throws NoSuchFieldException, IllegalAccessException {
        Field field = owner.getDeclaredField(name);
        if (!field.trySetAccessible()) {
            throw new IllegalAccessException(
                    "Could not access " + owner.getName() + "." + name
            );
        }
        return field;
    }

    record Eligibility(
            boolean exactForgeHolder,
            boolean valid,
            boolean registryPresent,
            boolean keyPresent,
            boolean targetPresent,
            boolean hasOverrideCandidates,
            boolean registryContainsKey,
            boolean dummied,
            boolean targetMatchesRegistryValue
    ) {
    }

    public record Statistics(
            boolean available,
            int holdersVisited,
            int forgeHoldersVisited,
            int redundantCallbacksRemoved,
            int overrideCallbacksRetained,
            int safetyCallbacksRetained,
            int failures
    ) {
        private static Statistics unavailable() {
            return new Statistics(false, 0, 0, 0, 0, 0, 0);
        }
    }
}

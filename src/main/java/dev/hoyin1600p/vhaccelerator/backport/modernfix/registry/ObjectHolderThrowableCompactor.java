/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/registry/ObjectHolderClearer.java
 * Upstream commit: f36a8f4266355aea3d365ea867925713f6815f09
 * Original copyright: Copyright (c) 2023 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; deferred cleanup to Forge load completion, restricted
 * replacement to synthetic instance captures, retained every holder callback,
 * and added per-handler failure isolation and statistics.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Releases registration-only caller stack traces retained by Forge registry
 * callbacks after a successful mod-loading lifecycle.
 *
 * <p>The callbacks themselves remain installed. Forge 40 reapplies them when
 * registry snapshots are injected or restored, so removing callbacks would
 * change remap behavior. Only compiler-generated instance fields that capture
 * a {@link Throwable} are replaced.</p>
 */
public final class ObjectHolderThrowableCompactor {
    private static final Throwable CLEARED_CALLING_SITE = clearedCallingSite();

    private ObjectHolderThrowableCompactor() {
    }

    public static Statistics compactForgeHolders() {
        try {
            Class<?> registryClass = Class.forName(
                    "net.minecraftforge.registries.ObjectHolderRegistry"
            );
            Field holdersField = registryClass.getDeclaredField("objectHolders");
            if (!holdersField.trySetAccessible()) {
                return Statistics.unavailable();
            }
            synchronized (registryClass) {
                Object value = holdersField.get(null);
                if (!(value instanceof Set<?> holders)) {
                    return Statistics.unavailable();
                }
                return compactHandlers(holders);
            }
        } catch (LinkageError | ReflectiveOperationException exception) {
            return Statistics.unavailable();
        }
    }

    static Statistics compactHandlersForTesting(Iterable<?> handlers) {
        return compactHandlers(handlers);
    }

    private static Statistics compactHandlers(Iterable<?> handlers) {
        Map<Class<?>, Field[]> fieldsByClass = new HashMap<>();
        int holdersVisited = 0;
        int throwablesCleared = 0;
        int failures = 0;
        for (Object handler : handlers) {
            if (handler == null) {
                continue;
            }
            holdersVisited++;
            Field[] fields = fieldsByClass.computeIfAbsent(
                    handler.getClass(),
                    ObjectHolderThrowableCompactor::capturedThrowableFields
            );
            for (Field field : fields) {
                try {
                    if (!field.trySetAccessible()) {
                        failures++;
                        continue;
                    }
                    Object existing = field.get(handler);
                    if (existing instanceof Throwable
                            && existing != CLEARED_CALLING_SITE) {
                        field.set(handler, CLEARED_CALLING_SITE);
                        throwablesCleared++;
                    }
                } catch (RuntimeException | ReflectiveOperationException exception) {
                    failures++;
                }
            }
        }
        return new Statistics(true, holdersVisited, throwablesCleared, failures);
    }

    private static Field[] capturedThrowableFields(Class<?> handlerClass) {
        return java.util.Arrays.stream(handlerClass.getDeclaredFields())
                .filter(Field::isSynthetic)
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .filter(field -> Throwable.class.isAssignableFrom(field.getType()))
                .toArray(Field[]::new);
    }

    private static Throwable clearedCallingSite() {
        Throwable throwable = new Throwable(
                "[Registration calling-site stack trace released after load]"
        );
        throwable.setStackTrace(new StackTraceElement[0]);
        return throwable;
    }

    public record Statistics(
            boolean available,
            int holdersVisited,
            int throwablesCleared,
            int failures
    ) {
        private static Statistics unavailable() {
            return new Statistics(false, 0, 0, 0);
        }
    }
}

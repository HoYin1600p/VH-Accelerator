/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted from ModernFix's EitherUtil used by ticking-chunk allocation cuts.
 * See THIRD_PARTY_NOTICES.md and docs/MODERNFIX_BACKPORTS.md.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.world;

import com.mojang.datafixers.util.Either;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

public final class EitherValueAccess {
    private static final Class<?> LEFT_CLASS;
    private static final MethodHandle LEFT_VALUE;

    static {
        Class<?> leftClass = null;
        MethodHandle leftValue = null;
        try {
            leftClass = Class.forName(
                    "com.mojang.datafixers.util.Either$Left"
            );
            Field value = leftClass.getDeclaredField("value");
            value.setAccessible(true);
            leftValue = MethodHandles.lookup()
                    .unreflectGetter(value)
                    .asType(MethodType.methodType(
                            Object.class,
                            Either.class
                    ));
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError ignored) {
            // The fallback below retains vanilla behavior if DFU changes.
        }
        LEFT_CLASS = leftClass;
        LEFT_VALUE = leftValue;
    }

    private EitherValueAccess() {
    }

    @SuppressWarnings("unchecked")
    public static <L, R> L leftOrNull(Either<L, R> either) {
        if (LEFT_VALUE != null && either.getClass() == LEFT_CLASS) {
            try {
                return (L) LEFT_VALUE.invokeExact(either);
            } catch (Throwable ignored) {
                // Retain vanilla's Optional path if reflective access fails.
            }
        }
        return either.left().orElse(null);
    }

    public static boolean optimizedAccessAvailable() {
        return LEFT_VALUE != null;
    }
}

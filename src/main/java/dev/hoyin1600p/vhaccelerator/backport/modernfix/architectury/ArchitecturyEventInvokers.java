/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/architectury_event_dispatch/EventFactoryMixin.java
 * Upstream commit: 0ce7f03ce75d27e8a5c0c3137d5a4c8d89cf2c2d
 * Original copyright: Copyright (c) 2026; embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-11-27; extracted cached MethodHandle invoker lookup out of the mixin class for Forge 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.architectury;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches adapted {@link MethodHandle} invokers for Architectury's dynamic event proxies so
 * listener dispatch does not unreflect and adapt a fresh handle on every invocation.
 */
public final class ArchitecturyEventInvokers {
    private static final MethodType INVOKER_TYPE =
            MethodType.methodType(Object.class, Object.class, Object[].class);
    private static final Map<Method, MethodHandle> INVOKERS = new ConcurrentHashMap<>();

    private ArchitecturyEventInvokers() {
    }

    public static Object invoke(
            MethodHandles.Lookup lookup,
            Object listener,
            Method method,
            Object[] args
    ) throws Throwable {
        MethodHandle invoker = INVOKERS.get(method);
        if (invoker == null) {
            // Fixed arity: for varargs methods the proxy passes the trailing array as a single element of args.
            MethodHandle created = lookup.unreflect(method)
                    .asFixedArity()
                    .asSpreader(Object[].class, method.getParameterCount())
                    .asType(INVOKER_TYPE);
            MethodHandle existing = INVOKERS.putIfAbsent(method, created);
            invoker = existing != null ? existing : created;
        }
        return (Object) invoker.invokeExact(listener, args);
    }

    static int cachedCount() {
        return INVOKERS.size();
    }

    static void clearForTests() {
        INVOKERS.clear();
    }
}

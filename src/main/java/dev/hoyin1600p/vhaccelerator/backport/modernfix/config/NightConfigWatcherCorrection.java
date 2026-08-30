/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/config/NightConfigWatchThrottler.java
 * Upstream commit: ae20fa17c9e747211193820d0a327434f105349d
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; replaces the installed 5.18 wrapper with named, eagerly loaded classes and fail-safe reflection.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.config;

import com.electronwill.nightconfig.core.file.FileWatcher;
import com.google.common.collect.ForwardingCollection;
import com.google.common.collect.ForwardingMap;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

public final class NightConfigWatcherCorrection {
    private static final long DELAY_NANOS =
            TimeUnit.MILLISECONDS.toNanos(1_000L);
    private static final String MODERNFIX_WRAPPER_PREFIX =
            "org.embeddedt.modernfix.forge.config."
                    + "NightConfigWatchThrottler$";

    private NightConfigWatcherCorrection() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean apply() {
        try {
            FileWatcher watcher = FileWatcher.defaultInstance();
            Map installed = ObfuscationReflectionHelper.getPrivateValue(
                    FileWatcher.class,
                    watcher,
                    "watchedDirs"
            );
            if (installed == null) {
                return false;
            }
            Map delegate = unwrapModernFixDelegate(installed);
            CorrectedWatchingMap corrected = new CorrectedWatchingMap(
                    delegate,
                    Thread.currentThread()
            );

            // Construct both named wrapper classes on the launch thread before
            // NightConfig's watcher can encounter them through its iterator.
            corrected.values().iterator();
            ObfuscationReflectionHelper.setPrivateValue(
                    FileWatcher.class,
                    watcher,
                    corrected,
                    "watchedDirs"
            );
            return true;
        } catch (ReflectiveOperationException
                 | RuntimeException
                 | LinkageError failure) {
            return false;
        }
    }

    @SuppressWarnings("rawtypes")
    private static Map unwrapModernFixDelegate(Map installed)
            throws IllegalAccessException {
        if (!installed.getClass().getName().startsWith(
                MODERNFIX_WRAPPER_PREFIX
        )) {
            return installed;
        }
        for (Field field : installed.getClass().getDeclaredFields()) {
            if (!Map.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object candidate = field.get(installed);
            if (candidate instanceof Map map && candidate != installed) {
                return map;
            }
        }
        return installed;
    }

    static boolean shouldDelay(Thread launchThread, Thread currentThread) {
        return currentThread != launchThread;
    }

    @SuppressWarnings("rawtypes")
    private static final class CorrectedWatchingMap extends ForwardingMap {
        private final Map delegate;
        private final Thread launchThread;
        private Collection cachedValues;

        private CorrectedWatchingMap(Map delegate, Thread launchThread) {
            this.delegate = delegate;
            this.launchThread = launchThread;
        }

        @Override
        protected Map delegate() {
            return delegate;
        }

        @Override
        public Collection values() {
            if (cachedValues == null) {
                cachedValues = new ThrottledValues(
                        super.values(),
                        launchThread
                );
            }
            return cachedValues;
        }
    }

    @SuppressWarnings("rawtypes")
    private static final class ThrottledValues extends ForwardingCollection {
        private final Collection delegate;
        private final Thread launchThread;

        private ThrottledValues(Collection delegate, Thread launchThread) {
            this.delegate = delegate;
            this.launchThread = launchThread;
        }

        @Override
        protected Collection delegate() {
            return delegate;
        }

        @Override
        public Iterator iterator() {
            if (shouldDelay(launchThread, Thread.currentThread())) {
                LockSupport.parkNanos(DELAY_NANOS);
            }
            return super.iterator();
        }
    }
}

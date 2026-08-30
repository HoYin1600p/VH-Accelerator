/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/core/ModernFixMixinPlugin.java
 * Upstream commit: 12a0414f61f19826adff7ca6a02c2b9a185bb96b
 * Original copyright: Copyright (c) embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; combined commits ee6489fb and 12a0414f as an independently gated VHA bootstrap policy.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.thread;

public final class BackgroundWorkerLimit {
    private static final String PROPERTY = "max.bg.threads";
    private static final int RESERVED_CORES = 3;

    private BackgroundWorkerLimit() {
    }

    public static Result configure() {
        if (System.getProperty(PROPERTY) != null) {
            return Result.USER_VALUE_RETAINED;
        }
        int workers = recommendedWorkerCount(
                Runtime.getRuntime().availableProcessors()
        );
        System.setProperty(PROPERTY, Integer.toString(workers));
        return Result.CONFIGURED;
    }

    public static int recommendedWorkerCount(int availableProcessors) {
        return Math.max(1, availableProcessors - RESERVED_CORES);
    }

    public enum Result {
        CONFIGURED,
        USER_VALUE_RETAINED
    }
}

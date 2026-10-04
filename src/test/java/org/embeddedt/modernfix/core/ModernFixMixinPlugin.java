package org.embeddedt.modernfix.core;

import java.util.Map;

/**
 * Test double for ModernFix's early mixin plugin. VH Accelerator only reaches
 * it reflectively ({@code instance} and {@code isOptionEnabled}), so this
 * stand-in lets tests script ModernFix's effective option decisions.
 */
public final class ModernFixMixinPlugin {
    public static Object instance;
    private static Map<String, Boolean> options = Map.of();
    private static Boolean defaultAnswer;

    /** Installs a scripted instance; unknown options answer {@code defaultAnswer}. */
    public static void install(Map<String, Boolean> scripted, Boolean defaultAnswer) {
        options = scripted;
        ModernFixMixinPlugin.defaultAnswer = defaultAnswer;
        instance = new ModernFixMixinPlugin();
    }

    public static void uninstall() {
        instance = null;
        options = Map.of();
        defaultAnswer = null;
    }

    public boolean isOptionEnabled(String option) {
        Boolean scripted = options.get(option);
        if (scripted != null) {
            return scripted;
        }
        if (defaultAnswer == null) {
            throw new IllegalStateException("unscripted option " + option);
        }
        return defaultAnswer;
    }
}

/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/dynamic_languages/ClientLanguageMixin.java
 * Upstream commit: d749205427d714a4865155f03c16a48f8e564117
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-08; replace only the final storage argument, preserving
 * vanilla resource reads, merge order and mod-injected translations.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.language;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.language.DynamicLanguageStorage;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = ClientLanguage.class, priority = 2000)
public abstract class ClientLanguageMixin {
    @ModifyArg(
            method = "loadFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/language/ClientLanguage;<init>(Ljava/util/Map;Z)V"
            ),
            index = 0
    )
    private static Map<String, String> vha$useDynamicLanguageStorage(Map<String, String> storage) {
        DynamicLanguageStorage.BuildResult result = DynamicLanguageStorage.createStorage(storage);
        DynamicLanguageStorage.log(result);
        return result.storage();
    }
}

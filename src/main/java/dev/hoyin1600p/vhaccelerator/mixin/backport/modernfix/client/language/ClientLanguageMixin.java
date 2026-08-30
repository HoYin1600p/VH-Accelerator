/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/common/mixin/perf/dynamic_languages/ClientLanguageMixin.java
 * Upstream commit: d749205427d714a4865155f03c16a48f8e564117
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 and replaced MixinExtras
 * shared locals with an external load context and standard Mixin injectors.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.language;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.language.DynamicLanguageStorage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientLanguage.class, priority = 2000)
public abstract class ClientLanguageMixin {
    @Inject(method = "loadFrom", at = @At("HEAD"))
    private static void vha$beginDynamicLanguageLoad(
            ResourceManager resourceManager,
            List<LanguageInfo> languageInfo,
            CallbackInfoReturnable<ClientLanguage> callback
    ) {
        DynamicLanguageStorage.begin(resourceManager);
    }

    @Redirect(
            method = "loadFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;getResources(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/List;"
            )
    )
    private static List<Resource> vha$collectLanguageLocation(
            ResourceManager resourceManager,
            ResourceLocation location
    ) throws IOException {
        List<Resource> resources = resourceManager.getResources(location);
        DynamicLanguageStorage.record(location);
        return resources;
    }

    @ModifyArg(
            method = "loadFrom",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/language/ClientLanguage;<init>(Ljava/util/Map;Z)V"
            ),
            index = 0
    )
    private static Map<String, String> vha$useDynamicLanguageStorage(
            Map<String, String> storage
    ) {
        DynamicLanguageStorage.BuildResult result =
                DynamicLanguageStorage.finish(storage);
        DynamicLanguageStorage.log(result);
        return result.storage();
    }

    @Inject(method = "loadFrom", at = @At("RETURN"))
    private static void vha$clearDynamicLanguageLoadContext(
            ResourceManager resourceManager,
            List<LanguageInfo> languageInfo,
            CallbackInfoReturnable<ClientLanguage> callback
    ) {
        DynamicLanguageStorage.abort();
    }
}

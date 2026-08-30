/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: forge/src/main/java/org/embeddedt/modernfix/forge/mixin/perf/model_optimizations/OBJLoaderMixin.java
 * Upstream commit: fe855f15304ed788122a27cda4c2495a78374528
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated exact Forge 40 cache replacement behind VHA ownership.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.model.obj;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.obj.MaterialLibrary;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.client.model.obj.OBJModel;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = OBJLoader.class, remap = false)
public abstract class OBJLoaderMixin {
    @Shadow
    @Final
    @Mutable
    private Map<ResourceLocation, MaterialLibrary> materialCache;

    @Shadow
    @Final
    @Mutable
    private Map<OBJModel.ModelSettings, OBJModel> modelCache;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraftforge/client/model/obj/OBJLoader;"
                            + "materialCache:Ljava/util/Map;"
            )
    )
    private void vha$useConcurrentMaterialCache(
            OBJLoader loader,
            Map<ResourceLocation, MaterialLibrary> ignored
    ) {
        this.materialCache = new ConcurrentHashMap<>();
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraftforge/client/model/obj/OBJLoader;"
                            + "modelCache:Ljava/util/Map;"
            )
    )
    private void vha$useConcurrentModelCache(
            OBJLoader loader,
            Map<OBJModel.ModelSettings, OBJModel> ignored
    ) {
        this.modelCache = new ConcurrentHashMap<>();
    }
}

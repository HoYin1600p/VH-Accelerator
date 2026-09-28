/*
 * SPDX-License-Identifier: LGPL-3.0-or-later AND MIT
 *
 * Adapted for VH Accelerator from FerriteCore.
 * Upstream repository: https://github.com/malte0811/FerriteCore
 * Upstream source: Common/src/main/java/malte0811/ferritecore/mixin/modelsides/SimpleBakedModelMixin.java
 * Upstream commit: 7baeea0bd337188114f889c581a28f02b74f3364 (branch 1.21.1)
 * Original copyright: Copyright (c) 2020 malte0811 (MIT License)
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-09-26; backported to Minecraft 1.18.2.
 */
package dev.hoyin1600p.vhaccelerator.mixin.client.compat.ferritecore;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.ferritecore.ModelFaceLists;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Not applied when Vault Render Optimization, which compacts these lists itself, is loaded. */
@Mixin(SimpleBakedModel.class)
public abstract class SimpleBakedModelFaceListsMixin {
    @Shadow
    @Final
    @Mutable
    protected Map<Direction, List<BakedQuad>> culledFaces;

    @Shadow
    @Final
    @Mutable
    protected List<BakedQuad> unculledFaces;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vhaccelerator$compactFaceLists(CallbackInfo callback) {
        if (VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.compactModelFaceLists
                )) {
            this.unculledFaces = ModelFaceLists.unculled(this.unculledFaces);
            this.culledFaces = ModelFaceLists.culled(this.culledFaces);
        }
    }
}

/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/faster_texture_stitching/StitcherMixin.java
 * Upstream commit: 94c848b0debbb5291ab3c709353e3f11613fd14d
 * Original copyright: Copyright (c) 2022 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; isolated behind VHA exact-option ownership and retained
 * vanilla stitching for small atlases to preserve alignment-sensitive mods.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.client.texture;

import com.mojang.datafixers.util.Pair;
import dev.hoyin1600p.vhaccelerator.backport.modernfix.texture.StbTextureStitcher;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.renderer.texture.Stitcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Stitcher.class)
public abstract class StitcherMixin {
    @Shadow
    @Final
    private Set<Stitcher.Holder> texturesToBeStitched;

    @Shadow
    private int storageX;

    @Shadow
    private int storageY;

    @Shadow
    @Final
    private static Comparator<Stitcher.Holder> HOLDER_COMPARATOR;

    @Shadow
    @Final
    private int maxWidth;

    @Shadow
    @Final
    private int maxHeight;

    @Unique
    private List<StbTextureStitcher.LoadableSpriteInfo> vha$packedSprites;

    @Inject(method = "stitch", at = @At("HEAD"), cancellable = true)
    private void vha$stitchLargeAtlas(CallbackInfo callback) {
        this.vha$packedSprites = null;
        if (this.texturesToBeStitched.size() < 100) {
            return;
        }

        ObjectArrayList<Stitcher.Holder> holders = new ObjectArrayList<>(
                this.texturesToBeStitched
        );
        holders.sort(HOLDER_COMPARATOR);
        Stitcher.Holder[] holderArray = holders.toArray(
                new Stitcher.Holder[0]
        );
        Optional<Pair<
                Pair<Integer, Integer>,
                List<StbTextureStitcher.LoadableSpriteInfo>
                >> packed = StbTextureStitcher.pack(
                        holderArray,
                        this.maxWidth,
                        this.maxHeight
                );
        if (packed.isEmpty()) {
            return;
        }
        Pair<
                Pair<Integer, Integer>,
                List<StbTextureStitcher.LoadableSpriteInfo>
                > result = packed.get();
        this.storageX = result.getFirst().getFirst();
        this.storageY = result.getFirst().getSecond();

        this.vha$packedSprites = result.getSecond();
        callback.cancel();
    }

    @Inject(method = "gatherSprites", at = @At("HEAD"), cancellable = true)
    private void vha$loadPackedSprites(
            Stitcher.SpriteLoader loader,
            CallbackInfo callback
    ) {
        if (this.vha$packedSprites == null) {
            return;
        }
        callback.cancel();
        for (StbTextureStitcher.LoadableSpriteInfo sprite
                : this.vha$packedSprites) {
            loader.load(
                    sprite.info(),
                    sprite.atlasWidth(),
                    sprite.atlasHeight(),
                    sprite.x(),
                    sprite.y()
            );
        }
    }
}

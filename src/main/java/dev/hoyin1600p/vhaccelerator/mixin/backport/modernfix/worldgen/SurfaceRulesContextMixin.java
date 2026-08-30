/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/perf/worldgen_allocation/SurfaceRulesContextMixin.java
 * Upstream commit: 2193aa11a408251b7b5b5e03ecfadc3d166c291c; visibility correction 639f0e2c1a763fff5531ee2016354d6c6bf88c1f
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted Minecraft 1.18.2 updateY, retained its runtime-public visibility, and used a null-safe reusable supplier.
 */
package dev.hoyin1600p.vhaccelerator.mixin.backport.modernfix.worldgen;

import dev.hoyin1600p.vhaccelerator.backport.modernfix.worldgen.PositionMemoizedSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(
        targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context",
        priority = 500
)
public abstract class SurfaceRulesContextMixin {
    @Shadow
    private long lastUpdateY;

    @Shadow
    private int blockY;

    @Shadow
    private int waterHeight;

    @Shadow
    private int stoneDepthBelow;

    @Shadow
    private int stoneDepthAbove;

    @Shadow
    private Supplier<Holder<Biome>> biome;

    @Shadow
    @Final
    private Function<BlockPos, Holder<Biome>> biomeGetter;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos pos;

    /**
     * Repositions one reusable lazy supplier rather than creating a capturing
     * lambda and a Guava memoizing wrapper for every generated block position.
     *
     * @author embeddedt, HoYin1600p
     * @reason Retain lazy biome resolution while eliminating per-position suppliers.
     */
    @Overwrite
    public void updateY(
            int stoneDepthAbove,
            int stoneDepthBelow,
            int waterHeight,
            int blockX,
            int blockY,
            int blockZ
    ) {
        ++this.lastUpdateY;
        PositionMemoizedSupplier<Holder<Biome>> positioned;
        if (this.biome instanceof PositionMemoizedSupplier<?>) {
            @SuppressWarnings("unchecked")
            PositionMemoizedSupplier<Holder<Biome>> existing =
                    (PositionMemoizedSupplier<Holder<Biome>>) this.biome;
            positioned = existing;
        } else {
            positioned = new PositionMemoizedSupplier<>(this.biomeGetter, this.pos);
            this.biome = positioned;
        }
        positioned.update(blockX, blockY, blockZ);
        this.blockY = blockY;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
    }
}

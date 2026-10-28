package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.cdf;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfOptimizer;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfPolicy;
import iskallia.vault.core.world.loot.generator.TieredLootTableGenerator;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Changes only local bucket construction; Vault owns eager publication and cache invalidation. */
@Mixin(value = TieredLootTableGenerator.CDF.class, remap = false)
public abstract class TieredLootTableGeneratorEagerCdfMixin {
    @Unique
    private static final String vhaccelerator$mixin =
            "dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.cdf.TieredLootTableGeneratorEagerCdfMixin";

    @Shadow @Final private int samples;
    @Shadow @Final private double[] weights;
    @Shadow public abstract long pack(int[] frequencies);
    @Shadow public abstract int[] unpack(long packed);
    @Shadow public abstract double getHeuristic(int[] frequencies);
    @Shadow public abstract double getProbability(int[] frequencies);

    @Inject(method = "compute()Lit/unimi/dsi/fastutil/longs/Long2DoubleMap;",
            at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$computeEagerly(CallbackInfoReturnable<Long2DoubleMap> callback) {
        if (!VaultLootCdfPolicy.enabled()
                || !VaultLootCdfOptimizer.supports(samples, weights.length)
                || !CompatMixinGroups.activationAllowed(vhaccelerator$mixin)) return;
        Long2DoubleMap complete = VaultLootCdfOptimizer.compute(samples, weights.length,
                this::getHeuristic, this::pack, this::unpack, this::getProbability);
        CompatMixinGroups.recordOptimizationUse(vhaccelerator$mixin);
        callback.setReturnValue(complete);
    }
}

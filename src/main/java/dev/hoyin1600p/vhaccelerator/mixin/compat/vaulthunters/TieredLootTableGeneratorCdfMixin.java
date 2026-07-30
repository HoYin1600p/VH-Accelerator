package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.VaultLootCdfOptimizer;
import iskallia.vault.core.world.loot.generator.TieredLootTableGenerator;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TieredLootTableGenerator.CDF.class, remap = false)
public abstract class TieredLootTableGeneratorCdfMixin {
    @Shadow
    @Final
    private int samples;

    @Shadow
    @Final
    private double[] weights;

    @Shadow
    public abstract long pack(int[] frequencies);

    @Shadow
    public abstract int[] unpack(long packed);

    @Shadow
    public abstract double getHeuristic(int[] frequencies);

    @Shadow
    public abstract double getProbability(int[] frequencies);

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$useHashBuckets(
            CallbackInfoReturnable<Long2DoubleMap> cir
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.optimizeVaultLootCdf
                )) {
            return;
        }

        cir.setReturnValue(VaultLootCdfOptimizer.compute(
                this.samples,
                this.weights.length,
                this::getHeuristic,
                this::pack,
                this::unpack,
                this::getProbability
        ));
    }
}

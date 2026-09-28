package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.compat.vaulthunters.VaultLootCdfOptimizer;
import iskallia.vault.core.world.loot.generator.TieredLootTableGenerator;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
    @Final
    private Long2DoubleMap map;

    /** The distribution, computed on first use when the constructor skipped it. */
    @Unique
    private volatile Long2DoubleMap vhaccelerator$lazyMap;

    @Shadow
    public abstract long pack(int[] frequencies);

    @Shadow
    public abstract Long2DoubleMap compute();

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

    /**
     * Vault builds 53 distributions per supported loot table on every config
     * load (about 110 MB in Asgard), though only loot generation reads them. A
     * multiplayer client never does; a server needs a few, each in about 2 ms.
     */
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Liskallia/vault/core/world/loot/generator/"
                            + "TieredLootTableGenerator$CDF;compute()"
                            + "Lit/unimi/dsi/fastutil/longs/Long2DoubleMap;"
            )
    )
    private Long2DoubleMap vhaccelerator$deferCompute(TieredLootTableGenerator.CDF self) {
        if (VHAcceleratorClientConfig.optimizationsEnabled()
                && VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.lazyVaultLootCdf
                )) {
            return null;
        }
        return self.compute();
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$getLazily(
            int[] frequencies,
            CallbackInfoReturnable<Double> cir
    ) {
        if (this.map == null) {
            cir.setReturnValue(vhaccelerator$distribution().get(this.pack(frequencies)));
        }
    }

    @Inject(method = "getMap", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$getMapLazily(CallbackInfoReturnable<Long2DoubleMap> cir) {
        if (this.map == null) {
            cir.setReturnValue(vhaccelerator$distribution());
        }
    }

    @Unique
    private Long2DoubleMap vhaccelerator$distribution() {
        Long2DoubleMap computed = vhaccelerator$lazyMap;
        if (computed == null) {
            synchronized (this) {
                computed = vhaccelerator$lazyMap;
                if (computed == null) {
                    long started = System.nanoTime();
                    computed = this.compute();
                    vhaccelerator$lazyMap = computed;
                    if (VaultLootCdfOptimizer.shouldLogFirstUse()) {
                        dev.hoyin1600p.vhaccelerator.VHAccelerator.LOGGER.info(
                                "[debug] Computed a Vault loot distribution on first use: "
                                        + "{} rolls, {} outcomes, {} ms on {}",
                                this.samples,
                                computed.size(),
                                (System.nanoTime() - started) / 1_000_000L,
                                Thread.currentThread().getName()
                        );
                    }
                }
            }
        }
        return computed;
    }
}

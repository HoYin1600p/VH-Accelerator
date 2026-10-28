package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters.cdflazy;

import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.VaultLootCdfLayouts;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfDistribution;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfLazy;
import dev.hoyin1600p.vhaccelerator.compat.vaulthunters.VaultLootCdfPolicy;
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

/**
 * Dedicated servers: defers {@code CDF.compute()} from the constructor to the first {@code get} or
 * {@code getMap}, then keeps the result as one compact array.
 *
 * <p>It never replaces or wraps {@code compute()} or {@code getProbability()}: the deferred call is the
 * ordinary {@code this.compute()}, so Vault's, VHAPI's or VHA's eager arithmetic runs unchanged, only later.
 * Vault builds 53 distributions for each supported loot table on every config load and keeps all of them
 * in hash maps (about 138 MB on Wolds), though loot generation reads a handful.
 */
@Mixin(value = TieredLootTableGenerator.CDF.class, remap = false)
public abstract class TieredLootTableGeneratorLazyCdfMixin {
    @Shadow @Final private int samples;
    @Shadow @Final private double[] weights;
    @Shadow @Final private int packBits;
    @Shadow @Final private Long2DoubleMap map;

    /** The resolved distribution, published once; null until first use (and forever when not deferred). */
    @Unique
    private volatile VaultLootCdfDistribution vhaccelerator$resolved;

    @Shadow
    public abstract Long2DoubleMap compute();

    @Shadow
    public abstract long pack(int[] frequencies);

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Liskallia/vault/core/world/loot/generator/"
                            + "TieredLootTableGenerator$CDF;compute()"
                            + "Lit/unimi/dsi/fastutil/longs/Long2DoubleMap;"
            )
    )
    // Sponge requires a redirect handler's static modifier to equal its target's; CDF.<init> is an instance method.
    private Long2DoubleMap vhaccelerator$deferCompute(TieredLootTableGenerator.CDF self) {
        if (CompatMixinGroups.activationAllowed(VaultLootCdfLayouts.LAZY_MIXIN)
                && VaultLootCdfPolicy.lazyCompactEnabled()) {
            return null;
        }
        return self.compute();
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$getLazily(int[] frequencies, CallbackInfoReturnable<Double> cir) {
        if (this.map == null) {
            VaultLootCdfDistribution distribution = vhaccelerator$resolve();
            if (distribution != null) cir.setReturnValue(distribution.get(this.pack(frequencies)));
        }
    }

    @Inject(method = "getMap", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$getMapLazily(CallbackInfoReturnable<Long2DoubleMap> cir) {
        if (this.map == null) {
            VaultLootCdfDistribution distribution = vhaccelerator$resolve();
            if (distribution != null) cir.setReturnValue(distribution.map());
        }
    }

    /** Computes once under this distribution's lock; the volatile write publishes the immutable result. */
    @Unique
    private VaultLootCdfDistribution vhaccelerator$resolve() {
        VaultLootCdfDistribution resolved = this.vhaccelerator$resolved;
        if (resolved == null) {
            synchronized (this) {
                resolved = this.vhaccelerator$resolved;
                if (resolved == null) {
                    long started = System.nanoTime();
                    Long2DoubleMap computed;
                    synchronized (VaultLootCdfLazy.COMPUTE_LOCK) {
                        computed = this.compute();
                    }
                    resolved = VaultLootCdfLazy.resolve(computed, this.samples, this.weights.length,
                            this.packBits, System.nanoTime() - started);
                    this.vhaccelerator$resolved = resolved;
                    if (resolved != null) CompatMixinGroups.recordOptimizationUse(VaultLootCdfLayouts.LAZY_MIXIN);
                }
            }
        }
        return resolved;
    }
}

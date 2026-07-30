package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.DataFixerBuilder;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Leaves data migration available while avoiding its optional all-rules warm-up.
 *
 * <p>DataFixerBuilder returns a complete fixer before its executor-backed
 * optimization tasks finish. Skipping those speculative tasks preserves
 * on-demand rule compilation for old client data without allowing a full CPU
 * core and substantial allocation pressure to compete with launch.</p>
 */
@Mixin(value = DataFixerBuilder.class, remap = false)
public abstract class DataFixerBuilderMixin {
    private static final Executor VHA_NO_RULE_WARMUP = command -> {
    };

    @ModifyVariable(
            method = "build",
            at = @At("HEAD"),
            argsOnly = true,
            require = 1
    )
    private Executor vhaccelerator$skipSpeculativeRuleWarmup(
            Executor original
    ) {
        if (!VHAcceleratorConfig.compareModeEnabled()) {
            return VHA_NO_RULE_WARMUP;
        }
        return original;
    }
}

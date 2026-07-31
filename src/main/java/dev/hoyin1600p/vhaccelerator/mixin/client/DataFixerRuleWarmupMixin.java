package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.util.concurrent.Executor;
import net.minecraft.util.datafix.DataFixers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Leaves data migration available while avoiding its optional all-rules warm-up.
 *
 * <p>DataFixerBuilder returns a complete fixer before its executor-backed
 * optimization tasks finish. Skipping those speculative tasks preserves
 * on-demand rule compilation for old client data without allowing a full CPU
 * core and substantial allocation pressure to compete with launch.</p>
 */
@Mixin(DataFixers.class)
public abstract class DataFixerRuleWarmupMixin {
    @Redirect(
            method = "createFixerUpper",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/datafixers/DataFixerBuilder;"
                            + "build(Ljava/util/concurrent/Executor;)"
                            + "Lcom/mojang/datafixers/DataFixer;",
                    remap = false
            ),
            require = 1
    )
    private static DataFixer vhaccelerator$skipSpeculativeRuleWarmup(
            DataFixerBuilder builder,
            Executor original
    ) {
        if (!VHAcceleratorConfig.compareModeEnabled()) {
            // This must remain method-local because the target invokes this
            // redirect before Mixin appends this mixin's static initializers.
            Executor noRuleWarmupExecutor = command -> {
            };
            return builder.build(noRuleWarmupExecutor);
        }
        return builder.build(original);
    }
}

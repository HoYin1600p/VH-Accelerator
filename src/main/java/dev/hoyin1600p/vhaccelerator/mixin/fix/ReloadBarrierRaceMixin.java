package dev.hoyin1600p.vhaccelerator.mixin.fix;

import dev.hoyin1600p.vhaccelerator.backport.fix.ReloadBarrierSet;
import java.util.Set;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fixes a lost-update race in vanilla's reload barrier. The set of listeners
 * still preparing is a plain HashSet that the preparation barrier callbacks
 * modify from several threads on a dedicated server, which can leave the
 * "all prepared" future incomplete forever. The field is assigned with a
 * thread-safe copy at the moment the constructor stores it, before any
 * listener can start, so no callback ever sees the unsafe set.
 */
@Mixin(SimpleReloadInstance.class)
abstract class ReloadBarrierRaceMixin {
    @Shadow
    @Final
    @Mutable
    Set<PreparableReloadListener> preparingListeners;

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;"
                            + "preparingListeners:Ljava/util/Set;",
                    opcode = Opcodes.PUTFIELD
            ),
            require = 1
    )
    private void vha$storeConcurrentListenerSet(
            SimpleReloadInstance<?> owner,
            Set<PreparableReloadListener> vanillaSet
    ) {
        this.preparingListeners = ReloadBarrierSet.copyOf(vanillaSet);
    }
}

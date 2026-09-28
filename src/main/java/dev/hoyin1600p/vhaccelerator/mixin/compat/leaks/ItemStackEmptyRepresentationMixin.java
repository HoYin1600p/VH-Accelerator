package dev.hoyin1600p.vhaccelerator.mixin.compat.leaks;

import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An item entity whose synced stack is empty stores itself on the shared
 * {@code ItemStack.EMPTY}, which then keeps that entity, its level and its
 * chunks alive after the level is gone. The shared empty stack never takes
 * an entity representation (clearing it is still allowed). Idea from AllTheLeaks (reimplemented).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackEmptyRepresentationMixin {
    @Inject(method = "setEntityRepresentation", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$keepEmptyStackUnbound(@Nullable Entity entity, CallbackInfo callback) {
        if (entity != null && (Object) this == ItemStack.EMPTY) {
            callback.cancel();
        }
    }
}

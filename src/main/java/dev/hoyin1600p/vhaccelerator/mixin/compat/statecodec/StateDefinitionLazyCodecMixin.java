package dev.hoyin1600p.vhaccelerator.mixin.compat.statecodec;

import com.mojang.serialization.MapCodec;
import dev.hoyin1600p.vhaccelerator.compat.statecodec.LazyStateCodec;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dedicated servers: builds the property codec of each block and fluid state definition on first use.
 *
 * <p>The constructor calls {@code appendPropertyCodec} once per property to chain the codec that every state
 * keeps and only {@code BlockState.CODEC} reads (chunk palettes, worldgen and datapack JSON). This redirect
 * records the property instead; see {@link LazyStateCodec}. Applied only after a dedicated-server gate, a
 * pre-weave shape check and, on the woven class, a check that no other mixin owns the class.
 */
@Mixin(StateDefinition.class)
public abstract class StateDefinitionLazyCodecMixin {
    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/StateDefinition;appendPropertyCodec("
                            + "Lcom/mojang/serialization/MapCodec;Ljava/util/function/Supplier;"
                            + "Ljava/lang/String;Lnet/minecraft/world/level/block/state/properties/Property;)"
                            + "Lcom/mojang/serialization/MapCodec;"
            ),
            require = 0
    )
    // Sponge requires a redirect handler's static modifier to equal its target's; StateDefinition.<init> is an instance
    // method, so this is an instance handler that takes the static call's arguments (Mixin loads this itself).
    private <S extends StateHolder<?, S>, T extends Comparable<T>> MapCodec<S> vhaccelerator$lazyAppend(
            MapCodec<S> previous, Supplier<S> supplier, String name, Property<T> property) {
        return LazyStateCodec.append(previous, supplier, name, property);
    }
}

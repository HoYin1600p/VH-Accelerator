package dev.hoyin1600p.vhaccelerator.mixin.compat.ferritecore;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import dev.hoyin1600p.vhaccelerator.compat.ferritecore.FerriteCorePropertyMaps;
import java.util.Map;
import malte0811.ferritecore.classloading.FastImmutableMapDefiner;
import malte0811.ferritecore.ducks.FastMapStateHolder;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * See FerriteCorePropertyMaps. Applied after FerriteCore (priority 1100), so
 * the TAIL injection runs at the end of FerriteCore's populateNeighbours
 * overwrite. A null {@code values} field means "read the FastMap"; any other
 * value (a vanilla map, or a state FerriteCore did not convert) is used as is.
 */
@Mixin(value = StateHolder.class, priority = 1100)
public abstract class StateHolderPropertyMapMixin {
    @Shadow
    @Final
    @Mutable
    private ImmutableMap<Property<?>, Comparable<?>> values;

    @Inject(method = "populateNeighbours", at = @At("TAIL"))
    private void vhaccelerator$dropPropertyView(Map<?, ?> states, CallbackInfo callback) {
        if (values != null
                && FerriteCorePropertyMaps.VIEW_CLASS.equals(values.getClass().getName())
                && ((FastMapStateHolder<?>) (Object) this).getStateMap() != null) {
            values = null;
        }
    }

    @Redirect(
            method = {"getValue", "getOptionalValue", "setValue"},
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap;get(Ljava/lang/Object;)Ljava/lang/Object;",
                    remap = false
            )
    )
    private Object vhaccelerator$get(ImmutableMap<?, ?> map, Object key) {
        if (map != null) {
            return map.get(key);
        }
        FastMapStateHolder<?> holder = (FastMapStateHolder<?>) (Object) this;
        return holder.getStateMap().getValue(holder.getStateIndex(), key);
    }

    @Redirect(
            method = "hasProperty",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap;containsKey(Ljava/lang/Object;)Z",
                    remap = false
            )
    )
    private boolean vhaccelerator$containsKey(ImmutableMap<?, ?> map, Object key) {
        if (map != null) {
            return map.containsKey(key);
        }
        return ((FastMapStateHolder<?>) (Object) this).getStateMap().getPropertySet().contains(key);
    }

    @Redirect(
            method = "getProperties",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableMap;keySet()Lcom/google/common/collect/ImmutableSet;",
                    remap = false
            )
    )
    private ImmutableSet<?> vhaccelerator$keySet(ImmutableMap<?, ?> map) {
        if (map != null) {
            return map.keySet();
        }
        return ((FastMapStateHolder<?>) (Object) this).getStateMap().getPropertySet();
    }

    @Redirect(
            method = "getValues",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/state/StateHolder;values:Lcom/google/common/collect/ImmutableMap;"
            )
    )
    private ImmutableMap<Property<?>, Comparable<?>> vhaccelerator$values(StateHolder<?, ?> self) {
        ImmutableMap<Property<?>, Comparable<?>> current = values;
        return current != null
                ? current
                : FastImmutableMapDefiner.makeMap((FastMapStateHolder<?>) (Object) this);
    }
}

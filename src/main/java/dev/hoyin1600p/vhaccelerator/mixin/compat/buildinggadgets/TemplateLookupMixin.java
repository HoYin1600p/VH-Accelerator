package dev.hoyin1600p.vhaccelerator.mixin.compat.buildinggadgets;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Map;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.BuildingGadgetsTemplateCompaction;
import dev.hoyin1600p.vhaccelerator.compat.buildinggadgets.PackedTemplatePositions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only fresh maps made by the reviewed template methods cross this boundary. */
@Pseudo
@Mixin(targets = "com.direwolf20.buildinggadgets.common.tainted.template.Template", remap = false)
public abstract class TemplateLookupMixin {
    /**
     * The template methods that only iterate the position map (one {@code entrySet().iterator()} call each) get
     * their entries streamed from the packed arrays when the template is packed. {@code withName},
     * {@code withNameAndAuthor} and {@code clearMaterials} pass the field value on unchanged, so copies share
     * the packed arrays. The layout verifier requires exactly these call sites.
     */
    @Redirect(method = {"serialize", "mirror", "replace", "normalize",
                    "rotate(Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/world/level/block/Rotation;)Lcom/direwolf20/buildinggadgets/common/tainted/template/Template;"},
            at = @At(value = "INVOKE", target =
                    "Lcom/google/common/collect/ImmutableSet;iterator()Lcom/google/common/collect/UnmodifiableIterator;"),
            require = 0, remap = false)
    private UnmodifiableIterator<?> vhaccelerator$packedEntries(ImmutableSet<?> entrySet) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        UnmodifiableIterator<?> entries = PackedTemplatePositions.entries((ImmutableSet) entrySet);
        return entries;
    }

    /** A paste's build view reads a packed template through a light map over the arrays instead of a rebuilt map. */
    @Redirect(method = "createViewInContext",
            at = @At(value = "INVOKE", target =
                    "Lcom/direwolf20/buildinggadgets/common/tainted/building/view/PositionalBuildView;createUnsafe(Lcom/direwolf20/buildinggadgets/common/tainted/building/view/BuildContext;Ljava/util/Map;Lcom/direwolf20/buildinggadgets/common/tainted/building/Region;)Lcom/direwolf20/buildinggadgets/common/tainted/building/view/PositionalBuildView;"),
            require = 0, remap = false)
    @Coerce
    private Object vhaccelerator$packedView(@Coerce Object context, Map<?, ?> map, @Coerce Object region) {
        return PackedTemplatePositions.createView(context, map, region);
    }

    @Inject(method = "deserialize(Lnet/minecraft/nbt/CompoundTag;Lcom/direwolf20/buildinggadgets/common/tainted/template/TemplateHeader;Z)Lcom/direwolf20/buildinggadgets/common/tainted/template/Template;",
            at = @At("RETURN"), require = 0, remap = false)
    private static void vhaccelerator$compactLoadedTemplate(CallbackInfoReturnable<Object> callback) {
        BuildingGadgetsTemplateCompaction.compactFreshResult(callback.getReturnValue());
    }

    @Inject(method = {
            "normalize()Lcom/direwolf20/buildinggadgets/common/tainted/template/Template;",
            "rotate(Lnet/minecraft/core/Direction$Axis;Lnet/minecraft/world/level/block/Rotation;)Lcom/direwolf20/buildinggadgets/common/tainted/template/Template;",
            "mirror(Lnet/minecraft/core/Direction$Axis;)Lcom/direwolf20/buildinggadgets/common/tainted/template/Template;"},
            at = @At("RETURN"), require = 0, remap = false)
    private void vhaccelerator$compactFreshTemplate(CallbackInfoReturnable<Object> callback) {
        if (callback.getReturnValue() != this) {
            // normalize() is also what serialize() calls on a throwaway copy; that one is not compacted.
            if ("normalize".equals(callback.getId())) {
                BuildingGadgetsTemplateCompaction.compactNormalizedResult(callback.getReturnValue());
            } else {
                BuildingGadgetsTemplateCompaction.compactFreshResult(callback.getReturnValue());
            }
        }
    }
}

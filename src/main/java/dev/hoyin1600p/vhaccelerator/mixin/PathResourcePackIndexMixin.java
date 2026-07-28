package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.resource.ImmutableModResourceIndex;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.PathResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathResourcePack.class)
public abstract class PathResourcePackIndexMixin {
    @Unique
    private ImmutableModResourceIndex
            vhaccelerator$resourceIndex;
    @Unique
    private boolean vhaccelerator$indexChecked;

    @Shadow(remap = false)
    public abstract Path getSource();

    @Shadow(remap = false)
    protected abstract Path resolve(String... paths);

    @Inject(
            method = "getResources",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$listIndexedResources(
            PackType type,
            String namespace,
            String prefix,
            int maxDepth,
            Predicate<String> filter,
            CallbackInfoReturnable<
                    Collection<ResourceLocation>> callback
    ) {
        ImmutableModResourceIndex index =
                vhaccelerator$index();
        if (index == null) {
            return;
        }
        Collection<ResourceLocation> resources =
                index.resources(
                        type,
                        namespace,
                        prefix,
                        maxDepth,
                        filter
                );
        if (resources != null) {
            callback.setReturnValue(resources);
        }
    }

    @Inject(
            method = "hasResource(Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vhaccelerator$findIndexedResource(
            String name,
            CallbackInfoReturnable<Boolean> callback
    ) {
        ImmutableModResourceIndex index =
                vhaccelerator$index();
        if (index == null) {
            return;
        }
        Boolean exists = index.existingResource(name);
        if (exists != null) {
            callback.setReturnValue(exists);
        }
    }

    @Unique
    private ImmutableModResourceIndex vhaccelerator$index() {
        if (!VHAcceleratorConfig.commonOptimizationsEnabled()
                || !VHAcceleratorConfig.COMMON
                .indexImmutableModResources
                .get()) {
            return null;
        }
        if (!vhaccelerator$indexChecked) {
            vhaccelerator$indexChecked = true;
            vhaccelerator$resourceIndex =
                    ImmutableModResourceIndex.create(
                            getSource(),
                            this::resolve
                    );
        }
        return vhaccelerator$resourceIndex;
    }
}

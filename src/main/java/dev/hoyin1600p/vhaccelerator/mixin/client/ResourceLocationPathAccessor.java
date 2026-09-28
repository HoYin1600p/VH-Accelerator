package dev.hoyin1600p.vhaccelerator.mixin.client;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ResourceLocation.class)
public interface ResourceLocationPathAccessor {
    @Accessor("path")
    @Mutable
    void vhaccelerator$setPath(String path);
}

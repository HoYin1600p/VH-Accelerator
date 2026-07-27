package dev.hoyin1600p.vhaccelerator.mixin.client;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagNetworkSerialization;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TagNetworkSerialization.NetworkPayload.class)
public interface TagNetworkPayloadAccessor {
    @Accessor("tags")
    Map<ResourceLocation, IntList> vhaccelerator$getTags();
}

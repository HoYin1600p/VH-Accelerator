package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Read access to the decoded tags of a tag network payload. The accessor mixin
 * for the payload extends this interface, so logic can cast the payload to it
 * without depending on the mixin package.
 */
public interface TagPayloadView {
    Map<ResourceLocation, IntList> vhaccelerator$getTags();
}

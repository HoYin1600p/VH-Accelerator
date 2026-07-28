package dev.hoyin1600p.vhaccelerator.client.compat.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.tag.CraftTweakerTagRegistry;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagManager;
import net.minecraft.tags.TagNetworkSerialization;

/**
 * Rebuilds CraftTweaker's independent tag view in parallel by registry.
 *
 * <p>Each worker owns its output map and only reads the immutable synchronized
 * network payload plus the active registries. CraftTweaker receives the
 * complete ordered result on the Minecraft thread after every worker joins.
 */
public final class ParallelCraftTweakerTagBinding {
    private ParallelCraftTweakerTagBinding() {
    }

    public static boolean tryBind(
            CraftTweakerTagRegistry tagRegistry,
            Map<
                    ResourceKey<? extends Registry<?>>,
                    TagNetworkSerialization.NetworkPayload
                    > payloads,
            CraftTweakerTagRegistry.BindContext context
    ) {
        if (payloads.size() < 2
                || AdaptiveJeiWorkScheduler.currentParallelism() <= 1) {
            return false;
        }

        long started = System.nanoTime();
        try {
            RegistryAccess registryAccess =
                    CraftTweakerAPI.getAccessibleElementsProvider()
                            .client()
                            .registryAccess();
            List<Map.Entry<
                    ResourceKey<? extends Registry<?>>,
                    TagNetworkSerialization.NetworkPayload
                    >> entries = new ArrayList<>(payloads.entrySet());
            List<CompletableFuture<TagManager.LoadResult<?>>> futures =
                    new ArrayList<>(entries.size());
            for (Map.Entry<
                    ResourceKey<? extends Registry<?>>,
                    TagNetworkSerialization.NetworkPayload
                    > entry : entries) {
                futures.add(AdaptiveJeiWorkScheduler.submitIsolated(
                        () -> decode(registryAccess, entry)
                ));
            }

            CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            ).join();

            List<TagManager.LoadResult<?>> results =
                    new ArrayList<>(futures.size());
            for (CompletableFuture<TagManager.LoadResult<?>> future : futures) {
                results.add(future.join());
            }

            Set<ResourceKey<? extends Registry<?>>> received =
                    new HashSet<>(payloads.keySet());
            registryAccess.registries().forEach(entry -> {
                if (!received.contains(entry.key())) {
                    results.add(emptyResult(entry.key()));
                }
            });

            tagRegistry.bind(results, context);
            VHAccelerator.LOGGER.info(
                    "Parallel CraftTweaker tag binding rebuilt {} synchronized "
                            + "registries with {} workers in {} ms",
                    entries.size(),
                    Math.min(
                            entries.size(),
                            AdaptiveJeiWorkScheduler.currentParallelism()
                    ),
                    formatMillis(System.nanoTime() - started)
            );
            return true;
        } catch (RuntimeException | LinkageError exception) {
            VHAccelerator.LOGGER.warn(
                    "Parallel CraftTweaker tag binding failed; retrying "
                            + "CraftTweaker's original sequential path",
                    exception
            );
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TagManager.LoadResult<?> decode(
            RegistryAccess registryAccess,
            Map.Entry<
                    ResourceKey<? extends Registry<?>>,
                    TagNetworkSerialization.NetworkPayload
                    > entry
    ) {
        ResourceKey registryKey = entry.getKey();
        Registry registry = registryAccess.registryOrThrow(registryKey);
        Map<ResourceLocation, Tag> tags = new HashMap<>();
        TagNetworkSerialization.deserializeTagsFromNetwork(
                registryKey,
                registry,
                entry.getValue(),
                (tagKey, holders) -> tags.put(
                        tagKey.location(),
                        new Tag(holders)
                )
        );
        return new TagManager.LoadResult(registryKey, tags);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static TagManager.LoadResult<?> emptyResult(
            ResourceKey<? extends Registry<?>> key
    ) {
        return new TagManager.LoadResult((ResourceKey) key, new HashMap<>());
    }

    private static String formatMillis(long nanos) {
        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }
}

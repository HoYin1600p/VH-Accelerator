package dev.hoyin1600p.launchfastertoo.mixin;

import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import dev.hoyin1600p.launchfastertoo.LaunchFasterTooConfig;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.Unit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
    @Unique
    private final Map<String, Map<Predicate<String>, Collection<ResourceLocation>>>
            launchfastertoo$listResourcesCache = new ConcurrentHashMap<>();

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    private void launchfastertoo$returnCachedResources(
            String prefix,
            Predicate<String> filter,
            CallbackInfoReturnable<Collection<ResourceLocation>> callback
    ) {
        if (!launchfastertoo$cacheEnabled()) {
            return;
        }

        Map<Predicate<String>, Collection<ResourceLocation>> prefixCache =
                launchfastertoo$listResourcesCache.get(prefix);
        if (prefixCache == null) {
            return;
        }

        Collection<ResourceLocation> cached = prefixCache.get(filter);
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "listResources", at = @At("RETURN"))
    private void launchfastertoo$cacheListedResources(
            String prefix,
            Predicate<String> filter,
            CallbackInfoReturnable<Collection<ResourceLocation>> callback
    ) {
        if (!launchfastertoo$cacheEnabled() || callback.getReturnValue() == null) {
            return;
        }

        Collection<ResourceLocation> stableCopy = List.copyOf(callback.getReturnValue());
        launchfastertoo$listResourcesCache
                .computeIfAbsent(prefix, ignored -> new ConcurrentHashMap<>())
                .put(filter, stableCopy);
    }

    @Inject(method = "createReload", at = @At("HEAD"))
    private void launchfastertoo$clearResourceListCache(
            Executor backgroundExecutor,
            Executor gameExecutor,
            CompletableFuture<Unit> waitedFor,
            List<PackResources> packs,
            CallbackInfoReturnable<ReloadInstance> callback
    ) {
        if (!launchfastertoo$listResourcesCache.isEmpty()) {
            LaunchFasterToo.LOGGER.debug(
                    "Clearing {} cached listResources prefixes",
                    launchfastertoo$listResourcesCache.size()
            );
            launchfastertoo$listResourcesCache.clear();
        }
    }

    @Unique
    private static boolean launchfastertoo$cacheEnabled() {
        return LaunchFasterTooConfig.COMMON.enableCommonOptimizations.get()
                && LaunchFasterTooConfig.COMMON.cacheResourceListing.get();
    }
}


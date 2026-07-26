package dev.hoyin1600p.vhaccelerator.mixin.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import iskallia.vault.config.Config;
import iskallia.vault.config.TooltipConfig;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Memoizes the result of Vault's otherwise linear tooltip-list scan while
 * preserving locale-specific overrides and cached misses.
 */
@Mixin(value = TooltipConfig.class, remap = false)
public abstract class TooltipConfigMixin {
    @Unique
    private final Map<String, Map<Item, Optional<String>>> vhaccelerator$tooltipCache =
            new ConcurrentHashMap<>();

    @Inject(method = "getTooltipString", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$readCachedTooltip(
            Item item,
            CallbackInfoReturnable<Optional<String>> callback
    ) {
        if (!vhaccelerator$cachingEnabled()) {
            return;
        }

        Map<Item, Optional<String>> localeCache =
                vhaccelerator$tooltipCache.get(Config.getClientLocale());
        if (localeCache != null) {
            Optional<String> cached = localeCache.get(item);
            if (cached != null) {
                callback.setReturnValue(cached);
            }
        }
    }

    @Inject(method = "getTooltipString", at = @At("RETURN"))
    private void vhaccelerator$cacheTooltip(
            Item item,
            CallbackInfoReturnable<Optional<String>> callback
    ) {
        if (!vhaccelerator$cachingEnabled()) {
            return;
        }

        vhaccelerator$tooltipCache
                .computeIfAbsent(Config.getClientLocale(), ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(item, callback.getReturnValue());
    }

    @Unique
    private static boolean vhaccelerator$cachingEnabled() {
        return VHAcceleratorClientConfig.VALUES.enableClientOptimizations.get()
                && VHAcceleratorClientConfig.VALUES.cacheVaultTooltips.get();
    }
}

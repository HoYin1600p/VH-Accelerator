package dev.hoyin1600p.vhaccelerator.mixin.compat.thermal;

import cofh.thermal.core.util.managers.dynamo.StirlingFuelManager;
import cofh.thermal.core.util.recipes.dynamo.StirlingFuel;
import cofh.thermal.lib.util.recipes.internal.IDynamoFuel;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.PersistentStirlingFuelCache;
import dev.hoyin1600p.vhaccelerator.client.compat.thermal.ThermalRefreshPhase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = StirlingFuelManager.class, remap = false)
public abstract class StirlingFuelManagerMixin {
    @Shadow(remap = false)
    protected List<StirlingFuel> convertedFuels;

    @Shadow(remap = false)
    protected abstract StirlingFuel convert(ItemStack stack, int energy);

    @Inject(
            method = "createConvertedRecipes",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void vhaccelerator$buildConvertedFuelsOnce(
            RecipeManager recipeManager,
            CallbackInfo callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.VALUES
                        .parallelThermalRecipeRefresh.get()) {
            return;
        }

        if (ThermalRefreshPhase.isApplyingRecipes()) {
            VHAccelerator.LOGGER.info(
                    "Deferred Thermal Stirling furnace-fuel conversion until "
                            + "synchronized item tags are applied"
            );
            callback.cancel();
            return;
        }

        long started = System.nanoTime();
        LoginStateFingerprint.Snapshot fingerprint =
                LoginStateFingerprint.current();
        if (fingerprint != null
                && vhaccelerator$restore(fingerprint, started)) {
            callback.cancel();
            return;
        }

        List<StirlingFuel> rebuilt = new ArrayList<>();
        List<PersistentStirlingFuelCache.FuelEntry> persistent =
                new ArrayList<>();
        int scanned = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            scanned++;
            ItemStack stack = new ItemStack(item);
            try {
                if (stack.getCapability(
                        CapabilityFluidHandler
                                .FLUID_HANDLER_ITEM_CAPABILITY
                ).isPresent() || item.hasContainerItem(stack)) {
                    continue;
                }

                int energy = ForgeHooks.getBurnTime(stack, null) * 10;
                if (energy < 1000) {
                    continue;
                }
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                if (itemId != null) {
                    persistent.add(
                            new PersistentStirlingFuelCache.FuelEntry(
                                    itemId.toString(),
                                    energy
                            )
                    );
                }
                if (vhaccelerator$getFuel(stack) == null) {
                    rebuilt.add(convert(stack, energy));
                }
            } catch (Exception exception) {
                VHAccelerator.LOGGER.error(
                        "Could not create a Thermal Stirling fuel for {}",
                        ForgeRegistries.ITEMS.getKey(item)
                );
            }
        }

        convertedFuels.clear();
        convertedFuels.addAll(rebuilt);
        VHAccelerator.LOGGER.info(
                "Built {} Thermal Stirling converted fuels from {} items "
                        + "with one burn-time query each in {} ms",
                rebuilt.size(),
                scanned,
                (System.nanoTime() - started) / 1_000_000L
        );
        if (fingerprint != null) {
            PersistentStirlingFuelCache.save(
                    fingerprint.serverKey(),
                    fingerprint.fuel(),
                    persistent
            );
        }
        callback.cancel();
    }

    private boolean vhaccelerator$restore(
            LoginStateFingerprint.Snapshot fingerprint,
            long started
    ) {
        PersistentStirlingFuelCache.LookupResult lookup =
                PersistentStirlingFuelCache.find(
                        fingerprint.serverKey(),
                        fingerprint.fuel()
                );
        if (!lookup.hit()) {
            VHAccelerator.LOGGER.info(
                    "Persistent Thermal Stirling fuel cache miss because {}; "
                            + "performing one active-world scan",
                    lookup.missReason()
            );
            return false;
        }

        List<StirlingFuel> restored =
                new ArrayList<>(lookup.cached().entries().size());
        try {
            for (PersistentStirlingFuelCache.FuelEntry entry
                    : lookup.cached().entries()) {
                ResourceLocation itemId =
                        ResourceLocation.tryParse(entry.itemId());
                if (itemId == null
                        || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                    return false;
                }
                ItemStack stack = new ItemStack(
                        ForgeRegistries.ITEMS.getValue(itemId)
                );
                if (vhaccelerator$getFuel(stack) == null) {
                    restored.add(convert(stack, entry.energy()));
                }
            }
        } catch (RuntimeException | LinkageError exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not restore Thermal Stirling fuels; rebuilding them",
                    exception
            );
            return false;
        }

        convertedFuels.clear();
        convertedFuels.addAll(restored);
        VHAccelerator.LOGGER.info(
                "Restored {} Thermal Stirling converted fuels from the "
                        + "validated persistent cache in {} ms",
                restored.size(),
                (System.nanoTime() - started) / 1_000_000L
        );
        return true;
    }

    private IDynamoFuel vhaccelerator$getFuel(ItemStack stack) {
        return ((SingleItemFuelManagerAccessor) (Object) this)
                .vhaccelerator$invokeGetFuel(stack);
    }
}

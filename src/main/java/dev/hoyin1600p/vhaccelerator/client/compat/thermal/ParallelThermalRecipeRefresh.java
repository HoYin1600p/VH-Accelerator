package dev.hoyin1600p.vhaccelerator.client.compat.thermal;

import cofh.thermal.lib.util.managers.IManager;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.compat.jei.AdaptiveJeiWorkScheduler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Refreshes independent Thermal machine managers concurrently and waits for
 * every manager before returning control to the Forge recipe/tag event.
 */
public final class ParallelThermalRecipeRefresh {
    private ParallelThermalRecipeRefresh() {
    }

    public static boolean tryRefresh(
            RecipeManager recipeManager,
            List<IManager> registeredManagers
    ) {
        if (recipeManager == null
                || registeredManagers.size() < 2
                || AdaptiveJeiWorkScheduler.currentParallelism() <= 1) {
            return false;
        }

        List<IManager> managers = List.copyOf(registeredManagers);
        long started = System.nanoTime();
        List<CompletableFuture<ManagerTiming>> futures =
                new ArrayList<>(managers.size());
        for (IManager manager : managers) {
            futures.add(AdaptiveJeiWorkScheduler.submitIsolated(
                    () -> refresh(manager, recipeManager)
            ));
        }

        try {
            CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            ).join();
            List<ManagerTiming> timings = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingLong(
                            ManagerTiming::elapsedNanos
                    ).reversed())
                    .toList();
            long elapsed = System.nanoTime() - started;
            VHAccelerator.LOGGER.info(
                    "Parallel Thermal recipe refresh completed {} managers "
                            + "with {} workers in {} ms [slowest {}: {} ms]",
                    managers.size(),
                    Math.min(
                            managers.size(),
                            AdaptiveJeiWorkScheduler.currentParallelism()
                    ),
                    formatMillis(elapsed),
                    timings.get(0).managerName(),
                    formatMillis(timings.get(0).elapsedNanos())
            );
            return true;
        } catch (RuntimeException | LinkageError exception) {
            /*
             * allOf completes only after every worker has terminated. It is
             * therefore safe for the caller to rerun Thermal's original loop.
             */
            VHAccelerator.LOGGER.warn(
                    "Parallel Thermal recipe refresh failed; retrying "
                            + "Thermal's original sequential path",
                    exception
            );
            return false;
        }
    }

    private static ManagerTiming refresh(
            IManager manager,
            RecipeManager recipeManager
    ) {
        long started = System.nanoTime();
        manager.refresh(recipeManager);
        return new ManagerTiming(
                manager.getClass().getSimpleName(),
                System.nanoTime() - started
        );
    }

    private static String formatMillis(long nanos) {
        return String.format(
                java.util.Locale.ROOT,
                "%.3f",
                nanos / 1_000_000.0
        );
    }

    private record ManagerTiming(
            String managerName,
            long elapsedNanos
    ) {
    }
}

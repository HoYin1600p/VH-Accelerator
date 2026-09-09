package dev.hoyin1600p.vhaccelerator.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/** Releases the serial disk lane before waiting for independent CPU preparation. */
public final class StagedPreload {
    private StagedPreload() { }

    public static <T> CompletableFuture<T> start(Supplier<T> read,
            Function<T, T> prepare, Executor io, Executor compute, boolean separate) {
        if (!separate) {
            return CompletableFuture.supplyAsync(() -> prepare.apply(read.get()), io);
        }
        return CompletableFuture.supplyAsync(read, io).thenApplyAsync(prepare, compute);
    }
}

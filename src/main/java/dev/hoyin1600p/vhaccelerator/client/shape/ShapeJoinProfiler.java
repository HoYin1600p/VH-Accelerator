package dev.hoyin1600p.vhaccelerator.client.shape;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.LaunchTimer;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ShapeJoinProfiler {
    private static final Set<IdentityJoin> SEEN =
            ConcurrentHashMap.newKeySet();
    private static final LongAdder CALLS = new LongAdder();
    private static final LongAdder REPEATS = new LongAdder();
    private static final LongAdder NANOS = new LongAdder();
    private static final LongAdder REPEAT_NANOS = new LongAdder();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();

    private ShapeJoinProfiler() {
    }

    public static Sample begin(
            VoxelShape first,
            VoxelShape second,
            BooleanOp operation
    ) {
        if (FINISHED.get()
                || LaunchTimer.isFinished()
                || !VHAcceleratorClientConfig.launchProfilingEnabled()) {
            return null;
        }
        boolean repeated = !SEEN.add(new IdentityJoin(
                first,
                second,
                operation
        ));
        return new Sample(System.nanoTime(), repeated);
    }

    public static void finish(Sample sample) {
        if (sample == null || FINISHED.get()) {
            return;
        }
        long elapsed = System.nanoTime() - sample.startedNanos;
        CALLS.increment();
        NANOS.add(elapsed);
        if (sample.repeated) {
            REPEATS.increment();
            REPEAT_NANOS.add(elapsed);
        }
    }

    public static void reportAndClear() {
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }
        if (VHAcceleratorClientConfig.launchProfilingEnabled()) {
            VHAccelerator.LOGGER.info(
                    "Voxel-shape joins: {} call(s), {} exact identity "
                            + "repeat(s), {} unique pair(s), {} ms total, "
                            + "{} ms in repeated work",
                    CALLS.sum(),
                    REPEATS.sum(),
                    SEEN.size(),
                    millis(NANOS.sum()),
                    millis(REPEAT_NANOS.sum())
            );
        }
        SEEN.clear();
    }

    private static String millis(long nanos) {
        return String.format(
                Locale.ROOT,
                "%.1f",
                nanos / 1_000_000.0
        );
    }

    public static final class Sample {
        private final long startedNanos;
        private final boolean repeated;

        private Sample(long startedNanos, boolean repeated) {
            this.startedNanos = startedNanos;
            this.repeated = repeated;
        }
    }

    private static final class IdentityJoin {
        private final VoxelShape first;
        private final VoxelShape second;
        private final BooleanOp operation;
        private final int hash;

        private IdentityJoin(
                VoxelShape first,
                VoxelShape second,
                BooleanOp operation
        ) {
            this.first = first;
            this.second = second;
            this.operation = operation;
            hash = 31 * (31 * System.identityHashCode(first)
                    + System.identityHashCode(second))
                    + System.identityHashCode(operation);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof IdentityJoin join
                    && first == join.first
                    && second == join.second
                    && operation == join.operation;
        }
    }
}

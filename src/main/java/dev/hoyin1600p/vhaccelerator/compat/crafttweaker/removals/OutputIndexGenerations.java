package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

/** Fresh, non-published index ownership around the original script runner. */
public final class OutputIndexGenerations {
    private static final ThreadLocal<GenerationOutputIndex> CURRENT = new ThreadLocal<>();

    private OutputIndexGenerations() { }

    @FunctionalInterface
    public interface Run {
        void run() throws Exception;
    }

    public static GenerationOutputIndex current() {
        return CURRENT.get();
    }

    public static void run(Run run) throws Exception {
        GenerationOutputIndex previous = CURRENT.get();
        if (previous != null) previous.invalidate();
        GenerationOutputIndex current = new GenerationOutputIndex();
        CURRENT.set(current);
        try {
            run.run();
        } catch (Exception | Error failure) {
            current.fail();
            throw failure;
        } finally {
            // Indexes never survive successful or failed runs. CT owns any
            // partial recipe mutations and its normal undo/reload semantics.
            current.close();
            if (previous == null) CURRENT.remove();
            else {
                previous.invalidate();
                CURRENT.set(previous);
            }
        }
    }
}

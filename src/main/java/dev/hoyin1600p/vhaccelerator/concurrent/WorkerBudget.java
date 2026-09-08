package dev.hoyin1600p.vhaccelerator.concurrent;

/** Uses all JVM-visible processors in one shared budget, not per feature. */
public record WorkerBudget(int total, int compute, int io) {
    public static WorkerBudget forProcessors(int processors) {
        int total = Math.max(0, processors);
        int io = total == 0 ? 0 : 1;
        return new WorkerBudget(total, total - io, io);
    }
}

package dev.hoyin1600p.vhaccelerator.concurrent;

/** One shared budget for VHA-owned workers, not an allowance per feature. */
public record WorkerBudget(int total, int compute, int io) {
    public static WorkerBudget forProcessors(int processors) {
        int total = Math.max(0, processors / 2);
        int io = total == 0 ? 0 : 1;
        return new WorkerBudget(total, total - io, io);
    }
}

package dev.hoyin1600p.vhaccelerator.startup;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded inclusive method observations; retains scalar metadata only, never receivers or arguments. */
public final class StartupOperationMetrics {
    private static final int LIMIT = 256;
    private final Map<Key, Totals> totals = new LinkedHashMap<>();
    private final Map<Long, List<Open>> active = new LinkedHashMap<>();
    private int openCount;
    private long dropped;
    private long mismatchedReturns;

    public synchronized void enter(String operation, String owner) {
        enter(operation, owner, null, null);
    }

    public synchronized void enter(String operation, String owner, String parentSpanId, Long reloadGeneration) {
        Thread thread = Thread.currentThread();
        List<Open> stack = active.get(thread.getId());
        String parentOperation = stack == null || stack.isEmpty() ? null : stack.get(stack.size() - 1).key.operation;
        Key key = new Key(operation, owner, thread.getId(), thread.getName(), parentSpanId, reloadGeneration, parentOperation);
        if (operation.length() > 512 || owner.length() > 512 || thread.getName().length() > 512) {
            dropped++;
            return;
        }
        if (openCount >= LIMIT || !totals.containsKey(key) && totals.size() >= LIMIT) {
            dropped++;
            return;
        }
        totals.computeIfAbsent(key, unused -> new Totals()).entered++;
        active.computeIfAbsent(thread.getId(), unused -> new ArrayList<>())
                .add(new Open(key, System.nanoTime(), cpu()));
        openCount++;
    }

    public synchronized void exit(String operation, Long payloadBytes) {
        exit(operation, payloadBytes, null);
    }

    public synchronized void exit(String operation, Long payloadBytes, Long items) {
        long thread = Thread.currentThread().getId();
        List<Open> stack = active.get(thread);
        if (stack == null || stack.isEmpty() || !stack.get(stack.size() - 1).key.operation.equals(operation)) {
            mismatchedReturns++;
            return;
        }
        Open open = stack.remove(stack.size() - 1);
        if (stack.isEmpty()) active.remove(thread);
        openCount--;
        Totals total = totals.get(open.key);
        total.completed++;
        long wall = Math.max(0, System.nanoTime() - open.start);
        total.inclusiveWallNanos += wall;
        total.maxWallNanos = Math.max(total.maxWallNanos, wall);
        Long endCpu = cpu();
        if (open.cpu != null && endCpu != null) {
            total.cpuMeasured++;
            total.inclusiveCpuNanos += Math.max(0, endCpu - open.cpu);
        }
        if (payloadBytes != null) {
            total.payloadMeasured++;
            total.payloadBytes += Math.max(0, payloadBytes);
        }
        if (items != null) {
            total.itemMeasured++;
            total.items += Math.max(0, items);
        }
    }

    public synchronized Map<String, Object> snapshot() {
        List<Map<String, Object>> rows = new ArrayList<>();
        totals.forEach((key, value) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("operation", key.operation);
            row.put("ownerClass", key.owner);
            row.put("threadId", key.thread);
            row.put("threadName", key.threadName);
            row.put("parentSpanId", key.parentSpanId);
            row.put("reloadGeneration", key.reloadGeneration);
            row.put("parentOperation", key.parentOperation);
            row.put("entered", value.entered);
            row.put("completed", value.completed);
            row.put("inclusiveWallNanos", value.inclusiveWallNanos);
            row.put("maxWallNanos", value.maxWallNanos);
            row.put("cpuMeasuredCount", value.cpuMeasured);
            row.put("inclusiveCpuNanos", value.cpuMeasured == 0 ? null : value.inclusiveCpuNanos);
            row.put("payloadMeasuredCount", value.payloadMeasured);
            row.put("reportedPayloadBytes", value.payloadMeasured == 0 ? null : value.payloadBytes);
            row.put("itemMeasuredCount", value.itemMeasured);
            row.put("reportedItems", value.itemMeasured == 0 ? null : value.items);
            rows.add(row);
        });
        return Map.of("schemaVersion", 1, "rows", rows, "unfinishedCount", openCount,
                "droppedOperations", dropped, "mismatchedReturns", mismatchedReturns,
                "complete", openCount == 0 && dropped == 0 && mismatchedReturns == 0,
                "interpretation", "inclusive method intervals overlap; payload bytes/items describe input or logical index sizes, not physical I/O or retained allocation; normal returns only");
    }

    private static Long cpu() {
        try {
            var bean = ManagementFactory.getThreadMXBean();
            if (!bean.isCurrentThreadCpuTimeSupported() || !bean.isThreadCpuTimeEnabled()) return null;
            long nanos = bean.getCurrentThreadCpuTime();
            return nanos < 0 ? null : nanos;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private record Key(String operation, String owner, long thread, String threadName,
                       String parentSpanId, Long reloadGeneration, String parentOperation) { }
    private record Open(Key key, long start, Long cpu) { }
    private static final class Totals {
        long entered, completed, inclusiveWallNanos, maxWallNanos, cpuMeasured, inclusiveCpuNanos;
        long payloadMeasured, payloadBytes, itemMeasured, items;
    }
}

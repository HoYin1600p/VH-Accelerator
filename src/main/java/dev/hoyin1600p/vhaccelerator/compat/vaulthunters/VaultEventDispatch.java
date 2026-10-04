package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Vault Hunters' {@code Event.invoke} copies its listener table on every
 * call: the priority map twice per priority, the list of listener groups, and
 * each group. Every vault modifier stack registers its own listeners, so a
 * long cake vault holds thousands of them, and {@code EntitySpawnEvent} is
 * posted for every {@code LivingSpawnEvent} (including each mob's despawn
 * check every tick) at all six priorities: at 600 cakes the copies alone were
 * a third of that event's server time. The same listeners now run from a
 * snapshot taken once per change of the table.
 *
 * <p>Order and error handling are unchanged. The original copies each
 * priority's groups when it reaches the priority and each group when it
 * reaches the group, so a listener registered or released while the event
 * runs is seen by later priorities and groups; once the table changes
 * mid-call, the rest of the call reads the live table the same way.
 */
public final class VaultEventDispatch {
    private VaultEventDispatch() {
    }

    /** Implemented on Vault's {@code Event} by the mixin. */
    public interface Owner {
        State vhaccelerator$dispatchState();
    }

    /** Per-event state kept by the mixin. */
    public static final class State {
        private final boolean clientEvent;
        private volatile long version;
        private volatile Snapshot snapshot;

        public State(boolean clientEvent) {
            this.clientEvent = clientEvent;
        }

        /** Client render events stay with the original code (and VRO). */
        public boolean clientEvent() {
            return clientEvent;
        }

        /**
         * Called after every register or release on the event. Vault's
         * ForgeEvent adds listeners straight into its parent's table, so its
         * own register method reports here too; the snapshot also compares
         * the table's sizes on every post, so a writer that reports nothing
         * still cannot leave a new listener owner out.
         */
        public void changed() {
            synchronized (this) {
                version++;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void invoke(State state, Map<Integer, Map<Object, List<Consumer>>> listeners, Object data) {
        long version = state.version;
        Snapshot snapshot = state.snapshot;
        if (snapshot == null || snapshot.version != version || !snapshot.matches(listeners)) {
            snapshot = Snapshot.build(version, listeners);
            state.snapshot = snapshot;
        }
        for (int p = 0; p < snapshot.priorities.length; p++) {
            List[] groups;
            if (state.version == version) {
                groups = snapshot.groups[p];
            } else {
                Map<Object, List<Consumer>> byOwner = listeners.get(snapshot.priorities[p]);
                if (byOwner == null) {
                    continue;
                }
                groups = new ArrayList<>(byOwner.values()).toArray(new List[0]);
            }
            for (int g = 0; g < groups.length; g++) {
                Object[] consumers = state.version == version && groups == snapshot.groups[p]
                        ? snapshot.consumers[p][g]
                        : groups[g].toArray();
                for (Object consumer : consumers) {
                    try {
                        ((Consumer) consumer).accept(data);
                    } catch (Exception exception) {
                        VHAccelerator.LOGGER.error("Vault event consumer failed", exception);
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private record Snapshot(long version, Integer[] priorities, List[][] groups, Object[][][] consumers,
                            Map[] owners) {
        /** Same priorities and listener owners as when taken; a few size reads per post. */
        boolean matches(Map<Integer, Map<Object, List<Consumer>>> listeners) {
            if (listeners.size() != priorities.length) {
                return false;
            }
            for (int p = 0; p < owners.length; p++) {
                if (owners[p].size() != groups[p].length) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        static Snapshot build(long version, Map<Integer, Map<Object, List<Consumer>>> listeners) {
            TreeMap<Integer, Map<Object, List<Consumer>>> ordered = new TreeMap<>(Collections.reverseOrder());
            synchronized (listeners) {
                ordered.putAll(listeners);
            }
            Integer[] priorities = ordered.keySet().toArray(new Integer[0]);
            List[][] groups = new List[priorities.length][];
            Object[][][] consumers = new Object[priorities.length][][];
            Map[] owners = new Map[priorities.length];
            for (int p = 0; p < priorities.length; p++) {
                Map<Object, List<Consumer>> byOwner = ordered.get(priorities[p]);
                owners[p] = byOwner;
                synchronized (byOwner) {
                    groups[p] = byOwner.values().toArray(new List[0]);
                }
                consumers[p] = new Object[groups[p].length][];
                for (int g = 0; g < groups[p].length; g++) {
                    consumers[p][g] = groups[p][g].toArray();
                }
            }
            return new Snapshot(version, priorities, groups, consumers, owners);
        }
    }
}

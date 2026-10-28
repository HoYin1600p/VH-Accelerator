package dev.hoyin1600p.vhaccelerator.registry;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.logging.log4j.LogManager;

/**
 * Sizes the block-state id map once, before Forge's first fill of it.
 *
 * <p>Forge numbers every block state by adding it to {@code Block.BLOCK_STATE_REGISTRY}, a vanilla
 * {@code IdMapper} that starts with room for 512 entries: an identity hash map (state to id) and an array list
 * (id to state) that double over and over while about 1.5 million states are added, re-hashing every state each
 * time. The first fill of that map sizes the map's hash table and list once, from the states the blocks
 * declare. Only capacities change: states are added by the same {@code IdMapper.add} calls in the same order, so
 * every id, the id order and the lookups are what Forge's own fill produces.
 *
 * <p>It acts on an exact layout only: a Forge state map (a subclass of {@code GameData}'s clearable map), still
 * empty, whose table is a plain {@code Object2IntOpenCustomHashMap} with vanilla's identity strategy and a
 * default of -1 and whose list is a plain {@code ArrayList}. It acts once per map, so the refill after Forge
 * clears the map (which keeps its capacity) is left alone. Anything else, including any failure, keeps the
 * original behaviour.
 */
public final class StateIdMapPresize {
    /** Forge's private clearable map the state map is an anonymous subclass of. */
    static final String FORGE_MAP_SUPERCLASS = "net.minecraftforge.registries.GameData$ClearableObjectIntIdentityMap";
    /** Forge's slave-map key of the state id map in the block registry ({@code GameData.BLOCKSTATE_TO_ID}). */
    static final ResourceLocation STATE_MAP_KEY = new ResourceLocation("minecraft:blockstatetoid");
    /** Below this a presize gains nothing. */
    static final long MIN_STATES = 4096;
    /** Above this something is wrong (the table would need more than 2^26 slots). */
    static final long MAX_STATES = 1L << 25;

    private StateIdMapPresize() {
    }

    /** What the {@code IdMapper} mixin adds to the vanilla class. */
    public interface Slots {
        /** The state-to-id table, vanilla's {@code tToId}. */
        Object2IntMap<?> vhaccelerator$table();

        /** The id-to-state list, vanilla's {@code idToT}. */
        List<?> vhaccelerator$list();

        /** The next free id, vanilla's {@code nextId}. */
        int vhaccelerator$nextId();

        /** Replaces the (empty) table. */
        void vhaccelerator$replaceTable(Object2IntMap<?> table);

        /** True once a presize was attempted on this map, whatever its outcome. */
        boolean vhaccelerator$presizeAttempted();

        void vhaccelerator$markPresizeAttempted();
    }

    /** Called before the block registry's bake: counts the declared states and presizes the registry's id map. */
    public static void beforeBlockBake(ForgeRegistry<?> registry) {
        try {
            ResourceLocation name = registry.getRegistryName();
            if (name == null || !"minecraft".equals(name.getNamespace()) || !"block".equals(name.getPath())) {
                return;
            }
            Object mapper = registry.getSlaveMap(STATE_MAP_KEY, Object.class);
            if (!(mapper instanceof Slots slots) || slots.vhaccelerator$presizeAttempted()) {
                return;
            }
            long states = 0;
            for (Object entry : registry) {
                if (entry instanceof Block block) {
                    states += block.getStateDefinition().getPossibleStates().size();
                }
            }
            long start = System.nanoTime();
            if (presize(mapper, states)) {
                LogManager.getLogger("VH Accelerator").info(
                        "Sized the block-state id map for {} states in {} ms",
                        states, (System.nanoTime() - start) / 1_000_000L);
            }
        } catch (Throwable failure) {
            LogManager.getLogger("VH Accelerator").debug("Block-state id map presize skipped", failure);
        }
    }

    /** Presizes a Forge state map for {@code expectedStates} states; true when the map was resized. */
    public static boolean presize(Object mapper, long expectedStates) {
        return presize(mapper, expectedStates, FORGE_MAP_SUPERCLASS);
    }

    static boolean presize(Object mapper, long expectedStates, String requiredSuperclass) {
        if (!(mapper instanceof Slots slots) || slots.vhaccelerator$presizeAttempted()) {
            return false;
        }
        slots.vhaccelerator$markPresizeAttempted();
        if (expectedStates < MIN_STATES || expectedStates > MAX_STATES) {
            return false;
        }
        Class<?> superclass = mapper.getClass().getSuperclass();
        if (superclass == null || !requiredSuperclass.equals(superclass.getName())) {
            return false;
        }
        Object2IntMap<?> table = slots.vhaccelerator$table();
        List<?> list = slots.vhaccelerator$list();
        if (table == null || list == null
                || table.getClass() != Object2IntOpenCustomHashMap.class || list.getClass() != ArrayList.class) {
            return false;
        }
        if (slots.vhaccelerator$nextId() != 0 || !table.isEmpty() || !list.isEmpty()) {
            return false;
        }
        Object2IntOpenCustomHashMap<?> open = (Object2IntOpenCustomHashMap<?>) table;
        if (open.strategy() != Util.identityStrategy() || open.defaultReturnValue() != -1) {
            return false;
        }
        int expected = (int) expectedStates;
        Object2IntOpenCustomHashMap<Object> sized = new Object2IntOpenCustomHashMap<>(expected, Util.identityStrategy());
        sized.defaultReturnValue(-1);
        ((ArrayList<?>) list).ensureCapacity(expected);
        slots.vhaccelerator$replaceTable(sized);
        return true;
    }
}

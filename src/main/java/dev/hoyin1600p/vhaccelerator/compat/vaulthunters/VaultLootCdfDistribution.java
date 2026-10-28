package dev.hoyin1600p.vhaccelerator.compat.vaulthunters;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;

/** A computed Vault loot distribution, readable by packed frequency key. */
public interface VaultLootCdfDistribution {
    /** The value Vault's own map would return for {@code packed}, including its default for absent keys. */
    double get(long packed);

    /** The map {@code CDF.getMap()} returns: the computed map itself, or a read-only equal view. */
    Long2DoubleMap map();

    /** Whether this holds the compact array instead of the computed hash map. */
    boolean compact();
}

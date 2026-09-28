package dev.hoyin1600p.vhaccelerator.client.model;

/** A bakery that may skip certified block-state graphs; see {@link BlockGraphSkipSession}. */
public interface BlockGraphOwner {
    /** The active session, or null. */
    BlockGraphSkipSession vhaccelerator$blockGraphSession();
}

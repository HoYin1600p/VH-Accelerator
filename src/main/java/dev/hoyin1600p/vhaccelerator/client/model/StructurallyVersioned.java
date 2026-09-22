package dev.hoyin1600p.vhaccelerator.client.model;

/**
 * A map whose key-set changes are counted. A negative version means the
 * owner cannot vouch for every mutation route and callers must not cache.
 */
public interface StructurallyVersioned {
    long structuralVersion();
}

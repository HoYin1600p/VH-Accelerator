package dev.hoyin1600p.vhaccelerator.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Shared message digest factories. */
public final class Digests {
    private Digests() {
    }

    /** A new SHA-256 digest; every Java runtime ships one, so a failure is a broken runtime. */
    public static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

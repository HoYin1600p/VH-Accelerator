package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

/** An asset-affecting input changed or could not be read while it was being fingerprinted. */
final class UnstableFingerprintInputException
        extends RuntimeException {
    UnstableFingerprintInputException(String message) {
        super(message);
    }

    UnstableFingerprintInputException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}

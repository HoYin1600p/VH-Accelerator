package dev.hoyin1600p.vhaccelerator.util;

import java.nio.file.Path;

/** Path text helpers for fingerprints and manifests that must not depend on the OS separator. */
public final class PathNames {
    private PathNames() {
    }

    /** {@code path} relative to {@code root}, always with forward slashes. */
    public static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}

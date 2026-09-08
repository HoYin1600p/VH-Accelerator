package dev.hoyin1600p.vhaccelerator.client.cache;

import java.nio.file.Path;
import java.util.Locale;

final class ActiveModFilePolicy {
    private ActiveModFilePolicy() { }
    static boolean isJar(Path path) { return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"); }
}

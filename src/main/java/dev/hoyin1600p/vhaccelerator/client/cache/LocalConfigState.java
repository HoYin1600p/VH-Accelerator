package dev.hoyin1600p.vhaccelerator.client.cache;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Reload notifications invalidate only the affected file's retained content hash. */
public final class LocalConfigState {
    private static final AtomicLong REVISION = new AtomicLong();
    private static final ConcurrentHashMap<Path, FileDigest> FILES = new ConcurrentHashMap<>();

    private LocalConfigState() { }
    public static long revision() { return REVISION.get(); }

    public static synchronized void changed(Path file) {
        REVISION.incrementAndGet();
        if (file == null) { FILES.clear(); }
        else { FILES.remove(file.toAbsolutePath().normalize()); }
        // Readers straddling removal must not publish a stale cache entry.
        REVISION.incrementAndGet();
    }
    public static boolean isStable(long revision) { return (revision & 1) == 0 && revision == REVISION.get(); }

    static String digest(Path path) throws IOException {
        Path key = path.toAbsolutePath().normalize();
        long revision = REVISION.get();
        long size = Files.size(key);
        FileTime modified = Files.getLastModifiedTime(key);
        FileDigest previous = FILES.get(key);
        if (previous != null && previous.size == size && previous.modified.equals(modified)) {
            return previous.digest;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(key)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0;) { digest.update(buffer, 0, read); }
            }
            if (size != Files.size(key) || !modified.equals(Files.getLastModifiedTime(key))) {
                throw new IOException("Config changed while its fingerprint was being read");
            }
            String value = HexFormat.of().formatHex(digest.digest());
            FILES.compute(key, (ignored, cached) -> isStable(revision)
                    ? new FileDigest(size, modified, value) : cached);
            return value;
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private record FileDigest(long size, FileTime modified, String digest) { }
}

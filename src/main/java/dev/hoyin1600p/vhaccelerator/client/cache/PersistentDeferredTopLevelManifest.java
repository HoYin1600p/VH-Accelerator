package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Persists the certified inventory top-level models of an eager launch and
 * the complete atlas material list of each model's graph.
 *
 * <p>Only identifiers are stored; no live model object is serialized. A
 * manifest is trusted only when its magic, version, fingerprint, counts,
 * identifiers, canonical ordering, material constraints, and SHA-256 digest
 * all validate. Anything else reads as absent, and the launch loads every
 * model eagerly.</p>
 */
public final class PersistentDeferredTopLevelManifest {
    static final int MAGIC = 0x56484454;
    static final int FORMAT_VERSION = 1;
    static final int MAX_ENTRIES = 100_000;
    static final int MAX_MATERIALS_PER_ENTRY = 512;
    static final int MAX_TOTAL_MATERIALS = 2_000_000;
    static final int MAX_FINGERPRINT_LENGTH = 4_096;
    static final int MAX_IDENTIFIER_LENGTH = 512;
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int DIGEST_BYTES = 32;
    private static final String FILE_NAME =
            "deferred-item-top-level-v1.bin.gz";
    public static final String BLOCK_ATLAS =
            "minecraft:textures/atlas/blocks.png";
    public static final String MISSING_TEXTURE = "minecraft:missingno";
    public static final String INVENTORY_SUFFIX = "#inventory";

    private PersistentDeferredTopLevelManifest() {
    }

    /**
     * Marks a ModelBakery whose material collection adds the manifest's
     * materials before atlas stitching. Without it nothing is skipped.
     */
    public interface MaterialSink {
    }

    public record MaterialId(String atlas, String texture)
            implements Comparable<MaterialId> {
        @Override
        public int compareTo(MaterialId other) {
            int atlasOrder = atlas.compareTo(other.atlas);
            return atlasOrder != 0
                    ? atlasOrder
                    : texture.compareTo(other.texture);
        }
    }

    /** An immutable, validated manifest. */
    public static final class Manifest {
        private final String fingerprint;
        private final Map<String, List<MaterialId>> entries;
        private final int totalMaterials;

        private Manifest(
                String fingerprint,
                Map<String, List<MaterialId>> entries,
                int totalMaterials
        ) {
            this.fingerprint = fingerprint;
            this.entries = entries;
            this.totalMaterials = totalMaterials;
        }

        public String fingerprint() {
            return fingerprint;
        }

        public boolean matches(String currentFingerprint) {
            return currentFingerprint != null
                    && currentFingerprint.equals(fingerprint);
        }

        public int size() {
            return entries.size();
        }

        public int totalMaterials() {
            return totalMaterials;
        }

        public boolean contains(String modelKey) {
            return entries.containsKey(modelKey);
        }

        /** Sorted material list, or {@code null} for an unknown key. */
        public List<MaterialId> materials(String modelKey) {
            return entries.get(modelKey);
        }

        public Set<String> keys() {
            return entries.keySet();
        }

        /** Distinct materials of the given keys; unknown keys add nothing. */
        public Set<MaterialId> union(Collection<String> modelKeys) {
            TreeSet<MaterialId> union = new TreeSet<>();
            for (String key : modelKeys) {
                List<MaterialId> materials = entries.get(key);
                if (materials != null) {
                    union.addAll(materials);
                }
            }
            return Collections.unmodifiableSet(union);
        }
    }

    /**
     * Collects certified entries. An entry that is malformed or incomplete
     * is rejected, never truncated.
     */
    public static final class Builder {
        private final TreeMap<String, List<MaterialId>> entries =
                new TreeMap<>();
        private long totalMaterials;
        private int rejected;

        public boolean add(String modelKey, Collection<MaterialId> materials) {
            List<MaterialId> canonical = canonicalEntry(modelKey, materials);
            if (canonical == null
                    || entries.containsKey(modelKey)
                    || entries.size() >= MAX_ENTRIES
                    || totalMaterials + canonical.size()
                            > MAX_TOTAL_MATERIALS) {
                rejected++;
                return false;
            }
            entries.put(modelKey, canonical);
            totalMaterials += canonical.size();
            return true;
        }

        public int rejected() {
            return rejected;
        }

        public int size() {
            return entries.size();
        }

        /** Null when the fingerprint is unusable or nothing was certified. */
        public Manifest build(String fingerprint) {
            if (!validFingerprint(fingerprint) || entries.isEmpty()) {
                return null;
            }
            return new Manifest(
                    fingerprint,
                    Collections.unmodifiableMap(new TreeMap<>(entries)),
                    (int) totalMaterials
            );
        }
    }

    /** Sorted, distinct materials for a valid entry, else {@code null}. */
    static List<MaterialId> canonicalEntry(
            String modelKey,
            Collection<MaterialId> materials
    ) {
        if (!validModelKey(modelKey)
                || materials == null
                || materials.isEmpty()) {
            return null;
        }
        TreeSet<MaterialId> sorted = new TreeSet<>();
        for (MaterialId material : materials) {
            if (!validMaterial(material)) {
                return null;
            }
            sorted.add(material);
        }
        if (sorted.size() > MAX_MATERIALS_PER_ENTRY) {
            return null;
        }
        return List.copyOf(sorted);
    }

    static boolean validMaterial(MaterialId material) {
        return material != null
                && BLOCK_ATLAS.equals(material.atlas())
                && validLocation(material.texture())
                && !MISSING_TEXTURE.equals(material.texture());
    }

    static boolean validFingerprint(String fingerprint) {
        return fingerprint != null
                && !fingerprint.isEmpty()
                && fingerprint.length() <= MAX_FINGERPRINT_LENGTH;
    }

    /** {@code namespace:path#inventory} with ResourceLocation characters. */
    static boolean validModelKey(String key) {
        return key != null
                && key.endsWith(INVENTORY_SUFFIX)
                && validLocation(key.substring(
                        0,
                        key.length() - INVENTORY_SUFFIX.length()
                ));
    }

    /** Explicit {@code namespace:path}, as written by ResourceLocation. */
    static boolean validLocation(String location) {
        if (location == null || location.length() > MAX_IDENTIFIER_LENGTH) {
            return false;
        }
        int separator = location.indexOf(':');
        if (separator <= 0 || separator == location.length() - 1) {
            return false;
        }
        for (int index = 0; index < location.length(); index++) {
            char c = location.charAt(index);
            boolean common = c == '_' || c == '-' || c == '.'
                    || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9';
            if (index < separator ? !common
                    : index > separator && !common && c != '/') {
                return false;
            }
        }
        return true;
    }

    /** Writes the uncompressed canonical form followed by its digest. */
    public static void write(Manifest manifest, OutputStream target)
            throws IOException {
        MessageDigest digest = sha256();
        DigestOutputStream digesting = new DigestOutputStream(target, digest);
        DataOutputStream output = new DataOutputStream(digesting);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        output.writeUTF(manifest.fingerprint);
        output.writeInt(manifest.entries.size());
        output.writeInt(manifest.totalMaterials);
        for (Map.Entry<String, List<MaterialId>> entry
                : manifest.entries.entrySet()) {
            output.writeUTF(entry.getKey());
            output.writeInt(entry.getValue().size());
            for (MaterialId material : entry.getValue()) {
                output.writeUTF(material.atlas());
                output.writeUTF(material.texture());
            }
        }
        output.flush();
        digesting.on(false);
        output.write(digest.digest());
        output.flush();
    }

    /**
     * Reads a manifest written by {@link #write}. Returns {@code null} for
     * another format version; throws for anything corrupt or incomplete.
     */
    public static Manifest read(InputStream source) throws IOException {
        MessageDigest digest = sha256();
        DigestInputStream digesting = new DigestInputStream(source, digest);
        DataInputStream input = new DataInputStream(digesting);
        if (input.readInt() != MAGIC) {
            throw new IOException("Not a deferred top-level manifest");
        }
        if (input.readInt() != FORMAT_VERSION) {
            return null;
        }
        String fingerprint = input.readUTF();
        if (!validFingerprint(fingerprint)) {
            throw new IOException("Invalid manifest fingerprint");
        }
        int count = input.readInt();
        int declaredMaterials = input.readInt();
        if (count <= 0 || count > MAX_ENTRIES
                || declaredMaterials <= 0
                || declaredMaterials > MAX_TOTAL_MATERIALS) {
            throw new IOException("Invalid manifest counts");
        }
        TreeMap<String, List<MaterialId>> entries = new TreeMap<>();
        String previousKey = null;
        long totalMaterials = 0L;
        for (int index = 0; index < count; index++) {
            String key = input.readUTF();
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw new IOException("Manifest keys are not canonical");
            }
            previousKey = key;
            int materialCount = input.readInt();
            if (materialCount <= 0
                    || materialCount > MAX_MATERIALS_PER_ENTRY) {
                throw new IOException("Invalid manifest material count");
            }
            totalMaterials += materialCount;
            if (totalMaterials > declaredMaterials) {
                throw new IOException("Manifest exceeds its material count");
            }
            List<MaterialId> materials = new ArrayList<>(materialCount);
            MaterialId previous = null;
            for (int materialIndex = 0;
                    materialIndex < materialCount;
                    materialIndex++) {
                MaterialId material = new MaterialId(
                        input.readUTF(),
                        input.readUTF()
                );
                if (previous != null && previous.compareTo(material) >= 0) {
                    throw new IOException(
                            "Manifest materials are not canonical"
                    );
                }
                previous = material;
                materials.add(material);
            }
            List<MaterialId> canonical = canonicalEntry(key, materials);
            if (canonical == null) {
                throw new IOException("Invalid manifest entry " + key);
            }
            entries.put(key, canonical);
        }
        if (totalMaterials != declaredMaterials) {
            throw new IOException("Manifest material count mismatch");
        }
        digesting.on(false);
        byte[] expected = digest.digest();
        byte[] stored = new byte[DIGEST_BYTES];
        input.readFully(stored);
        if (!MessageDigest.isEqual(expected, stored)) {
            throw new IOException("Manifest checksum mismatch");
        }
        if (input.read() != -1) {
            throw new IOException("Manifest contains trailing data");
        }
        return new Manifest(
                fingerprint,
                Collections.unmodifiableMap(entries),
                declaredMaterials
        );
    }

    /** Reads the persisted manifest off the loading thread. */
    public static CompletableFuture<Manifest> readAsync() {
        return CompletableFuture.supplyAsync(
                PersistentDeferredTopLevelManifest::readFile,
                SharedWorkers.io()
        );
    }

    public static void writeAsync(Manifest manifest) {
        CompletableFuture.runAsync(
                () -> writeFile(manifest),
                SharedWorkers.io()
        );
    }

    private static Path cacheFile() {
        return FMLPaths.GAMEDIR.get()
                .resolve("cache")
                .resolve("vhaccelerator")
                .resolve("client-assets")
                .resolve(FILE_NAME);
    }

    private static Manifest readFile() {
        Path file = cacheFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IOException("Manifest file is too large");
            }
            try (InputStream input = new GZIPInputStream(
                    new BufferedInputStream(Files.newInputStream(file))
            )) {
                return read(input);
            }
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not read the deferred item top-level manifest; "
                            + "every model will load eagerly",
                    failure
            );
            return null;
        }
    }

    private static void writeFile(Manifest manifest) {
        Path file = cacheFile();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = new GZIPOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(temporary)
                    )
            )) {
                write(manifest, output);
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            VHAccelerator.LOGGER.info(
                    "Saved {} certified inventory top-level models ({} "
                            + "materials) to the deferred model manifest",
                    manifest.size(),
                    manifest.totalMaterials()
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the deferred item top-level manifest",
                    failure
            );
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // An incomplete temporary file is never read.
            }
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }
}

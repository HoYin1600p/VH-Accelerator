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
 * Persists certified Minecraft block-state keys and the complete block-atlas
 * material list of each key for a later warm-launch experiment.
 *
 * <p>Only identifiers are stored; no model object is serialized. Nothing in
 * the current launch reads or writes this file. A manifest is trusted only
 * when its magic, version, fingerprint, counts, identifiers, canonical
 * ordering, material constraints, and SHA-256 digest all validate. Another
 * format version reads as absent. Any other failure leaves block states
 * eager.</p>
 *
 * <p>Accepted keys are {@code minecraft} model-location strings whose
 * variant is a nonempty {@code name=value} property list. Inventory
 * variants, empty variants, and every other namespace are rejected.
 * Property order inside a key is significant and is not rewritten. Entry
 * keys and material lists are stored in canonical order.</p>
 */
public final class PersistentDeferredBlockStateManifest {
    /** File magic {@code VHBS}, distinct from the inventory manifest. */
    static final int MAGIC = 0x56484253;
    static final int FORMAT_VERSION = 1;
    static final int MAX_ENTRIES = 100_000;
    static final int MAX_MATERIALS_PER_ENTRY = 512;
    static final int MAX_TOTAL_MATERIALS = 2_000_000;
    static final int MAX_FINGERPRINT_LENGTH = 4_096;
    static final int MAX_IDENTIFIER_LENGTH = 512;
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int DIGEST_BYTES = 32;
    private static final String FILE_NAME =
            "deferred-block-state-v1.bin.gz";
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String INVENTORY_VARIANT = "inventory";
    public static final String BLOCK_ATLAS =
            "minecraft:textures/atlas/blocks.png";
    public static final String MISSING_TEXTURE = "minecraft:missingno";

    private PersistentDeferredBlockStateManifest() {
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

        /**
         * Whether this manifest was certified for the supplied fingerprint.
         * A mismatch must not be used for warm block-state loading.
         */
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
     * Collects certified entries. Malformed, duplicate, or over-limit input
     * is rejected whole and left out; accepted entries stay canonical.
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

    /**
     * {@code minecraft:path#name=value(,name=value)*}. Inventory and empty
     * variants are rejected. Blocks with no properties are outside this
     * stage because their variant is empty.
     */
    static boolean validModelKey(String key) {
        if (key == null || key.length() > MAX_IDENTIFIER_LENGTH) {
            return false;
        }
        int hash = key.indexOf('#');
        if (hash <= 0 || hash != key.lastIndexOf('#')) {
            return false;
        }
        String location = key.substring(0, hash);
        return validLocation(location)
                && location.startsWith(MINECRAFT_PREFIX)
                && validVariant(key.substring(hash + 1));
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

    private static boolean validVariant(String variant) {
        if (variant == null
                || variant.isEmpty()
                || INVENTORY_VARIANT.equals(variant)) {
            return false;
        }
        TreeSet<String> names = new TreeSet<>();
        int start = 0;
        while (start < variant.length()) {
            int comma = variant.indexOf(',', start);
            int end = comma < 0 ? variant.length() : comma;
            if (end == start) {
                return false;
            }
            String property = variant.substring(start, end);
            int equals = property.indexOf('=');
            if (equals <= 0
                    || equals != property.lastIndexOf('=')
                    || equals == property.length() - 1) {
                return false;
            }
            String name = property.substring(0, equals);
            String value = property.substring(equals + 1);
            if (!validPropertyName(name)
                    || !validPropertyValue(value)
                    || !names.add(name)) {
                return false;
            }
            if (comma < 0) {
                return true;
            }
            start = comma + 1;
        }
        return false;
    }

    private static boolean validPropertyName(String name) {
        if (name.isEmpty() || !isLetter(name.charAt(0))) {
            return false;
        }
        for (int index = 1; index < name.length(); index++) {
            char c = name.charAt(index);
            if (!isLetter(c) && !isDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    /** Enum-style tokens, or a canonical non-negative integer. */
    private static boolean validPropertyValue(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        if (isDigit(first)) {
            if (first == '0') {
                return value.length() == 1;
            }
            for (int index = 1; index < value.length(); index++) {
                if (!isDigit(value.charAt(index))) {
                    return false;
                }
            }
            return true;
        }
        return validPropertyName(value);
    }

    private static boolean isLetter(char c) {
        return c >= 'a' && c <= 'z';
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
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
            throw new IOException("Not a deferred block-state manifest");
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
                PersistentDeferredBlockStateManifest::readFile,
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
                    "Could not read the deferred block-state manifest; "
                            + "block states will stay eager",
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
                    "Saved {} certified minecraft block-state models ({} "
                            + "materials) to the deferred block-state "
                            + "manifest",
                    manifest.size(),
                    manifest.totalMaterials()
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not save the deferred block-state manifest",
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


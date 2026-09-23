package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
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
import java.util.HashMap;
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
 * Persists certified Minecraft block-state keys, the complete block-atlas
 * material list of each key, and each key's model-group code for a later
 * warm-launch experiment.
 *
 * <p>Only identifiers and small integers are stored; no model object is
 * serialized. Only the opt-in initial-launch recorder writes this file, and
 * nothing reads it yet. A manifest is trusted only when its magic, version,
 * fingerprint, counts, identifiers, block coverage, key-to-block
 * association, group codes, canonical ordering, material constraints, and
 * SHA-256 digest all validate. Another format version, including v1, reads
 * as absent. Any other failure leaves block states eager.</p>
 *
 * <p>Group codes mirror {@code ModelBakery}'s model groups within one
 * block: {@link #UNGROUPED} ({@code -1}, the map default), {@link
 * #NON_MODEL_GROUP} ({@code 0}, non-{@code MODEL} render shape), or a
 * positive label. Positive labels keep only the equality partition of the
 * bakery's IDs: within a block they are renumbered from 1 in key order, so
 * unstable global IDs are never stored. Each block also records its full
 * possible-state count and how many of its states were recorded. A block
 * whose recorded count is below its state count is partial; nothing may
 * treat a partial block as safe to skip.</p>
 *
 * <p>A gzip cache read may consume at most
 * {@link #MAX_UNCOMPRESSED_BYTES} inflated bytes. Oversize or malformed
 * cache input is absent. Modified-UTF-8 lengths are rejected before
 * that payload is allocated.</p>
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
    static final int FORMAT_VERSION = 2;
    /** Ungrouped state: the bakery's model-group map default. */
    public static final int UNGROUPED = -1;
    /** State whose render shape is not {@code MODEL}. */
    public static final int NON_MODEL_GROUP = 0;
    static final int MAX_ENTRIES = 100_000;
    static final int MAX_STATES_PER_BLOCK = 65_536;
    static final int MAX_MATERIALS_PER_ENTRY = 512;
    static final int MAX_TOTAL_MATERIALS = 2_000_000;
    static final int MAX_FINGERPRINT_LENGTH = 4_096;
    /** Modified UTF-8 byte ceiling for a maximum-length fingerprint. */
    static final int MAX_FINGERPRINT_UTF_BYTES =
            MAX_FINGERPRINT_LENGTH * 3;
    static final int MAX_IDENTIFIER_LENGTH = 512;
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    /** Inflated bytes a gzip cache read may consume. */
    static final long MAX_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final int DIGEST_BYTES = 32;
    private static final String FILE_NAME =
            "deferred-block-state-v2.bin.gz";
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

    /**
     * Whole-block coverage: every possible state of the block, and how many
     * of them this manifest records.
     */
    public record BlockCoverage(int states, int recorded) {
        /** Whether every possible state of the block is recorded. */
        public boolean complete() {
            return recorded == states;
        }
    }

    /** One key's group code and sorted materials. */
    private record Entry(int group, List<MaterialId> materials) {
    }

    /** An immutable, validated manifest. */
    public static final class Manifest {
        private final String fingerprint;
        private final Map<String, Entry> entries;
        private final Map<String, BlockCoverage> blocks;
        private final int totalMaterials;
        private final int completeBlocks;

        private Manifest(
                String fingerprint,
                Map<String, Entry> entries,
                Map<String, BlockCoverage> blocks,
                int totalMaterials
        ) {
            this.fingerprint = fingerprint;
            this.entries = entries;
            this.blocks = blocks;
            this.totalMaterials = totalMaterials;
            int complete = 0;
            for (BlockCoverage coverage : blocks.values()) {
                if (coverage.complete()) {
                    complete++;
                }
            }
            this.completeBlocks = complete;
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
            Entry entry = entries.get(modelKey);
            return entry == null ? null : entry.materials();
        }

        /**
         * Canonical group code of a key: {@link #UNGROUPED}, {@link
         * #NON_MODEL_GROUP}, or a positive label that is meaningful only
         * against other keys of the same block. {@code null} for an unknown
         * key.
         */
        public Integer group(String modelKey) {
            Entry entry = entries.get(modelKey);
            return entry == null ? null : entry.group();
        }

        public Set<String> keys() {
            return entries.keySet();
        }

        /** Block IDs with at least one recorded state, sorted. */
        public Set<String> blocks() {
            return blocks.keySet();
        }

        /** Coverage of a block, or {@code null} for an unknown block. */
        public BlockCoverage coverage(String block) {
            return blocks.get(block);
        }

        /** Blocks whose every possible state is recorded. */
        public int completeBlocks() {
            return completeBlocks;
        }

        /** Distinct materials of the given keys; unknown keys add nothing. */
        public Set<MaterialId> union(Collection<String> modelKeys) {
            TreeSet<MaterialId> union = new TreeSet<>();
            for (String key : modelKeys) {
                Entry entry = entries.get(key);
                if (entry != null) {
                    union.addAll(entry.materials());
                }
            }
            return Collections.unmodifiableSet(union);
        }
    }

    /**
     * Collects certified entries. Malformed, duplicate, incomplete, or
     * over-limit input is left out, and any rejected add closes this
     * builder so {@link #build} stays null.
     *
     * <p>Each add carries the block's full possible-state count and the
     * bakery's raw group ID of that state. Every key of one block must
     * report the same state count, a block cannot record more keys than it
     * has states, and a positive raw ID may belong to only one block. Raw
     * positive IDs are relabeled per block at {@link #build}.</p>
     */
    public static final class Builder {
        /** Raw group IDs until {@link #build}. */
        private final TreeMap<String, Entry> entries = new TreeMap<>();
        private final Map<String, BlockCoverage> blocks = new HashMap<>();
        private final Map<Integer, String> groupOwners = new HashMap<>();
        private long totalMaterials;
        private int rejected;
        private boolean failed;

        public boolean add(
                String modelKey,
                int blockStates,
                int group,
                Collection<MaterialId> materials
        ) {
            List<MaterialId> canonical =
                    admissible(modelKey, blockStates, group, materials);
            if (canonical == null) {
                rejected++;
                failed = true;
                return false;
            }
            String block = blockOf(modelKey);
            BlockCoverage coverage = blocks.get(block);
            blocks.put(block, new BlockCoverage(
                    blockStates,
                    coverage == null ? 1 : coverage.recorded() + 1
            ));
            if (group > NON_MODEL_GROUP) {
                groupOwners.putIfAbsent(group, block);
            }
            entries.put(modelKey, new Entry(group, canonical));
            totalMaterials += canonical.size();
            return true;
        }

        /**
         * Whether {@link #add} would accept this entry on an open builder.
         * Never changes or closes the builder, so a caller can leave an
         * ineligible entry out before the sticky {@code add}.
         */
        public boolean accepts(
                String modelKey,
                int blockStates,
                int group,
                Collection<MaterialId> materials
        ) {
            return !failed
                    && admissible(modelKey, blockStates, group, materials)
                            != null;
        }

        private List<MaterialId> admissible(
                String modelKey,
                int blockStates,
                int group,
                Collection<MaterialId> materials
        ) {
            List<MaterialId> canonical = canonicalEntry(modelKey, materials);
            if (canonical == null
                    || entries.containsKey(modelKey)
                    || entries.size() >= MAX_ENTRIES
                    || totalMaterials + canonical.size()
                            > MAX_TOTAL_MATERIALS
                    || blockStates < 1
                    || blockStates > MAX_STATES_PER_BLOCK
                    || group < UNGROUPED) {
                return null;
            }
            String block = blockOf(modelKey);
            BlockCoverage coverage = blocks.get(block);
            if (coverage != null
                    && (coverage.states() != blockStates
                            || coverage.recorded() >= blockStates)) {
                return null;
            }
            if (group > NON_MODEL_GROUP) {
                String owner = groupOwners.get(group);
                if (owner != null && !owner.equals(block)) {
                    return null;
                }
            }
            return canonical;
        }

        public int rejected() {
            return rejected;
        }

        public int size() {
            return entries.size();
        }

        /**
         * Null when any add was rejected, the fingerprint is unusable,
         * or nothing was certified. A rejected builder stays closed;
         * certify with a fresh builder.
         */
        public Manifest build(String fingerprint) {
            if (failed
                    || !validFingerprint(fingerprint)
                    || entries.isEmpty()) {
                return null;
            }
            return new Manifest(
                    fingerprint,
                    Collections.unmodifiableMap(canonicalGroups(entries)),
                    Collections.unmodifiableMap(new TreeMap<>(blocks)),
                    (int) totalMaterials
            );
        }

        /**
         * Relabels positive raw IDs per block, from 1 in key order, keeping
         * only which keys share a group.
         */
        private static TreeMap<String, Entry> canonicalGroups(
                TreeMap<String, Entry> raw
        ) {
            TreeMap<String, Entry> canonical = new TreeMap<>();
            Map<String, Map<Integer, Integer>> labels = new HashMap<>();
            for (Map.Entry<String, Entry> entry : raw.entrySet()) {
                int group = entry.getValue().group();
                if (group > NON_MODEL_GROUP) {
                    Map<Integer, Integer> blockLabels = labels.computeIfAbsent(
                            blockOf(entry.getKey()),
                            ignored -> new HashMap<>()
                    );
                    Integer label = blockLabels.get(group);
                    if (label == null) {
                        label = blockLabels.size() + 1;
                        blockLabels.put(group, label);
                    }
                    group = label;
                }
                canonical.put(
                        entry.getKey(),
                        new Entry(group, entry.getValue().materials())
                );
            }
            return canonical;
        }
    }

    /** Block ID of a valid model key: the location before {@code #}. */
    public static String blockOf(String modelKey) {
        return modelKey.substring(0, modelKey.indexOf('#'));
    }

    static boolean validBlock(String block) {
        return validLocation(block) && block.startsWith(MINECRAFT_PREFIX);
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
    public static boolean validModelKey(String key) {
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

    /**
     * Writes the uncompressed canonical form followed by its digest: the
     * header, the sorted block coverage table, then every entry in key
     * order. Key order is block-major because every identifier character
     * sorts after {@code #}.
     */
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
        output.writeInt(manifest.blocks.size());
        for (Map.Entry<String, BlockCoverage> block
                : manifest.blocks.entrySet()) {
            output.writeUTF(block.getKey());
            output.writeInt(block.getValue().states());
            output.writeInt(block.getValue().recorded());
        }
        for (Map.Entry<String, Entry> entry : manifest.entries.entrySet()) {
            output.writeUTF(entry.getKey());
            output.writeInt(entry.getValue().group());
            output.writeInt(entry.getValue().materials().size());
            for (MaterialId material : entry.getValue().materials()) {
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
     * Modified-UTF-8 lengths are rejected before that payload is allocated.
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
        String fingerprint = readModifiedUtf(
                input,
                MAX_FINGERPRINT_UTF_BYTES,
                "Invalid manifest fingerprint"
        );
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
        int blockCount = input.readInt();
        if (blockCount <= 0 || blockCount > count) {
            throw new IOException("Invalid manifest counts");
        }
        TreeMap<String, BlockCoverage> blocks = new TreeMap<>();
        String previousBlock = null;
        long coveredEntries = 0L;
        for (int index = 0; index < blockCount; index++) {
            String block = readModifiedUtf(
                    input,
                    MAX_IDENTIFIER_LENGTH,
                    "Manifest identifier is too long"
            );
            if (!validBlock(block)) {
                throw new IOException("Invalid manifest block");
            }
            if (previousBlock != null
                    && previousBlock.compareTo(block) >= 0) {
                throw new IOException("Manifest blocks are not canonical");
            }
            previousBlock = block;
            int states = input.readInt();
            int recorded = input.readInt();
            if (states <= 0 || states > MAX_STATES_PER_BLOCK
                    || recorded <= 0 || recorded > states) {
                throw new IOException("Invalid manifest block coverage");
            }
            coveredEntries += recorded;
            if (coveredEntries > count) {
                throw new IOException("Manifest block coverage mismatch");
            }
            blocks.put(block, new BlockCoverage(states, recorded));
        }
        if (coveredEntries != count) {
            throw new IOException("Manifest block coverage mismatch");
        }
        TreeMap<String, Entry> entries = new TreeMap<>();
        String previousKey = null;
        long totalMaterials = 0L;
        for (Map.Entry<String, BlockCoverage> block : blocks.entrySet()) {
            String blockId = block.getKey();
            // Positive labels must appear as 1, 2, ... in key order.
            int nextLabel = 1;
            for (int blockIndex = 0;
                    blockIndex < block.getValue().recorded();
                    blockIndex++) {
            String key = readModifiedUtf(
                    input,
                    MAX_IDENTIFIER_LENGTH,
                    "Manifest identifier is too long"
            );
            if (previousKey != null && previousKey.compareTo(key) >= 0) {
                throw new IOException("Manifest keys are not canonical");
            }
            previousKey = key;
            if (key.length() <= blockId.length()
                    || !key.startsWith(blockId)
                    || key.charAt(blockId.length()) != '#') {
                throw new IOException(
                        "Manifest key does not match its block"
                );
            }
            int group = input.readInt();
            if (group < UNGROUPED || group > nextLabel) {
                throw new IOException("Invalid manifest group");
            }
            if (group == nextLabel) {
                nextLabel++;
            }
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
                        readModifiedUtf(
                                input,
                                MAX_IDENTIFIER_LENGTH,
                                "Manifest identifier is too long"
                        ),
                        readModifiedUtf(
                                input,
                                MAX_IDENTIFIER_LENGTH,
                                "Manifest identifier is too long"
                        )
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
            entries.put(key, new Entry(group, canonical));
            }
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
                Collections.unmodifiableMap(blocks),
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
            try (InputStream input = Files.newInputStream(file)) {
                return readCompressed(input, MAX_UNCOMPRESSED_BYTES);
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
            if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                return; // The recorder already logged its one summary line.
            }
            VHAccelerator.LOGGER.info(
                    "[debug] Saved {} certified minecraft block-state models ({} "
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

    /**
     * Gzip cache decode. Malformed, truncated, and over-limit input is
     * absent. {@code maxUncompressed} counts inflated bytes.
     */
    static Manifest readGzipCache(byte[] gzipBytes, long maxUncompressed) {
        if (gzipBytes == null) {
            return null;
        }
        try (InputStream input = new ByteArrayInputStream(gzipBytes)) {
            return readCompressed(input, maxUncompressed);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    static Manifest readCompressed(
            InputStream compressed,
            long maxUncompressed
    ) throws IOException {
        if (maxUncompressed < 0) {
            throw new IOException("Manifest exceeds uncompressed limit");
        }
        try (InputStream gzip = new GZIPInputStream(
                new BufferedInputStream(compressed))) {
            return read(new BoundedInputStream(gzip, maxUncompressed));
        }
    }

    /**
     * Rejects a modified-UTF-8 length before allocating its payload.
     * Bytes already read remain on the caller's digest stream.
     */
    private static String readModifiedUtf(
            DataInputStream input,
            int maxBytes,
            String tooLongMessage
    ) throws IOException {
        int utfLength = input.readUnsignedShort();
        if (utfLength > maxBytes) {
            throw new IOException(tooLongMessage);
        }
        byte[] prefixed = new byte[utfLength + 2];
        prefixed[0] = (byte) (utfLength >>> 8);
        prefixed[1] = (byte) utfLength;
        input.readFully(prefixed, 2, utfLength);
        return new DataInputStream(new ByteArrayInputStream(prefixed))
                .readUTF();
    }

    /** Counts inflated bytes and fails closed at the cache budget. */
    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return endOrOverflow();
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {
            if (length == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return endOrOverflow();
            }
            int allowed = (int) Math.min(remaining, length);
            int count = delegate.read(buffer, offset, allowed);
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        private int endOrOverflow() throws IOException {
            int next = delegate.read();
            if (next < 0) {
                return -1;
            }
            throw new IOException("Manifest exceeds uncompressed limit");
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

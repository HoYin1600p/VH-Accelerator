package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.util.CacheFiles;
import dev.hoyin1600p.vhaccelerator.util.Digests;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Compact, block-level certification of plain block-state model graphs for
 * warm-launch graph skipping.
 *
 * <p>Each certified block records its possible-state count, each state's
 * model-group label by index in the block's state order, and the union of
 * the block-atlas textures of all its states' graphs as indexes into one
 * shared texture table. No per-state model keys are stored, so a pack with
 * a million block-state models produces a small file that loads quickly.
 * Group labels are {@link #UNGROUPED}, {@link #NON_MODEL_GROUP}, or a
 * positive label meaningful only within its block.</p>
 *
 * <p>The file is gzip-compressed, carries the client asset fingerprint and
 * ends with a SHA-256 digest of its contents. Anything malformed, oversize
 * or from another format version reads as absent.</p>
 */
public final class BlockGraphManifest {
    static final int MAGIC = 0x56484247; // "VHBG"
    static final int FORMAT_VERSION = 1;
    public static final int UNGROUPED = -1;
    public static final int NON_MODEL_GROUP = 0;
    static final int MAX_BLOCKS = 200_000;
    static final int MAX_STATES_PER_BLOCK = 65_536;
    static final int MAX_TEXTURES = 500_000;
    static final int MAX_TEXTURES_PER_BLOCK = 8_192;
    static final int MAX_IDENTIFIER_LENGTH = 512;
    static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;
    private static final int DIGEST_BYTES = 32;
    private static final String FILE_NAME = "block-graphs-v" + FORMAT_VERSION + ".bin.gz";

    private BlockGraphManifest() {
    }

    /** One certified block. {@code textures} index the manifest texture table. */
    public record Block(int[] groups, int[] textures) {
        public int states() {
            return groups.length;
        }
    }

    /** An immutable, validated manifest. */
    public static final class Manifest {
        private final String fingerprint;
        private final List<String> textures;
        private final Map<String, Block> blocks;

        private Manifest(String fingerprint, List<String> textures, Map<String, Block> blocks) {
            this.fingerprint = fingerprint;
            this.textures = textures;
            this.blocks = blocks;
        }

        public String fingerprint() {
            return fingerprint;
        }

        public boolean matches(String currentFingerprint) {
            return fingerprint.equals(currentFingerprint);
        }

        /** The certified block, or null. */
        public Block block(String blockId) {
            return blocks.get(blockId);
        }

        public Map<String, Block> blocks() {
            return blocks;
        }

        public String texture(int index) {
            return textures.get(index);
        }

        public int textureCount() {
            return textures.size();
        }
    }

    /** Builds a manifest; one texture table shared by every block. */
    public static final class Builder {
        private final Map<String, Integer> textureIndex = new HashMap<>();
        private final List<String> textures = new ArrayList<>();
        private final Map<String, Block> blocks = new LinkedHashMap<>();

        /**
         * Adds a block whose every state is certified. {@code rawGroups} are
         * the bakery's raw group codes by state index; positive codes are
         * relabeled from 1 in first-seen order. Returns false, adding
         * nothing, for invalid input.
         */
        public boolean add(String blockId, int[] rawGroups, List<String> blockTextures) {
            if (!validIdentifier(blockId)
                    || blocks.containsKey(blockId)
                    || blocks.size() >= MAX_BLOCKS
                    || rawGroups == null
                    || rawGroups.length == 0
                    || rawGroups.length > MAX_STATES_PER_BLOCK
                    || blockTextures == null
                    || blockTextures.size() > MAX_TEXTURES_PER_BLOCK) {
                return false;
            }
            int[] groups = relabel(rawGroups);
            if (groups == null) {
                return false;
            }
            for (String texture : blockTextures) {
                if (!validIdentifier(texture)) {
                    return false;
                }
            }
            long added = blockTextures.stream().distinct()
                    .filter(texture -> !textureIndex.containsKey(texture)).count();
            if (textures.size() + added > MAX_TEXTURES) {
                return false;
            }
            int[] indexes = blockTextures.stream()
                    .distinct()
                    .sorted()
                    .mapToInt(texture -> textureIndex.computeIfAbsent(texture, key -> {
                        textures.add(key);
                        return textures.size() - 1;
                    }))
                    .toArray();
            blocks.put(blockId, new Block(groups, indexes));
            return true;
        }

        public int size() {
            return blocks.size();
        }

        public Manifest build(String fingerprint) {
            if (fingerprint == null || fingerprint.isEmpty() || blocks.isEmpty()) {
                return null;
            }
            return new Manifest(
                    fingerprint,
                    List.copyOf(textures),
                    Collections.unmodifiableMap(new LinkedHashMap<>(blocks))
            );
        }
    }

    /** Relabels positive raw groups from 1 in first-seen order; null if invalid. */
    static int[] relabel(int[] rawGroups) {
        Map<Integer, Integer> labels = new HashMap<>();
        int[] groups = new int[rawGroups.length];
        for (int index = 0; index < rawGroups.length; index++) {
            int raw = rawGroups[index];
            if (raw < UNGROUPED) {
                return null;
            }
            groups[index] = raw <= NON_MODEL_GROUP
                    ? raw
                    : labels.computeIfAbsent(raw, ignored -> labels.size() + 1);
        }
        return groups;
    }

    static boolean validIdentifier(String value) {
        if (value == null || value.isEmpty() || value.length() > MAX_IDENTIFIER_LENGTH) {
            return false;
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            boolean common = c == '_' || c == '-' || c == '.'
                    || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
            if (!(common || c == ':' && index == separator || c == '/' && index > separator)) {
                return false;
            }
        }
        return true;
    }

    public static void write(Manifest manifest, OutputStream target) throws IOException {
        MessageDigest digest = Digests.sha256();
        DigestOutputStream digesting = new DigestOutputStream(target, digest);
        DataOutputStream output = new DataOutputStream(digesting);
        output.writeInt(MAGIC);
        output.writeInt(FORMAT_VERSION);
        writeString(output, manifest.fingerprint);
        output.writeInt(manifest.textures.size());
        for (String texture : manifest.textures) {
            writeString(output, texture);
        }
        output.writeInt(manifest.blocks.size());
        for (Map.Entry<String, Block> entry : manifest.blocks.entrySet()) {
            writeString(output, entry.getKey());
            Block block = entry.getValue();
            output.writeInt(block.groups.length);
            for (int group : block.groups) {
                output.writeInt(group);
            }
            output.writeInt(block.textures.length);
            for (int texture : block.textures) {
                output.writeInt(texture);
            }
        }
        output.flush();
        digesting.on(false);
        output.write(digest.digest());
        output.flush();
    }

    /** Reads a manifest; null for another format version, throws if corrupt. */
    public static Manifest read(InputStream source) throws IOException {
        MessageDigest digest = Digests.sha256();
        DigestInputStream digesting = new DigestInputStream(source, digest);
        DataInputStream input = new DataInputStream(digesting);
        if (input.readInt() != MAGIC) {
            throw new IOException("Not a block graph manifest");
        }
        if (input.readInt() != FORMAT_VERSION) {
            return null;
        }
        String fingerprint = readString(input);
        int textureCount = input.readInt();
        if (textureCount < 0 || textureCount > MAX_TEXTURES) {
            throw new IOException("Invalid texture count " + textureCount);
        }
        List<String> textures = new ArrayList<>(textureCount);
        for (int index = 0; index < textureCount; index++) {
            String texture = readString(input);
            if (!validIdentifier(texture)) {
                throw new IOException("Invalid texture identifier");
            }
            textures.add(texture);
        }
        int blockCount = input.readInt();
        if (blockCount <= 0 || blockCount > MAX_BLOCKS) {
            throw new IOException("Invalid block count " + blockCount);
        }
        Map<String, Block> blocks = new LinkedHashMap<>(blockCount * 2);
        for (int index = 0; index < blockCount; index++) {
            String blockId = readString(input);
            int states = input.readInt();
            if (!validIdentifier(blockId) || blocks.containsKey(blockId)
                    || states <= 0 || states > MAX_STATES_PER_BLOCK) {
                throw new IOException("Invalid block entry");
            }
            int[] groups = new int[states];
            for (int state = 0; state < states; state++) {
                groups[state] = input.readInt();
                if (groups[state] < UNGROUPED) {
                    throw new IOException("Invalid group");
                }
            }
            int textureRefs = input.readInt();
            if (textureRefs < 0 || textureRefs > MAX_TEXTURES_PER_BLOCK) {
                throw new IOException("Invalid texture reference count");
            }
            int[] indexes = new int[textureRefs];
            for (int ref = 0; ref < textureRefs; ref++) {
                indexes[ref] = input.readInt();
                if (indexes[ref] < 0 || indexes[ref] >= textureCount) {
                    throw new IOException("Texture reference out of range");
                }
            }
            blocks.put(blockId, new Block(groups, indexes));
        }
        digesting.on(false);
        byte[] expected = digest.digest();
        byte[] actual = input.readNBytes(DIGEST_BYTES);
        if (!Arrays.equals(expected, actual) || input.read() != -1) {
            throw new IOException("Block graph manifest digest mismatch");
        }
        return new Manifest(fingerprint, List.copyOf(textures), Collections.unmodifiableMap(blocks));
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_IDENTIFIER_LENGTH * 16) {
            throw new IOException("Invalid string length " + length);
        }
        return new String(input.readNBytes(length), StandardCharsets.UTF_8);
    }

    public static CompletableFuture<Manifest> readAsync() {
        return CompletableFuture.supplyAsync(BlockGraphManifest::readFile, SharedWorkers.io());
    }

    public static void writeAsync(Manifest manifest) {
        CompletableFuture.runAsync(() -> writeFile(manifest), SharedWorkers.io());
    }

    /** Deletes a manifest proven stale at runtime; the next launch re-records it. */
    public static void invalidateAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(cacheFile());
            } catch (IOException failure) {
                VHAccelerator.LOGGER.warn("Could not delete the stale block graph manifest", failure);
            }
        }, SharedWorkers.io());
    }

    private static Path cacheFile() {
        return FMLPaths.GAMEDIR.get().resolve("cache").resolve("vhaccelerator")
                .resolve("client-assets").resolve(FILE_NAME);
    }

    private static Manifest readFile() {
        Path file = cacheFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IOException("Block graph manifest is too large");
            }
            try (InputStream input = new GZIPInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                return read(input);
            }
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not read the block graph manifest; block states load eagerly", failure);
            return null;
        }
    }

    private static void writeFile(Manifest manifest) {
        Path file = cacheFile();
        Path temporary = file.resolveSibling(file.getFileName() + CacheFiles.TEMP_SUFFIX);
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                write(manifest, output);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not write the block graph manifest", failure);
        }
    }
}

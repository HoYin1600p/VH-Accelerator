/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: src/main/java/org/embeddedt/modernfix/resources/ZipPackIndex.java
 * Upstream commit: f1492cc829b7172da10fa55b14cf14ec35f23c47
 * Original copyright: Copyright (c) 2026 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; retargeted the central-directory tree to Minecraft
 * 1.18.2's collection-returning FilePackResources API and retained its
 * max-depth, filename-filter, and metadata-exclusion contracts.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.resource;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/** Immutable index over the relevant entries in a ZIP resource pack. */
public final class ZipPackIndex {
    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int EOCD_SIZE = 22;
    private static final int EOCD_OFF_CD_SIZE = 12;
    private static final int EOCD_OFF_CD_OFFSET = 16;
    private static final int EOCD_MAX_COMMENT_LENGTH = 65_535;

    private static final int CD_ENTRY_SIGNATURE = 0x02014b50;
    private static final int CD_ENTRY_HEADER_SIZE = 46;
    private static final int CD_OFF_FILENAME_LENGTH = 28;
    private static final int CD_OFF_EXTRA_LENGTH = 30;
    private static final int CD_OFF_COMMENT_LENGTH = 32;
    private static final IntList EMPTY_OFFSETS = IntList.of();

    private final ByteBuffer centralDirectory;
    private final Set<String> trackedRoots;
    private final DirectoryNode root;

    public ZipPackIndex(Path zipPath) throws IOException {
        this.centralDirectory = readCentralDirectory(zipPath);
        Set<String> roots = new HashSet<>();
        roots.add(PackType.CLIENT_RESOURCES.getDirectory());
        roots.add(PackType.SERVER_DATA.getDirectory());
        this.trackedRoots = Set.copyOf(roots);
        this.root = buildTree();
    }

    public Set<String> namespaces(PackType type) {
        DirectoryNode typeNode = this.root.children.get(type.getDirectory());
        if (typeNode == null) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, DirectoryNode> entry
                : typeNode.children.entrySet()) {
            String namespace = entry.getKey();
            if (!entry.getValue().isEmpty()
                    && namespace.equals(namespace.toLowerCase(Locale.ROOT))) {
                result.add(namespace);
            }
        }
        return Set.copyOf(result);
    }

    public Collection<ResourceLocation> resources(
            PackType type,
            String namespace,
            String path,
            int maxDepth,
            Predicate<String> filter
    ) {
        if (namespace == null
                || namespace.isEmpty()
                || path == null
                || path.indexOf('\\') >= 0
                || path.startsWith("/")) {
            return null;
        }
        if (maxDepth < 0) {
            return Set.of();
        }

        DirectoryNode node = this.root.children.get(type.getDirectory());
        if (node == null) {
            return Set.of();
        }
        node = node.children.get(namespace);
        if (node == null) {
            return Set.of();
        }

        String[] components = split(path);
        for (String component : components) {
            node = node.children.get(component);
            if (node == null) {
                return Set.of();
            }
        }

        ListBuilder result = new ListBuilder(namespace, maxDepth, filter);
        result.collect(node, String.join("/", components), components.length);
        return result.result();
    }

    private DirectoryNode buildTree() throws IOException {
        DirectoryNode treeRoot = new DirectoryNode();
        if (this.centralDirectory == null) {
            treeRoot.freeze();
            return treeRoot;
        }

        int position = 0;
        int limit = this.centralDirectory.limit();
        while (position + CD_ENTRY_HEADER_SIZE <= limit) {
            if (this.centralDirectory.getInt(position)
                    != CD_ENTRY_SIGNATURE) {
                break;
            }
            position += indexEntry(position, limit, treeRoot);
        }
        if (position == 0 && limit != 0) {
            throw new IOException("ZIP central directory has no readable entries");
        }
        treeRoot.freeze();
        return treeRoot;
    }

    private int indexEntry(
            int position,
            int limit,
            DirectoryNode treeRoot
    ) throws IOException {
        int nameLength = Short.toUnsignedInt(this.centralDirectory.getShort(
                position + CD_OFF_FILENAME_LENGTH
        ));
        int extraLength = Short.toUnsignedInt(this.centralDirectory.getShort(
                position + CD_OFF_EXTRA_LENGTH
        ));
        int commentLength = Short.toUnsignedInt(this.centralDirectory.getShort(
                position + CD_OFF_COMMENT_LENGTH
        ));
        int recordLength = CD_ENTRY_HEADER_SIZE
                + nameLength
                + extraLength
                + commentLength;
        if (position + recordLength > limit) {
            throw new IOException("Truncated ZIP central directory");
        }

        byte[] nameBytes = new byte[nameLength];
        this.centralDirectory.get(
                position + CD_ENTRY_HEADER_SIZE,
                nameBytes
        );
        DirectoryNode current = treeRoot;
        boolean tracked = false;
        boolean skipped = false;
        int segmentStart = 0;

        for (int index = 0; index < nameLength; index++) {
            if (nameBytes[index] != '/') {
                continue;
            }
            int segmentLength = index - segmentStart;
            if (segmentLength > 0) {
                String segment = new String(
                        nameBytes,
                        segmentStart,
                        segmentLength,
                        StandardCharsets.UTF_8
                );
                if (!tracked) {
                    if (!this.trackedRoots.contains(segment)) {
                        skipped = true;
                        break;
                    }
                    tracked = true;
                }
                current = current.children.computeIfAbsent(
                        segment,
                        ignored -> new DirectoryNode()
                );
            }
            segmentStart = index + 1;
        }

        if (!skipped && tracked && segmentStart < nameLength) {
            if (current.fileOffsets == EMPTY_OFFSETS) {
                current.fileOffsets = new IntArrayList();
            }
            current.fileOffsets.add(position);
        }
        return recordLength;
    }

    private String basename(int centralDirectoryOffset) {
        int nameLength = Short.toUnsignedInt(this.centralDirectory.getShort(
                centralDirectoryOffset + CD_OFF_FILENAME_LENGTH
        ));
        byte[] nameBytes = new byte[nameLength];
        this.centralDirectory.get(
                centralDirectoryOffset + CD_ENTRY_HEADER_SIZE,
                nameBytes
        );
        int lastSlash = -1;
        for (int index = nameBytes.length - 1; index >= 0; index--) {
            if (nameBytes[index] == '/') {
                lastSlash = index;
                break;
            }
        }
        return new String(
                nameBytes,
                lastSlash + 1,
                nameLength - lastSlash - 1,
                StandardCharsets.UTF_8
        );
    }

    private static ByteBuffer readCentralDirectory(Path path)
            throws IOException {
        try (SeekableByteChannel channel = openChannel(path)) {
            long fileSize = channel.size();
            if (fileSize < EOCD_SIZE) {
                return null;
            }

            int tailSize = (int) Math.min(
                    fileSize,
                    (long) EOCD_SIZE + EOCD_MAX_COMMENT_LENGTH
            );
            ByteBuffer tail = ByteBuffer.allocate(tailSize)
                    .order(ByteOrder.LITTLE_ENDIAN);
            long tailStart = fileSize - tailSize;
            readFully(channel, tail, tailStart);
            tail.flip();

            int end = -1;
            for (int index = tailSize - EOCD_SIZE; index >= 0; index--) {
                if (tail.getInt(index) == EOCD_SIGNATURE) {
                    int commentLength = Short.toUnsignedInt(
                            tail.getShort(index + 20)
                    );
                    if (index + EOCD_SIZE + commentLength == tailSize) {
                        end = index;
                        break;
                    }
                }
            }
            if (end < 0) {
                throw new IOException("ZIP end-of-central-directory not found");
            }

            long directorySize = Integer.toUnsignedLong(tail.getInt(
                    end + EOCD_OFF_CD_SIZE
            ));
            long directoryOffset = Integer.toUnsignedLong(tail.getInt(
                    end + EOCD_OFF_CD_OFFSET
            ));
            if (directorySize == 0) {
                return null;
            }
            if (directorySize == 0xffff_ffffL
                    || directoryOffset == 0xffff_ffffL
                    || directorySize > Integer.MAX_VALUE) {
                throw new IOException("ZIP64 central directory is unsupported");
            }
            if (directoryOffset > fileSize - directorySize) {
                throw new IOException("Invalid ZIP central-directory range");
            }

            if (channel instanceof FileChannel fileChannel) {
                try {
                    return fileChannel.map(
                            FileChannel.MapMode.READ_ONLY,
                            directoryOffset,
                            directorySize
                    ).order(ByteOrder.LITTLE_ENDIAN);
                } catch (IOException | RuntimeException ignored) {
                    // Some filesystems cannot map archives. Heap-copy instead.
                }
            }

            ByteBuffer directory = ByteBuffer.allocate((int) directorySize)
                    .order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, directory, directoryOffset);
            directory.flip();
            return directory;
        }
    }

    private static SeekableByteChannel openChannel(Path path)
            throws IOException {
        try {
            return FileChannel.open(path, StandardOpenOption.READ);
        } catch (IOException | RuntimeException failure) {
            return Files.newByteChannel(path, StandardOpenOption.READ);
        }
    }

    private static void readFully(
            SeekableByteChannel channel,
            ByteBuffer target,
            long offset
    ) throws IOException {
        while (target.hasRemaining()) {
            channel.position(offset + target.position());
            int read = channel.read(target);
            if (read < 0) {
                throw new IOException("Unexpected end of ZIP file");
            }
            if (read == 0) {
                Thread.onSpinWait();
            }
        }
    }

    private static String[] split(String path) {
        if (path.isEmpty()) {
            return new String[0];
        }
        return java.util.Arrays.stream(path.split("/"))
                .filter(component -> !component.isEmpty())
                .toArray(String[]::new);
    }

    private final class ListBuilder {
        private final String namespace;
        private final int maxDepth;
        private final Predicate<String> filter;
        private final Collection<ResourceLocation> resources =
                new ArrayList<>();

        private ListBuilder(
                String namespace,
                int maxDepth,
                Predicate<String> filter
        ) {
            this.namespace = namespace;
            this.maxDepth = maxDepth;
            this.filter = filter;
        }

        private void collect(
                DirectoryNode node,
                String path,
                int directoryDepth
        ) {
            if (directoryDepth < this.maxDepth) {
                for (int index = 0; index < node.fileOffsets.size(); index++) {
                    String basename = ZipPackIndex.this.basename(
                            node.fileOffsets.getInt(index)
                    );
                    if (basename.endsWith(".mcmeta")
                            || !this.filter.test(basename)) {
                        continue;
                    }
                    String resourcePath = path.isEmpty()
                            ? basename
                            : path + "/" + basename;
                    try {
                        this.resources.add(
                                ResourceLocation.fromNamespaceAndPath(
                                        this.namespace,
                                        resourcePath
                                )
                        );
                    } catch (ResourceLocationException ignored) {
                        // Invalid paths are not resources.
                    }
                }
            }
            if (directoryDepth >= this.maxDepth) {
                return;
            }
            for (Map.Entry<String, DirectoryNode> child
                    : node.children.entrySet()) {
                String childPath = path.isEmpty()
                        ? child.getKey()
                        : path + "/" + child.getKey();
                collect(child.getValue(), childPath, directoryDepth + 1);
            }
        }

        private Collection<ResourceLocation> result() {
            return List.copyOf(this.resources);
        }
    }

    private static final class DirectoryNode {
        private Map<String, DirectoryNode> children =
                new Object2ObjectOpenHashMap<>();
        private IntList fileOffsets = EMPTY_OFFSETS;

        private boolean isEmpty() {
            return this.children.isEmpty() && this.fileOffsets.isEmpty();
        }

        private void freeze() {
            if (this.fileOffsets instanceof IntArrayList offsets) {
                offsets.trim();
            }
            for (DirectoryNode child : this.children.values()) {
                child.freeze();
            }
            this.children = this.children.isEmpty()
                    ? Map.of()
                    : Map.copyOf(this.children);
        }
    }
}

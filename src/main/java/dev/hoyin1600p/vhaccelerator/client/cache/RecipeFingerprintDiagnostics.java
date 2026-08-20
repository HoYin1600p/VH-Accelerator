package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.VHAcceleratorConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraftforge.fml.loading.FMLPaths;

/** Debug-only comparison of canonical recipe serializer payloads. */
final class RecipeFingerprintDiagnostics {
    private static final Path FILE = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("recipe-fingerprint-debug.tsv");
    private static final int MAX_REPORTED_CHANGES = 32;

    private RecipeFingerprintDiagnostics() {
    }

    static void compare(List<Entry> current) {
        if (!VHAcceleratorConfig.debugDiagnosticsEnabled()) {
            return;
        }

        Map<String, Entry> previous = read();
        Map<String, Integer> changedSerializers = new HashMap<>();
        List<String> changedRecipes = new ArrayList<>();
        for (Entry entry : current) {
            Entry old = previous.get(entry.id());
            if (old != null && !old.payloadHash().equals(entry.payloadHash())) {
                changedSerializers.merge(entry.serializer(), 1, Integer::sum);
                if (changedRecipes.size() < MAX_REPORTED_CHANGES) {
                    changedRecipes.add(entry.id() + " [" + entry.serializer() + "]");
                }
            }
        }

        if (!previous.isEmpty()) {
            int changedCount = changedSerializers.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            VHAccelerator.LOGGER.info(
                    "Recipe fingerprint diagnostics compared {} recipes; "
                            + "{} serializer payloads changed across launches: {}",
                    current.size(),
                    changedCount,
                    changedSerializers.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue()
                                    .reversed())
                            .toList()
            );
            if (!changedRecipes.isEmpty()) {
                VHAccelerator.LOGGER.info(
                        "Changed recipe payload samples: {}",
                        changedRecipes
                );
            }
        }
        write(current);
    }

    static Entry entry(String id, String serializer, byte[] payload) {
        return new Entry(id, serializer, digest(payload));
    }

    private static Map<String, Entry> read() {
        if (!Files.isRegularFile(FILE)) {
            return Map.of();
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] fields = line.split("\\t", -1);
                if (fields.length == 3) {
                    entries.put(fields[0], new Entry(fields[0], fields[1], fields[2]));
                }
            }
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not read recipe fingerprint diagnostics",
                    exception
            );
        }
        return entries;
    }

    private static void write(List<Entry> entries) {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            List<String> lines = entries.stream()
                    .sorted(Comparator.comparing(Entry::id))
                    .map(entry -> entry.id() + "\t" + entry.serializer()
                            + "\t" + entry.payloadHash())
                    .toList();
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        temporary,
                        FILE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } catch (IOException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not write recipe fingerprint diagnostics",
                    exception
            );
        }
    }

    private static String digest(byte[] payload) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record Entry(String id, String serializer, String payloadHash) {
    }
}

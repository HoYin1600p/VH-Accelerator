package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Computes every stage of the client asset fingerprint over a fixed game
 * directory fixture and renders it as text, so the stages can be compared
 * before and after code is moved between classes.
 */
final class FingerprintFixtureReport {
    /** One stable-config result: the digest inputs, the manifest and the launch-time changes. */
    record ConfigResult(List<String> inputs, Map<String, String> manifest, Set<String> changedDuringLaunch) {
    }

    /** The fingerprint stages, however the production code happens to be organised. */
    interface Stages {
        Object captureEarlyConfigSnapshot();

        ConfigResult resolveStableConfigContents(Object earlyConfigs);

        ConfigResult scanAllStableConfigContents(Object earlyConfigs);

        void closeEarlyConfigMonitor(Object earlyConfigs);

        String digestStrings(List<String> values);

        void appendModFileMetadata(List<String> inputs, Path modsDirectory);

        void appendResourcePackMetadata(List<String> inputs, Path directory);

        String encode(String installation, String configs, String resourceFiles, String activePacks);

        List<String> parse(String encoded);

        void writeConfigManifest(Map<String, String> manifest);

        Map<String, String> readConfigManifest();

        Path manifestFile();
    }

    private FingerprintFixtureReport() {
    }

    /** Lays out configs, mods and resource packs with fixed content and timestamps. */
    static void populate(Path gameDir) throws IOException {
        write(gameDir.resolve("config/alpha-client.toml"), "volume = 3\n");
        write(gameDir.resolve("config/nested/beta.json"), "{\"a\": 1}\n");
        write(gameDir.resolve("config/nested/deeper/gamma.cfg"), "x=y\n");
        write(gameDir.resolve("config/vhaccelerator-client.toml"), "volatile = true\n");
        write(gameDir.resolve("config/byg/backups/last_working_configs_backup.zip"), "zip");
        write(gameDir.resolve("config/byg/byg-client.toml"), "byg = 1\n");
        write(gameDir.resolve("defaultconfigs/delta.toml"), "default = 1\n");
        try (RandomAccessFile big = new RandomAccessFile(gameDir.resolve("config/huge.bin").toFile(), "rw")) {
            big.setLength(33L * 1024L * 1024L);
        }
        write(gameDir.resolve("mods/one.jar"), "jar-one");
        write(gameDir.resolve("mods/two.JAR"), "jar-two-longer");
        write(gameDir.resolve("mods/notes.txt"), "not a jar");
        write(gameDir.resolve("resourcepacks/PackA/pack.mcmeta"), "{}");
        write(gameDir.resolve("resourcepacks/PackA/assets/minecraft/models/x.json"), "{}");
        write(gameDir.resolve("resourcepacks/PackB.zip"), "zipped");
        stamp(gameDir);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** Gives every regular file a timestamp derived from its relative path. */
    static void stamp(Path gameDir) throws IOException {
        try (var paths = Files.walk(gameDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = gameDir.relativize(path).toString().replace('\\', '/');
                Files.setLastModifiedTime(path, FileTime.fromMillis(1_700_000_000_000L + relative.hashCode() % 1_000_000L * 1000L));
            }
        }
    }

    static List<String> render(Stages stages, Path gameDir) throws IOException {
        List<String> lines = new ArrayList<>();

        Object early = stages.captureEarlyConfigSnapshot();
        ConfigResult monitored = stages.resolveStableConfigContents(early);
        describe(lines, "monitored", stages, monitored);

        Object earlyAgain = stages.captureEarlyConfigSnapshot();
        ConfigResult scanned = stages.scanAllStableConfigContents(earlyAgain);
        stages.closeEarlyConfigMonitor(earlyAgain);
        describe(lines, "scanned", stages, scanned);

        Object beforeEdit = stages.captureEarlyConfigSnapshot();
        Path edited = gameDir.resolve("config/alpha-client.toml");
        Files.writeString(edited, "volume = 11\n");
        Files.setLastModifiedTime(edited, FileTime.fromMillis(1_800_000_000_000L));
        Files.delete(gameDir.resolve("config/nested/beta.json"));
        write(gameDir.resolve("config/added.toml"), "new = 1\n");
        Files.setLastModifiedTime(gameDir.resolve("config/added.toml"), FileTime.fromMillis(1_800_000_001_000L));
        ConfigResult afterEdit = stages.scanAllStableConfigContents(beforeEdit);
        stages.closeEarlyConfigMonitor(beforeEdit);
        describe(lines, "after-edit", stages, afterEdit);

        List<String> installation = new ArrayList<>();
        installation.add("minecraft=fixture");
        stages.appendModFileMetadata(installation, gameDir.resolve("mods"));
        lines.add("installation inputs");
        installation.forEach(line -> lines.add("  " + line));
        List<String> missingMods = new ArrayList<>();
        stages.appendModFileMetadata(missingMods, gameDir.resolve("no-such-mods"));
        lines.add("missing mods dir " + missingMods);

        List<String> resourceFiles = new ArrayList<>();
        stages.appendResourcePackMetadata(resourceFiles, gameDir.resolve("resourcepacks"));
        lines.add("resource pack inputs");
        resourceFiles.forEach(line -> lines.add("  " + line));
        List<String> missingPacks = new ArrayList<>();
        stages.appendResourcePackMetadata(missingPacks, gameDir.resolve("no-such-packs"));
        lines.add("missing resource pack dir " + missingPacks);

        String installationDigest = stages.digestStrings(installation);
        String configDigest = stages.digestStrings(monitored.inputs());
        String resourceDigest = stages.digestStrings(resourceFiles);
        String packsDigest = stages.digestStrings(List.of("pack=0:Vanilla:vanilla", "pack=1:Mod:mod_resources"));
        String encoded = stages.encode(installationDigest, configDigest, resourceDigest, packsDigest);
        lines.add("encoded " + encoded);
        lines.add("parsed " + stages.parse(encoded));
        lines.add("parsed other schema " + stages.parse("v4|a|b|c|d"));
        lines.add("parsed short " + stages.parse("v5|a|b"));
        lines.add("empty digest " + stages.digestStrings(List.of()));
        lines.add("unicode digest " + stages.digestStrings(List.of("café", "\u0000", "")));

        stages.writeConfigManifest(monitored.manifest());
        lines.add("manifest file " + new String(Files.readAllBytes(stages.manifestFile()), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace("\n", "\\n"));
        lines.add("manifest round trip equal " + stages.readConfigManifest().equals(monitored.manifest()));
        Files.writeString(stages.manifestFile(), "no-tab-here\n");
        lines.add("malformed manifest " + stages.readConfigManifest());
        return lines;
    }

    private static void describe(List<String> lines, String name, Stages stages, ConfigResult result) {
        lines.add(name + " digest " + stages.digestStrings(result.inputs()));
        result.inputs().forEach(input -> lines.add("  input " + input));
        new TreeMap<>(result.manifest()).forEach((key, value) ->
                lines.add("  manifest " + key.replace('\u0000', '/') + " -> " + value));
        lines.add("  changed " + new TreeSet<>(result.changedDuringLaunch()).toString().replace('\u0000', '/'));
    }
}

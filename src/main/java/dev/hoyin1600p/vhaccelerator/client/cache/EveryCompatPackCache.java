package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.fingerprint.LocalScriptInputs;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.util.AtomicFiles;
import dev.hoyin1600p.vhaccelerator.util.CacheFiles;
import dev.hoyin1600p.vhaccelerator.util.PathNames;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Every Compat regenerates its whole client pack (recoloured textures,
 * models, block states for every wood type of every mod) on the render thread
 * during the first resource reload of every launch: 0.8-1.3 s in the test
 * packs. The generated resources are a function of the installed mod files,
 * the registered blocks and items, Every Compat's and Selene's configs, the
 * resource packs and KubeJS assets, and the active pack order, so after one
 * generation they are stored on disk keyed by exactly those inputs and put
 * back into the pack on the next launch instead of generating again. Only the
 * first reload of a launch is served; later reloads (F3+T, pack changes) run
 * Every Compat's own generation as before.
 */
public final class EveryCompatPackCache {
    private static final String PROVIDER =
            "net.mehvahdjukaar.every_compat.dynamicpack.ClientDynamicResourcesHandler";
    private static final int FORMAT = 1;
    private static final long MAGIC = 0x5648414543503031L; // "VHAECP01"
    private static final int MAX_ENTRIES = 1_000_000;
    private static final int MAX_ENTRY_BYTES = 64 << 20;
    private static final Path FILE = FMLPaths.GAMEDIR.get()
            .resolve("cache")
            .resolve("vhaccelerator")
            .resolve("client-assets")
            .resolve("everycomp-generated-pack-v" + FORMAT + ".bin");

    /** The key of the generation in progress, written once it completes. */
    private static volatile String pendingKey;

    private EveryCompatPackCache() {
    }

    /**
     * Called at the head of Selene's {@code reloadResources}. Returns true when
     * the pack was filled from disk and the original generation must be
     * skipped.
     */
    public static boolean restore(Object provider, ResourceManager manager) {
        if (!provider.getClass().getName().equals(PROVIDER)
                || !VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.persistentEveryCompatPack)) {
            return false;
        }
        try {
            Access access = Access.of(provider);
            if (access.initialized(provider)) {
                return false;
            }
            long started = System.nanoTime();
            String key = key(manager);
            if (key == null) {
                return false;
            }
            Stored stored = read(key);
            if (stored == null) {
                pendingKey = key;
                return false;
            }
            Object pack = access.pack(provider);
            access.markInitialized(provider);
            access.addPackLogo(pack);
            access.namespaces(pack).addAll(stored.namespaces);
            access.resources(pack).putAll(stored.resources);
            VHAccelerator.LOGGER.info(
                    "Restored Every Compat's generated client pack ({} resources) from disk in {} ms",
                    stored.resources.size(),
                    (System.nanoTime() - started) / 1_000_000L
            );
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not use the stored Every Compat pack; generating it as usual",
                    failure
            );
            pendingKey = null;
            return false;
        }
    }

    /** Called when Selene's {@code reloadResources} returns normally. */
    public static void generated(Object provider) {
        String key = pendingKey;
        if (key == null || !provider.getClass().getName().equals(PROVIDER)) {
            return;
        }
        pendingKey = null;
        try {
            Access access = Access.of(provider);
            Object pack = access.pack(provider);
            Map<ResourceLocation, byte[]> resources = new TreeMap<>(access.resources(pack));
            Set<String> namespaces = new TreeSet<>(access.namespaces(pack));
            SharedWorkers.io().execute(() -> write(key, namespaces, resources));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not store Every Compat's generated pack", failure);
        }
    }

    /**
     * Everything the generated pack is derived from. Configs of other mods
     * reach it only through the blocks and items they register.
     */
    private static String key(ResourceManager manager) {
        List<String> inputs = new ArrayList<>();
        inputs.add("format=" + FORMAT);
        inputs.add("minecraft=" + SharedConstants.getCurrentVersion().getName());
        ModList.get().getMods().stream()
                .sorted(Comparator.comparing(IModInfo::getModId))
                .forEach(mod -> inputs.add("mod=" + mod.getModId() + "@" + mod.getVersion()));
        Path game = FMLPaths.GAMEDIR.get();
        if (!appendFiles(inputs, game.resolve("mods"), 1, "mod-file", false)
                || !appendFiles(inputs, game.resolve("resourcepacks"), 32, "resourcepack", false)
                || !appendConfigs(inputs, game.resolve("config"))) {
            return null;
        }
        List<String> scripts = LocalScriptInputs.collect(game);
        if (scripts == null) {
            return null;
        }
        inputs.addAll(scripts);
        inputs.add("blocks=" + digest(ForgeRegistries.BLOCKS.getKeys().stream()
                .map(ResourceLocation::toString).sorted().toList()));
        inputs.add("items=" + digest(ForgeRegistries.ITEMS.getKeys().stream()
                .map(ResourceLocation::toString).sorted().toList()));
        List<String> packs = new ArrayList<>();
        try (Stream<PackResources> stream = manager.listPacks()) {
            stream.forEachOrdered(pack -> packs.add(pack.getClass().getName() + ":" + pack.getName()));
        }
        inputs.add("packs=" + digest(packs));
        return digest(inputs);
    }

    private static boolean appendFiles(
            List<String> inputs,
            Path directory,
            int depth,
            String label,
            boolean content
    ) {
        if (!Files.isDirectory(directory)) {
            inputs.add(label + "-directory-missing");
            return true;
        }
        try (Stream<Path> paths = Files.walk(directory, depth)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> PathNames.relative(directory, path)))
                    .toList()) {
                inputs.add(label + "=" + PathNames.relative(directory, path) + ":" + (content
                        ? digestFile(path)
                        : Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis()));
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    /** Every Compat's and Selene's own configs, by content. */
    private static boolean appendConfigs(List<String> inputs, Path config) {
        if (!Files.isDirectory(config)) {
            inputs.add("config-directory-missing");
            return true;
        }
        try (Stream<Path> paths = Files.list(config)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return (name.startsWith("everycomp") || name.startsWith("selene"))
                                && name.endsWith(".toml");
                    })
                    .sorted()
                    .toList()) {
                inputs.add("config=" + path.getFileName() + ":" + digestFile(path));
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }

    private static Stored read(String key) {
        if (!Files.isRegularFile(FILE)) {
            return null;
        }
        try (InputStream file = Files.newInputStream(FILE);
             CheckedInputStream checked = new CheckedInputStream(new BufferedInputStream(file, 1 << 16), new CRC32());
             DataInputStream in = new DataInputStream(checked)) {
            if (in.readLong() != MAGIC || in.readInt() != FORMAT || !key.equals(in.readUTF())) {
                return null;
            }
            int namespaceCount = in.readInt();
            if (namespaceCount < 0 || namespaceCount > MAX_ENTRIES) {
                return null;
            }
            Set<String> namespaces = new TreeSet<>();
            for (int i = 0; i < namespaceCount; i++) {
                namespaces.add(in.readUTF());
            }
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) {
                return null;
            }
            Map<ResourceLocation, byte[]> resources = new HashMap<>(count * 4 / 3 + 1);
            for (int i = 0; i < count; i++) {
                ResourceLocation location = new ResourceLocation(in.readUTF(), in.readUTF());
                int length = in.readInt();
                if (length < 0 || length > MAX_ENTRY_BYTES) {
                    return null;
                }
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                resources.put(location, bytes);
            }
            long expected = checked.getChecksum().getValue();
            if (in.readLong() != expected) {
                VHAccelerator.LOGGER.warn("Stored Every Compat pack failed its checksum; generating it again");
                return null;
            }
            return new Stored(namespaces, resources);
        } catch (EOFException truncated) {
            VHAccelerator.LOGGER.warn("Stored Every Compat pack is truncated; generating it again");
            return null;
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not read the stored Every Compat pack; generating it again", failure);
            return null;
        }
    }

    private static void write(String key, Set<String> namespaces, Map<ResourceLocation, byte[]> resources) {
        long started = System.nanoTime();
        Path temporary = FILE.resolveSibling(FILE.getFileName() + CacheFiles.TEMP_SUFFIX);
        long bytes = 0;
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream file = Files.newOutputStream(temporary);
                 CheckedOutputStream checked = new CheckedOutputStream(
                         new BufferedOutputStream(file, 1 << 16), new CRC32());
                 DataOutputStream out = new DataOutputStream(checked)) {
                out.writeLong(MAGIC);
                out.writeInt(FORMAT);
                out.writeUTF(key);
                out.writeInt(namespaces.size());
                for (String namespace : namespaces) {
                    out.writeUTF(namespace);
                }
                out.writeInt(resources.size());
                for (Map.Entry<ResourceLocation, byte[]> entry : resources.entrySet()) {
                    out.writeUTF(entry.getKey().getNamespace());
                    out.writeUTF(entry.getKey().getPath());
                    out.writeInt(entry.getValue().length);
                    out.write(entry.getValue());
                    bytes += entry.getValue().length;
                }
                out.flush();
                out.writeLong(checked.getChecksum().getValue());
            }
            AtomicFiles.moveIntoPlace(temporary, FILE);
            VHAccelerator.LOGGER.info(
                    "Stored Every Compat's generated client pack ({} resources, {} KB) in {} ms",
                    resources.size(),
                    bytes / 1024,
                    (System.nanoTime() - started) / 1_000_000L
            );
        } catch (IOException | RuntimeException failure) {
            VHAccelerator.LOGGER.warn("Could not store Every Compat's generated pack", failure);
        }
    }

    private static String digestFile(Path path) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String digest(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (encoded.length >>> 24));
                digest.update((byte) (encoded.length >>> 16));
                digest.update((byte) (encoded.length >>> 8));
                digest.update((byte) encoded.length);
                digest.update(encoded);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private record Stored(Set<String> namespaces, Map<ResourceLocation, byte[]> resources) {
    }

    /** Selene 1.17.14 and 1.17.17 share these members. */
    private record Access(
            Field initialized,
            Field pack,
            Field resources,
            Field namespaces,
            Method addPackLogo
    ) {
        private static volatile Access cached;

        static Access of(Object provider) throws ReflectiveOperationException {
            Access access = cached;
            if (access != null) {
                return access;
            }
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> providerType = Class.forName(
                    "net.mehvahdjukaar.selene.resourcepack.RPAwareDynamicResourceProvider", false, loader);
            Class<?> packType = Class.forName(
                    "net.mehvahdjukaar.selene.resourcepack.DynamicResourcePack", false, loader);
            Class<?> texturePackType = Class.forName(
                    "net.mehvahdjukaar.selene.resourcepack.DynamicTexturePack", false, loader);
            Method logo = texturePackType.getDeclaredMethod("addPackLogo");
            logo.setAccessible(true);
            access = new Access(
                    accessible(providerType.getDeclaredField("hasBeenInitialized")),
                    accessible(providerType.getDeclaredField("dynamicPack")),
                    accessible(packType.getDeclaredField("resources")),
                    accessible(packType.getDeclaredField("namespaces")),
                    logo
            );
            cached = access;
            return access;
        }

        private static Field accessible(Field field) {
            field.setAccessible(true);
            return field;
        }

        boolean initialized(Object provider) throws IllegalAccessException {
            return initialized.getBoolean(provider);
        }

        void markInitialized(Object provider) throws IllegalAccessException {
            initialized.setBoolean(provider, true);
        }

        Object pack(Object provider) throws IllegalAccessException {
            return pack.get(provider);
        }

        void addPackLogo(Object pack) throws ReflectiveOperationException {
            if (addPackLogo.getDeclaringClass().isInstance(pack)) {
                addPackLogo.invoke(pack);
            }
        }

        @SuppressWarnings("unchecked")
        Map<ResourceLocation, byte[]> resources(Object pack) throws IllegalAccessException {
            return (Map<ResourceLocation, byte[]>) resources.get(pack);
        }

        @SuppressWarnings("unchecked")
        Set<String> namespaces(Object pack) throws IllegalAccessException {
            return (Set<String>) namespaces.get(pack);
        }
    }
}

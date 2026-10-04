package dev.hoyin1600p.vhaccelerator.client.cache.fingerprint;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.concurrent.SharedWorkers;
import dev.hoyin1600p.vhaccelerator.util.Digests;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Fingerprints the client-observable state supplied during a normal Forge
 * login. Cached products must not be published until this fingerprint matches.
 */
public final class LoginStateFingerprint {
    private static final String RECIPE_PAYLOAD_NOT_CAPTURED =
            "recipe-payload-not-captured";
    private static final int SCHEMA_VERSION = 2;
    private static final int FUEL_SCHEMA_VERSION = 4;
    private static final int INGREDIENT_SCHEMA_VERSION = 2;
    private static final int RECIPE_SCHEMA_VERSION = 8;
    private static final Map<String, String> SERVER_CONFIGS =
            new ConcurrentHashMap<>();

    /** Set with the recipe fingerprint: the same digest ignoring result NBT. */
    private static volatile String structuralRecipePayload;
    private static final TagDependentFingerprint RECIPE_FINGERPRINT =
            new TagDependentFingerprint();
    private static volatile CompletableFuture<String> tagPayloadHash;
    private static volatile CompletableFuture<String> localCodeHash;
    private static volatile CompletableFuture<String> localConfigHash;
    private static volatile long localConfigRevision = -1;

    private LoginStateFingerprint() {
    }

    public static void beginConnection() {
        RECIPE_FINGERPRINT.clear();
        structuralRecipePayload = null;
        tagPayloadHash = null;
        SERVER_CONFIGS.clear();
        refreshLocalConfigs();
    }

    /** Restat registered inputs once at a genuine data sync, not per JEI category. */
    public static synchronized void refreshLocalConfigs() { localConfigHash = null; }

    public static void prewarmLocalEnvironment() {
        if (localCodeHash != null) {
            return;
        }

        synchronized (LoginStateFingerprint.class) {
            if (localCodeHash != null) {
                return;
            }

            List<String> inputs = new ArrayList<>();
            inputs.add("minecraft=" + SharedConstants.getCurrentVersion().getName());
            ModList.get().getMods().stream()
                    .sorted(Comparator.comparing(IModInfo::getModId))
                    .map(mod -> "mod=" + mod.getModId() + "@" + mod.getVersion())
                    .forEach(inputs::add);
            ForgeRegistries.ITEMS.getKeys().stream()
                    .sorted(Comparator.comparing(Object::toString))
                    .map(key -> "item=" + key)
                    .forEach(inputs::add);

            localCodeHash = backgroundDigest(
                    () -> digestCodeEnvironment(inputs),
                    "VH Accelerator code fingerprint"
            );
        }
    }

    public static void captureRecipePacket(
            ClientboundUpdateRecipesPacket packet
    ) {
        // Recipe packets precede tag application. Expanding ingredients here
        // would observe the previous world's tags and populate stale arrays.
        RECIPE_FINGERPRINT.receiveRecipes(() -> canonicalRecipePayload(packet));
        tagPayloadHash = null;
    }

    /**
     * Starts the synchronized recipe fingerprint in the background once the
     * join's tags are applied, so JEI's recipe registration (which needs it
     * to validate the persistent recipe index) does not compute it on the
     * render thread.
     */
    public static void prefetchRecipeFingerprint() {
        RECIPE_FINGERPRINT.prefetch();
    }

    private static String canonicalRecipePayload(
            ClientboundUpdateRecipesPacket packet
    ) {
        long fingerprintStarted = System.nanoTime();
        try {
            // Recipes are independent; entries keep packet order either way.
            List<CanonicalRecipeSemantics.Entry> semanticEntries =
                    packet.getRecipes().parallelStream()
                            .map(LoginStateFingerprint::semanticEntry)
                            .toList();
            String[] digests = CanonicalRecipeSemantics.digestPair(semanticEntries);
            structuralRecipePayload = digests[1];
            if (dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig.debugDiagnosticsEnabled()) {
                VHAccelerator.LOGGER.info(
                        "[debug] Synchronized recipe fingerprint over {} recipes in {} ms",
                        semanticEntries.size(),
                        (System.nanoTime() - fingerprintStarted) / 1_000_000L
                );
            }
            return digests[0];
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not build the canonical synchronized recipe "
                            + "fingerprint; bypassing persistent recipe caches",
                    failure
            );
            return null;
        }
    }

    private static CanonicalRecipeSemantics.Entry semanticEntry(Recipe<?> recipe) {
        ResourceLocation serializerId = Registry.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        if (serializerId == null) {
            throw new IllegalStateException(
                    "Recipe serializer is not registered: " + recipe.getId()
            );
        }
        List<List<String>> ingredients = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null) {
                ingredients.add(List.of("null"));
                continue;
            }
            ingredients.add(Arrays.stream(ingredient.getItems())
                    .map(LoginStateFingerprint::stackSemantics)
                    .toList());
        }
        return new CanonicalRecipeSemantics.Entry(
                recipe.getId().toString(),
                serializerId.toString(),
                recipe.getClass().getName(),
                recipe.isSpecial(),
                recipe.getGroup() == null ? "" : recipe.getGroup(),
                stackSemantics(recipe.getResultItem()),
                ingredients
        );
    }

    private static String stackSemantics(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        ResourceLocation itemId = Registry.ITEM.getKey(stack.getItem());
        return (itemId == null ? "unregistered" : itemId.toString())
                + "|" + stack.getCount()
                + "|" + stack.getDamageValue()
                + "|" + canonicalTag(stack.getTag());
    }

    private static String canonicalTag(Tag tag) {
        if (tag == null) {
            return "null";
        }
        if (tag instanceof CompoundTag compound) {
            StringBuilder value = new StringBuilder("{");
            compound.getAllKeys().stream().sorted().forEach(key -> value
                    .append(key.length())
                    .append(':')
                    .append(key)
                    .append('=')
                    .append(canonicalTag(compound.get(key)))
                    .append(';'));
            return value.append('}').toString();
        }
        if (tag instanceof ListTag list) {
            StringBuilder value = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                value.append(canonicalTag(list.get(index))).append(';');
            }
            return value.append(']').toString();
        }
        return tag.getId() + ":" + tag;
    }

    public static void captureCanonicalItemTags(
            ClientboundUpdateTagsPacket packet
    ) {
        RECIPE_FINGERPRINT.receiveTags();
        TagNetworkSerialization.NetworkPayload payload =
                packet.getTags().get(Registry.ITEM_REGISTRY);
        if (payload == null) {
            tagPayloadHash = CompletableFuture.completedFuture(
                    digestStrings(List.of())
            );
            VHAccelerator.LOGGER.info(
                    "Captured an empty decoded item-tag payload for cache validation"
            );
            return;
        }

        Map<ResourceLocation, IntList> decoded =
                ((TagPayloadView) (Object) payload)
                        .vhaccelerator$getTags();
        List<String> canonicalInputs = new ArrayList<>();
        decoded.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    canonicalInputs.add("tag=" + entry.getKey());
                    entry.getValue().intStream()
                            .mapToObj(Registry.ITEM::byId)
                            .filter(Objects::nonNull)
                            .map(Registry.ITEM::getKey)
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            // A tag is a set; Vault Hunters re-adds its config
                            // tags' members on every reload, so the synced
                            // lists gain duplicates from one join to the next.
                            .distinct()
                            .sorted()
                            .map(itemId -> "member=" + itemId)
                            .forEach(canonicalInputs::add);
                });
        VHAccelerator.LOGGER.info(
                "Captured {} decoded item tags before application for "
                        + "semantic cache validation",
                decoded.size()
        );
        tagPayloadHash = backgroundDigest(
                () -> digestStrings(canonicalInputs),
                "VH Accelerator item-tag fingerprint"
        );
    }

    public static void captureServerConfig(String fileName, byte[] contents) {
        SERVER_CONFIGS.put(fileName, digestBytes(contents));
    }

    public static Snapshot current() {
        return current(false);
    }

    public static Snapshot currentWithRecipes() {
        return current(true);
    }

    private static Snapshot current(boolean requireRecipes) {
        CompletableFuture<String> tagHash = tagPayloadHash;
        if (tagHash == null) {
            return null;
        }
        String recipes = requireRecipes ? RECIPE_FINGERPRINT.current() : null;
        if (requireRecipes && recipes == null) {
            return null;
        }
        if (recipes == null) {
            recipes = RECIPE_PAYLOAD_NOT_CAPTURED;
        }
        String tags = tagHash.join();

        prewarmLocalEnvironment();
        String localCode = localCodeHash.join();
        CompletableFuture<String> configFuture = localConfigHash();
        String localConfigs = configFuture.join();
        if (localConfigs == null || configFuture != localConfigHash
                || !LocalConfigState.isStable(localConfigRevision)) { return null; }
        List<String> serverConfigInputs = new ArrayList<>();
        SERVER_CONFIGS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "config=" + entry.getKey() + ":" + entry.getValue())
                .forEach(serverConfigInputs::add);
        String serverConfigs = digestStrings(serverConfigInputs);

        List<String> fullInputs = new ArrayList<>();
        fullInputs.add("schema=" + SCHEMA_VERSION);
        fullInputs.add("local-code=" + localCode);
        fullInputs.add("local-configs=" + localConfigs);
        fullInputs.add("recipes=" + recipes);
        fullInputs.add("tags=" + tags);
        fullInputs.add("server-configs=" + serverConfigs);

        List<String> fuelInputs = List.of(
                "fuel-schema=" + FUEL_SCHEMA_VERSION,
                "local-code=" + localCode,
                "local-configs=" + localConfigs,
                "tags=" + tags,
                "server-configs=" + serverConfigs
        );
        List<String> ingredientInputs = List.of(
                "ingredient-schema=" + INGREDIENT_SCHEMA_VERSION,
                "local-code=" + localCode,
                "local-configs=" + localConfigs,
                "tags=" + tags,
                "server-configs=" + serverConfigs
        );
        String structural = structuralRecipePayload;
        List<String> recipeIndexInputs = List.of(
                "recipe-index-schema=" + RECIPE_SCHEMA_VERSION,
                "local-code=" + localCode,
                "local-configs=" + localConfigs,
                "recipes-without-result-nbt=" + (structural == null ? recipes : structural),
                "tags=" + tags,
                "server-configs=" + serverConfigs
        );
        List<String> recipeInputs = List.of(
                "recipe-schema=" + RECIPE_SCHEMA_VERSION,
                "local-code=" + localCode,
                "local-configs=" + localConfigs,
                "recipes=" + recipes,
                "tags=" + tags,
                "server-configs=" + serverConfigs
        );

        String serverIdentity = serverIdentity();
        if (serverIdentity == null) {
            return null;
        }
        return new Snapshot(
                digestStrings(fullInputs),
                digestBytes(serverIdentity.getBytes(StandardCharsets.UTF_8)),
                SERVER_CONFIGS.size(),
                new FuelDependencies(
                        digestStrings(fuelInputs),
                        localCode,
                        tags,
                        serverConfigs
                ),
                new IngredientDependencies(
                        digestStrings(ingredientInputs),
                        localCode,
                        localConfigs,
                        tags,
                        serverConfigs
                ),
                new RecipeDependencies(
                        digestStrings(recipeInputs),
                        localCode,
                        recipes,
                        tags,
                        serverConfigs,
                        digestStrings(recipeIndexInputs)
                )
        );
    }

    /** The server key of the current connection, or null if unknown. */
    public static String currentServerKey() {
        String serverIdentity = serverIdentity();
        return serverIdentity == null
                ? null
                : digestBytes(serverIdentity.getBytes(StandardCharsets.UTF_8));
    }

    private static String serverIdentity() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(Locale.ROOT);
        }
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return "integrated-server";
        }
        return null;
    }

    private static String digestStrings(List<String> values) {
        MessageDigest digest = Digests.sha256();
        for (String value : values) {
            updateDigest(digest, value);
        }
        return toHex(digest.digest());
    }

    private static void updateDigest(
            MessageDigest digest,
            String value
    ) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (encoded.length >>> 24));
        digest.update((byte) (encoded.length >>> 16));
        digest.update((byte) (encoded.length >>> 8));
        digest.update((byte) encoded.length);
        digest.update(encoded);
    }

    private static String digestCodeEnvironment(List<String> baseInputs) {
        List<String> inputs = new ArrayList<>(baseInputs);
        appendModFileMetadata(inputs);
        return digestStrings(inputs);
    }

    private static CompletableFuture<String> localConfigHash() {
        CompletableFuture<String> current = localConfigHash;
        if (current != null && localConfigRevision == LocalConfigState.revision()) {
            return current;
        }
        synchronized (LoginStateFingerprint.class) {
            if (localConfigHash == null || localConfigRevision != LocalConfigState.revision()) {
                long revision = LocalConfigState.revision();
                localConfigRevision = revision;
                localConfigHash = backgroundDigest(
                        () -> {
                            String digest = digestLocalConfigs();
                            return LocalConfigState.isStable(revision) ? digest : null;
                        },
                        "VH Accelerator config fingerprint"
                );
            }
            return localConfigHash;
        }
    }

    private static String digestLocalConfigs() {
        List<String> inputs = new ArrayList<>();
        List<ModConfig> configs = new ArrayList<>();
        for (ModConfig.Type type : List.of(
                ModConfig.Type.CLIENT,
                ModConfig.Type.COMMON
        )) {
            var tracked = ConfigTracker.INSTANCE.configSets().get(type);
            if (tracked == null) {
                continue;
            }
            synchronized (tracked) {
                configs.addAll(tracked);
            }
        }

        configs.sort(
                Comparator.comparing((ModConfig config) ->
                                config.getType().name())
                        .thenComparing(ModConfig::getModId)
                        .thenComparing(ModConfig::getFileName)
        );
        Path configDirectory = FMLPaths.CONFIGDIR.get();
        for (ModConfig config : configs) {
            if (VHAccelerator.MOD_ID.equals(config.getModId())) {
                // VHA's options never change what these caches hold (each cache
                // is bypassed live when its own option is off), so toggling one
                // must not force a slow first join.
                continue;
            }
            Path path = configDirectory.resolve(config.getFileName()).normalize();
            if (!path.startsWith(configDirectory)
                    || !Files.isRegularFile(path)) {
                inputs.add(
                        "registered-local-config-missing="
                                + config.getType()
                                + ":"
                                + config.getModId()
                                + ":"
                                + config.getFileName()
                );
                continue;
            }
            String contentHash;
            try { contentHash = LocalConfigState.digest(path); }
            catch (IOException failure) {
                VHAccelerator.LOGGER.warn("Cannot validate a local config; bypassing persistent login caches", failure);
                return null;
            }
            inputs.add(
                    "registered-local-config="
                            + config.getType()
                            + ":"
                            + config.getModId()
                            + ":"
                            + config.getFileName()
                            + ":"
                            + contentHash
            );
        }
        List<String> scripts = LocalScriptInputs.collect(FMLPaths.GAMEDIR.get());
        if (scripts == null) {
            VHAccelerator.LOGGER.warn(
                    "Cannot validate local pack scripts; bypassing persistent login caches"
            );
            return null;
        }
        inputs.addAll(scripts);
        return digestStrings(inputs);
    }

    private static void appendModFileMetadata(List<String> inputs) {
        Path modsDirectory = FMLPaths.GAMEDIR.get().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.list(modsDirectory)) {
            paths.filter(Files::isRegularFile)
                    .filter(ActiveModFilePolicy::isJar)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        try {
                            inputs.add(
                                    "mod-file="
                                            + path.getFileName()
                                            + ":"
                                            + Files.size(path)
                                            + ":"
                                            + Files.getLastModifiedTime(path).toMillis()
                            );
                        } catch (IOException exception) {
                            inputs.add("mod-file-read-failed=" + path.getFileName());
                        }
                    });
        } catch (IOException exception) {
            inputs.add("mods-directory-read-failed");
        }
    }

    private static String digestBytes(byte[] value) {
        return toHex(Digests.sha256().digest(value));
    }

    private static CompletableFuture<String> backgroundDigest(
            Supplier<String> supplier,
            String threadName
    ) {
        return CompletableFuture.supplyAsync(
                supplier,
                SharedWorkers.io()
        );
    }

    private static String toHex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    public record Snapshot(
            String value,
            String serverKey,
            int synchronizedConfigCount,
            FuelDependencies fuel,
            IngredientDependencies ingredients,
            RecipeDependencies recipes
    ) {
    }

    public record FuelDependencies(
            String value,
            String localCodeHash,
            String tagPayloadHash,
            String serverConfigHash
    ) {
    }

    public record IngredientDependencies(
            String value,
            String localCodeHash,
            String localConfigHash,
            String tagPayloadHash,
            String serverConfigHash
    ) {
    }

    /**
     * {@code indexValue} ignores recipe result NBT; only the JEI recipe index
     * uses it, whose restore re-verifies each cached plan's output.
     */
    public record RecipeDependencies(
            String value,
            String localCodeHash,
            String recipePayloadHash,
            String tagPayloadHash,
            String serverConfigHash,
            String indexValue
    ) {
    }
}

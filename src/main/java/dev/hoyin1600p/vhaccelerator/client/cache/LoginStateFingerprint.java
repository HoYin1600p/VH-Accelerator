package dev.hoyin1600p.vhaccelerator.client.cache;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.mixin.client.TagNetworkPayloadAccessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Registry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
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
    private static final int FUEL_SCHEMA_VERSION = 3;
    private static final int INGREDIENT_SCHEMA_VERSION = 2;
    private static final int RECIPE_SCHEMA_VERSION = 5;
    private static final Map<String, String> SERVER_CONFIGS =
            new ConcurrentHashMap<>();

    private static volatile String recipePayloadHash;
    private static volatile boolean recipePayloadExact;
    private static volatile CompletableFuture<String> tagPayloadHash;
    private static volatile CompletableFuture<String> localCodeHash;
    private static volatile CompletableFuture<String> localConfigHash;

    private LoginStateFingerprint() {
    }

    public static void beginConnection() {
        recipePayloadHash = null;
        recipePayloadExact = false;
        tagPayloadHash = null;
        SERVER_CONFIGS.clear();
    }

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

    public static void captureRecipePayload(ByteBuf buffer) {
        recipePayloadHash = digestReadableBytes(buffer);
        recipePayloadExact = recipePayloadHash != null;
    }

    public static void captureRecipePayloadHash(String payloadHash) {
        if (payloadHash == null) {
            return;
        }
        recipePayloadHash = payloadHash;
        recipePayloadExact = true;
    }

    public static String fingerprintPayload(ByteBuf buffer) {
        return digestReadableBytes(buffer);
    }

    public static void captureTagPayload(ByteBuf buffer) {
        tagPayloadHash = CompletableFuture.completedFuture(
                digestReadableBytes(buffer)
        );
    }

    public static void captureRecipePacket(
            ClientboundUpdateRecipesPacket packet
    ) {
        String canonical = canonicalRecipePayload(packet);
        if (canonical != null) {
            recipePayloadHash = canonical;
            recipePayloadExact = true;
            return;
        }
        if (recipePayloadExact && recipePayloadHash != null) {
            return;
        }
        String serialized = digestPacket(packet::write);
        if (serialized != null) {
            recipePayloadHash = serialized;
            recipePayloadExact = true;
            return;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Recipe<?>> recipes = new ArrayList<>(packet.getRecipes());
            recipes.sort(Comparator.comparing(
                    recipe -> recipe.getId().toString()
            ));
            updateDigest(digest, "recipe-count=" + recipes.size());
            for (Recipe<?> recipe : recipes) {
                updateDigest(digest, "id=" + recipe.getId());
                updateDigest(
                        digest,
                        "serializer=" + Registry.RECIPE_SERIALIZER.getKey(
                                recipe.getSerializer()
                        )
                );
                updateDigest(digest, "class=" + recipe.getClass().getName());
                updateDigest(digest, "special=" + recipe.isSpecial());

                ItemStack result = recipe.getResultItem();
                if (result == null) {
                    updateDigest(digest, "result=null");
                } else {
                    updateDigest(
                            digest,
                            "result=" + Registry.ITEM.getKey(result.getItem())
                    );
                    updateDigest(
                            digest,
                            "result-count=" + result.getCount()
                    );
                }

                List<Ingredient> ingredients = recipe.getIngredients();
                if (ingredients == null) {
                    updateDigest(digest, "ingredients=null");
                    continue;
                }
                updateDigest(
                        digest,
                        "ingredient-count=" + ingredients.size()
                );
                for (Ingredient ingredient : ingredients) {
                    updateDigest(
                            digest,
                            "ingredient-class="
                                    + (ingredient == null
                                    ? "null"
                                    : ingredient.getClass().getName())
                    );
                }
            }
            recipePayloadHash = toHex(digest.digest());
            recipePayloadExact = false;
        } catch (RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not build the structural synchronized recipe "
                            + "fingerprint; falling back to packet serialization",
                    exception
            );
            recipePayloadHash = digestPacket(packet::write);
            recipePayloadExact = recipePayloadHash != null;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable",
                    exception
            );
        }
    }

    private static String canonicalRecipePayload(
            ClientboundUpdateRecipesPacket packet
    ) {
        try {
            return CanonicalRecipePayloadFingerprint.digest(
                    packet.getRecipes(),
                    recipe -> recipe.getId().toString(),
                    recipe -> {
                        ResourceLocation serializerId =
                                Registry.RECIPE_SERIALIZER.getKey(
                                        recipe.getSerializer()
                                );
                        if (serializerId == null) {
                            throw new IllegalStateException(
                                    "Recipe serializer is not registered: "
                                            + recipe.getId()
                            );
                        }
                        return serializerId.toString();
                    },
                    LoginStateFingerprint::serializeRecipe
            );
        } catch (RuntimeException | LinkageError failure) {
            VHAccelerator.LOGGER.warn(
                    "Could not build the canonical synchronized recipe "
                            + "fingerprint; retaining the raw packet "
                            + "fingerprint",
                    failure
            );
            return null;
        }
    }

    private static byte[] serializeRecipe(Recipe<?> recipe) {
        ByteBuf storage = Unpooled.buffer();
        try {
            FriendlyByteBuf buffer = new FriendlyByteBuf(storage);
            writeRecipe(
                    recipe.getSerializer(),
                    buffer,
                    recipe
            );
            byte[] payload = new byte[storage.readableBytes()];
            storage.getBytes(storage.readerIndex(), payload);
            return payload;
        } finally {
            storage.release();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void writeRecipe(
            RecipeSerializer serializer,
            FriendlyByteBuf buffer,
            Recipe recipe
    ) {
        serializer.toNetwork(buffer, recipe);
    }

    public static void captureTagPacket(ClientboundUpdateTagsPacket packet) {
        tagPayloadHash = CompletableFuture.completedFuture(
                digestPacket(packet::write)
        );
    }

    public static void captureCanonicalItemTags(
            ClientboundUpdateTagsPacket packet
    ) {
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
                ((TagNetworkPayloadAccessor) (Object) payload)
                        .vhaccelerator$getTags();
        List<String> canonicalInputs = new ArrayList<>();
        decoded.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    canonicalInputs.add("tag=" + entry.getKey());
                    entry.getValue().intStream()
                            .mapToObj(Registry.ITEM::byId)
                            .filter(java.util.Objects::nonNull)
                            .map(Registry.ITEM::getKey)
                            .filter(java.util.Objects::nonNull)
                            .sorted(Comparator.comparing(Object::toString))
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
        String recipes = recipePayloadHash;
        CompletableFuture<String> tagHash = tagPayloadHash;
        if (tagHash == null || (requireRecipes && recipes == null)) {
            return null;
        }
        if (recipes == null) {
            recipes = RECIPE_PAYLOAD_NOT_CAPTURED;
        }
        String tags = tagHash.join();

        prewarmLocalEnvironment();
        String localCode = localCodeHash.join();
        String localConfigs = localConfigHash().join();
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
        List<String> recipeInputs = List.of(
                "recipe-schema=" + RECIPE_SCHEMA_VERSION,
                "local-code=" + localCode,
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
                        serverConfigs
                )
        );
    }

    private static String serverIdentity() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isBlank()) {
            return server.ip.trim().toLowerCase(java.util.Locale.ROOT);
        }
        if (Minecraft.getInstance().hasSingleplayerServer()) {
            return "integrated-server";
        }
        return null;
    }

    private static String digestReadableBytes(ByteBuf buffer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int index = buffer.readerIndex();
            int length = buffer.readableBytes();
            for (ByteBuffer region : buffer.nioBuffers(index, length)) {
                digest.update(region);
            }
            return toHex(digest.digest());
        } catch (RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not fingerprint a synchronized server payload",
                    exception
            );
            return null;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String digestPacket(
            java.util.function.Consumer<FriendlyByteBuf> writer
    ) {
        ByteBuf storage = Unpooled.buffer();
        try {
            FriendlyByteBuf buffer = new FriendlyByteBuf(storage);
            writer.accept(buffer);
            return digestReadableBytes(buffer);
        } catch (RuntimeException exception) {
            VHAccelerator.LOGGER.warn(
                    "Could not serialize a synchronized server payload "
                            + "for cache validation",
                    exception
            );
            return null;
        } finally {
            storage.release();
        }
    }

    private static String digestStrings(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                updateDigest(digest, value);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
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
        if (current != null) {
            return current;
        }
        synchronized (LoginStateFingerprint.class) {
            if (localConfigHash == null) {
                localConfigHash = backgroundDigest(
                        LoginStateFingerprint::digestLocalConfigs,
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
            inputs.add(
                    "registered-local-config="
                            + config.getType()
                            + ":"
                            + config.getModId()
                            + ":"
                            + config.getFileName()
                            + ":"
                            + digestFile(path)
            );
        }
        return digestStrings(inputs);
    }

    private static void appendModFileMetadata(List<String> inputs) {
        Path modsDirectory = FMLPaths.GAMEDIR.get().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.list(modsDirectory)) {
            paths.filter(Files::isRegularFile)
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

    private static String digestFile(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(path)) {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (IOException exception) {
            return "read-failed";
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String digestBytes(byte[] value) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static CompletableFuture<String> backgroundDigest(
            java.util.function.Supplier<String> supplier,
            String threadName
    ) {
        return CompletableFuture.supplyAsync(
                supplier,
                runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    thread.start();
                }
        );
    }

    private static String toHex(byte[] value) {
        return java.util.HexFormat.of().formatHex(value);
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

    public record RecipeDependencies(
            String value,
            String localCodeHash,
            String recipePayloadHash,
            String tagPayloadHash,
            String serverConfigHash
    ) {
    }
}

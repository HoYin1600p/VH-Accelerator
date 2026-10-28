package dev.hoyin1600p.vhaccelerator.diagnostics;

import com.google.gson.*;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.fml.loading.FMLPaths;

/** Explicit, operator-triggered snapshots; never runs a reload or keeps game references. */
public final class RecipeDifferentialDump {
    private RecipeDifferentialDump() { }

    public static int execute(CommandSourceStack source) {
        try {
            if (!source.getServer().isSameThread()) {
                throw new IllegalStateException("Recipe snapshots require the server thread");
            }
            JsonObject dump = snapshot(source);
            Path directory = FMLPaths.GAMEDIR.get().resolve("logs/vhaccelerator");
            Files.createDirectories(directory);
            Path output = directory.resolve("recipe-dump-" + System.currentTimeMillis()
                    + "-" + UUID.randomUUID() + ".json");
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(dump)
                            + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            source.sendSuccess(new TextComponent("[VH Accelerator] Recipe snapshot: "
                    + output.getFileName() + " (capture complete=" + dump.get("complete")
                    + ")"), false);
            return dump.get("complete").getAsBoolean() ? 1 : 0;
        } catch (Exception | LinkageError failure) {
            VHAccelerator.LOGGER.warn("Recipe differential snapshot failed: {}", failure.toString());
            source.sendFailure(new TextComponent("[VH Accelerator] Recipe snapshot failed: "
                    + failure.getClass().getSimpleName()));
            return 0;
        }
    }

    private static JsonObject snapshot(CommandSourceStack source) throws Exception {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        JsonObject run = new JsonObject();
        run.addProperty("timestamp", Instant.now().toString());
        run.addProperty("compareMode", VHAcceleratorConfig.compareModeEnabled());
        run.addProperty("indexedRemovalsConfigured", VHAcceleratorConfig.COMMON.indexCraftTweakerRecipeRemovals.get());
        root.add("run", run);
        root.addProperty("coverage", "NETWORK_AND_PUBLIC_VIEWS");
        root.addProperty("coverageNote", "Full serializer network bytes, ingredient views and typed result NBT; "
                + "custom serializer server-only state is not generically observable.");
        JsonArray errors = new JsonArray();
        JsonArray uncovered = new JsonArray();
        JsonObject scripts = new JsonObject();
        Path scriptRoot = FMLPaths.GAMEDIR.get().resolve("scripts");
        if (!Files.isDirectory(scriptRoot)) {
            errors.add("scripts directory is missing");
        } else {
            try (Stream<Path> paths = Files.walk(scriptRoot)) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                    scripts.addProperty(scriptRoot.relativize(path).toString().replace('\\', '/'),
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
                }
            }
        }
        root.add("scripts", scripts);
        RecipeManager manager = source.getServer().getRecipeManager();
        JsonArray recipes = new JsonArray();
        for (var id : manager.getRecipeIds().toList()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", id.toString());
            try {
                Recipe<?> recipe = manager.byKey(id).orElseThrow();
                RecipeSerializer<?> serializer = recipe.getSerializer();
                String serializerId = Objects.requireNonNull(Registry.RECIPE_SERIALIZER.getKey(serializer)).toString();
                entry.addProperty("serializer", serializerId);
                entry.addProperty("serializerClass", serializer.getClass().getName());
                entry.addProperty("class", recipe.getClass().getName());
                entry.addProperty("type", Objects.requireNonNull(Registry.RECIPE_TYPE.getKey(recipe.getType())).toString());
                entry.addProperty("group", recipe.getGroup());
                entry.addProperty("special", recipe.isSpecial());
                entry.addProperty("network", networkRecipe(serializer, recipe));
                entry.add("displayResult", stack(recipe.getResultItem()));
                JsonArray ingredients = new JsonArray();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    JsonObject value = new JsonObject();
                    value.addProperty("class", ingredient.getClass().getName());
                    value.add("json", ingredient.toJson());
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        ingredient.toNetwork(buffer);
                        value.addProperty("network", bytes(buffer));
                    } finally { buffer.release(); }
                    JsonArray candidates = new JsonArray();
                    for (ItemStack item : ingredient.getItems()) candidates.add(stack(item));
                    value.add("candidates", candidates);
                    ingredients.add(value);
                }
                entry.add("ingredients", ingredients);
                // A custom class may omit arbitrary server state from its network format.
                if (!serializer.getClass().getName().startsWith("net.minecraft.world.item.crafting.")
                        || !recipe.getClass().getName().startsWith("net.minecraft.world.item.crafting.")) {
                    uncovered.add(id + " (" + serializerId + ")");
                }
            } catch (Exception | LinkageError failure) {
                errors.add(id + ": " + failure.getClass().getName() + ": " + failure.getMessage());
            }
            recipes.add(entry);
        }
        root.add("recipes", recipes);
        JsonObject typeOrder = new JsonObject();
        for (var typeEntry : Registry.RECIPE_TYPE.entrySet()) {
            JsonArray ids = new JsonArray();
            for (Recipe<?> recipe : allRecipes(manager, typeEntry.getValue())) ids.add(recipe.getId().toString());
            if (ids.size() != 0) typeOrder.add(typeEntry.getKey().location().toString(), ids);
        }
        root.add("typeOrder", typeOrder);
        JsonObject tags = new JsonObject();
        for (RegistryAccess.RegistryEntry<?> registry : source.getServer().registryAccess().registries().toList()) {
            tags.add(registry.key().location().toString(), tags(registry.value()));
        }
        root.add("tags", tags);
        root.add("errors", errors);
        root.add("uncoveredServerSemantics", uncovered);
        root.addProperty("complete", errors.size() == 0);
        root.addProperty("hiddenServerStateProven", false);
        return root;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Recipe<?>> allRecipes(RecipeManager manager, RecipeType<?> type) {
        return (List) manager.getAllRecipesFor((RecipeType) type);
    }

    private static <T> JsonObject tags(Registry<T> registry) {
        JsonObject result = new JsonObject();
        registry.getTags().forEach(pair -> {
            JsonArray values = new JsonArray();
            pair.getSecond().stream().forEach(holder ->
                    values.add(Objects.requireNonNull(registry.getKey(holder.value())).toString()));
            result.add(pair.getFirst().location().toString(), values);
        });
        return result;
    }

    private static JsonElement stack(ItemStack stack) {
        JsonObject result = new JsonObject();
        result.addProperty("item", Objects.requireNonNull(Registry.ITEM.getKey(stack.getItem())).toString());
        result.addProperty("count", stack.getCount());
        result.add("nbt", TypedNbtJson.encode(stack.save(new CompoundTag())));
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String networkRecipe(RecipeSerializer serializer, Recipe<?> recipe) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            return bytes(buffer);
        } finally { buffer.release(); }
    }

    private static String bytes(FriendlyByteBuf buffer) {
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

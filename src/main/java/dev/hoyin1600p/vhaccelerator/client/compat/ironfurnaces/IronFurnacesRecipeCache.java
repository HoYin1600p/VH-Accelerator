package dev.hoyin1600p.vhaccelerator.client.compat.ironfurnaces;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.cache.LoginStateFingerprint;
import ironfurnaces.init.Registration;
import ironfurnaces.Config;
import ironfurnaces.jei.RecipeCategoryGeneratorBlasting;
import ironfurnaces.jei.RecipeCategoryGeneratorRegular;
import ironfurnaces.jei.RecipeCategoryGeneratorSmoking;
import ironfurnaces.recipes.GeneratorRecipe;
import ironfurnaces.recipes.SimpleGeneratorRecipe;
import ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class IronFurnacesRecipeCache {
    private static List<SimpleGeneratorRecipe> regularRecipes;
    private static String regularFingerprint;
    private static List<SimpleGeneratorRecipe> smokingRecipes;
    private static BuildState menuBuild;
    private static boolean menuPrecompileAttempted;
    private static PrecompileStatus precompileStatus = PrecompileStatus.notStarted();

    private IronFurnacesRecipeCache() {
    }

    public static void beginMenuPrecompile() {
        LoginStateFingerprint.prewarmLocalEnvironment();
        PersistentFuelCache.prewarm();

        Minecraft minecraft = Minecraft.getInstance();
        if (menuPrecompileAttempted
                || smokingRecipes != null
                || !minecraft.isSameThread()
                || minecraft.level != null
                || !Config.enableJeiPlugin.get()) {
            return;
        }

        menuPrecompileAttempted = true;
        menuBuild = new BuildState(List.copyOf(ForgeRegistries.ITEMS.getValues()));
        precompileStatus = menuBuild.snapshot(PrecompilePhase.RUNNING);
        VHAccelerator.LOGGER.info(
                "Started frame-budgeted Iron Furnaces smoking-list precompile for {} items",
                menuBuild.items.size()
        );
    }

    public static void runMenuPrecompileSlice(int budgetMillis) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()
                || minecraft.level != null
                || menuBuild == null
                || smokingRecipes != null) {
            return;
        }

        BuildState build = menuBuild;
        long sliceStarted = System.nanoTime();
        long deadline = sliceStarted + budgetMillis * 1_000_000L;
        try {
            do {
                processNextSmokingItem(build);
            } while (!build.isComplete() && System.nanoTime() < deadline);

            long sliceNanos = System.nanoTime() - sliceStarted;
            build.workNanos += sliceNanos;
            build.maxSliceNanos = Math.max(build.maxSliceNanos, sliceNanos);
            if (build.isComplete()) {
                publishSmoking(build, "menu precompile");
            } else {
                precompileStatus = build.snapshot(PrecompilePhase.RUNNING);
            }
        } catch (Throwable throwable) {
            failMenuPrecompile(throwable);
        }
    }

    public static PrecompileStatus precompileStatus() {
        BuildState build = menuBuild;
        if (build != null && smokingRecipes == null) {
            return build.snapshot(PrecompilePhase.RUNNING);
        }
        return precompileStatus;
    }

    public static void beginConnection() {
        regularRecipes = null;
        regularFingerprint = null;
    }

    public static boolean registerRecipes(IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread() || minecraft.level == null) {
            return false;
        }

        try {
            ensureBuilt();
            registration.addRecipes(
                    (Collection<?>) regularRecipes,
                    RecipeCategoryGeneratorRegular.UID
            );

            List<GeneratorRecipe> generatorRecipes = List.copyOf(
                    minecraft.level.getRecipeManager().getAllRecipesFor(
                            Registration.RecipeTypes.GENERATOR
                    )
            );
            registration.addRecipes(
                    (Collection<?>) generatorRecipes,
                    RecipeCategoryGeneratorBlasting.UID
            );
            registration.addRecipes(
                    (Collection<?>) smokingRecipes,
                    RecipeCategoryGeneratorSmoking.UID
            );
            return true;
        } catch (Throwable throwable) {
            regularRecipes = null;
            smokingRecipes = null;
            menuBuild = null;
            precompileStatus = PrecompileStatus.failed();
            VHAccelerator.LOGGER.warn(
                    "Iron Furnaces JEI recipe cache failed; using the mod's original scan",
                    throwable
            );
            return false;
        }
    }

    private static void ensureBuilt() {
        LoginStateFingerprint.Snapshot fingerprint =
                LoginStateFingerprint.current();
        if (regularRecipes != null
                && smokingRecipes != null
                && (fingerprint == null
                || fingerprint.fuel().value().equals(regularFingerprint))) {
            VHAccelerator.LOGGER.debug("Reusing cached Iron Furnaces JEI fuel lists");
            return;
        }

        long started = System.nanoTime();
        if (smokingRecipes == null) {
            if (menuBuild != null) {
                finishSmokingPrecompile();
            } else {
                List<SimpleGeneratorRecipe> smoking = new ArrayList<>();
                for (Item item : ForgeRegistries.ITEMS.getValues()) {
                    addSmokingRecipe(item, smoking);
                }
                smokingRecipes = List.copyOf(smoking);
            }
        }

        regularRecipes = null;
        regularFingerprint = null;
        if (fingerprint != null
                && VHAcceleratorClientConfig.VALUES
                        .persistentIronFurnacesFuelCache
                        .get()
                && restorePersistentFuelList(fingerprint, started)) {
            return;
        }

        List<SimpleGeneratorRecipe> regular = new ArrayList<>();
        List<PersistentFuelCache.FuelEntry> persistentEntries =
                new ArrayList<>();
        List<SimpleGeneratorRecipe> smoking = smokingRecipes == null
                ? new ArrayList<>()
                : null;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            ItemStack stack = new ItemStack(item);
            int burnTime = BlockIronFurnaceTileBase.getBurnTime(stack);
            if (burnTime > 0) {
                regular.add(new SimpleGeneratorRecipe(burnTime * 20, stack));
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
                if (itemId != null) {
                    persistentEntries.add(new PersistentFuelCache.FuelEntry(
                            itemId.toString(),
                            burnTime
                    ));
                }
            }

            if (smoking != null) {
                addSmokingRecipe(item, smoking);
            }
        }

        regularRecipes = List.copyOf(regular);
        regularFingerprint = fingerprint == null
                ? null
                : fingerprint.fuel().value();
        if (smoking != null) {
            smokingRecipes = List.copyOf(smoking);
        }
        VHAccelerator.LOGGER.info(
                "Cached active-world Iron Furnaces JEI lists in {} ms "
                        + "({} fuel, {} smoking recipes)",
                (System.nanoTime() - started) / 1_000_000L,
                regularRecipes.size(),
                smokingRecipes.size()
        );
        if (fingerprint != null
                && VHAcceleratorClientConfig.VALUES
                        .persistentIronFurnacesFuelCache
                        .get()) {
            PersistentFuelCache.save(
                    fingerprint.serverKey(),
                    fingerprint.fuel(),
                    persistentEntries
            );
        } else if (fingerprint == null) {
            VHAccelerator.LOGGER.info(
                    "Iron Furnaces fuel results remain session-only because "
                            + "the server recipe/tag fingerprint was incomplete"
            );
        }
    }

    private static boolean restorePersistentFuelList(
            LoginStateFingerprint.Snapshot fingerprint,
            long started
    ) {
        PersistentFuelCache.LookupResult lookup = PersistentFuelCache.find(
                fingerprint.serverKey(),
                fingerprint.fuel()
        );
        if (!lookup.hit()) {
            VHAccelerator.LOGGER.info(
                    "Persistent Iron Furnaces fuel cache miss because {}; "
                            + "performing the active-world scan",
                    lookup.missReason()
            );
            return false;
        }
        PersistentFuelCache.CachedFuelList cached = lookup.cached();

        List<SimpleGeneratorRecipe> restored =
                new ArrayList<>(cached.entries().size());
        for (PersistentFuelCache.FuelEntry entry : cached.entries()) {
            ResourceLocation itemId = ResourceLocation.tryParse(entry.itemId());
            if (itemId == null || !ForgeRegistries.ITEMS.containsKey(itemId)) {
                VHAccelerator.LOGGER.warn(
                        "Persistent Iron Furnaces cache references missing item {}; "
                                + "rebuilding it",
                        entry.itemId()
                );
                return false;
            }
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item == null) {
                return false;
            }
            restored.add(new SimpleGeneratorRecipe(
                    entry.burnTime() * 20,
                    new ItemStack(item)
            ));
        }

        regularRecipes = List.copyOf(restored);
        regularFingerprint = fingerprint.fuel().value();
        VHAccelerator.LOGGER.info(
                "Restored {} authoritative Iron Furnaces fuel entries in {} ms "
                        + "after matching tags, registry, mods, and "
                        + "{} synchronized server config(s)",
                regularRecipes.size(),
                (System.nanoTime() - started) / 1_000_000L,
                fingerprint.synchronizedConfigCount()
        );
        return true;
    }

    private static void finishSmokingPrecompile() {
        BuildState build = menuBuild;
        long synchronousStarted = System.nanoTime();
        while (!build.isComplete()) {
            processNextSmokingItem(build);
        }
        build.workNanos += System.nanoTime() - synchronousStarted;
        publishSmoking(build, "JEI registration");
    }

    private static void processNextSmokingItem(BuildState build) {
        Item item = build.items.get(build.cursor++);
        addSmokingRecipe(item, build.smoking);
    }

    private static void addSmokingRecipe(
            Item item,
            List<SimpleGeneratorRecipe> smoking
    ) {
        FoodProperties food = item.getFoodProperties();
        if (food != null && food.getNutrition() > 0) {
            ItemStack smokingStack = new ItemStack(item);
            smoking.add(new SimpleGeneratorRecipe(
                    BlockIronFurnaceTileBase.getSmokingBurn(smokingStack) * 20,
                    smokingStack
            ));
        }
    }

    private static void publishSmoking(BuildState build, String source) {
        smokingRecipes = List.copyOf(build.smoking);
        precompileStatus = build.snapshot(PrecompilePhase.COMPLETED);
        menuBuild = null;
        VHAccelerator.LOGGER.info(
                "Published server-independent Iron Furnaces smoking list from {} "
                        + "in {} ms wall / {} ms work (max slice {} μs, {} recipes)",
                source,
                precompileStatus.elapsedMillis(),
                precompileStatus.workMillis(),
                precompileStatus.maxSliceMicros(),
                smokingRecipes.size()
        );
    }

    private static void failMenuPrecompile(Throwable throwable) {
        menuBuild = null;
        precompileStatus = PrecompileStatus.failed();
        VHAccelerator.LOGGER.warn(
                "Iron Furnaces menu precompile failed; JEI will use its normal login-time path",
                throwable
        );
    }

    public enum PrecompilePhase {
        NOT_STARTED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record PrecompileStatus(
            PrecompilePhase phase,
            int processedItems,
            int totalItems,
            int percent,
            long elapsedMillis,
            long workMillis,
            long maxSliceMicros
    ) {
        private static PrecompileStatus notStarted() {
            return new PrecompileStatus(
                    PrecompilePhase.NOT_STARTED,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L
            );
        }

        private static PrecompileStatus failed() {
            return new PrecompileStatus(
                    PrecompilePhase.FAILED,
                    0,
                    0,
                    0,
                    0L,
                    0L,
                    0L
            );
        }
    }

    private static final class BuildState {
        private final List<Item> items;
        private final List<SimpleGeneratorRecipe> smoking = new ArrayList<>();
        private final long startedNanos = System.nanoTime();
        private int cursor;
        private long workNanos;
        private long maxSliceNanos;

        private BuildState(List<Item> items) {
            this.items = items;
        }

        private boolean isComplete() {
            return cursor >= items.size();
        }

        private PrecompileStatus snapshot(PrecompilePhase phase) {
            int total = items.size();
            int progress = total == 0 ? 100 : (int) ((long) cursor * 100L / total);
            if (phase == PrecompilePhase.RUNNING) {
                progress = Math.min(progress, 99);
            }
            return new PrecompileStatus(
                    phase,
                    cursor,
                    total,
                    progress,
                    (System.nanoTime() - startedNanos) / 1_000_000L,
                    workNanos / 1_000_000L,
                    maxSliceNanos / 1_000L
            );
        }
    }
}

package dev.hoyin1600p.vhaccelerator.compat.crafttweaker.removals;

import static org.junit.jupiter.api.Assertions.*;

import com.blamejared.crafttweaker.api.recipe.RecipeList;
import com.blamejared.crafttweaker.mixin.common.access.recipe.AccessRecipeManager;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class CraftTweakerRecipeMapsTest {
    @Test
    void exactFreshCallerNamesCoverBothSidesAndRejectOtherPublication() {
        for (String caller : java.util.List.of("com.blamejared.crafttweaker.impl.script.ScriptReloadListener",
                "com.blamejared.crafttweaker.impl.script.RecipeManagerScriptLoader")) {
            assertTrue(CraftTweakerRecipeMaps.freshCaller(caller, "fixRecipeManager"));
            assertFalse(CraftTweakerRecipeMaps.freshCaller(caller, "loadScriptsFromManager"));
        }
        assertFalse(CraftTweakerRecipeMaps.freshCaller(getClass().getName(), "fixRecipeManager"));
        assertFalse(CraftTweakerRecipeMaps.freshCaller("com.blamejared.crafttweaker.impl.script.FutureLoader", "fixRecipeManager"));
    }

    @Test
    void productionInstallerKeepsRealRecipeManagerAndRecipeListBindingsCoherent() throws Exception {
        Manager manager = new Manager();
        ResourceLocation first = new ResourceLocation("test:first");
        ResourceLocation second = new ResourceLocation("test:second");
        Recipe<?> recipe = new FixtureRecipe(first);
        HashMap<ResourceLocation, Recipe<?>> inner = new HashMap<>(Map.of(first, recipe));
        HashMap<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> outer = new HashMap<>();
        outer.put(null, inner);
        manager.crafttweaker$setRecipes(outer);
        manager.crafttweaker$setByName(new HashMap<>(Map.of(first, recipe)));
        CraftTweakerRecipeMaps.publishFreshCopies(manager, false, true);
        assertSame(inner, outer.get(null));
        CraftTweakerRecipeMaps.publishFreshCopies(manager, true, false);
        assertSame(inner, outer.get(null));
        CraftTweakerRecipeMaps.publishFreshCopies(manager, true, true);
        Map<ResourceLocation, Recipe<?>> observed = outer.get(null);
        assertInstanceOf(TrackedRecipeMap.class, observed);
        assertSame(observed, manager.crafttweaker$getRecipes().get(null));
        RecipeList<Recipe<?>> list = new RecipeList<>(null, observed, manager.crafttweaker$getByName());
        Field listMap = RecipeList.class.getDeclaredField("recipes");
        listMap.setAccessible(true);
        assertSame(observed, listMap.get(list));
        assertEquals(java.util.List.of(first), manager.getRecipeIds().toList());
        list.add(second, new FixtureRecipe(second));
        assertTrue(manager.getRecipeIds().anyMatch(second::equals));
        assertTrue(manager.byKey(second).isPresent());
        list.removeByRecipeTest(value -> value.getId().equals(first));
        assertFalse(manager.getRecipeIds().anyMatch(first::equals));
        assertFalse(manager.byKey(first).isPresent());
        // Wrappers created later see the same journaled map, rather than a disconnected replacement.
        RecipeList<Recipe<?>> later = new RecipeList<>(null, manager.crafttweaker$getRecipes().get(null), manager.crafttweaker$getByName());
        assertSame(observed, listMap.get(later));
        assertEquals(java.util.List.of(second), later.getAllRecipes().stream().map(Recipe::getId).toList());
    }

    @Test
    void realProviderMixinTargetsItsSetterAndCallsThePublicInstaller() throws Exception {
        String path = "/dev/hoyin1600p/vhaccelerator/mixin/compat/crafttweaker/removals/AccessibleRecipeMapsMixin.class";
        ClassNode node = new ClassNode();
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        var mixin = node.invisibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        assertTrue(mixin.values.toString().contains("com.blamejared.crafttweaker.impl.helper.AccessibleElementsProvider"));
        var handler = node.methods.stream().filter(method -> method.name.equals("vhaccelerator$observeFreshMaps")).findFirst().orElseThrow();
        var injection = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        assertTrue(injection.values.toString().contains("recipeManager(Lnet/minecraft/world/item/crafting/RecipeManager;)V"));
        int calls = 0;
        for (var instruction : handler.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(
                    "dev/hoyin1600p/vhaccelerator/compat/crafttweaker/removals/CraftTweakerRecipeMaps")
                    && call.name.equals("publishFreshCopies")
                    && call.desc.equals("(Lnet/minecraft/world/item/crafting/RecipeManager;)V")) calls++;
        }
        assertEquals(1, calls);
    }

    /** Supplies CT's accessor contract over the actual Minecraft fields without bootstrapping Sponge. */
    private static final class Manager extends RecipeManager implements AccessRecipeManager {
        private static Field field(String name) {
            try { Field field = RecipeManager.class.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        private static final Field RECIPES = field("recipes");
        private static final Field BY_NAME = field("byName");
        private Object read(Field field) {
            try { return field.get(this); } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        private void write(Field field, Object value) {
            try { field.set(this, value); } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
        }
        @SuppressWarnings("unchecked")
        @Override public Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> crafttweaker$getRecipes() {
            return (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) read(RECIPES);
        }
        @Override public void crafttweaker$setRecipes(Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> value) { write(RECIPES, value); }
        @SuppressWarnings("unchecked")
        @Override public Map<ResourceLocation, Recipe<?>> crafttweaker$getByName() { return (Map<ResourceLocation, Recipe<?>>) read(BY_NAME); }
        @Override public void crafttweaker$setByName(Map<ResourceLocation, Recipe<?>> value) { write(BY_NAME, value); }
    }

    private record FixtureRecipe(ResourceLocation id) implements Recipe<Container> {
        @Override public boolean matches(Container container, Level level) { throw unexpected(); }
        @Override public ItemStack assemble(Container container) { throw unexpected(); }
        @Override public boolean canCraftInDimensions(int width, int height) { throw unexpected(); }
        @Override public ItemStack getResultItem() { throw unexpected(); }
        @Override public ResourceLocation getId() { return id; }
        @Override public RecipeSerializer<?> getSerializer() { throw unexpected(); }
        @Override public RecipeType<?> getType() { throw unexpected(); }
        private AssertionError unexpected() { return new AssertionError("Unexpected fixture recipe callback"); }
    }
}

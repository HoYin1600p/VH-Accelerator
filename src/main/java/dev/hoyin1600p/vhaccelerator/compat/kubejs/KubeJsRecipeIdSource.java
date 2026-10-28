package dev.hoyin1600p.vhaccelerator.compat.kubejs;

import dev.latvian.mods.kubejs.recipe.RecipeJS;
import dev.latvian.mods.kubejs.recipe.filter.IDFilter;
import dev.latvian.mods.kubejs.recipe.filter.RecipeFilter;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Recipe id table for {@link KubeJsRecipeIndex}. {@code IDFilter.test} is
 * {@code recipe.getOrCreateId().equals(id)}, so the recipes it accepts are exactly those whose id
 * field equals the filter's id. A recipe without an id is left unclassified (always a candidate), so
 * {@code getOrCreateId} still runs for it as before.
 */
final class KubeJsRecipeIdSource implements KubeJsRecipeIndex.Source<ResourceLocation> {
    private static final Field FILTER_ID = filterId();
    private ResourceLocation[] ids = new ResourceLocation[0];

    private static Field filterId() {
        try {
            Field field = IDFilter.class.getDeclaredField("id");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    @Override
    public String name() {
        return "recipe id";
    }

    @Override
    public ResourceLocation keyOf(RecipeFilter filter) {
        if (FILTER_ID == null || filter.getClass() != IDFilter.class) {
            return null;
        }
        try {
            return (ResourceLocation) FILTER_ID.get(filter);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return null;
        }
    }

    @Override
    public void snapshot(List<RecipeJS> recipes, KubeJsRecipeIndex.Sink<ResourceLocation> sink) {
        ResourceLocation[] read = new ResourceLocation[recipes.size()];
        for (int i = 0; i < read.length; i++) {
            ResourceLocation id = recipes.get(i).id;
            read[i] = id;
            if (id == null) {
                sink.unknown(i);
            } else {
                sink.put(i, id);
            }
        }
        ids = read;
    }

    @Override
    public boolean isCurrent(List<RecipeJS> recipes) {
        ResourceLocation[] known = ids;
        if (recipes.size() != known.length) {
            return false;
        }
        return KubeJsRecipeIndex.allMatch(known.length, i -> recipes.get(i).id == known[i]);
    }
}

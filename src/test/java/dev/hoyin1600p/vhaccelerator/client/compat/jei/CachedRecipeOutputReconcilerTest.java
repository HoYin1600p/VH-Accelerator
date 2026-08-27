package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CachedRecipeOutputReconcilerTest {
    private static final String OUTPUT = "OUTPUT";

    @Test
    void acceptsAnExactCurrentUidWithoutChangingThePlan() {
        Map<String, List<List<String>>> plan = plan("mod:item:stable");

        CachedRecipeOutputReconciler.Result result =
                CachedRecipeOutputReconciler.reconcile(
                        plan,
                        OUTPUT,
                        "mod:item:stable"
                );

        assertEquals(
                CachedRecipeOutputReconciler.Outcome.EXACT,
                result.outcome()
        );
        assertSame(plan, result.roleGroups());
    }

    @Test
    void rebindsOnlyTheTransientWoodTypeIdentity() {
        String cached = "sophisticatedstorage:barrel:{woodName:"
                + "net.minecraft.world.level.block.state.properties."
                + "WoodType@169b6608,flatTop:false}";
        String live = "sophisticatedstorage:barrel:{woodName:"
                + "net.minecraft.world.level.block.state.properties."
                + "WoodType@18e244de,flatTop:false}";

        CachedRecipeOutputReconciler.Result result =
                CachedRecipeOutputReconciler.reconcile(
                        plan(cached),
                        OUTPUT,
                        live
                );

        assertEquals(
                CachedRecipeOutputReconciler.Outcome.REBOUND,
                result.outcome()
        );
        assertTrue(result.rebound());
        assertEquals(
                List.of(List.of(live)),
                result.roleGroups().get(OUTPUT)
        );
    }

    @Test
    void rejectsAFlatTopSubtypeChange() {
        String cached = "sophisticatedstorage:barrel:{woodName:"
                + "net.minecraft.world.level.block.state.properties."
                + "WoodType@169b6608,flatTop:false}";
        String live = "sophisticatedstorage:barrel:{woodName:"
                + "net.minecraft.world.level.block.state.properties."
                + "WoodType@18e244de,flatTop:true}";

        CachedRecipeOutputReconciler.Result result =
                CachedRecipeOutputReconciler.reconcile(
                        plan(cached),
                        OUTPUT,
                        live
                );

        assertEquals(
                CachedRecipeOutputReconciler.Outcome.MISMATCH,
                result.outcome()
        );
        assertFalse(result.accepted());
    }

    @Test
    void doesNotNormalizeArbitraryClassIdentityStrings() {
        CachedRecipeOutputReconciler.Result result =
                CachedRecipeOutputReconciler.reconcile(
                        plan("mod:item:example.Type@1234abcd"),
                        OUTPUT,
                        "mod:item:example.Type@5678efab"
                );

        assertEquals(
                CachedRecipeOutputReconciler.Outcome.MISMATCH,
                result.outcome()
        );
    }

    @Test
    void trustsARealMultiVariantCategoryInsteadOfUsingItsDefaultResult() {
        Map<String, List<List<String>>> plan = Map.of(
                OUTPUT,
                List.of(List.of("fairylights:light:red", "fairylights:light:blue"))
        );

        CachedRecipeOutputReconciler.Result result =
                CachedRecipeOutputReconciler.reconcile(
                        plan,
                        OUTPUT,
                        "fairylights:light:default"
                );

        assertEquals(
                CachedRecipeOutputReconciler.Outcome.TRUSTED_VARIANTS,
                result.outcome()
        );
        assertSame(plan, result.roleGroups());
    }

    private static Map<String, List<List<String>>> plan(String uid) {
        return Map.of(OUTPUT, List.of(List.of(uid)));
    }
}

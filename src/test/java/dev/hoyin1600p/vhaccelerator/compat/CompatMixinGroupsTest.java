package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

class CompatMixinGroupsTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.";

    /** Maps configured mixin names onto the compiled preflight fixtures. */
    private static final Map<String, Class<?>> FIXTURES = Map.of(
            BASE + "sample.GoodMixin", MatchingMixin.class,
            BASE + "sample.BrokenMixin", RemovedMethodMixin.class,
            BASE + "other.GoodMixin", InheritedMembersMixin.class,
            BASE + "modelbake.GoodModelBakeMixin", MatchingMixin.class,
            BASE + "modelbake.BrokenModelBakeMixin", ChangedPointMixin.class
    );

    private static ClassNode lookup(String internalName) throws Exception {
        Class<?> fixture = FIXTURES.get(internalName.replace('/', '.'));
        return MixinTargetPreflightTest.read(
                fixture != null ? fixture.getName().replace('.', '/') : internalName
        );
    }

    private static CompatMixinGroups groups(List<String> gated) {
        return new CompatMixinGroups(
                new ArrayList<>(FIXTURES.keySet()),
                (target, mixin) -> gated.contains(mixin),
                CompatMixinGroupsTest::lookup
        );
    }

    @Test
    void skipsWholeGroupWhenAnyActiveMemberNoLongerMatches() {
        CompatMixinGroups groups = groups(List.copyOf(FIXTURES.keySet()));
        assertFalse(groups.allows(BASE + "sample.GoodMixin"));
        assertFalse(groups.allows(BASE + "sample.BrokenMixin"));
        assertTrue(groups.allows(BASE + "other.GoodMixin"));
    }

    @Test
    void ignoresMembersTheExistingGateWouldNotApply() {
        CompatMixinGroups groups = groups(List.of(BASE + "sample.GoodMixin"));
        assertTrue(groups.allows(BASE + "sample.GoodMixin"));
    }

    @Test
    void treatsEachModelBakeMixinAsItsOwnGroup() {
        CompatMixinGroups groups = groups(List.copyOf(FIXTURES.keySet()));
        assertTrue(groups.allows(BASE + "modelbake.GoodModelBakeMixin"));
        assertFalse(groups.allows(BASE + "modelbake.BrokenModelBakeMixin"));
    }

    @Test
    void leavesNonCompatibilityMixinsToTheExistingGate() {
        CompatMixinGroups groups = groups(List.of());
        assertTrue(groups.allows("dev.hoyin1600p.vhaccelerator.mixin.client.ModelBakeryMixin"));
    }

    @Test
    void keepsPriorBehaviorWhenOwnMixinBytecodeCannotBeRead() {
        CompatMixinGroups groups = new CompatMixinGroups(
                List.of(BASE + "sample.BrokenMixin"),
                (target, mixin) -> true,
                internalName -> null
        );
        assertTrue(groups.allows(BASE + "sample.BrokenMixin"));
    }

    @Test
    void readsConfiguredMixinNamesFromTheShippedConfig() {
        List<String> names = CompatMixinGroups.readConfiguredMixins(
                getClass().getClassLoader(),
                "vhaccelerator.mixins.json"
        );
        assertTrue(names.contains(
                "dev.hoyin1600p.vhaccelerator.mixin.compat.jei.v10.IngredientFilterMixin"
        ));
    }
}

package dev.hoyin1600p.vhaccelerator.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;

class CompatMixinGroupsTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.compat.";

    @Test
    void legacyParallelMatchingRequiresExactVersionContextCoverage() {
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed(null, false));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed(null, true));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed("9.1.213", false));
        assertTrue(CompatMixinGroups.legacyCraftTweakerParallelAllowed("9.1.213", true));
        assertTrue(CompatMixinGroups.legacyCraftTweakerParallelAllowed("9.1.212", false));
        assertTrue(CompatMixinGroups.legacyCraftTweakerParallelAllowed("9.1.214", true));
    }

    @Test
    void missingOrRejectedContextGroupCannotEnableLegacyParallelMatching() {
        CompatMixinGroups groups = new CompatMixinGroups(List.of(), (target, mixin) -> true,
                CompatMixinGroupsTest::lookup);
        groups.publishDiagnostics(Map.of("installedVersions", Map.of("crafttweaker", "9.1.213")));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
        groups.publishDiagnostics(Map.of("installedVersions", Map.of("crafttweaker", "9.1.212")));
        assertTrue(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
    }

    @Test
    void exactVersionRequiresAllThreeConfirmedContextTargets() {
        List<String> mixins = List.of(
                BASE + "crafttweaker.removals.ScriptRunIndexSessionMixin",
                BASE + "crafttweaker.removals.ScriptRunManagerOutputContextMixin",
                BASE + "crafttweaker.removals.RecipeListIndexedOutputMixin");
        CompatMixinGroups groups = new CompatMixinGroups(mixins, (target, mixin) -> true,
                CompatMixinGroupsTest::lookup);
        groups.publishDiagnostics(Map.of("physicalSide", "CLIENT", "debug", false,
                "installedVersions", Map.of("crafttweaker", "9.1.213")));
        assertEquals("passed", groups.snapshot().get(0).get("preflight"));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
        applyFixtureTarget(groups, mixins.get(0));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
        applyFixtureTarget(groups, mixins.get(1));
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
        applyFixtureTarget(groups, mixins.get(2));
        assertTrue(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
    }

    @Test
    void rejectedExactVersionContextKeepsOriginalLoopEvenAfterTargetNotifications() {
        String mixin = BASE + "crafttweaker.removals.ScriptRunIndexSessionMixin";
        CompatMixinGroups groups = new CompatMixinGroups(List.of(mixin), (target, name) -> true,
                CompatMixinGroupsTest::lookup);
        groups.publishDiagnostics(Map.of("physicalSide", "CLIENT", "debug", false,
                "installedVersions", Map.of("crafttweaker", "9.1.213")));
        groups.applicationRejected(mixin, "com.blamejared.crafttweaker.impl.script.scriptrun.ScriptRun",
                List.of("fixture rejected injector"));
        applyFixtureTarget(groups, mixin);
        assertFalse(CompatMixinGroups.legacyCraftTweakerParallelAllowed());
    }

    /** Maps configured mixin names onto the compiled preflight fixtures. */
    private static final Map<String, Class<?>> FIXTURES = Map.of(
            BASE + "sample.GoodMixin", MatchingMixin.class,
            BASE + "sample.BrokenMixin", RemovedMethodMixin.class,
            BASE + "other.GoodMixin", InheritedMembersMixin.class,
            BASE + "modelbake.GoodModelBakeMixin", MatchingMixin.class,
            BASE + "modelbake.BrokenModelBakeMixin", ChangedPointMixin.class,
            BASE + "startup.kubejs.ScriptObserverFixtureMixin", MatchingMixin.class,
            BASE + "startup.kubejs.RecipeObserverFixtureMixin", MatchingMixin.class
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
    void rejectsWholeGroupWhenOwnMixinBytecodeCannotBeRead() {
        CompatMixinGroups groups = new CompatMixinGroups(
                List.of(BASE + "sample.BrokenMixin"),
                (target, mixin) -> true,
                internalName -> null
        );
        assertFalse(groups.allows(BASE + "sample.BrokenMixin"));
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

    @Test
    void selectionApplicationAndInvocationRemainDistinct() {
        String mixin = BASE + "sample.GoodMixin";
        CompatMixinGroups groups = new CompatMixinGroups(List.of(mixin),
                (target, name) -> true, CompatMixinGroupsTest::lookup);
        groups.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", true));
        var selected = groups.snapshot().get(0);
        assertEquals("fallback", selected.get("state"));
        assertEquals("passed", selected.get("preflight"));
        assertEquals(false, selected.get("benchmarkValid"));
        var startup = new dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport(
                new dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport.ClockMapping(
                        0, System.nanoTime(), System.nanoTime(), "2026-10-23T00:00:00Z", 0, "fixture"), false);
        startup.checkpoint("attachment", "fixture");
        startup.checkpoint("done", "fixture");
        startup.checkpoint("server_started", "fixture");
        var incompleteReport = com.google.gson.JsonParser.parseString(startup.toJson()).getAsJsonObject();
        assertEquals("incomplete", incompleteReport.get("completionState").getAsString());
        assertEquals("compatibility_application_incomplete_or_rejected", incompleteReport.get("errorState").getAsString());
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>) selected.get("requiredTargets");
        String target = targets.get(0).substring(targets.get(0).indexOf(" -> ") + 4);
        groups.applied(mixin, target);
        assertEquals("applied", groups.snapshot().get(0).get("state"));
        assertEquals(true, groups.snapshot().get(0).get("benchmarkValid"));
        assertEquals("complete", com.google.gson.JsonParser.parseString(startup.toJson()).getAsJsonObject()
                .get("completionState").getAsString());
        assertEquals(0L, groups.snapshot().get(0).get("firstCallbackInvocationCount"));
        CompatMixinGroups.recordFirstUse(mixin);
        CompatMixinGroups.recordFirstUse(mixin);
        assertEquals(1L, groups.snapshot().get(0).get("firstCallbackInvocationCount"));
        assertNull(groups.snapshot().get(0).get("optimizationUseCount"));
        assertTrue(groups.snapshot().get(0).get("runtimeSettingEvidence").toString().contains("unavailable"));
        assertTrue(groups.snapshot().get(0).get("inventory").toString().contains(CompatibilityInventory.RESOURCE));
    }

    @Test
    void inactiveGroupsReportFallbackWithoutResolvingTargets() {
        CompatMixinGroups groups = new CompatMixinGroups(List.of(BASE + "sample.GoodMixin"),
                (target, mixin) -> false, name -> { fail("inactive target must not be read"); return null; });
        groups.publishDiagnostics(Map.of("physicalSide", "CLIENT", "debug", false));
        assertEquals("fallback", groups.snapshot().get(0).get("state"));
        assertEquals("not_selected", groups.snapshot().get(0).get("preflight"));
        assertNull(groups.snapshot().get(0).get("firstCallbackInvocationCount"));
    }

    @Test
    void unsafePartialInvocationRemainsInvalidEvenIfRemainingTargetsLaterApply() {
        String first = BASE + "sample.FirstMixin";
        String second = BASE + "sample.SecondMixin";
        CompatMixinGroups groups = new CompatMixinGroups(List.of(first, second), (target, mixin) -> true,
                name -> name.equals(first.replace('.', '/')) || name.equals(second.replace('.', '/'))
                        ? MixinTargetPreflightTest.read(MatchingMixin.class.getName().replace('.', '/')) : lookup(name));
        groups.publishDiagnostics(Map.of("physicalSide", "CLIENT", "debug", true));
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>) groups.snapshot().get(0).get("requiredTargets");
        String target = targets.get(0).substring(targets.get(0).indexOf(" -> ") + 4);
        groups.applied(first, target);
        CompatMixinGroups.recordFirstUse(first);
        groups.applied(second, target);
        assertEquals("applied", groups.snapshot().get(0).get("state"));
        assertEquals(false, groups.snapshot().get(0).get("benchmarkValid"));
        assertDoesNotThrow(CompatMixinGroups::verifyStartupApplication);
    }

    @Test
    void brokenDecisionEvidenceCannotEscapePublicationAndInvalidatesReport() throws Exception {
        var reportField = dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer.class.getDeclaredField("report");
        reportField.setAccessible(true);
        Object previous = reportField.get(null);
        long now = System.nanoTime();
        var report = new dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport(
                new dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport.ClockMapping(
                        0, now, now, "2026-10-23T00:00:00Z", 0, "fixture"), false);
        reportField.set(null, report);
        try {
            String mixin = BASE + "sample.GoodMixin";
            CompatMixinGroups groups = new CompatMixinGroups(List.of(mixin),
                    (target, name) -> true, CompatMixinGroupsTest::lookup);
            Map<String, Object> broken = new BrokenCompatibilityMetadata();
            assertDoesNotThrow(() -> groups.publishDiagnostics(Map.of(
                    "physicalSide", "CLIENT", "debug", true, "evidence", broken)));
            assertEquals("passed", groups.snapshot().get(0).get("preflight"));
            // Recover rendering input so the report can display the captured failure.
            groups.publishDiagnostics(Map.of("evidence", "recovered"));
            applyFixtureTarget(groups, mixin);
            report.checkpoint("attachment", "fixture");
            report.checkpoint("done", "fixture");
            report.checkpoint("server_started", "fixture");
            var json = com.google.gson.JsonParser.parseString(report.toJson()).getAsJsonObject();
            assertEquals("incomplete", json.get("completionState").getAsString());
            assertEquals("startup_diagnostics_failed", json.get("errorState").getAsString());
            assertTrue(json.getAsJsonArray("diagnosticFailures").toString()
                    .contains("broken compatibility evidence fixture"));
        } finally {
            reportField.set(null, previous);
        }
    }

    @Test
    void independentKubeJsObserverMayRunBeforeOtherTargetsLoad() {
        CompatMixinGroups groups = kubeJsObserverGroups();
        String script = BASE + "startup.kubejs.ScriptObserverFixtureMixin";
        String recipe = BASE + "startup.kubejs.RecipeObserverFixtureMixin";
        applyFixtureTarget(groups, script);
        CompatMixinGroups.recordFirstUse(script);
        assertEquals(false, groups.snapshot().get(0).get("unsafePartialUse"));
        assertEquals(false, groups.snapshot().get(0).get("benchmarkValid"));
        applyFixtureTarget(groups, recipe);
        CompatMixinGroups.recordFirstUse(recipe);
        assertEquals("applied", groups.snapshot().get(0).get("state"));
        assertEquals(true, groups.snapshot().get(0).get("benchmarkValid"));
        assertDoesNotThrow(CompatMixinGroups::verifyStartupApplication);
    }

    @Test
    void independentKubeJsObserverDoesNotMakeMissingTargetValid() {
        CompatMixinGroups groups = kubeJsObserverGroups();
        String script = BASE + "startup.kubejs.ScriptObserverFixtureMixin";
        applyFixtureTarget(groups, script);
        CompatMixinGroups.recordFirstUse(script);
        var snapshot = groups.snapshot().get(0);
        assertEquals(false, snapshot.get("unsafePartialUse"));
        assertEquals("fallback", snapshot.get("state"));
        assertEquals(false, snapshot.get("applicationComplete"));
        assertEquals(false, snapshot.get("benchmarkValid"));
    }

    @Test
    void kubeJsCallbackBeforeItsOwnTransformationRemainsInvalid() {
        CompatMixinGroups groups = kubeJsObserverGroups();
        String script = BASE + "startup.kubejs.ScriptObserverFixtureMixin";
        String recipe = BASE + "startup.kubejs.RecipeObserverFixtureMixin";
        CompatMixinGroups.recordFirstUse(script);
        applyFixtureTarget(groups, script);
        applyFixtureTarget(groups, recipe);
        assertEquals("applied", groups.snapshot().get(0).get("state"));
        assertEquals(true, groups.snapshot().get(0).get("unsafePartialUse"));
        assertEquals(false, groups.snapshot().get(0).get("benchmarkValid"));
        assertDoesNotThrow(CompatMixinGroups::verifyStartupApplication);
    }

    private static CompatMixinGroups kubeJsObserverGroups() {
        CompatMixinGroups groups = new CompatMixinGroups(List.of(
                BASE + "startup.kubejs.ScriptObserverFixtureMixin",
                BASE + "startup.kubejs.RecipeObserverFixtureMixin"),
                (target, mixin) -> true, CompatMixinGroupsTest::lookup);
        groups.publishDiagnostics(Map.of("physicalSide", "CLIENT", "debug", true));
        return groups;
    }

    private static void applyFixtureTarget(CompatMixinGroups groups, String mixin) {
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>) groups.snapshot().get(0).get("requiredTargets");
        String prefix = mixin + " -> ";
        String target = targets.stream().filter(value -> value.startsWith(prefix))
                .findFirst().orElseThrow().substring(prefix.length());
        groups.applied(mixin, target);
    }

    private static final class BrokenCompatibilityMetadata extends AbstractMap<String, Object> {
        @Override public Set<Entry<String, Object>> entrySet() {
            throw new IllegalArgumentException("broken compatibility evidence fixture");
        }
    }

}

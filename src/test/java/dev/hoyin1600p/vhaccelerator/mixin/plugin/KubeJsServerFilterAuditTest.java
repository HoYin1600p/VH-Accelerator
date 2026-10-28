package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.compat.CommonCompatibilityAudit;
import dev.hoyin1600p.vhaccelerator.compat.CompatMixinGroups;
import dev.hoyin1600p.vhaccelerator.compat.MixinTargetPreflight;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * The KubeJS recipe filter is the one KubeJS mixin that runs on a dedicated server: its handler and
 * helpers must reach no client class, and the whole compat.kubejs group must pass the same
 * whole-group preflight and dedicated-side audit as every other server compat group.
 */
class KubeJsServerFilterAuditTest {
    private static final String GROUP = "dev.hoyin1600p.vhaccelerator.mixin.compat.kubejs";
    private static final String FILTER = GROUP + ".RecipeEventJSParallelFilterMixin";
    private static final String INDEX = GROUP + ".RecipeEventJSRemoveIndexMixin";
    private static final Path KUBEJS = Path.of("libs/kubejs-forge-1802.5.5-build.569.jar");

    private static ClassNode read(ZipFile kubeJs, String name) throws IOException {
        var entry = kubeJs.getEntry(name + ".class");
        try (InputStream input = entry != null ? kubeJs.getInputStream(entry)
                : KubeJsServerFilterAuditTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @Test
    void theFilterHandlerAndItsHelpersReachNoClientClass() throws Exception {
        try (ZipFile kubeJs = new ZipFile(KUBEJS.toFile())) {
            MixinTargetPreflight.ClassLookup lookup = name -> read(kubeJs, name);
            var audit = CommonCompatibilityAudit.inspect(List.of(FILTER), lookup);
            assertEquals(List.of(), audit.problems(), String.valueOf(audit.helperClasses()));
            assertTrue(audit.helperClasses().contains(
                    "dev/hoyin1600p/vhaccelerator/compat/kubejs/KubeJsParallelFilters"), audit.helperClasses().toString());
        }
    }

    @Test
    void theRemovalIndexHandlerAndItsHelpersReachNoClientClass() throws Exception {
        try (ZipFile kubeJs = new ZipFile(KUBEJS.toFile())) {
            MixinTargetPreflight.ClassLookup lookup = name -> read(kubeJs, name);
            var audit = CommonCompatibilityAudit.inspect(List.of(INDEX), lookup);
            assertEquals(List.of(), audit.problems(), String.valueOf(audit.helperClasses()));
            for (String helper : List.of("KubeJsRecipeIndex", "KubeJsRecipeIdSource", "KubeJsRecipeOutputSource")) {
                assertTrue(audit.helperClasses().contains(
                        "dev/hoyin1600p/vhaccelerator/compat/kubejs/" + helper), audit.helperClasses().toString());
            }
        }
    }

    @Test
    void withBothSettingsTheDedicatedGroupSelectsTheIndexAndTheFilter() throws Exception {
        try (ZipFile kubeJs = new ZipFile(KUBEJS.toFile())) {
            GateFactsBuilder facts = new GateFactsBuilder();
            facts.modListKnown = true;
            facts.commonMasterEnabled = true;
            facts.parallelKubeJsFilters = true;
            facts.indexKubeJsRecipeRemovals = true;
            MixinGateRules rules = new MixinGateRules(facts.build(), new GateInputs() {
                public boolean startupAdapterSupported(String mixin) { return false; }
                public boolean debugEnabled() { return false; }
                public boolean vhaOwns(BackportFeature feature) { return false; }
                public boolean modernFixOptionEnabled(String option) { return false; }
                public boolean modernFixDynamicResourcesEnabled() { return false; }
                public void buildScapeInteropApplied() { }
                public void ctmMixinApplied() { }
                public void modernFixBakeDecision(boolean enabled) { }
            });
            List<String> configured = CompatMixinGroups.readConfiguredMixins(
                    getClass().getClassLoader(), "vhaccelerator.mixins.json");
            assertTrue(configured.contains(INDEX));
            CompatMixinGroups groups = new CompatMixinGroups(configured,
                    (target, mixin) -> rules.shouldApply(mixin), name -> read(kubeJs, name));
            groups.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", false));
            Map<String, Object> decision = groups.snapshot().stream()
                    .filter(entry -> GROUP.equals(entry.get("group"))).findFirst().orElseThrow();
            assertEquals("passed", decision.get("preflight"), String.valueOf(decision.get("reason")));
            assertEquals(java.util.Set.of(
                            FILTER + " -> dev.latvian.mods.kubejs.recipe.RecipeEventJS",
                            INDEX + " -> dev.latvian.mods.kubejs.recipe.RecipeEventJS"),
                    new java.util.HashSet<>((List<?>) decision.get("requiredTargets")));
            var sideAudit = (CommonCompatibilityAudit.Result) decision.get("commonSideAudit");
            assertNotNull(sideAudit, "the dedicated-side audit ran for the group");
            assertEquals(List.of(), sideAudit.problems());
        }
    }

    @Test
    void theDedicatedGroupSelectsOnlyTheFilterAndPassesTheWholeGroupPreflight() throws Exception {
        try (ZipFile kubeJs = new ZipFile(KUBEJS.toFile())) {
            GateFactsBuilder facts = new GateFactsBuilder();
            facts.modListKnown = true;
            facts.commonMasterEnabled = true;
            facts.parallelKubeJsFilters = true;
            MixinGateRules rules = new MixinGateRules(facts.build(), new GateInputs() {
                public boolean startupAdapterSupported(String mixin) { return false; }
                public boolean debugEnabled() { return false; }
                public boolean vhaOwns(BackportFeature feature) { return false; }
                public boolean modernFixOptionEnabled(String option) { return false; }
                public boolean modernFixDynamicResourcesEnabled() { return false; }
                public void buildScapeInteropApplied() { }
                public void ctmMixinApplied() { }
                public void modernFixBakeDecision(boolean enabled) { }
            });
            List<String> configured = CompatMixinGroups.readConfiguredMixins(
                    getClass().getClassLoader(), "vhaccelerator.mixins.json");
            assertTrue(configured.contains(FILTER));
            CompatMixinGroups groups = new CompatMixinGroups(configured,
                    (target, mixin) -> rules.shouldApply(mixin), name -> read(kubeJs, name));
            groups.publishDiagnostics(Map.of("physicalSide", "DEDICATED_SERVER", "debug", false));
            Map<String, Object> decision = groups.snapshot().stream()
                    .filter(entry -> GROUP.equals(entry.get("group"))).findFirst().orElseThrow();
            assertEquals("passed", decision.get("preflight"), String.valueOf(decision.get("reason")));
            assertEquals(List.of(FILTER + " -> dev.latvian.mods.kubejs.recipe.RecipeEventJS"),
                    decision.get("requiredTargets"));
            var sideAudit = (CommonCompatibilityAudit.Result) decision.get("commonSideAudit");
            assertNotNull(sideAudit, "the dedicated-side audit ran for the group");
            assertEquals(List.of(), sideAudit.problems());
        }
    }
}

package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.*;
import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import org.junit.jupiter.api.Test;

class ShapeMixinGateTest {
    private static final String BASE = "dev.hoyin1600p.vhaccelerator.mixin.shape.";
    private static final String BOX = BASE + "ShapesBoxCacheMixin";
    private static final String CACHE = BASE + "ShapesLaunchCacheMixin";
    private static final String OPTIMIZE = BASE + "VoxelShapeLaunchCacheMixin";
    private static final String MERGER = BASE + "ShapesCoordinateMergerMixin";
    private static final String LEAF = BASE + "VoxelShapeLeafContentMixin";

    private static MixinGateRules rules(GateFactsBuilder facts) {
        return new MixinGateRules(facts.build(), new GateInputs() {
            public boolean startupAdapterSupported(String mixin) { return false; }
            public boolean debugEnabled() { return false; }
            public boolean vhaOwns(BackportFeature feature) { return false; }
            public boolean modernFixOptionEnabled(String option) { return false; }
            public boolean modernFixDynamicResourcesEnabled() { return false; }
            public void buildScapeInteropApplied() { }
            public void ctmMixinApplied() { }
            public void modernFixBakeDecision(boolean enabled) { }
        });
    }

    private static GateFactsBuilder server() {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.modListKnown = true;
        facts.launchShapeCache = true;
        facts.flatShapeMerger = true;
        return facts;
    }

    @Test
    void aDedicatedServerAppliesBothFeaturesWhenSelectedAndTheModListIsKnown() {
        MixinGateRules rules = rules(server());
        for (String mixin : new String[] {BOX, CACHE, OPTIMIZE, MERGER}) {
            assertTrue(rules.shouldApply(mixin), mixin);
        }
    }

    @Test
    void eachServerSettingControlsOnlyItsOwnMixins() {
        GateFactsBuilder noCache = server();
        noCache.launchShapeCache = false;
        assertFalse(rules(noCache).shouldApply(BOX));
        assertFalse(rules(noCache).shouldApply(CACHE));
        assertFalse(rules(noCache).shouldApply(OPTIMIZE));
        assertTrue(rules(noCache).shouldApply(MERGER));
        GateFactsBuilder noMerger = server();
        noMerger.flatShapeMerger = false;
        assertTrue(rules(noMerger).shouldApply(BOX));
        assertTrue(rules(noMerger).shouldApply(CACHE));
        assertTrue(rules(noMerger).shouldApply(OPTIMIZE));
        assertFalse(rules(noMerger).shouldApply(MERGER));
    }

    @Test
    void aServerWithCanaryDropsOnlyTheMergerAndAddsContentMatching() {
        GateFactsBuilder canary = server();
        canary.canaryLoaded = true;
        assertTrue(rules(canary).shouldApply(BOX));
        assertFalse(rules(canary).shouldApply(MERGER));
        assertTrue(rules(canary).shouldApply(CACHE));
        assertTrue(rules(canary).shouldApply(OPTIMIZE));
        assertTrue(rules(canary).shouldApply(LEAF));
        GateFactsBuilder noCache = server();
        noCache.canaryLoaded = true;
        noCache.launchShapeCache = false;
        assertFalse(rules(noCache).shouldApply(BOX));
        assertFalse(rules(noCache).shouldApply(LEAF));
        assertFalse(rules(noCache).shouldApply(CACHE));
        assertFalse(rules(noCache).shouldApply(OPTIMIZE));
    }

    @Test
    void lithiumKeepsEveryShapeMixinOff() {
        for (String mixin : new String[] {BOX, CACHE, OPTIMIZE, MERGER}) {
            GateFactsBuilder lithium = server();
            lithium.lithiumLoaded = true;
            assertFalse(rules(lithium).shouldApply(mixin), mixin);
            GateFactsBuilder both = server();
            both.lithiumLoaded = true;
            both.canaryLoaded = true;
            assertFalse(rules(both).shouldApply(mixin), mixin);
        }
    }

    @Test
    void aServerYieldsToUnknownAndFailedModLists() {
        for (String mixin : new String[] {BOX, CACHE, OPTIMIZE, MERGER}) {
            GateFactsBuilder unknown = server();
            unknown.modListKnown = false;
            assertFalse(rules(unknown).shouldApply(mixin), mixin);
            GateFactsBuilder failed = server();
            failed.modDiscoveryFailed = true;
            assertFalse(rules(failed).shouldApply(mixin), mixin);
        }
    }

    @Test
    void aClientKeepsItsFormerRuleAndIgnoresTheServerSettings() {
        for (String mixin : new String[] {BOX, CACHE, OPTIMIZE, MERGER}) {
            GateFactsBuilder client = new GateFactsBuilder();
            client.physicalClient = true;
            assertTrue(rules(client).shouldApply(mixin), mixin);
            client.lithiumLoaded = true;
            assertFalse(rules(client).shouldApply(mixin), mixin);
        }
    }

    @Test
    void aClientWithCanaryDropsOnlyTheMergerAndAddsContentMatching() {
        GateFactsBuilder client = new GateFactsBuilder();
        client.physicalClient = true;
        client.canaryLoaded = true;
        assertTrue(rules(client).shouldApply(BOX));
        assertFalse(rules(client).shouldApply(MERGER));
        assertTrue(rules(client).shouldApply(CACHE));
        assertTrue(rules(client).shouldApply(OPTIMIZE));
        assertTrue(rules(client).shouldApply(LEAF));
    }

    @Test
    void theLeafContentMixinIsWovenOnlyBesideCanary() {
        assertFalse(rules(server()).shouldApply(LEAF));
        GateFactsBuilder client = new GateFactsBuilder();
        client.physicalClient = true;
        assertFalse(rules(client).shouldApply(LEAF));
        GateFactsBuilder lithiumAndCanary = server();
        lithiumAndCanary.canaryLoaded = true;
        lithiumAndCanary.lithiumLoaded = true;
        assertFalse(rules(lithiumAndCanary).shouldApply(LEAF));
    }
}

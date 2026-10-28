package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwner;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipResolver;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import org.junit.jupiter.api.Test;

class RegistryWriteConcurrencyGateTest {
    private static final String[] MIXINS = {
            "dev.hoyin1600p.vhaccelerator.mixin.fix.registry.MappedRegistryWriteLockMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.fix.registry.NamespacedHolderHelperWriteLockMixin",
            "dev.hoyin1600p.vhaccelerator.mixin.fix.registry.RegistryLoadersLockMixin"
    };

    private static boolean applies(String mixin, boolean physicalClient, boolean modernFixLoaded,
                                   boolean compareMode, boolean keyEnabled, boolean buildScape) {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = physicalClient;
        facts.modernFixLoaded = modernFixLoaded;
        facts.modListKnown = true;
        facts.buildScapeInstalled = buildScape;
        facts.buildScapeReloadBarrierFix = buildScape;
        BackportOwner owner = BackportOwnershipResolver.resolve(
                BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX,
                physicalClient,
                compareMode,
                keyEnabled,
                modernFixLoaded ? ModernFixOwnership.INACTIVE : ModernFixOwnership.ABSENT
        ).owner();
        return new MixinGateRules(facts.build(), new GateInputs() {
            public boolean debugEnabled() { return false; }
            public boolean vhaOwns(BackportFeature feature) {
                return feature == BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX && owner == BackportOwner.VHA;
            }
            public boolean modernFixOptionEnabled(String option) { return false; }
            public boolean modernFixDynamicResourcesEnabled() { return false; }
            public void buildScapeInteropApplied() { }
            public void ctmMixinApplied() { }
            public void modernFixBakeDecision(boolean enabled) { }
        }).shouldApply(mixin);
    }

    @Test
    void appliesOnBothSidesWithAndWithoutModernFixAndBuildScape() {
        for (String mixin : MIXINS) {
            for (boolean client : new boolean[] {false, true}) {
                for (boolean modernFix : new boolean[] {false, true}) {
                    for (boolean buildScape : new boolean[] {false, true}) {
                        assertTrue(applies(mixin, client, modernFix, false, true, buildScape),
                                mixin + " client=" + client + " modernFix=" + modernFix + " buildScape=" + buildScape);
                    }
                }
            }
        }
    }

    @Test
    void staysOnInCompareModeAndOffWhenTheKeyIsOff() {
        for (String mixin : MIXINS) {
            for (boolean client : new boolean[] {false, true}) {
                assertTrue(applies(mixin, client, false, true, true, false), "Compare Mode must not switch it off");
                assertFalse(applies(mixin, client, false, false, false, false));
                assertFalse(applies(mixin, client, false, true, false, false));
            }
        }
    }

    @Test
    void otherBackportsStillFollowCompareMode() {
        for (BackportFeature feature : BackportFeature.values()) {
            if (feature.ignoresCompareMode()) {
                assertTrue(feature == BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX
                        || feature == BackportFeature.EVENT_BUS_WRAPPER_CACHE_CONCURRENCY_FIX
                        || feature == BackportFeature.RELOAD_BARRIER_RACE_FIX, feature.id());
                continue;
            }
            if (!feature.implemented() || feature.side() != dev.hoyin1600p.vhaccelerator.backport.BackportSide.COMMON) {
                continue;
            }
            assertEquals(BackportOwner.DISABLED, BackportOwnershipResolver.resolve(
                    feature, false, true, true, ModernFixOwnership.ABSENT).owner(), feature.id());
        }
    }

    @Test
    void featureIsDefaultOnCommonAndNotInTheModernFixOverlapSet() {
        assertTrue(BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX.defaultEnabled());
        assertEquals("registryWriteConcurrencyFix", BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX.configKey());
        assertTrue(BackportFeature.REGISTRY_WRITE_CONCURRENCY_FIX.modernFixMixinKeys().isEmpty());
        for (String mixin : MIXINS) {
            assertFalse(MixinNameSets.MODERNFIX_OVERLAPS.contains(mixin));
        }
    }
}

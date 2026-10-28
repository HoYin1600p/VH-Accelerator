package dev.hoyin1600p.vhaccelerator.mixin.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hoyin1600p.vhaccelerator.backport.BackportFeature;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwner;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipResolver;
import dev.hoyin1600p.vhaccelerator.backport.ModernFixOwnership;
import org.junit.jupiter.api.Test;

class ReloadBarrierRaceGateTest {
    private static final String MIXIN =
            "dev.hoyin1600p.vhaccelerator.mixin.fix.ReloadBarrierRaceMixin";

    /** Ownership comes from the real resolver, so Compare Mode and the key behave as in production. */
    private static boolean applies(
            boolean physicalClient,
            boolean modernFixLoaded,
            boolean compareMode,
            boolean keyEnabled
    ) {
        return applies(physicalClient, modernFixLoaded, compareMode, keyEnabled, null);
    }

    /** buildScape: null = absent, false = installed without the fix, true = installed with it. */
    private static boolean applies(
            boolean physicalClient,
            boolean modernFixLoaded,
            boolean compareMode,
            boolean keyEnabled,
            Boolean buildScape
    ) {
        GateFactsBuilder facts = new GateFactsBuilder();
        facts.physicalClient = physicalClient;
        facts.modernFixLoaded = modernFixLoaded;
        facts.modListKnown = true;
        facts.buildScapeInstalled = buildScape != null;
        facts.buildScapeReloadBarrierFix = Boolean.TRUE.equals(buildScape);
        ModernFixOwnership modernFix = modernFixLoaded
                ? ModernFixOwnership.INACTIVE // no ModernFix option covers this race
                : ModernFixOwnership.ABSENT;
        BackportOwner owner = BackportOwnershipResolver.resolve(
                BackportFeature.RELOAD_BARRIER_RACE_FIX,
                physicalClient,
                compareMode,
                keyEnabled,
                modernFix
        ).owner();
        return new MixinGateRules(facts.build(), new GateInputs() {
            public boolean debugEnabled() { return false; }
            public boolean vhaOwns(BackportFeature feature) {
                return feature == BackportFeature.RELOAD_BARRIER_RACE_FIX
                        && owner == BackportOwner.VHA;
            }
            public boolean modernFixOptionEnabled(String option) { return false; }
            public boolean modernFixDynamicResourcesEnabled() { return false; }
            public void buildScapeInteropApplied() { }
            public void ctmMixinApplied() { }
            public void modernFixBakeDecision(boolean enabled) { }
        }).shouldApply(MIXIN);
    }

    @Test
    void appliesOnBothSidesWithAndWithoutModernFixWhenKeyIsOn() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean modernFix : new boolean[] {false, true}) {
                assertTrue(applies(client, modernFix, false, true),
                        "client=" + client + " modernFix=" + modernFix);
            }
        }
    }

    @Test
    void defersToBuildScapeThatShipsTheFixAndStaysOnOtherwise() {
        for (boolean client : new boolean[] {false, true}) {
            assertFalse(applies(client, false, false, true, true), "BuildScape with the fix owns it");
            assertTrue(applies(client, false, false, true, false), "older BuildScape: VHA fallback");
            assertTrue(applies(client, false, false, true, null), "no BuildScape: VHA fix");
        }
    }

    @Test
    void offWhenKeyIsOffEvenInCompareMode() {
        for (boolean client : new boolean[] {false, true}) {
            for (boolean modernFix : new boolean[] {false, true}) {
                assertFalse(applies(client, modernFix, false, false));
                assertFalse(applies(client, modernFix, true, false));
            }
        }
    }

    @Test
    void staysOnInCompareModeAndStillDefersToBuildScape() {
        assertTrue(BackportFeature.RELOAD_BARRIER_RACE_FIX.ignoresCompareMode());
        for (boolean client : new boolean[] {false, true}) {
            for (boolean modernFix : new boolean[] {false, true}) {
                assertTrue(applies(client, modernFix, true, true), "Compare Mode must not switch it off");
                assertTrue(applies(client, modernFix, true, true, false));
                assertFalse(applies(client, modernFix, true, true, true), "BuildScape with the fix still owns it");
            }
        }
    }

    @Test
    void featureIsDefaultOnCommonAndNotInTheModernFixOverlapSet() {
        assertTrue(BackportFeature.RELOAD_BARRIER_RACE_FIX.defaultEnabled());
        assertEquals("reloadBarrierRaceFix", BackportFeature.RELOAD_BARRIER_RACE_FIX.configKey());
        assertTrue(BackportFeature.RELOAD_BARRIER_RACE_FIX.modernFixMixinKeys().isEmpty());
        assertFalse(MixinNameSets.MODERNFIX_OVERLAPS.contains(MIXIN));
    }
}

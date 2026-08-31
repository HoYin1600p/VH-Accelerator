package dev.hoyin1600p.vhaccelerator.backport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class BackportOwnershipResolverTest {
    private static final BackportFeature COMMON =
            BackportFeature.FORGE_HANDSHAKE_BATCHING;
    private static final BackportFeature CLIENT =
            BackportFeature.DYNAMIC_CLIENT_LANGUAGES;

    @Test
    void modernFixActiveAlwaysOwnsCompatiblePath() {
        assertOwner(
                BackportOwner.MODERNFIX,
                COMMON,
                true,
                false,
                true,
                ModernFixOwnership.ACTIVE,
                true
        );
    }

    @Test
    void unknownModernFixStateFailsClosed() {
        assertOwner(
                BackportOwner.UNAVAILABLE,
                COMMON,
                true,
                false,
                true,
                ModernFixOwnership.UNKNOWN,
                true
        );
    }

    @Test
    void clientFeatureIsRejectedOnDedicatedServer() {
        assertOwner(
                BackportOwner.UNAVAILABLE,
                CLIENT,
                false,
                false,
                true,
                ModernFixOwnership.ABSENT,
                true
        );
    }

    @Test
    void compareModeDisablesImplementedBackport() {
        assertOwner(
                BackportOwner.DISABLED,
                COMMON,
                true,
                true,
                true,
                ModernFixOwnership.ABSENT,
                true
        );
    }

    @Test
    void independentConfigCanDisableImplementedBackport() {
        assertOwner(
                BackportOwner.DISABLED,
                COMMON,
                true,
                false,
                false,
                ModernFixOwnership.INACTIVE,
                true
        );
    }

    @Test
    void absentModernFixAllowsEnabledImplementedBackport() {
        assertOwner(
                BackportOwner.VHA,
                COMMON,
                true,
                false,
                true,
                ModernFixOwnership.ABSENT,
                true
        );
    }

    @Test
    void inventoryCannotClaimUnimplementedBackport() {
        assertOwner(
                BackportOwner.UNAVAILABLE,
                COMMON,
                true,
                false,
                true,
                ModernFixOwnership.ABSENT,
                false
        );
    }

    @Test
    void incompatibleModFailsClosedWithSpecificReason() {
        BackportDecision decision = BackportOwnershipResolver.resolve(
                CLIENT,
                true,
                false,
                true,
                ModernFixOwnership.ABSENT,
                true,
                "synthetic client compatibility blocker"
        );

        assertEquals(BackportOwner.UNAVAILABLE, decision.owner());
        assertEquals(
                "synthetic client compatibility blocker",
                decision.reason()
        );
    }

    private static void assertOwner(
            BackportOwner expected,
            BackportFeature feature,
            boolean physicalClient,
            boolean compareMode,
            boolean configuredEnabled,
            ModernFixOwnership modernFixOwnership,
            boolean implementationPresent
    ) {
        BackportDecision decision = BackportOwnershipResolver.resolve(
                feature,
                physicalClient,
                compareMode,
                configuredEnabled,
                modernFixOwnership,
                implementationPresent
        );
        assertEquals(expected, decision.owner());
    }
}

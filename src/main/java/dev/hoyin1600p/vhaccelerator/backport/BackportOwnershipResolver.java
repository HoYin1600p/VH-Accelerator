package dev.hoyin1600p.vhaccelerator.backport;

public final class BackportOwnershipResolver {
    private BackportOwnershipResolver() {
    }

    public static BackportDecision resolve(
            BackportFeature feature,
            boolean physicalClient,
            boolean compareMode,
            boolean configuredEnabled,
            ModernFixOwnership modernFixOwnership
    ) {
        return resolve(
                feature,
                physicalClient,
                compareMode,
                configuredEnabled,
                modernFixOwnership,
                feature.implemented()
        );
    }

    static BackportDecision resolve(
            BackportFeature feature,
            boolean physicalClient,
            boolean compareMode,
            boolean configuredEnabled,
            ModernFixOwnership modernFixOwnership,
            boolean implementationPresent
    ) {
        if (feature.side() == BackportSide.CLIENT && !physicalClient) {
            return decision(
                    feature,
                    BackportOwner.UNAVAILABLE,
                    "not available on a dedicated server"
            );
        }
        if (modernFixOwnership == ModernFixOwnership.ACTIVE) {
            return decision(
                    feature,
                    BackportOwner.MODERNFIX,
                    "the effective ModernFix option owns this path"
            );
        }
        if (modernFixOwnership == ModernFixOwnership.UNKNOWN) {
            return decision(
                    feature,
                    BackportOwner.UNAVAILABLE,
                    "ModernFix ownership could not be verified"
            );
        }
        if (compareMode) {
            return decision(
                    feature,
                    BackportOwner.DISABLED,
                    "Compare Mode suppresses VH Accelerator optimizations"
            );
        }
        if (!implementationPresent) {
            return decision(
                    feature,
                    BackportOwner.UNAVAILABLE,
                    "planned for 1.0.14; implementation is not present yet"
            );
        }
        if (!configuredEnabled) {
            return decision(
                    feature,
                    BackportOwner.DISABLED,
                    "disabled in vhaccelerator-common.toml"
            );
        }
        return decision(
                feature,
                BackportOwner.VHA,
                "enabled and owned by VH Accelerator"
        );
    }

    private static BackportDecision decision(
            BackportFeature feature,
            BackportOwner owner,
            String reason
    ) {
        return new BackportDecision(feature, owner, reason);
    }
}

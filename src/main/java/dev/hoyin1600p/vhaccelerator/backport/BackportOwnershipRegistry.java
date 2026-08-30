package dev.hoyin1600p.vhaccelerator.backport;

import dev.hoyin1600p.vhaccelerator.BootstrapBackportConfig;
import dev.hoyin1600p.vhaccelerator.BootstrapCompareMode;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BackportOwnershipRegistry {
    private static volatile Map<BackportFeature, BackportDecision> decisions =
            unavailableSnapshot("mixin selection has not completed");

    private BackportOwnershipRegistry() {
    }

    public static synchronized void initialize(
            boolean physicalClient,
            Function<BackportFeature, ModernFixOwnership> modernFixProbe,
            Function<BackportFeature, String> compatibilityProbe
    ) {
        BootstrapBackportConfig.capture();
        EnumMap<BackportFeature, BackportDecision> resolved =
                new EnumMap<>(BackportFeature.class);
        for (BackportFeature feature : BackportFeature.values()) {
            resolved.put(
                    feature,
                    BackportOwnershipResolver.resolve(
                            feature,
                            physicalClient,
                            BootstrapCompareMode.enabled(),
                            BootstrapBackportConfig.enabled(feature),
                            modernFixProbe.apply(feature),
                            feature.implemented(),
                            compatibilityProbe.apply(feature)
                    )
            );
        }
        decisions = Map.copyOf(resolved);
    }

    public static BackportDecision decision(BackportFeature feature) {
        return decisions.get(feature);
    }

    public static boolean vhaOwns(BackportFeature feature) {
        return decision(feature).owner() == BackportOwner.VHA;
    }

    public static List<String> reportLines() {
        return Arrays.stream(BackportFeature.values())
                .map(feature -> {
                    BackportDecision decision = decision(feature);
                    return feature.id()
                            + " [" + feature.side().displayName() + "]="
                            + decision.owner().name()
                            + " - " + decision.reason();
                })
                .toList();
    }

    public static String summary() {
        return decisions.values().stream()
                .collect(Collectors.groupingBy(
                        BackportDecision::owner,
                        () -> new EnumMap<>(BackportOwner.class),
                        Collectors.counting()
                ))
                .entrySet().stream()
                .map(entry -> entry.getKey().name()
                        + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    private static Map<BackportFeature, BackportDecision> unavailableSnapshot(
            String reason
    ) {
        EnumMap<BackportFeature, BackportDecision> unavailable =
                new EnumMap<>(BackportFeature.class);
        for (BackportFeature feature : BackportFeature.values()) {
            unavailable.put(
                    feature,
                    new BackportDecision(
                            feature,
                            BackportOwner.UNAVAILABLE,
                            reason
                    )
            );
        }
        return Map.copyOf(unavailable);
    }
}

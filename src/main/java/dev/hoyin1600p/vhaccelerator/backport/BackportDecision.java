package dev.hoyin1600p.vhaccelerator.backport;

public record BackportDecision(
        BackportFeature feature,
        BackportOwner owner,
        String reason
) {
}

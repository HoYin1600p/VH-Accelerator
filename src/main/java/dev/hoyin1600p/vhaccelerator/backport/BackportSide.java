package dev.hoyin1600p.vhaccelerator.backport;

public enum BackportSide {
    COMMON("client + server"),
    CLIENT("client");

    private final String displayName;

    BackportSide(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}

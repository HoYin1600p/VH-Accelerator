package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Objects;

public record UpdateNotice(
        String modId,
        String displayName,
        String targetVersion,
        Severity severity,
        String message,
        String downloadUrl
) {
    public UpdateNotice {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(targetVersion, "targetVersion");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(downloadUrl, "downloadUrl");
    }

    public enum Severity {
        NORMAL(10),
        CRITICAL(5);

        private final int reminderInterval;

        Severity(int reminderInterval) {
            this.reminderInterval = reminderInterval;
        }

        public int reminderInterval() {
            return reminderInterval;
        }
    }
}

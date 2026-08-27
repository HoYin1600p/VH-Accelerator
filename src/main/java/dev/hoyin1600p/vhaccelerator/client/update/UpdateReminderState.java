package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Objects;

final class UpdateReminderState {
    private String targetVersion = "";
    private String severity = "";
    private String message = "";
    private int successfulJoinsSinceReminder;
    private boolean notified;

    boolean recordSuccessfulJoin(UpdateNotice notice) {
        if (!matches(notice)) {
            targetVersion = notice.targetVersion();
            severity = notice.severity().name();
            message = notice.message();
            successfulJoinsSinceReminder = 0;
            notified = false;
        }

        successfulJoinsSinceReminder++;
        if (!notified
                || successfulJoinsSinceReminder
                >= notice.severity().reminderInterval()) {
            notified = true;
            successfulJoinsSinceReminder = 0;
            return true;
        }
        return false;
    }

    int successfulJoinsSinceReminder() {
        return successfulJoinsSinceReminder;
    }

    boolean notified() {
        return notified;
    }

    private boolean matches(UpdateNotice notice) {
        return Objects.equals(targetVersion, notice.targetVersion())
                && Objects.equals(severity, notice.severity().name())
                && Objects.equals(message, notice.message());
    }
}

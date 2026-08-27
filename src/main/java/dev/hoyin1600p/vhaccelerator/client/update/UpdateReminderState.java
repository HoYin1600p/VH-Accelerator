package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Objects;

final class UpdateReminderState {
    private String targetVersion = "";
    private String severity = "";
    private String message = "";
    private int eligibleLaunchesSinceReminder;

    boolean recordEligibleLaunch(UpdateNotice notice) {
        if (!matches(notice)) {
            targetVersion = notice.targetVersion();
            severity = notice.severity().name();
            message = notice.message();
            eligibleLaunchesSinceReminder = 0;
        }

        eligibleLaunchesSinceReminder++;
        if (eligibleLaunchesSinceReminder
                >= notice.severity().reminderInterval()) {
            eligibleLaunchesSinceReminder = 0;
            return true;
        }
        return false;
    }

    int eligibleLaunchesSinceReminder() {
        return eligibleLaunchesSinceReminder;
    }

    private boolean matches(UpdateNotice notice) {
        return Objects.equals(targetVersion, notice.targetVersion())
                && Objects.equals(severity, notice.severity().name())
                && Objects.equals(message, notice.message());
    }
}

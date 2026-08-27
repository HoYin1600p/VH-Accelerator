package dev.hoyin1600p.vhaccelerator.client.update;

/**
 * Tracks the one update-reminder opportunity owned by a single client JVM.
 */
final class UpdateNoticeSession {
    private boolean awaitingPlayableFrame;
    private boolean worldPlayable;
    private boolean firstPlayableFrameSeen;
    private boolean launchRecorded;
    private boolean reminderArmed;
    private boolean reminderDelivered;

    void markPlayerLoggedIn() {
        awaitingPlayableFrame = true;
        worldPlayable = false;
    }

    void markPlayerLoggedOut() {
        awaitingPlayableFrame = false;
        worldPlayable = false;
    }

    void markReceivingLevel() {
        worldPlayable = false;
    }

    boolean needsPlayableFrame() {
        return awaitingPlayableFrame || !worldPlayable;
    }

    void markPlayableFrame() {
        awaitingPlayableFrame = false;
        worldPlayable = true;
        firstPlayableFrameSeen = true;
    }

    boolean claimEligibleLaunch() {
        if (!firstPlayableFrameSeen || launchRecorded) {
            return false;
        }
        launchRecorded = true;
        return true;
    }

    void armReminder(boolean armed) {
        reminderArmed = armed;
    }

    boolean claimReminderDelivery() {
        if (!reminderArmed || reminderDelivered || !worldPlayable) {
            return false;
        }
        reminderArmed = false;
        reminderDelivered = true;
        return true;
    }

    void disable() {
        reminderArmed = false;
    }
}

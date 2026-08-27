package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Map;
import java.util.Optional;
import org.apache.maven.artifact.versioning.ComparableVersion;

public final class UpdateNoticeParser {
    private static final String CRITICAL_PREFIX = "[CRITICAL]";
    private static final String NORMAL_PREFIX = "[NORMAL]";
    private static final int MAX_MESSAGE_LENGTH = 96;

    private UpdateNoticeParser() {
    }

    public static Optional<UpdateNotice> parse(
            String modId,
            String currentVersion,
            String targetVersion,
            Map<String, String> changes,
            String displayName,
            String downloadUrl
    ) {
        if (currentVersion == null
                || targetVersion == null
                || new ComparableVersion(currentVersion).compareTo(
                new ComparableVersion(targetVersion)
        ) >= 0) {
            return Optional.empty();
        }

        ParsedMessage parsedMessage = parseMessage(
                findTargetMessage(targetVersion, changes)
        );
        return Optional.of(new UpdateNotice(
                modId,
                displayName,
                targetVersion,
                parsedMessage.severity(),
                parsedMessage.message(),
                downloadUrl
        ));
    }

    static ParsedMessage parseMessage(String rawMessage) {
        String message = normalizeMessage(rawMessage);
        UpdateNotice.Severity severity = UpdateNotice.Severity.NORMAL;

        if (startsWithIgnoreCase(message, CRITICAL_PREFIX)) {
            severity = UpdateNotice.Severity.CRITICAL;
            message = message.substring(CRITICAL_PREFIX.length()).trim();
        } else if (startsWithIgnoreCase(message, NORMAL_PREFIX)) {
            message = message.substring(NORMAL_PREFIX.length()).trim();
        }

        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH - 1).trim()
                    + "…";
        }
        return new ParsedMessage(severity, message);
    }

    private static String findTargetMessage(
            String target,
            Map<String, String> changes
    ) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }

        ComparableVersion comparableTarget = new ComparableVersion(target);
        for (Map.Entry<String, String> entry : changes.entrySet()) {
            if (new ComparableVersion(entry.getKey()).compareTo(
                    comparableTarget
            ) == 0) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static String normalizeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.length() >= prefix.length()
                && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    record ParsedMessage(UpdateNotice.Severity severity, String message) {
    }
}

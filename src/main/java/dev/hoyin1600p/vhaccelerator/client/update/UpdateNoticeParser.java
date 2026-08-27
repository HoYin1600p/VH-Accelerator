package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Map;
import java.util.Optional;
import net.minecraftforge.fml.VersionChecker;
import org.apache.maven.artifact.versioning.ComparableVersion;

public final class UpdateNoticeParser {
    private static final String CRITICAL_PREFIX = "[CRITICAL]";
    private static final String NORMAL_PREFIX = "[NORMAL]";
    private static final int MAX_MESSAGE_LENGTH = 96;

    private UpdateNoticeParser() {
    }

    public static Optional<UpdateNotice> parse(
            String modId,
            VersionChecker.CheckResult result,
            String displayName,
            String downloadUrl
    ) {
        if (result == null
                || (result.status() != VersionChecker.Status.OUTDATED
                && result.status() != VersionChecker.Status.BETA_OUTDATED)
                || result.target() == null) {
            return Optional.empty();
        }

        ParsedMessage parsedMessage = parseMessage(
                findTargetMessage(result.target(), result.changes())
        );
        return Optional.of(new UpdateNotice(
                modId,
                displayName,
                result.target().toString(),
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
            ComparableVersion target,
            Map<ComparableVersion, String> changes
    ) {
        if (changes == null || changes.isEmpty()) {
            return "";
        }

        for (Map.Entry<ComparableVersion, String> entry : changes.entrySet()) {
            if (entry.getKey().compareTo(target) == 0) {
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

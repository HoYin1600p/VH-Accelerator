package dev.hoyin1600p.vhaccelerator.client.update;

import java.util.Locale;
import java.util.Objects;

/**
 * Selects which remote update severities may be shown to the player.
 */
public enum UpdateNoticeFilter {
    CRITICAL,
    ALL;

    public boolean allows(UpdateNotice notice) {
        Objects.requireNonNull(notice, "notice");
        return this == ALL
                || notice.severity() == UpdateNotice.Severity.CRITICAL;
    }

    public static UpdateNoticeFilter fromConfigValue(
            Object value,
            UpdateNoticeFilter fallback
    ) {
        Objects.requireNonNull(fallback, "fallback");
        if (value instanceof UpdateNoticeFilter filter) {
            return filter;
        }
        if (value instanceof String text) {
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Use the caller's safe default for unknown future values.
            }
        }
        return fallback;
    }
}

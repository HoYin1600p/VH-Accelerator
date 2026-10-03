package dev.hoyin1600p.vhaccelerator.client.bugreport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes personal details from bug-report text before it is previewed or sent: user-folder paths
 * (Windows, Linux and macOS), the player's name and UUID, and the OS account name.
 */
public final class BugReportScrubber {
    public static final String USER = "<user>";
    public static final String PLAYER = "<player>";
    public static final String UUID_MARK = "<uuid>";

    // C:\Users\Name\..., C:/Users/Name/..., and the doubled backslashes of escaped strings. The name
    // runs to the next separator, so names with spaces are removed whole.
    private static final Pattern WINDOWS_HOME = Pattern.compile(
            "(?i)\\b[a-z]:(?:\\\\{1,2}|/)users(?:\\\\{1,2}|/)[^\\\\/\\r\\n\"'<>|:*?]+");
    private static final Pattern UNIX_HOME = Pattern.compile("(?<![\\w.])/(?:home|Users)/[^/\\s\"']+");
    private static final Pattern MINIMUM_NAME = Pattern.compile("[A-Za-z0-9_.\\- ]{3,}");

    private record Replacement(Pattern pattern, String text) {
    }

    private final List<Replacement> names = new ArrayList<>();
    private final List<Pattern> uuids = new ArrayList<>();

    /**
     * @param playerName the Minecraft username, or null
     * @param playerUuid the Minecraft UUID with or without dashes, or null
     * @param osUserName the operating-system account name, or null
     */
    public BugReportScrubber(String playerName, String playerUuid, String osUserName) {
        addName(playerName, PLAYER);
        addName(osUserName, USER);
        if (playerUuid != null && !playerUuid.isBlank()) {
            String compact = playerUuid.replace("-", "").toLowerCase(Locale.ROOT);
            if (compact.length() == 32) {
                String dashed = compact.substring(0, 8) + "-" + compact.substring(8, 12) + "-"
                        + compact.substring(12, 16) + "-" + compact.substring(16, 20) + "-" + compact.substring(20);
                uuids.add(Pattern.compile("(?i)" + Pattern.quote(dashed)));
                uuids.add(Pattern.compile("(?i)" + Pattern.quote(compact)));
            }
        }
    }

    private void addName(String name, String replacement) {
        // Very short names would erase ordinary words; paths still catch the account folder.
        if (name != null && MINIMUM_NAME.matcher(name).matches()) {
            names.add(new Replacement(
                    Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])"),
                    Matcher.quoteReplacement(replacement)));
        }
    }

    public String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = WINDOWS_HOME.matcher(text).replaceAll(Matcher.quoteReplacement(USER));
        result = UNIX_HOME.matcher(result).replaceAll(Matcher.quoteReplacement(USER));
        for (Pattern uuid : uuids) {
            result = uuid.matcher(result).replaceAll(Matcher.quoteReplacement(UUID_MARK));
        }
        for (Replacement name : names) {
            result = name.pattern().matcher(result).replaceAll(name.text());
        }
        return result;
    }
}

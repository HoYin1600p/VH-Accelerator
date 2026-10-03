package dev.hoyin1600p.vhaccelerator.client.bugreport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Finds the newest Minecraft crash report and pulls out its exception line. */
public final class CrashReports {
    private static final Pattern THROWABLE_LINE = Pattern.compile(
            "^(?:Caused by: )?[\\w$]+(?:\\.[\\w$]+)+(?:Exception|Error|Throwable)\\b.*");

    private CrashReports() {
    }

    /** The most recently modified {@code .txt} in the folder, if any. */
    public static Optional<Path> newest(Path crashReportsDirectory) {
        if (!Files.isDirectory(crashReportsDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(crashReportsDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".txt"))
                    .max(Comparator.comparingLong(CrashReports::modified));
        } catch (IOException failure) {
            return Optional.empty();
        }
    }

    /** Reads the file leniently: a crash report with broken bytes is still worth sending. */
    public static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * The exception line of a vanilla crash report: the first non-blank line after "Description:".
     * Falls back to the first line that looks like a throwable, or an empty string.
     */
    public static String exceptionLine(String report) {
        String[] lines = report.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("Description:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String line = lines[j].strip();
                    if (!line.isEmpty()) {
                        return line;
                    }
                }
            }
        }
        for (String line : lines) {
            String stripped = line.strip();
            if (THROWABLE_LINE.matcher(stripped).matches()) {
                return stripped;
            }
        }
        return "";
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException failure) {
            return Long.MIN_VALUE;
        }
    }
}

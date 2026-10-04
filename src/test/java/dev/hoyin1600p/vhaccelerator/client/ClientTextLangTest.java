package dev.hoyin1600p.vhaccelerator.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** The chat, timer and update-notice text: each key exists and takes as many values as the code passes. */
class ClientTextLangTest {
    /** Key to the number of arguments VHAcceleratorClient / UpdateNoticeService pass for it. */
    private static final Map<String, Integer> KEYS = Map.ofEntries(
            Map.entry("vhaccelerator.jei.reload.start", 0),
            Map.entry("vhaccelerator.jei.reload.failed", 1),
            Map.entry("vhaccelerator.jei.reload.done", 1),
            Map.entry("vhaccelerator.timer.compare_suffix", 0),
            Map.entry("vhaccelerator.timer.launch", 2),
            Map.entry("vhaccelerator.timer.launch_login", 3),
            Map.entry("vhaccelerator.timer.transfer", 1),
            Map.entry("vhaccelerator.timer.post_login", 1),
            Map.entry("vhaccelerator.timer.post_login_running", 0),
            Map.entry("vhaccelerator.timer.post_login_done", 1),
            Map.entry("vhaccelerator.update.available", 1),
            Map.entry("vhaccelerator.update.message", 1),
            Map.entry("vhaccelerator.update.download", 0),
            Map.entry("vhaccelerator.update.download_tooltip", 1)
    );
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:[0-9]+[$])?s");

    @Test
    void everyKeyExistsIsNotBlankAndHasTheRightPlaceholderCount() throws Exception {
        JsonObject lang;
        try (InputStream in = getClass().getResourceAsStream("/assets/vhaccelerator/lang/en_us.json")) {
            assertNotNull(in);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        KEYS.forEach((key, arguments) -> {
            assertNotNull(lang.get(key), "missing lang key " + key);
            String text = lang.get(key).getAsString();
            assertFalse(text.isBlank(), "blank lang value for " + key);
            Matcher matcher = PLACEHOLDER.matcher(text);
            int found = 0;
            while (matcher.find()) {
                found++;
            }
            assertEquals(arguments.intValue(), found, "placeholder count for " + key);
        });
    }
}

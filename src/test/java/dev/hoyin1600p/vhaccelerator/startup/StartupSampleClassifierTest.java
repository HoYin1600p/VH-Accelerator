package dev.hoyin1600p.vhaccelerator.startup;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StartupSampleClassifierTest {
    @Test
    void rendererSeparatesStartupNearestInclusiveAndUnavailableStacks() throws Exception {
        Path directory = Path.of("src/test/resources/dev/hoyin1600p/vhaccelerator/startup");
        Gson gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        var input = StartupSampleClassifier.readInput(Files.readString(directory.resolve("sample-classifier-input.json")));
        Map<String, Object> result = StartupSampleClassifier.classify(input);
        assertEquals(6L, result.get("selectedSamples"));
        assertEquals(2L, result.get("outsideStartupSamples"));
        assertEquals(1L, result.get("missingStackSamples"));
        assertEquals(2L, result.get("truncatedStackSamples"));
        assertEquals(6L, ((Map<?, Long>) result.get("nearestOwnerCounts")).values().stream().mapToLong(Long::longValue).sum());
        assertEquals(9L, ((Map<?, Long>) result.get("inclusiveOwnerCounts")).values().stream().mapToLong(Long::longValue).sum());
        Path output = directory.resolve("sample-classifier-output.json");
        if ("1".equals(System.getenv("VHA_REGENERATE_SAMPLE_SNAPSHOT"))) Files.writeString(output, gson.toJson(result) + System.lineSeparator());
        assertEquals(JsonParser.parseString(Files.readString(output)), gson.toJsonTree(result));
    }

    @Test
    void distinctDepthLimitsAndUnknownOwnersAreExplicit() {
        var frames = new ArrayList<String>(java.util.Collections.nCopies(32, "java.util.HashMap"));
        frames.add("com.razz.decocraft.models.bbmodel.BBModelLoader");
        var input = new StartupSampleClassifier.Input(1, 0, 2,
                List.of(new StartupSampleClassifier.Sample(1, frames, false, null)));
        Map<String, Object> result = StartupSampleClassifier.classify(input);
        assertEquals(Map.of("platform_or_framework", 1L), result.get("nearestOwnerCounts"));
        assertEquals(Map.of("Decocraft", 1L), result.get("inclusiveOwnerCounts"));
        assertEquals(1L, result.get("nearestDepthLimitedSamples"));
        assertEquals(0L, result.get("inclusiveDepthLimitedSamples"));
        assertThrows(IllegalArgumentException.class, () -> StartupSampleClassifier.classify(
                new StartupSampleClassifier.Input(1, 2, 0, List.of())));
        assertThrows(IllegalArgumentException.class, () -> StartupSampleClassifier.classify(
                new StartupSampleClassifier.Input(1, 0, 2, List.of(new StartupSampleClassifier.Sample(1, List.of("java/lang/Object"), false, null)))));
    }
}

package dev.hoyin1600p.vhaccelerator.client.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class UpdateManifestParser {
    private UpdateManifestParser() {
    }

    static Optional<UpdateNotice> parse(
            String json,
            String modId,
            String displayName,
            String currentVersion,
            String minecraftVersion,
            String downloadUrl
    ) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject promos = requireObject(root, "promos");
        String targetVersion = requireString(
                promos,
                minecraftVersion + "-latest"
        );

        Map<String, String> changes = new LinkedHashMap<>();
        JsonElement versionElement = root.get(minecraftVersion);
        if (versionElement != null && versionElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : versionElement.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()
                        && entry.getValue().getAsJsonPrimitive().isString()) {
                    changes.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }

        return UpdateNoticeParser.parse(
                modId,
                currentVersion,
                targetVersion,
                changes,
                displayName,
                downloadUrl
        );
    }

    private static JsonObject requireObject(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Update manifest is missing object: " + name
            );
        }
        return element.getAsJsonObject();
    }

    private static String requireString(JsonObject parent, String name) {
        JsonElement element = parent.get(name);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()
                || element.getAsString().isBlank()) {
            throw new IllegalArgumentException(
                    "Update manifest is missing string: " + name
            );
        }
        return element.getAsString().trim();
    }
}

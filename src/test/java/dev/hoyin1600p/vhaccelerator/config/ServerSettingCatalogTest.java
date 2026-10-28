package dev.hoyin1600p.vhaccelerator.config;

import static org.junit.jupiter.api.Assertions.*;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog;
import dev.hoyin1600p.vhaccelerator.network.ServerSettingsProtocol;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.Test;

class ServerSettingCatalogTest {
    @Test void everyCommonSpecKeyIsAllowlistedExactlyOnceAndAppearsInTheServerTab() throws Exception {
        Set<String> expected = new LinkedHashSet<>(); collect(VHAcceleratorConfig.COMMON_SPEC.getValues(), expected);
        Set<String> actual = new LinkedHashSet<>();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        var definitions = new ForgeServerSettings().definitions();
        for (var entry : ServerSettingCatalog.all()) {
            assertTrue(actual.add(entry.key()), "duplicate key");
            assertTrue(entry.key().length() <= ServerSettingsProtocol.MAX_KEY_LENGTH);
            var client = ConfigSettingCatalog.serverSettings().stream()
                    .filter(setting -> setting.path().equals(entry.path())).toList();
            assertEquals(1, client.size(), entry.key());
            assertEquals(entry.restartRequired(), client.get(0).restartRequired());
            assertEquals("vhaccelerator.config." + entry.category() + "." + entry.id(), client.get(0).labelKey());
            Object value = definitions.get(entry.key()).defaultValue();
            assertTrue(value instanceof Boolean || value instanceof Integer || value instanceof Double,
                    "Extend the primitive protocol for any new setting type");
            snapshot.put(entry.key(), Map.of("category", entry.category(), "default", value, "restart", entry.restartRequired()));
        }
        assertEquals(expected, actual); assertEquals(expected, definitions.keySet());
        assertTrue(actual.size() <= ServerSettingsProtocol.MAX_KEYS);
        Path path = Path.of("src/test/resources/server-settings-catalog.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        if ("1".equals(System.getenv("VHA_REGENERATE_SERVER_SETTINGS_SNAPSHOT")))
            Files.writeString(path, gson.toJson(snapshot) + "\n");
        assertEquals(JsonParser.parseString(Files.readString(path)), gson.toJsonTree(snapshot));
    }

    @Test void connectedLocalControlsCannotEditCommonSettingsAndRemoteExperimentsUseServerDefaults() {
        for (var setting : ConfigSettingCatalog.all()) {
            assertTrue(ConfigSettingCatalog.localEditable(setting, false));
            assertEquals(setting.storage() == ConfigSettingCatalog.Storage.CLIENT_TOML,
                    ConfigSettingCatalog.localEditable(setting, true));
            if (setting.storage() == ConfigSettingCatalog.Storage.COMMON_TOML) {
                assertFalse(ConfigSettingCatalog.experimental(setting, true));
                assertEquals(!setting.diagnostics() && !ConfigSettingCatalog.EXPERIMENTAL_EXCLUSIONS.contains(setting.id()),
                        ConfigSettingCatalog.experimental(setting, false));
            }
        }
    }

    @Test void everyFixedServerScreenMessageHasEnglishText() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/vhaccelerator/lang/en_us.json")) {
            assertNotNull(input);
            var language = JsonParser.parseString(new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String suffix : java.util.List.of("category.remote_server", "badge.server_restart", "button.local_default",
                    "button.local_experimental", "server.header", "server.explanation", "server.join", "server.denied",
                    "server.unavailable", "server.loading", "server.unconfirmed", "server.invalid", "server.save_failed",
                    "server.saved", "server.saved_restart", "server.defaults", "server.experimental", "server.confirm_defaults",
                    "server.confirm_experimental", "server.local_defaults")) {
                String key = "vhaccelerator.config." + suffix;
                assertTrue(language.has(key), key); assertFalse(language.get(key).getAsString().isBlank(), key);
            }
        }
    }

    @Test void serverCodeHasNoClientOrClothReferencesEvenInItsBytecode() throws Exception {
        for (String name : java.util.List.of("config/ServerSettingCatalog", "config/ServerSettingsService",
                "config/ForgeServerSettings", "network/ServerSettingsProtocol", "network/ServerSettingsNetwork")) {
            try (var input = getClass().getResourceAsStream("/dev/hoyin1600p/vhaccelerator/" + name + ".class")) {
                assertNotNull(input);
                String bytes = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/"), name);
                assertFalse(bytes.contains("vhaccelerator/client/"), name);
                assertFalse(bytes.contains("me/shedaniel/"), name);
            }
        }
    }

    @Test void dedicatedClassLoaderCanInitializeAndLinkTheServerEntryPointsWithoutClientClasses() throws Exception {
        ClassLoader isolated = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.minecraft.client.") || name.startsWith("dev.hoyin1600p.vhaccelerator.client.")
                        || name.startsWith("me.shedaniel.")) throw new ClassNotFoundException("Client class blocked: " + name);
                boolean owned = name.startsWith("dev.hoyin1600p.vhaccelerator.config.ServerSetting")
                        || name.startsWith("dev.hoyin1600p.vhaccelerator.config.ForgeServerSettings")
                        || name.startsWith("dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig")
                        || name.startsWith("dev.hoyin1600p.vhaccelerator.network.ServerSetting");
                if (!owned) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try (var input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes(); loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };
        for (String name : java.util.List.of("config.ServerSettingCatalog", "config.ServerSettingsService",
                "config.ForgeServerSettings", "network.ServerSettingsProtocol", "network.ServerSettingsNetwork")) {
            Class<?> type = Class.forName("dev.hoyin1600p.vhaccelerator." + name, true, isolated);
            type.getDeclaredMethods();
            if (name.endsWith("ForgeServerSettings")) type.getConstructor().newInstance();
        }
    }

    private static void collect(UnmodifiableConfig config, Set<String> out) {
        for (Object value : config.valueMap().values()) {
            if (value instanceof UnmodifiableConfig child) collect(child, out);
            else if (value instanceof ForgeConfigSpec.ConfigValue<?> setting) out.add(String.join(".", setting.getPath()));
        }
    }
}

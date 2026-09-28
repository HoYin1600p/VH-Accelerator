package dev.hoyin1600p.vhaccelerator.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;

/**
 * Applies third-party compatibility mixins all-or-nothing per group.
 *
 * <p>Mixins in one compatibility package cooperate (for example the JEI
 * search-index mixins share indexing state), so a group is applied only when
 * every member that would otherwise apply passes {@link MixinTargetPreflight}.
 * A group whose target mod changed is skipped with one warning, leaving that
 * mod's original behavior, instead of failing startup on a required injector.
 * Model-bake mixins each target a different mod and form their own groups.
 */
public final class CompatMixinGroups {
    private static final Logger LOGGER = LogManager.getLogger("VH Accelerator");
    private static final String COMPAT_PACKAGE = ".mixin.compat.";
    private static final String MODEL_BAKE_PACKAGE = ".mixin.compat.modelbake.";

    private final List<String> configuredMixins;
    private final BiPredicate<String, String> gate;
    private final MixinTargetPreflight.ClassLookup lookup;
    private final Map<String, Boolean> decisions = new HashMap<>();
    private boolean preflightUnavailable;

    /**
     * @param configuredMixins fully qualified names of every configured mixin
     * @param gate the plugin's existing decision for (target, mixin), with
     *             dotted class names, evaluated without this preflight
     * @param lookup class lookup by internal name; returns null when absent
     */
    public CompatMixinGroups(
            List<String> configuredMixins,
            BiPredicate<String, String> gate,
            MixinTargetPreflight.ClassLookup lookup
    ) {
        this.configuredMixins = List.copyOf(configuredMixins);
        this.gate = gate;
        this.lookup = lookup;
    }

    /** Whether the preflight allows {@code mixinClassName} to apply. */
    public synchronized boolean allows(String mixinClassName) {
        String group = groupOf(mixinClassName);
        if (group == null || preflightUnavailable) {
            return true;
        }
        Boolean decision = decisions.get(group);
        if (decision == null) {
            decision = evaluate(group);
            decisions.put(group, decision);
        }
        return decision;
    }

    static String groupOf(String mixinClassName) {
        if (!mixinClassName.contains(COMPAT_PACKAGE)) {
            return null;
        }
        if (mixinClassName.contains(MODEL_BAKE_PACKAGE)) {
            return mixinClassName;
        }
        int end = mixinClassName.lastIndexOf('.');
        return end < 0 ? mixinClassName : mixinClassName.substring(0, end);
    }

    private boolean evaluate(String group) {
        List<String> problems = new ArrayList<>();
        for (String mixin : configuredMixins) {
            if (!group.equals(groupOf(mixin))) {
                continue;
            }
            ClassNode node;
            try {
                node = lookup.find(mixin.replace('.', '/'));
            } catch (Exception exception) {
                node = null;
            }
            if (node == null) {
                // VHA's own mixin bytecode must be readable; if it is not, the
                // lookup service does not work here, so keep prior behavior.
                preflightUnavailable = true;
                LOGGER.warn(
                        "Compatibility mixin preflight is unavailable ({} could "
                                + "not be read); applying compatibility mixins "
                                + "without target verification",
                        mixin
                );
                return true;
            }
            for (String target : MixinTargetPreflight.targets(node)) {
                if (!gate.test(target.replace('/', '.'), mixin)) {
                    continue;
                }
                for (String problem : MixinTargetPreflight.problems(node, target, lookup)) {
                    problems.add(simpleName(mixin) + ": " + problem);
                }
            }
        }
        if (problems.isEmpty()) {
            return true;
        }
        LOGGER.warn(
                "Skipping VH Accelerator compatibility group {} because the "
                        + "installed mod no longer matches it; that mod keeps its "
                        + "original behavior. Details: {}",
                group.substring(group.indexOf(COMPAT_PACKAGE) + COMPAT_PACKAGE.length()),
                String.join("; ", problems)
        );
        return false;
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Reads the fully qualified mixin names from a mixin configuration JSON
     * resource. Returns an empty list if it cannot be read.
     */
    public static List<String> readConfiguredMixins(
            ClassLoader loader,
            String resource
    ) {
        List<String> names = new ArrayList<>();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return names;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                String mixinPackage = config.get("package").getAsString();
                for (String section : List.of("mixins", "client", "server")) {
                    JsonElement element = config.get(section);
                    if (element instanceof JsonArray array) {
                        for (JsonElement entry : array) {
                            names.add(mixinPackage + "." + entry.getAsString());
                        }
                    }
                }
            }
        } catch (Exception exception) {
            LOGGER.debug("Unable to read mixin configuration {}", resource, exception);
            names.clear();
        }
        return names;
    }
}

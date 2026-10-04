package dev.hoyin1600p.vhaccelerator.client;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Renders the client config spec as text: one line per value in declaration
 * order (path, default, range, comment, restart flag) followed by the TOML
 * file Forge would generate from the defaults. Declaration order is the order
 * of the generated file, so it is part of the contract.
 */
final class ClientConfigSpecSnapshot {
    private ClientConfigSpecSnapshot() {
    }

    static List<String> render() {
        List<String> lines = new ArrayList<>();
        ForgeConfigSpec spec = VHAcceleratorClientConfig.SPEC;
        walk(spec.getSpec(), "", lines);

        CommentedConfig defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        lines.add("--- generated toml ---");
        lines.addAll(List.of(TomlFormat.instance().createWriter().writeToString(defaults).split("\\R")));
        return lines;
    }

    private static void walk(UnmodifiableConfig config, String prefix, List<String> lines) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig section) {
                lines.add("section " + path);
                walk(section, path + ".", lines);
            } else if (value instanceof ForgeConfigSpec.ValueSpec valueSpec) {
                lines.add("value " + path
                        + " | default=" + valueSpec.getDefault()
                        + " | range=" + valueSpec.getRange()
                        + " | restart=" + valueSpec.needsWorldRestart()
                        + " | translation=" + valueSpec.getTranslationKey()
                        + " | comment=" + String.valueOf(valueSpec.getComment()).replace("\n", "\\n"));
            } else {
                lines.add("other " + path + " | " + value);
            }
        }
    }
}

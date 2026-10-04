package dev.hoyin1600p.vhaccelerator.client.bugreport;

import com.mojang.blaze3d.platform.GlUtil;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog.Setting;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingStore;
import dev.hoyin1600p.vhaccelerator.config.VHAcceleratorConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.versions.forge.ForgeVersion;

/** Gathers what a bug report needs on the render thread and scrubs every piece of it. */
public final class BugReportCollector {
    /** Other mods whose versions matter for VHA bugs: the ones it optimizes or works around. */
    private static final List<String> RELATED_MOD_IDS = List.of(
            "the_vault", "jei", "modernfix", "embeddium", "rubidium", "vault_render_optimization",
            "farsight_view", "buildscape", "ctm", "kubejs", "crafttweaker"
    );

    /** A crash report found on disk, already scrubbed. */
    public record CrashFile(String fileName, String text, String exceptionLine) {
    }

    public record Collected(GitHubIssue.Report report, Optional<CrashFile> crash) {
    }

    private BugReportCollector() {
    }

    public static Collected collect() {
        Minecraft minecraft = Minecraft.getInstance();
        User user = minecraft.getUser();
        BugReportScrubber scrubber = new BugReportScrubber(
                user == null ? null : user.getName(),
                user == null ? null : user.getUuid(),
                System.getProperty("user.name"));

        List<String> versions = List.of(
                "VH Accelerator: " + modVersion(VHAccelerator.MOD_ID),
                "Minecraft: " + SharedConstants.getCurrentVersion().getName(),
                "Forge: " + ForgeVersion.getVersion(),
                "Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")",
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                        + " (" + System.getProperty("os.arch") + ")"
        );
        List<String> environment = new ArrayList<>();
        // Model and atlas problems can depend on the GPU and driver.
        environment.add("GPU: " + GlUtil.getRenderer() + " (" + GlUtil.getVendor() + ")");
        environment.add("OpenGL and driver: " + GlUtil.getOpenGLVersion());
        for (String modId : RELATED_MOD_IDS) {
            environment.add(modId + ": " + modVersion(modId));
        }

        GitHubIssue.Report report = new GitHubIssue.Report(
                "Bug: ",
                scrub(scrubber, versions),
                scrub(scrubber, environment),
                scrub(scrubber, modState()),
                scrub(scrubber, nonDefaultSettings()),
                scrub(scrubber, statusLines())
        );
        return new Collected(report, crash(minecraft.gameDirectory.toPath().resolve("crash-reports"), scrubber));
    }

    /** Switches that change everything VHA does, for triage. */
    private static List<String> modState() {
        return List.of(
                "Compare Mode: " + (VHAcceleratorConfig.compareModeEnabled() ? "ON" : "off"),
                "Client optimizations: " + (VHAcceleratorClientConfig.optimizationsEnabled() ? "on" : "OFF"),
                "Common optimizations: " + (VHAcceleratorConfig.commonOptimizationsEnabled() ? "on" : "OFF"),
                "Debug diagnostics: " + (VHAcceleratorConfig.debugDiagnosticsEnabled() ? "on" : "off")
        );
    }

    /** VHA has no multi-line status text; the section stays hidden. */
    private static List<String> statusLines() {
        return List.of();
    }

    private static Optional<CrashFile> crash(Path directory, BugReportScrubber scrubber) {
        return CrashReports.newest(directory).flatMap(file -> {
            try {
                String text = scrubber.scrub(CrashReports.read(file).replace("\t", "    "));
                return Optional.of(new CrashFile(file.getFileName().toString(), text, CrashReports.exceptionLine(text)));
            } catch (IOException failure) {
                VHAccelerator.LOGGER.warn("Could not read crash report {}", file.getFileName(), failure);
                return Optional.empty();
            }
        });
    }

    private static List<String> nonDefaultSettings() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Setting, Object> entry : ConfigSettingStore.currentValues().entrySet()) {
            Setting setting = entry.getKey();
            if (!Objects.equals(entry.getValue(), ConfigSettingStore.defaultValue(setting))) {
                lines.add(String.join(".", setting.path()) + " = " + entry.getValue());
            }
        }
        return lines;
    }

    private static String modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("not installed");
    }

    private static List<String> scrub(BugReportScrubber scrubber, List<String> lines) {
        return lines.stream().map(scrubber::scrub).toList();
    }
}

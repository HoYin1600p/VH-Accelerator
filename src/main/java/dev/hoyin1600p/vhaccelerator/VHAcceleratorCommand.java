package dev.hoyin1600p.vhaccelerator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

/**
 * Controls optimization and instrumentation settings at runtime.
 */
public final class VHAcceleratorCommand {
    private VHAcceleratorCommand() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            boolean requireAdministrator
    ) {
        dispatcher.register(
                Commands.literal("vha")
                        .requires(source ->
                                !requireAdministrator
                                        || source.hasPermission(2))
                        .executes(context ->
                                reportAll(context.getSource()))
                        .then(Commands.literal("compare")
                                .executes(context ->
                                        reportCompare(
                                                context.getSource()
                                        ))
                                .then(Commands.literal("on")
                                        .executes(context ->
                                                setCompare(
                                                        context.getSource(),
                                                        true
                                                )))
                                .then(Commands.literal("off")
                                        .executes(context ->
                                                setCompare(
                                                        context.getSource(),
                                                        false
                                                )))
                                .then(Commands.literal("status")
                                        .executes(context ->
                                                reportCompare(
                                                        context.getSource()
                                                ))))
                        .then(toggleCommand(
                                "timers",
                                VHAcceleratorCommand::setTimers,
                                VHAcceleratorCommand::reportTimers
                        ))
                        .then(toggleCommand(
                                "debug",
                                VHAcceleratorCommand::setDebug,
                                VHAcceleratorCommand::reportDebug
                        ))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggleCommand(
                    String name,
                    ToggleSetter setter,
                    StatusReporter reporter
    ) {
        return Commands.literal(name)
                .executes(context ->
                        reporter.report(context.getSource()))
                .then(Commands.literal("on")
                        .executes(context ->
                                setter.set(
                                        context.getSource(),
                                        true
                                )))
                .then(Commands.literal("off")
                        .executes(context ->
                                setter.set(
                                        context.getSource(),
                                        false
                                )))
                .then(Commands.literal("status")
                        .executes(context ->
                                reporter.report(
                                        context.getSource()
                                )));
    }

    private static int setCompare(
            CommandSourceStack source,
            boolean enabled
    ) {
        VHAcceleratorConfig.setCompareMode(enabled);
        String state = enabled ? "enabled" : "disabled";
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Compare Mode "
                                + state
                                + " and saved. Restart before measuring "
                                + "launch time."
                ),
                false
        );
        return 1;
    }

    private static int reportCompare(CommandSourceStack source) {
        boolean enabled =
                VHAcceleratorConfig.compareModeEnabled();
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Compare Mode is "
                                + (enabled ? "ON" : "OFF")
                                + ". Timers and debug diagnostics are "
                                + "controlled independently."
                ),
                false
        );
        return enabled ? 1 : 0;
    }

    private static int setTimers(
            CommandSourceStack source,
            boolean enabled
    ) {
        VHAcceleratorConfig.setTimersEnabled(enabled);
        sendState(
                source,
                "Timers",
                enabled,
                "Saved. The display and routine timer logging update "
                        + "immediately."
        );
        return 1;
    }

    private static int reportTimers(CommandSourceStack source) {
        boolean enabled = VHAcceleratorConfig.timersEnabled();
        sendState(source, "Timers", enabled, null);
        return enabled ? 1 : 0;
    }

    private static int setDebug(
            CommandSourceStack source,
            boolean enabled
    ) {
        VHAcceleratorConfig.setDebugDiagnosticsEnabled(enabled);
        sendState(
                source,
                "Debug diagnostics",
                enabled,
                enabled
                        ? "Saved. Reconnect for connection diagnostics and "
                                + "restart for launch diagnostics."
                        : "Saved. New diagnostic sampling stops immediately."
        );
        return 1;
    }

    private static int reportDebug(CommandSourceStack source) {
        boolean enabled =
                VHAcceleratorConfig.debugDiagnosticsEnabled();
        sendState(source, "Debug diagnostics", enabled, null);
        return enabled ? 1 : 0;
    }

    private static int reportAll(CommandSourceStack source) {
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Compare="
                                + state(
                                        VHAcceleratorConfig
                                                .compareModeEnabled()
                                )
                                + ", timers="
                                + state(
                                        VHAcceleratorConfig
                                                .timersEnabled()
                                )
                                + ", debug="
                                + state(
                                        VHAcceleratorConfig
                                                .debugDiagnosticsEnabled()
                                )
                ),
                false
        );
        return 1;
    }

    private static void sendState(
            CommandSourceStack source,
            String name,
            boolean enabled,
            String detail
    ) {
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] "
                                + name
                                + " is "
                                + state(enabled)
                                + "."
                                + (detail == null ? "" : " " + detail)
                ),
                false
        );
    }

    private static String state(boolean enabled) {
        return enabled ? "ON" : "OFF";
    }

    @FunctionalInterface
    private interface ToggleSetter {
        int set(CommandSourceStack source, boolean enabled);
    }

    @FunctionalInterface
    private interface StatusReporter {
        int report(CommandSourceStack source);
    }
}

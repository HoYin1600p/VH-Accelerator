package dev.hoyin1600p.vhaccelerator;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.TextComponent;

/**
 * Controls the persistent optimization baseline without disabling diagnostics.
 */
public final class CompareModeCommand {
    private CompareModeCommand() {
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
                        .then(Commands.literal("compare")
                                .executes(context ->
                                        report(context.getSource()))
                                .then(Commands.literal("on")
                                        .executes(context ->
                                                set(
                                                        context.getSource(),
                                                        true
                                                )))
                                .then(Commands.literal("off")
                                        .executes(context ->
                                                set(
                                                        context.getSource(),
                                                        false
                                                )))
                                .then(Commands.literal("status")
                                        .executes(context ->
                                                report(
                                                        context.getSource()
                                                ))))
        );
    }

    private static int set(
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

    private static int report(CommandSourceStack source) {
        boolean enabled =
                VHAcceleratorConfig.compareModeEnabled();
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Compare Mode is "
                                + (enabled ? "ON" : "OFF")
                                + ". Timers and profilers remain active."
                ),
                false
        );
        return enabled ? 1 : 0;
    }
}

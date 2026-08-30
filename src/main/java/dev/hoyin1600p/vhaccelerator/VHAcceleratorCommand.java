package dev.hoyin1600p.vhaccelerator;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.hoyin1600p.vhaccelerator.client.update.UpdateNoticeFilter;
import dev.hoyin1600p.vhaccelerator.backport.BackportOwnershipRegistry;
import java.util.function.ToIntFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
        register(
                dispatcher,
                requireAdministrator,
                null,
                null,
                null,
                null,
                null
        );
    }

    public static void registerClient(
            CommandDispatcher<CommandSourceStack> dispatcher,
            ToIntFunction<CommandSourceStack> reloadJei,
            BooleanSupplier updateChecksEnabled,
            Consumer<Boolean> updateChecksSetter,
            Supplier<UpdateNoticeFilter> updateFilter,
            Consumer<UpdateNoticeFilter> updateFilterSetter
    ) {
        register(
                dispatcher,
                false,
                reloadJei,
                updateChecksEnabled,
                updateChecksSetter,
                updateFilter,
                updateFilterSetter
        );
    }

    private static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            boolean requireAdministrator,
            ToIntFunction<CommandSourceStack> reloadJei,
            BooleanSupplier updateChecksEnabled,
            Consumer<Boolean> updateChecksSetter,
            Supplier<UpdateNoticeFilter> updateFilter,
            Consumer<UpdateNoticeFilter> updateFilterSetter
    ) {
        LiteralArgumentBuilder<CommandSourceStack> root =
                Commands.literal("vha")
                        .requires(source ->
                                !requireAdministrator
                                        || source.hasPermission(2))
                        .executes(context ->
                                reportAll(
                                        context.getSource(),
                                        updateChecksEnabled,
                                        updateFilter
                                ))
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
                        .then(toggleCommand(
                                "jei_audit",
                                VHAcceleratorCommand::setJeiAudit,
                                VHAcceleratorCommand::reportJeiAudit
                        ))
                        .then(Commands.literal("backports")
                                .executes(context -> reportBackports(
                                        context.getSource()
                                )));
        if (updateChecksEnabled != null
                && updateChecksSetter != null
                && updateFilter != null
                && updateFilterSetter != null) {
            root.then(updateCommand(
                    updateChecksEnabled,
                    updateChecksSetter,
                    updateFilter,
                    updateFilterSetter
            ));
        }
        if (reloadJei != null) {
            root.then(Commands.literal("reload_jei")
                    .executes(context ->
                            reloadJei.applyAsInt(
                                    context.getSource()
                            )));
        }
        dispatcher.register(root);
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
                "Saved. Routine chat timing messages and timing logs update "
                        + "immediately. The main-menu launch time remains "
                        + "visible."
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

    private static int setJeiAudit(
            CommandSourceStack source,
            boolean enabled
    ) {
        VHAcceleratorConfig.setJeiRecipeAuditEnabled(enabled);
        sendState(
                source,
                "JEI recipe-cache audit",
                enabled,
                enabled
                        ? "Saved. Reconnect to record repaired recipe IDs "
                                + "and cached-versus-live plan differences."
                        : "Saved. Targeted JEI audit logging stops "
                                + "immediately."
        );
        return 1;
    }

    private static int reportJeiAudit(CommandSourceStack source) {
        boolean enabled = VHAcceleratorConfig.jeiRecipeAuditEnabled();
        sendState(source, "JEI recipe-cache audit", enabled, null);
        return enabled ? 1 : 0;
    }

    private static int setUpdates(
            CommandSourceStack source,
            boolean enabled,
            Consumer<Boolean> updateChecksSetter
    ) {
        updateChecksSetter.accept(enabled);
        sendState(
                source,
                "Update checks",
                enabled,
                "Saved. GitHub update checks and notices update immediately."
        );
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> updateCommand(
            BooleanSupplier updateChecksEnabled,
            Consumer<Boolean> updateChecksSetter,
            Supplier<UpdateNoticeFilter> updateFilter,
            Consumer<UpdateNoticeFilter> updateFilterSetter
    ) {
        return Commands.literal("updates")
                .executes(context -> reportUpdates(
                        context.getSource(),
                        updateChecksEnabled,
                        updateFilter
                ))
                .then(Commands.literal("on")
                        .executes(context -> setUpdates(
                                context.getSource(),
                                true,
                                updateChecksSetter
                        )))
                .then(Commands.literal("off")
                        .executes(context -> setUpdates(
                                context.getSource(),
                                false,
                                updateChecksSetter
                        )))
                .then(Commands.literal("status")
                        .executes(context -> reportUpdates(
                                context.getSource(),
                                updateChecksEnabled,
                                updateFilter
                        )))
                .then(Commands.literal("critical")
                        .executes(context -> setUpdateFilter(
                                context.getSource(),
                                UpdateNoticeFilter.CRITICAL,
                                updateFilterSetter
                        )))
                .then(Commands.literal("all")
                        .executes(context -> setUpdateFilter(
                                context.getSource(),
                                UpdateNoticeFilter.ALL,
                                updateFilterSetter
                        )));
    }

    private static int setUpdateFilter(
            CommandSourceStack source,
            UpdateNoticeFilter filter,
            Consumer<UpdateNoticeFilter> updateFilterSetter
    ) {
        updateFilterSetter.accept(filter);
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Update notices now show "
                                + filterDescription(filter)
                                + ". Saved and applied immediately."
                ),
                false
        );
        return 1;
    }

    private static int reportUpdates(
            CommandSourceStack source,
            BooleanSupplier updateChecksEnabled,
            Supplier<UpdateNoticeFilter> updateFilter
    ) {
        boolean enabled = updateChecksEnabled.getAsBoolean();
        sendState(
                source,
                "Update checks",
                enabled,
                "Showing " + filterDescription(updateFilter.get()) + "."
        );
        return enabled ? 1 : 0;
    }

    private static int reportAll(
            CommandSourceStack source,
            BooleanSupplier updateChecksEnabled,
            Supplier<UpdateNoticeFilter> updateFilter
    ) {
        String updateState = updateChecksEnabled == null
                ? ""
                : ", updates=" + state(updateChecksEnabled.getAsBoolean())
                + ", updateTypes=" + updateFilter.get().name();
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
                                + ", jeiAudit="
                                + state(
                                        VHAcceleratorConfig
                                                .jeiRecipeAuditEnabled()
                                )
                                + ", backports="
                                + BackportOwnershipRegistry.summary()
                                + updateState
                ),
                false
        );
        return 1;
    }

    private static int reportBackports(CommandSourceStack source) {
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Backport ownership for this launch:"
                ),
                false
        );
        for (String line : BackportOwnershipRegistry.reportLines()) {
            source.sendSuccess(
                    new TextComponent("[VH Accelerator] " + line),
                    false
            );
        }
        source.sendSuccess(
                new TextComponent(
                        "[VH Accelerator] Backport settings are restart-bound."
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

    private static String filterDescription(UpdateNoticeFilter filter) {
        return filter == UpdateNoticeFilter.ALL
                ? "all available updates"
                : "critical updates only";
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

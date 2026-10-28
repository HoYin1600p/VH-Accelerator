package dev.hoyin1600p.vhaccelerator.client.config.cloth;

import dev.hoyin1600p.vhaccelerator.client.bugreport.BugReportScreen;
import dev.hoyin1600p.vhaccelerator.client.config.ScreenScale;
import dev.hoyin1600p.vhaccelerator.client.config.ServerSettingsClient;
import dev.hoyin1600p.vhaccelerator.config.ServerSettingsService;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog.Category;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog.Setting;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingCatalog;
import dev.hoyin1600p.vhaccelerator.client.config.catalog.ConfigSettingStore;
import dev.hoyin1600p.vhaccelerator.client.gui.GuiLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The mod's settings screen. Every Cloth Config reference lives in this package, and this class is
 * only loaded after {@code ModList} confirmed Cloth is installed. Tabs, entries and the
 * Default/Experimental sets all come from {@link ConfigSettingCatalog}; writing goes through
 * {@link ConfigSettingStore}.
 */
public final class SettingsScreen {
    // Cloth's footer: Cancel and Save are centered, each min(200, (width - 62) / 3) wide.
    private static final int FOOTER_MARGIN = GuiLayout.FOOTER_MARGIN;
    // At the 960-unit virtual width there is room for 265; keep the extra buttons button-sized.
    private static final int MAX_SIDE_BUTTON_WIDTH = 150;
    // Dialog lists stop here so the dialog buttons stay on screen.
    private static final int MAX_LISTED = 8;
    // Small ranges read best as sliders; wide ones (memory sizes, counts) as typed numbers.
    private static final int MAX_SLIDER_RANGE = 64;

    private static Screen openScreen;
    private static Screen openParent;
    private static boolean buttonListenerRegistered;

    private SettingsScreen() {
    }

    public static void open(Screen parent) {
        // Fetch before controls are shown, so an async refresh cannot discard an edited local row.
        Screen loading = loading(parent);
        show(loading);
        ServerSettingsClient.request(Map.of(), result -> {
            if (Minecraft.getInstance().screen == loading) openLoaded(parent);
        });
    }

    private static void openLoaded(Screen parent) {
        try {
            Minecraft.getInstance().setScreen(create(parent));
        } catch (LinkageError incompatible) {
            dev.hoyin1600p.vhaccelerator.VHAccelerator.LOGGER.warn("Could not open the settings screen with this Cloth Config", incompatible);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(parent);
            if (minecraft.player != null) minecraft.player.displayClientMessage(
                    new TranslatableComponent("vhaccelerator.config.message.cloth_incompatible"), false);
        }
    }

    public static Screen create(Screen parent) {
        Map<Setting, Object> current = ConfigSettingStore.currentValues();
        Map<Setting, Object> pending = new LinkedHashMap<>();
        Map<Setting, Object> serverPending = new LinkedHashMap<>();
        Object serverSession = ServerSettingsClient.session();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TranslatableComponent("vhaccelerator.config.title"));
        ConfigEntryBuilder entries = builder.entryBuilder();
        for (Category category : Category.values()) {
            if (category == Category.REMOTE_SERVER) {
                addServerTab(builder, entries, serverPending, parent, serverSession);
                continue;
            }
            List<Setting> settings = ConfigSettingCatalog.in(category).stream().filter(SettingsScreen::localSetting).toList();
            if (settings.isEmpty() && category != Category.DIAGNOSTICS) {
                continue;
            }
            ConfigCategory tab = builder.getOrCreateCategory(new TranslatableComponent(category.translationKey()));
            if (category == Category.DIAGNOSTICS) {
                // Opens a preview first; nothing is sent until the player clicks there.
                tab.addEntry(new ButtonListEntry(new TranslatableComponent("vhaccelerator.config.button.report_bug"),
                        button -> show(new BugReportScreen(Minecraft.getInstance().screen))));
                tab.addEntry(new ExpandableDescriptionEntry(
                        new TranslatableComponent("vhaccelerator.config.button.report_bug.summary"),
                        new TranslatableComponent("vhaccelerator.config.button.report_bug.tooltip")));
            }
            for (Setting setting : settings) {
                tab.addEntry(entry(entries, setting, current.get(setting), pending));
                // The grey summary under each option; clicking it shows the full explanation. This
                // replaces hover tooltips, which Cloth 6.5 draws as one unwrapped line.
                tab.addEntry(new ExpandableDescriptionEntry(
                        new TranslatableComponent(setting.summaryKey()),
                        new TranslatableComponent(setting.tooltipKey())));
            }
        }
        builder.setSavingRunnable(() -> {
            pending.keySet().removeIf(setting -> !localSetting(setting));
            List<Setting> restart = ConfigSettingStore.apply(Map.copyOf(pending));
            pending.clear();
            Map<String, Object> changes = serverChanges(serverPending);
            if (!changes.isEmpty()) {
                Minecraft.getInstance().tell(() -> saveServer(changes, parent, restart, serverSession));
            } else if (!restart.isEmpty()) {
                // Cloth returns to the parent right after this runnable; show the notice after that.
                Minecraft.getInstance().tell(() -> showRestartNotice(restart, parent));
            }
        });

        // Owned, so it is laid out at the fixed virtual size (ScreenScale).
        Screen screen = ScreenScale.own(builder.build());
        openScreen = screen;
        openParent = parent;
        if (!buttonListenerRegistered) {
            buttonListenerRegistered = true;
            MinecraftForge.EVENT_BUS.addListener(SettingsScreen::onScreenInit);
        }
        return screen;
    }

    private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries, Setting setting, Object value,
                                                    Map<Setting, Object> pending) {
        return entry(entries, setting, value, ConfigSettingStore.defaultValue(setting),
                ConfigSettingStore.intRange(setting), ConfigSettingStore.doubleRange(setting), pending, false);
    }

    private static AbstractConfigListEntry<?> entry(ConfigEntryBuilder entries, Setting setting, Object value,
                                                    Object defaultValue, int[] intRange, double[] doubleRange,
                                                    Map<Setting, Object> pending, boolean server) {
        Component label = label(setting, server);
        if (defaultValue instanceof Boolean defaultBoolean) {
            return entries.startBooleanToggle(label, (Boolean) value)
                    .setDefaultValue(defaultBoolean)
                    .setSaveConsumer(saved -> pending.put(setting, saved))
                    .build();
        }
        if (defaultValue instanceof Integer defaultInt) {
            int[] range = intRange;
            int min = range == null ? Integer.MIN_VALUE : range[0];
            int max = range == null ? Integer.MAX_VALUE : range[1];
            if (range != null && max - min <= MAX_SLIDER_RANGE) {
                return entries.startIntSlider(label, (Integer) value, min, max)
                        .setDefaultValue(defaultInt)
                        .setSaveConsumer(saved -> pending.put(setting, saved))
                        .build();
            }
            return entries.startIntField(label, (Integer) value)
                    .setMin(min)
                    .setMax(max)
                    .setDefaultValue(defaultInt)
                    .setSaveConsumer(saved -> pending.put(setting, saved))
                    .build();
        }
        if (defaultValue instanceof Double defaultDouble) {
            double[] range = doubleRange;
            return entries.startDoubleField(label, (Double) value)
                    .setMin(range == null ? -Double.MAX_VALUE : range[0])
                    .setMax(range == null ? Double.MAX_VALUE : range[1])
                    .setDefaultValue(defaultDouble)
                    .setSaveConsumer(saved -> pending.put(setting, saved))
                    .build();
        }
        if (defaultValue instanceof Enum<?> defaultEnum) {
            return enumEntry(entries, label, defaultEnum, value, saved -> pending.put(setting, saved));
        }
        // Strings and lists have no control here: put their paths in ConfigSettingCatalog.UNLISTED_PATHS.
        throw new IllegalStateException("Unsupported setting type for " + setting.id());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AbstractConfigListEntry<?> enumEntry(ConfigEntryBuilder entries, Component label,
                                                        Enum<?> defaultValue, Object value,
                                                        Consumer<Object> save) {
        Class type = defaultValue.getDeclaringClass();
        Enum<?> currentValue = type.isInstance(value) ? (Enum<?>) value : defaultValue;
        return entries.startEnumSelector(label, type, currentValue)
                .setEnumNameProvider(constant -> new TranslatableComponent(enumKey((Enum<?>) constant)))
                .setDefaultValue(defaultValue)
                .setSaveConsumer(save::accept)
                .build();
    }

    /** Player-facing name of an enum choice, e.g. vhaccelerator.config.enum.critical. */
    static String enumKey(Enum<?> constant) {
        return ConfigSettingCatalog.LANG_PREFIX + "enum."
                + constant.name().toLowerCase(Locale.ROOT);
    }

    static MutableComponent label(Setting setting) {
        return label(setting, false);
    }

    private static MutableComponent label(Setting setting, boolean server) {
        MutableComponent label = new TranslatableComponent(setting.labelKey());
        if (setting.restartRequired()) {
            label.append(" ").append(new TranslatableComponent(server ? "vhaccelerator.config.badge.server_restart"
                            : "vhaccelerator.config.badge.restart")
                    .withStyle(ChatFormatting.GOLD));
        }
        return label;
    }

    private static void onScreenInit(ScreenEvent.InitScreenEvent.Post event) {
        Screen screen = event.getScreen();
        if (screen != openScreen || screen == null) {
            return;
        }
        int clothButtonWidth = Math.min(200, (screen.width - 50 - 12) / 3);
        int sideWidth = Math.min(MAX_SIDE_BUTTON_WIDTH, screen.width / 2 - clothButtonWidth - 3 - 2 * FOOTER_MARGIN);
        int y = screen.height - 26;
        event.addListener(new Button(FOOTER_MARGIN, y, sideWidth, GuiLayout.BUTTON_HEIGHT,
                new TranslatableComponent(ServerSettingsClient.connected() ? "vhaccelerator.config.button.local_default"
                        : "vhaccelerator.config.button.default"), button -> confirmDefaults(screen)));
        event.addListener(new Button(screen.width - FOOTER_MARGIN - sideWidth, y, sideWidth, GuiLayout.BUTTON_HEIGHT,
                new TranslatableComponent(ServerSettingsClient.connected() ? "vhaccelerator.config.button.local_experimental"
                        : "vhaccelerator.config.button.experimental"), button -> confirmExperimental(screen)));
    }

    private static void confirmDefaults(Screen clothScreen) {
        Screen parent = openParent;
        show(new OpaqueConfirmScreen(
                confirmed -> {
                    if (!confirmed) {
                        Minecraft.getInstance().setScreen(clothScreen);
                        return;
                    }
                    Map<Setting, Object> defaults = new LinkedHashMap<>();
                    ConfigSettingStore.defaults().forEach((setting, value) -> {
                        if (localSetting(setting)) defaults.put(setting, value);
                    });
                    applyAndReopen(defaults, parent);
                },
                new TranslatableComponent("vhaccelerator.config.dialog.default.title"),
                new TranslatableComponent(ServerSettingsClient.connected() ? "vhaccelerator.config.server.local_defaults"
                        : "vhaccelerator.config.dialog.default.message")
        ));
    }

    private static void confirmExperimental(Screen clothScreen) {
        Screen parent = openParent;
        List<Setting> changes = ConfigSettingStore.experimentalChanges(ConfigSettingStore.currentValues())
                .stream().filter(SettingsScreen::localSetting).toList();
        if (changes.isEmpty()) {
            show(new OpaqueAlertScreen(
                    () -> Minecraft.getInstance().setScreen(clothScreen),
                    new TranslatableComponent("vhaccelerator.config.dialog.experimental.title"),
                    new TranslatableComponent("vhaccelerator.config.dialog.experimental.none")
            ));
            return;
        }
        Map<Setting, Object> values = new LinkedHashMap<>();
        changes.forEach(setting -> values.put(setting, Boolean.TRUE));
        show(new OpaqueConfirmScreen(
                confirmed -> {
                    if (!confirmed) {
                        Minecraft.getInstance().setScreen(clothScreen);
                        return;
                    }
                    applyAndReopen(values, parent);
                },
                new TranslatableComponent("vhaccelerator.config.dialog.experimental.title"),
                new TranslatableComponent("vhaccelerator.config.dialog.experimental.message", list(changes))
        ));
    }

    private static boolean localSetting(Setting setting) {
        return ConfigSettingCatalog.localEditable(setting, ServerSettingsClient.connected());
    }

    private static void addServerTab(ConfigBuilder builder, ConfigEntryBuilder entries,
                                     Map<Setting, Object> pending, Screen parent, Object session) {
        ConfigCategory tab = builder.getOrCreateCategory(new TranslatableComponent(Category.REMOTE_SERVER.translationKey()));
        tab.addEntry(new ExpandableDescriptionEntry(new TranslatableComponent("vhaccelerator.config.server.header"),
                new TranslatableComponent("vhaccelerator.config.server.explanation")));
        var snapshot = ServerSettingsClient.snapshot();
        if (snapshot.status() != ServerSettingsService.Status.OK) {
            String key = !ServerSettingsClient.connected() ? "join" : snapshot.status().name().toLowerCase(Locale.ROOT);
            tab.addEntry(new ExpandableDescriptionEntry(new TranslatableComponent("vhaccelerator.config.server." + key),
                    new TranslatableComponent("vhaccelerator.config.server.explanation")));
            return;
        }
        tab.addEntry(new ButtonListEntry(new TranslatableComponent("vhaccelerator.config.server.defaults"),
                button -> confirmServerDefaults(false, parent, session)));
        tab.addEntry(new ButtonListEntry(new TranslatableComponent("vhaccelerator.config.server.experimental"),
                button -> confirmServerDefaults(true, parent, session)));
        for (Setting original : ConfigSettingCatalog.serverSettings()) {
            var row = snapshot.values().get(String.join(".", original.path()));
            if (row == null || !row.value().getClass().equals(row.defaultValue().getClass())) continue;
            Setting setting = new Setting(original.id(), original.category(), original.storage(), original.path(),
                    row.restartRequired(), null);
            int[] ints = row.min() != null && row.defaultValue() instanceof Integer
                    ? new int[] {row.min().intValue(), row.max().intValue()} : null;
            double[] doubles = row.min() != null && row.defaultValue() instanceof Double
                    ? new double[] {row.min().doubleValue(), row.max().doubleValue()} : null;
            tab.addEntry(entry(entries, setting, row.value(), row.defaultValue(), ints, doubles, pending, true));
            tab.addEntry(new ExpandableDescriptionEntry(new TranslatableComponent(setting.summaryKey()),
                    new TranslatableComponent(setting.tooltipKey())));
        }
    }

    private static Map<String, Object> serverChanges(Map<Setting, Object> pending) {
        Map<String, Object> values = new LinkedHashMap<>();
        var snapshot = ServerSettingsClient.snapshot();
        pending.forEach((setting, value) -> {
            String key = String.join(".", setting.path());
            var current = snapshot.values().get(key);
            if (current != null && !Objects.equals(current.value(), value)) values.put(key, value);
        });
        return values;
    }

    private static void confirmServerDefaults(boolean experimental, Screen parent, Object session) {
        Screen previous = Minecraft.getInstance().screen;
        Map<String, Object> changes = new LinkedHashMap<>();
        var snapshot = ServerSettingsClient.snapshot();
        for (Setting setting : ConfigSettingCatalog.serverSettings()) {
            String key = String.join(".", setting.path());
            var row = snapshot.values().get(key);
            if (row == null) continue;
            if (experimental && !ConfigSettingCatalog.experimental(setting, row.defaultValue())) continue;
            Object target = experimental ? Boolean.TRUE : row.defaultValue();
            if (!Objects.equals(target, row.value())) changes.put(key, target);
        }
        show(new OpaqueConfirmScreen(confirmed -> {
            if (confirmed) saveServer(changes, parent, List.of(), session);
            else Minecraft.getInstance().setScreen(previous);
        }, new TranslatableComponent("vhaccelerator.config.server." + (experimental ? "experimental" : "defaults")),
                new TranslatableComponent("vhaccelerator.config.server." + (experimental ? "confirm_experimental" : "confirm_defaults"),
                        changes.size(), list(ConfigSettingCatalog.all().stream().filter(setting ->
                                changes.containsKey(String.join(".", setting.path()))).toList()))));
    }

    private static Screen loading(Screen parent) {
        return ScreenScale.own(new OpaqueAlertScreen(() -> Minecraft.getInstance().setScreen(parent),
                new TranslatableComponent(Category.REMOTE_SERVER.translationKey()),
                new TranslatableComponent("vhaccelerator.config.server.loading"), false));
    }

    private static void saveServer(Map<String, Object> changes, Screen parent, List<Setting> localRestarts, Object session) {
        Screen waiting = loading(parent);
        show(waiting);
        ServerSettingsClient.requestFor(session, changes, result -> {
            if (Minecraft.getInstance().screen != waiting) return;
            String status = result.status() == ServerSettingsService.Status.OK
                    ? (result.restartKeys().isEmpty() ? "saved" : "saved_restart")
                    : result.status() == ServerSettingsService.Status.UNAVAILABLE ? "unconfirmed"
                    : result.status().name().toLowerCase(Locale.ROOT);
            show(new OpaqueAlertScreen(() -> {
                Screen fresh = create(parent);
                if (localRestarts.isEmpty()) Minecraft.getInstance().setScreen(fresh);
                else showRestartNotice(localRestarts, fresh);
            }, new TranslatableComponent(Category.REMOTE_SERVER.translationKey()),
                    new TranslatableComponent("vhaccelerator.config.server." + status, result.restartKeys().size(),
                            list(ConfigSettingCatalog.serverSettings().stream().filter(setting ->
                                    result.restartKeys().contains(String.join(".", setting.path()))).toList()))));
        });
    }

    /** Saves and applies, then opens a fresh screen so every entry shows the new saved values. */
    private static void applyAndReopen(Map<Setting, Object> values, Screen parent) {
        List<Setting> restart = ConfigSettingStore.apply(values);
        Screen fresh = create(parent);
        if (restart.isEmpty()) {
            Minecraft.getInstance().setScreen(fresh);
        } else {
            showRestartNotice(restart, fresh);
        }
    }

    /** Opens a dialog or screen of the mod; every one of them is laid out at the fixed virtual size. */
    private static void show(Screen screen) {
        Minecraft.getInstance().setScreen(ScreenScale.own(screen));
    }

    private static void showRestartNotice(List<Setting> restart, Screen next) {
        show(new OpaqueAlertScreen(
                () -> Minecraft.getInstance().setScreen(next),
                new TranslatableComponent("vhaccelerator.config.dialog.restart.title"),
                new TranslatableComponent("vhaccelerator.config.dialog.restart.message", list(restart))
        ));
    }

    /** One setting label per line, without the restart badge; long lists end with "and N more". */
    private static Component list(List<Setting> settings) {
        MutableComponent text = new TextComponent("");
        int shown = settings.size() > MAX_LISTED ? MAX_LISTED - 1 : settings.size();
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                text.append("\n");
            }
            text.append(new TextComponent("- ")).append(new TranslatableComponent(settings.get(i).labelKey()));
        }
        if (shown < settings.size()) {
            text.append("\n").append(new TranslatableComponent("vhaccelerator.config.dialog.more", settings.size() - shown));
        }
        return text;
    }

    /**
     * The dialogs use the settings screen's opaque background. In a world, the vanilla
     * translucent one lets HUD overlays that draw afterwards show through the dialog.
     */
    private static final class OpaqueConfirmScreen extends ConfirmScreen {
        OpaqueConfirmScreen(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, Component title, Component message) {
            super(callback, title, message);
        }

        @Override
        public void renderBackground(com.mojang.blaze3d.vertex.PoseStack poseStack, int offset) {
            renderDirtBackground(offset);
        }
    }

    private static final class OpaqueAlertScreen extends AlertScreen {
        private final boolean pause;
        OpaqueAlertScreen(Runnable callback, Component title, Component message) {
            this(callback, title, message, true);
        }

        OpaqueAlertScreen(Runnable callback, Component title, Component message, boolean pause) {
            super(callback, title, message);
            this.pause = pause;
        }

        @Override public boolean isPauseScreen() { return pause; }

        @Override
        public void renderBackground(com.mojang.blaze3d.vertex.PoseStack poseStack, int offset) {
            renderDirtBackground(offset);
        }
    }
}

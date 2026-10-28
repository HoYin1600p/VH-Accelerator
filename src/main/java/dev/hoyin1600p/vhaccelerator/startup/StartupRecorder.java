package dev.hoyin1600p.vhaccelerator.startup;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.bootstrap.BootstrapCommonConfig;
import dev.hoyin1600p.vhaccelerator.startup.LifecycleListenerLedger.Described;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Built-in startup recorder for dedicated servers: a per-listener ledger of lifecycle events
 * (enabled by {@code diagnostics.timers}) and an optional JFR recording
 * ({@code diagnostics.startupJfr}). All entry points are observational and run inside the
 * {@link StartupDiagnostics} failure boundary.
 */
public final class StartupRecorder {
    private static final int LOG_COUNT = 10;
    private static final StartupRecorder INSTANCE = new StartupRecorder(
            new MemoizedFlag(() -> BootstrapCommonConfig.bool("diagnostics", "timers", false)),
            new MemoizedFlag(() -> BootstrapCommonConfig.bool("diagnostics", "startupJfr", false)),
            StartupRecorder::forgeBuses,
            message -> VHAccelerator.LOGGER.info("{}", message),
            message -> VHAccelerator.LOGGER.warn("{}", message));

    /** Buses and mod lookup, replaceable so tests can use a standalone EventBus. */
    interface Buses {
        /** Wraps the mod-bus lifecycle events of every mod. */
        void installModBuses(LifecycleListenerLedger ledger, Consumer<LifecycleListenerWrapping.Installation> sink);

        /** Wraps the server lifecycle events on the Forge bus. */
        void installForgeBus(LifecycleListenerLedger ledger, Consumer<LifecycleListenerWrapping.Installation> sink);

        Function<Class<?>, String> modOfClass();
    }

    private static final class Session {
        final LifecycleListenerLedger ledger;
        final List<LifecycleListenerWrapping.Installation> installations = new ArrayList<>();
        ServerStartupReport report;
        Runnable emit;
        boolean finished;
        boolean broken;

        Session(LifecycleListenerLedger ledger) {
            this.ledger = ledger;
        }
    }

    private final BooleanSupplier timers;
    private final BooleanSupplier jfrEnabled;
    private final java.util.function.Supplier<Buses> busFactory;
    private final Consumer<String> info;
    private final Consumer<String> warn;
    private final LongSupplier originNanos;
    private final StartupJfrRecorder.Backend jfrBackend;
    private Session session;
    private StartupJfrRecorder jfr;
    private boolean startedHandled;

    StartupRecorder(BooleanSupplier timers, BooleanSupplier jfrEnabled,
                    java.util.function.Supplier<Buses> busFactory, Consumer<String> info, Consumer<String> warn) {
        this(timers, jfrEnabled, busFactory, info, warn, ServerLaunchTimer::processStartNanos, null);
    }

    StartupRecorder(BooleanSupplier timers, BooleanSupplier jfrEnabled,
                    java.util.function.Supplier<Buses> busFactory, Consumer<String> info, Consumer<String> warn,
                    LongSupplier originNanos, StartupJfrRecorder.Backend jfrBackend) {
        this.timers = timers;
        this.jfrEnabled = jfrEnabled;
        this.busFactory = busFactory;
        this.info = info;
        this.warn = warn;
        this.originNanos = originNanos;
        this.jfrBackend = jfrBackend;
    }

    private static Buses forgeBuses;

    /**
     * Mod classes are defined by securejarhandler's ModuleClassLoader, whose module name is the secure jar
     * name and whose code source is a union-file-system URI that never equals a default-filesystem path.
     * The module name is therefore the primary key; a same-filesystem path match is the fallback.
     */
    static String resolveMod(String moduleName, Map<String, String> modsByModule,
                             java.util.function.Supplier<Path> location, Map<Path, String> modsByPath) {
        if (moduleName != null) {
            String byModule = modsByModule.get(moduleName);
            if (byModule != null) return byModule;
        }
        Path where = location.get();
        if (where == null) return null;
        String direct = modsByPath.get(where);
        if (direct != null) return direct;
        for (Map.Entry<Path, String> entry : modsByPath.entrySet()) {
            try {
                if (where.getFileSystem() == entry.getKey().getFileSystem() && where.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            } catch (RuntimeException ignored) {
                // Mixed file systems: no match.
            }
        }
        return null;
    }

    /** Created on first use so Forge event classes are not loaded at server main. */
    private static synchronized Buses forgeBuses() {
        if (forgeBuses == null) forgeBuses = new ForgeBuses();
        return forgeBuses;
    }

    // ---- Static hooks called from the mixins. ----

    /** Earliest VHA code on a dedicated server: starts the optional JFR recording. */
    public static void launchStarted(String runId) {
        INSTANCE.onLaunchStarted(runId, () -> FMLPaths.GAMEDIR.get().resolve("logs/vhaccelerator"));
    }

    /** After construction and registries, before common setup, IMC and load complete. */
    public static void beforeModLoading() {
        INSTANCE.onBeforeModLoading();
    }

    /** At DedicatedServer.initServer HEAD: all Forge-bus registrations of the server events exist. */
    public static void beforeServerInit() {
        INSTANCE.onBeforeServerInit();
    }

    /** At the end of ServerLifecycleHooks.handleServerStarted, after every started-event listener. */
    public static void startedReturned() {
        INSTANCE.onStartedReturned();
    }

    /** Lets the report be written after the ledger is complete; false when the caller should write it now. */
    static boolean deferOutput(ServerStartupReport report, Runnable emit) {
        return INSTANCE.onDeferOutput(report, emit);
    }

    // ---- Implementation. ----

    void onLaunchStarted(String runId, java.util.function.Supplier<Path> logDirectory) {
        StartupDiagnostics.run("jfr.start", () -> {
            if (!jfrEnabled.getAsBoolean()) return;
            StartupJfrRecorder recorder = jfrBackend == null
                    ? new StartupJfrRecorder(info, warn)
                    : new StartupJfrRecorder(jfrBackend, info, warn);
            synchronized (this) {
                jfr = recorder;
            }
            recorder.start(logDirectory.get(), runId);
        });
    }

    void onBeforeModLoading() {
        StartupDiagnostics.run("lifecycle.install.mods", () -> {
            Session active = activeSession();
            if (active == null) return;
            try {
                busFactory.get().installModBuses(active.ledger, active.installations::add);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                abandon(active);
                throw failure;
            }
        });
    }

    void onBeforeServerInit() {
        StartupDiagnostics.run("lifecycle.install.forge", () -> {
            Session active = activeSession();
            if (active == null) return;
            try {
                busFactory.get().installForgeBus(active.ledger, active.installations::add);
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                abandon(active);
                throw failure;
            }
        });
    }

    /** A failed install must leave no wrapper behind: restore what was wrapped and stop recording. */
    private void abandon(Session active) {
        synchronized (this) {
            active.broken = true;
            active.finished = true;
        }
        for (LifecycleListenerWrapping.Installation installation : active.installations) {
            StartupDiagnostics.run("lifecycle.restore", installation::uninstall);
        }
        active.installations.clear();
    }

    private synchronized Session activeSession() {
        if (session == null) {
            if (!timers.getAsBoolean()) return null;
            session = new Session(new LifecycleListenerLedger(originNanos.getAsLong()));
        }
        return session.finished || session.broken ? null : session;
    }

    synchronized boolean onDeferOutput(ServerStartupReport report, Runnable emit) {
        Session active = session;
        if (active == null || active.finished || active.installations.isEmpty()) return false;
        active.report = report;
        active.emit = emit;
        return true;
    }

    void onStartedReturned() {
        StartupJfrRecorder recorder;
        Session active;
        synchronized (this) {
            if (startedHandled) return;
            startedHandled = true;
            recorder = jfr;
            active = session != null && session.broken ? null : session;
            if (active != null) active.finished = true;
        }
        if (recorder != null) {
            StartupDiagnostics.run("jfr.schedule", () -> {
                if (recorder.state() == StartupJfrRecorder.State.RECORDING) {
                    recorder.stopLater(StartupJfrRecorder.TAIL_SECONDS * 1000L);
                }
            });
        }
        if (active == null) return;
        try {
            for (LifecycleListenerWrapping.Installation installation : active.installations) {
                StartupDiagnostics.run("lifecycle.uninstall", installation::uninstall);
            }
            StartupDiagnostics.run("lifecycle.report", () -> publish(active));
        } finally {
            Runnable emit = active.emit;
            if (emit != null) emit.run();
        }
    }

    private void publish(Session active) {
        LifecycleListenerLedger.Snapshot snapshot = active.ledger.snapshot();
        if (active.report != null) active.report.lifecycleLedger(LifecycleListenerLedger.toReport(snapshot));
        info.accept(LifecycleListenerLedger.logBlock(snapshot, LOG_COUNT));
    }

    private static final class MemoizedFlag implements BooleanSupplier {
        private final BooleanSupplier source;
        private volatile Boolean value;

        MemoizedFlag(BooleanSupplier source) {
            this.source = source;
        }

        @Override
        public boolean getAsBoolean() {
            Boolean cached = value;
            if (cached == null) {
                cached = source.getAsBoolean();
                value = cached;
            }
            return cached;
        }
    }

    /** The real Forge buses and mod files. */
    private static final class ForgeBuses implements Buses {
        private static final List<Class<? extends Event>> MOD_EVENTS = List.of(
                FMLCommonSetupEvent.class, FMLDedicatedServerSetupEvent.class, InterModEnqueueEvent.class,
                InterModProcessEvent.class, FMLLoadCompleteEvent.class);
        private static final List<Class<? extends Event>> FORGE_EVENTS = List.of(
                ServerAboutToStartEvent.class, ServerStartingEvent.class, ServerStartedEvent.class,
                RegisterCommandsEvent.class, AddReloadListenerEvent.class);
        private Map<Path, String> modFiles;
        private Map<String, String> moduleMods;
        private ListenerOwners owners;

        private synchronized ListenerOwners owners() {
            if (owners == null) owners = new ListenerOwners(modOfClass());
            return owners;
        }

        @Override
        public void installModBuses(LifecycleListenerLedger ledger, Consumer<LifecycleListenerWrapping.Installation> sink) {
            Function<IEventListener, Described> describe = describer();
            ModList.get().forEachModContainer((id, container) -> {
                if (container instanceof FMLModContainer fml) {
                    sink.accept(LifecycleListenerWrapping.install(ledger, "mod:" + id,
                            LifecycleListenerWrapping.busId(fml.getEventBus()), MOD_EVENTS, describe));
                }
            });
        }

        @Override
        public void installForgeBus(LifecycleListenerLedger ledger, Consumer<LifecycleListenerWrapping.Installation> sink) {
            sink.accept(LifecycleListenerWrapping.install(ledger, "forge",
                    LifecycleListenerWrapping.busId(MinecraftForge.EVENT_BUS), FORGE_EVENTS, describer()));
        }

        private Function<IEventListener, Described> describer() {
            // Resolution is deferred to the snapshot; the owner table is built then.
            return listener -> owners().describe(listener);
        }

        @Override
        public synchronized Function<Class<?>, String> modOfClass() {
            if (modFiles == null) {
                Map<Path, String> files = new HashMap<>();
                Map<String, String> modules = new HashMap<>();
                for (var info : ModList.get().getModFiles()) {
                    if (info.getMods().isEmpty()) continue;
                    String modId = info.getMods().get(0).getModId();
                    files.put(info.getFile().getFilePath().toAbsolutePath().normalize(), modId);
                    try {
                        modules.putIfAbsent(info.getFile().getSecureJar().name(), modId);
                    } catch (RuntimeException | LinkageError unavailable) {
                        // The code-source fallback below still applies.
                    }
                }
                modFiles = files;
                moduleMods = modules;
            }
            Map<Path, String> files = modFiles;
            Map<String, String> modules = moduleMods;
            return type -> resolveMod(type.getModule().getName(), modules, () -> locationOf(type), files);
        }

        private static Path locationOf(Class<?> type) {
            try {
                CodeSource source = type.getProtectionDomain().getCodeSource();
                URL url = source == null ? null : source.getLocation();
                if (url == null) return null;
                String text = url.toString();
                if (text.startsWith("jar:")) {
                    text = text.substring(4);
                    int bang = text.indexOf("!/");
                    if (bang >= 0) text = text.substring(0, bang);
                }
                return Paths.get(new URI(text)).toAbsolutePath().normalize();
            } catch (URISyntaxException | RuntimeException failure) {
                return null;
            }
        }
    }
}

package dev.hoyin1600p.vhaccelerator.client.compat.jei;

import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import mezz.jei.api.IModPlugin;
import mezz.jei.common.Internal;
import mezz.jei.common.ingredients.RegisteredIngredients;
import mezz.jei.common.load.PluginCaller;
import mezz.jei.common.runtime.JeiHelpers;
import mezz.jei.common.runtime.JeiRuntime;
import mezz.jei.common.startup.JeiEventHandlers;
import mezz.jei.common.startup.JeiStarter;
import mezz.jei.common.util.RecipeErrorUtil;
import mezz.jei.forge.events.RuntimeEventSubscriptions;
import mezz.jei.forge.startup.EventRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Owns one JEI preparation at a time. Worker-built globals and runtime
 * callbacks stay in a thread-local publication until the matching connection
 * generation finalizes on Minecraft's main thread.
 */
public final class AsyncJeiCoordinator {
    private static final Object LOCK = new Object();
    private static final AtomicLong GENERATION = new AtomicLong();
    private static final ThreadLocal<Publication> PUBLICATION = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYNCHRONOUS_FALLBACK =
            ThreadLocal.withInitial(() -> false);
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(
            new JeiThreadFactory()
    );

    private static volatile Build active;
    private static Recovery pendingRecovery;

    private AsyncJeiCoordinator() {
    }

    public static boolean isManagingStartup() {
        return active != null;
    }

    public static void start(JeiStarter starter, RuntimeEventSubscriptions subscriptions) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            VHAccelerator.LOGGER.error("Cannot prepare JEI without a client level");
            return;
        }
        if (!subscriptions.isEmpty()) {
            VHAccelerator.LOGGER.error("Cannot prepare JEI while runtime events are already registered");
            return;
        }

        Build build = new Build(GENERATION.incrementAndGet(), level, starter, subscriptions);
        synchronized (LOCK) {
            Build previous = active;
            pendingRecovery = null;
            active = build;
            cancel(previous);
            build.future = WORKER.submit(() -> prepare(build));
        }
        VHAccelerator.LOGGER.info("Preparing JEI generation {} on a guarded worker", build.generation);
    }

    public static void stop(RuntimeEventSubscriptions subscriptions) {
        GENERATION.incrementAndGet();
        synchronized (LOCK) {
            Build previous = active;
            active = null;
            pendingRecovery = null;
            cancel(previous);
        }
        subscriptions.clear();
        Internal.setRuntime(null);
    }

    /**
     * Preserve enough lifecycle state to rebuild JEI when a proxy transfer
     * interrupts its first, unpublished generation. An already-published
     * runtime is deliberately left alone for transfer mods that retain JEI
     * across backend switches.
     */
    public static void onClientDisconnected() {
        Build interrupted;
        synchronized (LOCK) {
            interrupted = active;
            if (interrupted == null) {
                return;
            }

            GENERATION.incrementAndGet();
            active = null;
            pendingRecovery = new Recovery(
                    interrupted.starter,
                    interrupted.subscriptions,
                    interrupted.generation
            );
            cancel(interrupted);
        }
        VHAccelerator.LOGGER.info(
                "Paused unpublished JEI generation {} for a possible server transfer",
                interrupted.generation
        );
    }

    /**
     * Called only after the destination has rendered a playable frame, when
     * client level and connection state are safe for mod plugin callbacks.
     */
    public static void recoverAfterTransfer() {
        Recovery recovery;
        synchronized (LOCK) {
            recovery = pendingRecovery;
            if (recovery == null || active != null) {
                return;
            }
            pendingRecovery = null;
        }

        if (Internal.getRuntime().isPresent()) {
            VHAccelerator.LOGGER.info(
                    "JEI was already published; retained it across the server transfer"
            );
            return;
        }

        VHAccelerator.LOGGER.info(
                "Restarting JEI after server transfer interrupted generation {}",
                recovery.interruptedGeneration
        );
        start(recovery.starter, recovery.subscriptions);
    }

    public static void setRegisteredIngredients(RegisteredIngredients ingredients) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            Internal.setRegisteredIngredients(ingredients);
        } else {
            publication.registeredIngredients = ingredients;
        }
    }

    public static void setRecipeErrorIngredients(RegisteredIngredients ingredients) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            RecipeErrorUtil.setRegisteredIngredients(ingredients);
        } else {
            publication.recipeErrorIngredients = ingredients;
        }
    }

    public static void setHelpers(JeiHelpers helpers) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            Internal.setHelpers(helpers);
        } else {
            publication.helpers = helpers;
        }
    }

    public static void setRuntime(JeiRuntime runtime) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            Internal.setRuntime(runtime);
        } else {
            publication.runtime = runtime;
        }
    }

    public static void callRuntimePlugins(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> callback
    ) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            PluginCaller.callOnPlugins(title, plugins, callback);
        } else {
            publication.pluginPublication =
                    () -> PluginCaller.callOnPlugins(title, plugins, callback);
        }
    }

    /**
     * JEI invokes every mod's registration callbacks through PluginCaller.
     * Those callbacks may use live Minecraft, network, and mod state and must
     * therefore execute on the client thread. The worker waits while the
     * isolated publication context is temporarily installed on that thread.
     *
     * @return true when the caller must cancel the original worker invocation
     */
    public static boolean routePluginCallToMain(
            String title,
            List<IModPlugin> plugins,
            Consumer<IModPlugin> callback
    ) {
        Publication publication = PUBLICATION.get();
        Minecraft minecraft = Minecraft.getInstance();
        if (publication == null
                || SYNCHRONOUS_FALLBACK.get()
                || minecraft.isSameThread()) {
            return false;
        }

        Build build = publication.build;
        requireCurrent(build);
        CompletableFuture<Void> mainCall = new CompletableFuture<>();
        minecraft.execute(() -> {
            if (!isCurrent(build)) {
                mainCall.completeExceptionally(new CancellationException(
                        "JEI generation changed before plugin phase " + title
                ));
                return;
            }

            Publication previous = PUBLICATION.get();
            PUBLICATION.set(publication);
            try {
                PluginCaller.callOnPlugins(title, plugins, callback);
                mainCall.complete(null);
            } catch (Throwable throwable) {
                mainCall.completeExceptionally(throwable);
            } finally {
                if (previous == null) {
                    PUBLICATION.remove();
                } else {
                    PUBLICATION.set(previous);
                }
            }
        });

        try {
            mainCall.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException(
                    "JEI generation interrupted during plugin phase " + title
            );
        } catch (ExecutionException exception) {
            throwUnchecked(exception.getCause());
        }
        requireCurrent(build);
        return true;
    }

    public static JeiHelpers getThreadHelpers() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.helpers;
    }

    public static RegisteredIngredients getThreadRegisteredIngredients() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.registeredIngredients;
    }

    public static Optional<JeiRuntime> getThreadRuntime() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : Optional.ofNullable(publication.runtime);
    }

    private static void prepare(Build build) {
        Publication publication = new Publication(build);
        PUBLICATION.set(publication);
        try {
            JeiEventHandlers handlers = build.starter.start();
            requireCurrent(build);
            Minecraft.getInstance().execute(() -> finalizeOnMain(build, publication, handlers));
        } catch (Throwable throwable) {
            if (isCancellation(build, throwable)) {
                clearIfCurrent(build);
                VHAccelerator.LOGGER.info(
                        "Cancelled stale JEI generation {}", build.generation
                );
            } else {
                Minecraft.getInstance().execute(() -> fallbackOnMain(build, throwable));
            }
        } finally {
            PUBLICATION.remove();
        }
    }

    private static void finalizeOnMain(
            Build build,
            Publication publication,
            JeiEventHandlers handlers
    ) {
        if (!isCurrent(build)) {
            clearIfCurrent(build);
            VHAccelerator.LOGGER.info("Discarded stale JEI generation {}", build.generation);
            return;
        }
        if (publication.registeredIngredients == null
                || publication.recipeErrorIngredients == null
                || publication.helpers == null
                || publication.runtime == null
                || publication.pluginPublication == null) {
            fallbackOnMain(
                    build,
                    new IllegalStateException("JEI worker returned an incomplete publication")
            );
            return;
        }

        try {
            Internal.setRegisteredIngredients(publication.registeredIngredients);
            RecipeErrorUtil.setRegisteredIngredients(publication.recipeErrorIngredients);
            Internal.setHelpers(publication.helpers);
            Internal.setRuntime(publication.runtime);
            publication.pluginPublication.run();
            EventRegistration.registerEvents(build.subscriptions, handlers);
            clearIfCurrent(build);
            VHAccelerator.LOGGER.info("Published JEI generation {} on the main thread", build.generation);
        } catch (Throwable throwable) {
            build.subscriptions.clear();
            Internal.setRuntime(null);
            fallbackOnMain(build, throwable);
        }
    }

    private static void fallbackOnMain(Build build, Throwable asyncFailure) {
        if (!isCurrent(build)) {
            return;
        }

        VHAccelerator.LOGGER.error(
                "JEI generation {} failed during guarded preparation; retrying synchronously",
                build.generation,
                asyncFailure
        );
        build.subscriptions.clear();
        Internal.setRuntime(null);
        SYNCHRONOUS_FALLBACK.set(true);
        try {
            JeiEventHandlers handlers = build.starter.start();
            if (!isCurrent(build)) {
                Internal.setRuntime(null);
                return;
            }
            EventRegistration.registerEvents(build.subscriptions, handlers);
            clearIfCurrent(build);
            VHAccelerator.LOGGER.info(
                    "JEI generation {} recovered with synchronous startup",
                    build.generation
            );
        } catch (Throwable fallbackFailure) {
            build.subscriptions.clear();
            Internal.setRuntime(null);
            clearIfCurrent(build);
            VHAccelerator.LOGGER.error(
                    "JEI generation {} also failed during synchronous recovery",
                    build.generation,
                    fallbackFailure
            );
        } finally {
            SYNCHRONOUS_FALLBACK.remove();
        }
    }

    private static boolean isCurrent(Build build) {
        return !build.cancelled
                && active == build
                && GENERATION.get() == build.generation
                && Minecraft.getInstance().level == build.level;
    }

    private static void requireCurrent(Build build) {
        if (!isCurrent(build)) {
            throw new CancellationException("JEI generation is no longer current");
        }
    }

    private static boolean isCancellation(Build build, Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof CancellationException
                    || current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }
        return build.cancelled || !isCurrent(build);
    }

    private static void throwUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }

    private static void cancel(Build build) {
        if (build == null) {
            return;
        }
        build.cancelled = true;
        if (build.future != null) {
            build.future.cancel(true);
        }
    }

    private static void clearIfCurrent(Build build) {
        synchronized (LOCK) {
            if (active == build) {
                active = null;
            }
        }
    }

    private static final class Publication {
        private final Build build;
        private RegisteredIngredients registeredIngredients;
        private RegisteredIngredients recipeErrorIngredients;
        private JeiHelpers helpers;
        private JeiRuntime runtime;
        private Runnable pluginPublication;

        private Publication(Build build) {
            this.build = build;
        }
    }

    private static final class Build {
        private final long generation;
        private final ClientLevel level;
        private final JeiStarter starter;
        private final RuntimeEventSubscriptions subscriptions;
        private volatile boolean cancelled;
        private volatile Future<?> future;

        private Build(
                long generation,
                ClientLevel level,
                JeiStarter starter,
                RuntimeEventSubscriptions subscriptions
        ) {
            this.generation = generation;
            this.level = level;
            this.starter = starter;
            this.subscriptions = subscriptions;
        }
    }

    private static final class Recovery {
        private final JeiStarter starter;
        private final RuntimeEventSubscriptions subscriptions;
        private final long interruptedGeneration;

        private Recovery(
                JeiStarter starter,
                RuntimeEventSubscriptions subscriptions,
                long interruptedGeneration
        ) {
            this.starter = starter;
            this.subscriptions = subscriptions;
            this.interruptedGeneration = interruptedGeneration;
        }
    }

    private static final class JeiThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "VH-Accelerator-JEI");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            thread.setUncaughtExceptionHandler((worker, throwable) ->
                    VHAccelerator.LOGGER.error(
                            "Uncaught asynchronous JEI error on {}", worker.getName(), throwable
                    )
            );
            return thread;
        }
    }
}

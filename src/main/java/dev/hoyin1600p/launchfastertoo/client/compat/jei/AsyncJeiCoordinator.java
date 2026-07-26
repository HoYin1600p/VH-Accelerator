package dev.hoyin1600p.launchfastertoo.client.compat.jei;

import dev.hoyin1600p.launchfastertoo.LaunchFasterToo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import mezz.jei.Internal;
import mezz.jei.api.IModPlugin;
import mezz.jei.forge.events.RuntimeEventSubscriptions;
import mezz.jei.ingredients.IngredientVisibility;
import mezz.jei.ingredients.RegisteredIngredients;
import mezz.jei.load.PluginCaller;
import mezz.jei.runtime.JeiHelpers;
import mezz.jei.runtime.JeiRuntime;
import mezz.jei.startup.JeiStarter;
import mezz.jei.util.RecipeErrorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;

/**
 * Owns one JEI preparation at a time. Worker-built globals, event listeners,
 * and runtime callbacks stay isolated until the matching connection
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

    private AsyncJeiCoordinator() {
    }

    public static boolean isManagingStartup() {
        return active != null;
    }

    public static boolean isPreparingOnCurrentThread() {
        return PUBLICATION.get() != null && !SYNCHRONOUS_FALLBACK.get();
    }

    public static void start(JeiStarter starter, RuntimeEventSubscriptions subscriptions) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            LaunchFasterToo.LOGGER.error("Cannot prepare JEI without a client level");
            return;
        }
        if (!subscriptions.isEmpty()) {
            LaunchFasterToo.LOGGER.error("Cannot prepare JEI while runtime events are already registered");
            return;
        }

        Build build = new Build(GENERATION.incrementAndGet(), level, starter, subscriptions);
        synchronized (LOCK) {
            Build previous = active;
            active = build;
            if (previous != null && previous.future != null) {
                previous.future.cancel(true);
            }
            build.future = WORKER.submit(() -> prepare(build));
        }
        LaunchFasterToo.LOGGER.info("Preparing JEI generation {} on a guarded worker", build.generation);
    }

    public static void stop(RuntimeEventSubscriptions subscriptions) {
        GENERATION.incrementAndGet();
        synchronized (LOCK) {
            Build previous = active;
            active = null;
            if (previous != null && previous.future != null) {
                previous.future.cancel(true);
            }
        }
        subscriptions.clear();
        Internal.setRuntime(null);
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

    public static void setIngredientVisibility(IngredientVisibility visibility) {
        Publication publication = PUBLICATION.get();
        if (publication == null || SYNCHRONOUS_FALLBACK.get()) {
            Internal.setIngredientVisibility(visibility);
        } else {
            publication.ingredientVisibility = visibility;
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

    public static RegisteredIngredients getThreadRegisteredIngredients() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.registeredIngredients;
    }

    public static IngredientVisibility getThreadIngredientVisibility() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.ingredientVisibility;
    }

    public static JeiHelpers getThreadHelpers() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.helpers;
    }

    public static JeiRuntime getThreadRuntime() {
        Publication publication = PUBLICATION.get();
        return publication == null ? null : publication.runtime;
    }

    private static void prepare(Build build) {
        Publication publication = new Publication();
        StagedSubscriptions stagedSubscriptions = new StagedSubscriptions();
        PUBLICATION.set(publication);
        try {
            build.starter.start(stagedSubscriptions);
            Minecraft.getInstance().execute(
                    () -> finalizeOnMain(build, publication, stagedSubscriptions)
            );
        } catch (Throwable throwable) {
            Minecraft.getInstance().execute(() -> fallbackOnMain(build, throwable));
        } finally {
            PUBLICATION.remove();
        }
    }

    private static void finalizeOnMain(
            Build build,
            Publication publication,
            StagedSubscriptions stagedSubscriptions
    ) {
        if (!isCurrent(build)) {
            LaunchFasterToo.LOGGER.info("Discarded stale JEI generation {}", build.generation);
            return;
        }
        if (publication.registeredIngredients == null
                || publication.recipeErrorIngredients == null
                || publication.ingredientVisibility == null
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
            Internal.setIngredientVisibility(publication.ingredientVisibility);
            Internal.setHelpers(publication.helpers);
            Internal.setRuntime(publication.runtime);
            publication.pluginPublication.run();
            stagedSubscriptions.publishTo(build.subscriptions);
            clearIfCurrent(build);
            LaunchFasterToo.LOGGER.info("Published JEI generation {} on the main thread", build.generation);
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

        LaunchFasterToo.LOGGER.error(
                "JEI generation {} failed during guarded preparation; retrying synchronously",
                build.generation,
                asyncFailure
        );
        build.subscriptions.clear();
        Internal.setRuntime(null);
        SYNCHRONOUS_FALLBACK.set(true);
        try {
            build.starter.start(build.subscriptions);
            if (!isCurrent(build)) {
                build.subscriptions.clear();
                Internal.setRuntime(null);
                return;
            }
            clearIfCurrent(build);
            LaunchFasterToo.LOGGER.info(
                    "JEI generation {} recovered with synchronous startup",
                    build.generation
            );
        } catch (Throwable fallbackFailure) {
            build.subscriptions.clear();
            Internal.setRuntime(null);
            clearIfCurrent(build);
            LaunchFasterToo.LOGGER.error(
                    "JEI generation {} also failed during synchronous recovery",
                    build.generation,
                    fallbackFailure
            );
        } finally {
            SYNCHRONOUS_FALLBACK.remove();
        }
    }

    private static boolean isCurrent(Build build) {
        return active == build
                && GENERATION.get() == build.generation
                && Minecraft.getInstance().level == build.level;
    }

    private static void clearIfCurrent(Build build) {
        synchronized (LOCK) {
            if (active == build) {
                active = null;
            }
        }
    }

    private static final class Publication {
        private RegisteredIngredients registeredIngredients;
        private RegisteredIngredients recipeErrorIngredients;
        private IngredientVisibility ingredientVisibility;
        private JeiHelpers helpers;
        private JeiRuntime runtime;
        private Runnable pluginPublication;
    }

    private static final class Build {
        private final long generation;
        private final ClientLevel level;
        private final JeiStarter starter;
        private final RuntimeEventSubscriptions subscriptions;
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

    private static final class StagedSubscriptions extends RuntimeEventSubscriptions {
        private final List<Registration<?>> registrations = new ArrayList<>();

        private StagedSubscriptions() {
            super(MinecraftForge.EVENT_BUS);
        }

        @Override
        public <T extends Event> void register(Class<T> eventType, Consumer<T> listener) {
            registrations.add(new Registration<>(eventType, listener));
        }

        @Override
        public boolean isEmpty() {
            return registrations.isEmpty();
        }

        @Override
        public void clear() {
            registrations.clear();
        }

        private void publishTo(RuntimeEventSubscriptions target) {
            for (Registration<?> registration : registrations) {
                registration.publishTo(target);
            }
        }
    }

    private static final class Registration<T extends Event> {
        private final Class<T> eventType;
        private final Consumer<T> listener;

        private Registration(Class<T> eventType, Consumer<T> listener) {
            this.eventType = eventType;
            this.listener = listener;
        }

        private void publishTo(RuntimeEventSubscriptions target) {
            target.register(eventType, listener);
        }
    }

    private static final class JeiThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "LaunchFasterToo-JEI");
            thread.setDaemon(true);
            thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
            thread.setUncaughtExceptionHandler((worker, throwable) ->
                    LaunchFasterToo.LOGGER.error(
                            "Uncaught asynchronous JEI error on {}", worker.getName(), throwable
                    )
            );
            return thread;
        }
    }
}

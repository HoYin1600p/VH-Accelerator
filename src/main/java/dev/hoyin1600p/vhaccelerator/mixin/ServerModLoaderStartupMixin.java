package dev.hoyin1600p.vhaccelerator.mixin;

import dev.hoyin1600p.vhaccelerator.startup.ServerLaunchTimer;
import dev.hoyin1600p.vhaccelerator.startup.StartupRecorder;
import dev.hoyin1600p.vhaccelerator.startup.ServerStartupReport;
import java.util.concurrent.Executor;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.ModWorkManager;
import net.minecraftforge.server.loading.ServerModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** These late-loaded entry points are observable; core FML is loaded before mixin attachment. */
@Mixin(value = ServerModLoader.class, remap = false)
public abstract class ServerModLoaderStartupMixin {
    @Redirect(method = "load", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/fml/ModLoader;gatherAndInitializeMods(Lnet/minecraftforge/fml/ModWorkManager$DrivenExecutor;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)V"))
    private static void vhaccelerator$gather(ModLoader loader, ModWorkManager.DrivenExecutor sync,
                                             Executor parallel, Runnable periodic) {
        ServerLaunchTimer.checkpoint("registry_start", "proxy:before Forge gather; includes construction and registries");
        ServerStartupReport.Span span = ServerLaunchTimer.beginSpan("forge.gather_construction_registry", null, null, null);
        Throwable failed = null;
        try {
            loader.gatherAndInitializeMods(sync, parallel, periodic);
        } catch (RuntimeException | Error failure) {
            failed = failure;
            throw failure;
        } finally {
            ServerLaunchTimer.endSpan(span, failed);
            ServerLaunchTimer.checkpoint("registry_end", "proxy:after Forge gather; includes construction and registries");
        }
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/fml/ModLoader;loadMods(Lnet/minecraftforge/fml/ModWorkManager$DrivenExecutor;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)V"))
    private static void vhaccelerator$load(ModLoader loader, ModWorkManager.DrivenExecutor sync,
                                           Executor parallel, Runnable periodic) {
        StartupRecorder.beforeModLoading();
        ServerStartupReport.Span span = ServerLaunchTimer.beginSpan("forge.load_common_sided_setup", null, null, null);
        Throwable failed = null;
        try {
            loader.loadMods(sync, parallel, periodic);
        } catch (RuntimeException | Error failure) {
            failed = failure;
            throw failure;
        } finally {
            ServerLaunchTimer.endSpan(span, failed);
            ServerLaunchTimer.checkpoint("setup_barrier", "hook:ModLoader.loadMods returned");
        }
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target =
            "Lnet/minecraftforge/fml/ModLoader;finishMods(Lnet/minecraftforge/fml/ModWorkManager$DrivenExecutor;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)V"))
    private static void vhaccelerator$finish(ModLoader loader, ModWorkManager.DrivenExecutor sync,
                                             Executor parallel, Runnable periodic) {
        ServerStartupReport.Span span = ServerLaunchTimer.beginSpan("forge.load_completion", null, null, null);
        Throwable failed = null;
        try {
            loader.finishMods(sync, parallel, periodic);
            // RegisterCommandsEvent and AddReloadListenerEvent fire in Main before initServer: wrap now.
            StartupRecorder.beforeServerInit();
        } catch (RuntimeException | Error failure) {
            failed = failure;
            throw failure;
        } finally {
            ServerLaunchTimer.endSpan(span, failed);
            ServerLaunchTimer.checkpoint("load_completion_barrier", "hook:ModLoader.finishMods returned");
        }
    }
}

package dev.hoyin1600p.vhaccelerator.mixin.client;

import dev.hoyin1600p.vhaccelerator.client.LaunchEventProfiler;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraftforge.client.loading.ClientModLoader;
import net.minecraftforge.fml.ModWorkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Profiles Forge's client-specific loading entry points. Unlike core FML and
 * EventBus classes, this class is first loaded after client mixins are active.
 */
@Mixin(value = ClientModLoader.class, remap = false)
public abstract class ClientModLoaderProfilerMixin {
    @Unique
    private static volatile long
            vhaccelerator$initializationStartedNanos;
    @Unique
    private static volatile long vhaccelerator$loadStartedNanos;
    @Unique
    private static volatile long vhaccelerator$finishStartedNanos;
    @Unique
    private static volatile long vhaccelerator$completionStartedNanos;

    @Inject(method = "begin", at = @At("HEAD"))
    private static void vhaccelerator$beginInitializationProfile(
            Minecraft minecraft,
            PackRepository packRepository,
            ReloadableResourceManager resourceManager,
            ClientPackSource clientPackSource,
            CallbackInfo callback
    ) {
        vhaccelerator$initializationStartedNanos =
                LaunchEventProfiler.beginStage();
    }

    @Inject(method = "begin", at = @At("RETURN"))
    private static void vhaccelerator$finishInitializationProfile(
            Minecraft minecraft,
            PackRepository packRepository,
            ReloadableResourceManager resourceManager,
            ClientPackSource clientPackSource,
            CallbackInfo callback
    ) {
        LaunchEventProfiler.finishStage(
                "initialize mods and client packs",
                vhaccelerator$initializationStartedNanos
        );
        vhaccelerator$initializationStartedNanos = 0L;
    }

    @Inject(method = "startModLoading", at = @At("HEAD"))
    private static void vhaccelerator$beginModLoadingProfile(
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            CallbackInfo callback
    ) {
        vhaccelerator$loadStartedNanos =
                LaunchEventProfiler.beginStage();
    }

    @Inject(method = "startModLoading", at = @At("RETURN"))
    private static void vhaccelerator$finishModLoadingProfile(
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            CallbackInfo callback
    ) {
        LaunchEventProfiler.finishStage(
                "load mods",
                vhaccelerator$loadStartedNanos
        );
        vhaccelerator$loadStartedNanos = 0L;
    }

    @Inject(method = "finishModLoading", at = @At("HEAD"))
    private static void vhaccelerator$beginModFinishingProfile(
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            CallbackInfo callback
    ) {
        vhaccelerator$finishStartedNanos =
                LaunchEventProfiler.beginStage();
    }

    @Inject(method = "finishModLoading", at = @At("RETURN"))
    private static void vhaccelerator$finishModFinishingProfile(
            ModWorkManager.DrivenExecutor syncExecutor,
            Executor parallelExecutor,
            CallbackInfo callback
    ) {
        LaunchEventProfiler.finishStage(
                "finish mods",
                vhaccelerator$finishStartedNanos
        );
        vhaccelerator$finishStartedNanos = 0L;
    }

    @Inject(method = "completeModLoading", at = @At("HEAD"))
    private static void vhaccelerator$beginCompletionProfile(
            CallbackInfoReturnable<Boolean> callback
    ) {
        vhaccelerator$completionStartedNanos =
                LaunchEventProfiler.beginStage();
    }

    @Inject(method = "completeModLoading", at = @At("RETURN"))
    private static void vhaccelerator$finishCompletionProfile(
            CallbackInfoReturnable<Boolean> callback
    ) {
        LaunchEventProfiler.finishStage(
                "complete mod loading",
                vhaccelerator$completionStartedNanos
        );
        vhaccelerator$completionStartedNanos = 0L;
    }
}

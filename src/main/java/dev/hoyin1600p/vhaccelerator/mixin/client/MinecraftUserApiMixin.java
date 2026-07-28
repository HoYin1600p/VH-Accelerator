package dev.hoyin1600p.vhaccelerator.mixin.client;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import dev.hoyin1600p.vhaccelerator.VHAccelerator;
import dev.hoyin1600p.vhaccelerator.client.DeferredUserApiService;
import dev.hoyin1600p.vhaccelerator.client.VHAcceleratorClientConfig;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftUserApiMixin {
    @Inject(method = "createUserApiService", at = @At("HEAD"), cancellable = true)
    private void vhaccelerator$createUserApiServiceAsynchronously(
            YggdrasilAuthenticationService authenticationService,
            GameConfig gameConfig,
            CallbackInfoReturnable<UserApiService> callback
    ) {
        if (!VHAcceleratorClientConfig.optimizationsEnabled()
                || !VHAcceleratorClientConfig.launchValue(
                        VHAcceleratorClientConfig.VALUES.asyncUserApiService
                )) {
            return;
        }

        String accessToken = gameConfig.user.user.getAccessToken();
        if ("0".equals(accessToken)) {
            return;
        }

        VHAccelerator.LOGGER.info("Creating UserApiService on Minecraft's IO pool");
        CompletableFuture<UserApiService> serviceFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return authenticationService.createUserApiService(accessToken);
            } catch (Exception exception) {
                VHAccelerator.LOGGER.warn(
                        "Asynchronous UserApiService creation failed; using offline service",
                        exception
                );
                return UserApiService.OFFLINE;
            }
        }, Util.ioPool());

        callback.setReturnValue(new DeferredUserApiService(serviceFuture));
    }
}

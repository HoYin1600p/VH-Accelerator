package dev.hoyin1600p.launchfastertoo.client;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Non-blocking facade for a UserApiService being created on the IO pool.
 *
 * <p>The original LaunchFaster discarded the completed service and left
 * Minecraft permanently offline. This facade uses offline-safe answers while
 * creation is in flight and delegates to the real service once available.</p>
 */
public final class DeferredUserApiService implements UserApiService {
    private final CompletableFuture<UserApiService> serviceFuture;

    public DeferredUserApiService(CompletableFuture<UserApiService> serviceFuture) {
        this.serviceFuture = serviceFuture;
    }

    private UserApiService current() {
        return serviceFuture.getNow(UserApiService.OFFLINE);
    }

    @Override
    public UserProperties properties() {
        return current().properties();
    }

    @Override
    public boolean isBlockedPlayer(UUID playerId) {
        return current().isBlockedPlayer(playerId);
    }

    @Override
    public void refreshBlockList() {
        serviceFuture.thenAccept(UserApiService::refreshBlockList);
    }

    @Override
    public TelemetrySession newTelemetrySession(Executor executor) {
        return current().newTelemetrySession(executor);
    }
}


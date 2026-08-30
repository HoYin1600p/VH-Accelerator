package dev.hoyin1600p.vhaccelerator.backport.modernfix.telemetry;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class TelemetryBlockingUserApiServiceTest {
    @Test
    void blocksOnlyTelemetryAndDelegatesOtherUserServices() {
        AtomicBoolean refreshed = new AtomicBoolean();
        UserApiService delegate = new UserApiService() {
            @Override
            public UserProperties properties() {
                return OFFLINE_PROPERTIES;
            }

            @Override
            public boolean isBlockedPlayer(UUID playerId) {
                return true;
            }

            @Override
            public void refreshBlockList() {
                refreshed.set(true);
            }

            @Override
            public TelemetrySession newTelemetrySession(Executor executor) {
                throw new AssertionError("delegate telemetry must not be called");
            }
        };
        TelemetryBlockingUserApiService service =
                new TelemetryBlockingUserApiService(delegate);

        assertSame(UserApiService.OFFLINE_PROPERTIES, service.properties());
        assertTrue(service.isBlockedPlayer(UUID.randomUUID()));
        service.refreshBlockList();
        assertTrue(refreshed.get());
        assertSame(
                TelemetrySession.DISABLED,
                service.newTelemetrySession(Runnable::run)
        );
        assertSame(service, TelemetryBlockingUserApiService.wrap(service));
    }
}

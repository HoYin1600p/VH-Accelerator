/*
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * Adapted for VH Accelerator from ModernFix.
 * Upstream repository: https://github.com/embeddedt/ModernFix
 * Upstream source: common/src/main/java/org/embeddedt/modernfix/common/mixin/feature/remove_telemetry/ClientTelemetryManagerMixin.java
 * Upstream commit: b72959f257480258cc869897d5ab32f2612a3c55
 * Original copyright: Copyright (c) 2024 embeddedt and ModernFix contributors
 * VH Accelerator modifications: Copyright (C) 2026 HoYin1600p
 * Modified: 2026-08-30; adapted telemetry suppression to Minecraft 1.18.2's
 * UserApiService telemetry-session API while delegating every other service.
 */
package dev.hoyin1600p.vhaccelerator.backport.modernfix.telemetry;

import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

public final class TelemetryBlockingUserApiService implements UserApiService {
    private final UserApiService delegate;

    public TelemetryBlockingUserApiService(UserApiService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public static UserApiService wrap(UserApiService service) {
        if (service instanceof TelemetryBlockingUserApiService) {
            return service;
        }
        return new TelemetryBlockingUserApiService(service);
    }

    @Override
    public UserProperties properties() {
        return delegate.properties();
    }

    @Override
    public boolean isBlockedPlayer(UUID playerId) {
        return delegate.isBlockedPlayer(playerId);
    }

    @Override
    public void refreshBlockList() {
        delegate.refreshBlockList();
    }

    @Override
    public TelemetrySession newTelemetrySession(Executor executor) {
        return TelemetrySession.DISABLED;
    }
}

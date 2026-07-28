package dev.hoyin1600p.vhaccelerator.client;

import com.mojang.authlib.GameProfile;
import java.util.function.Consumer;

/**
 * Prevents a player-head profile result from mutating a world after its login
 * generation has been invalidated.
 */
public final class TrackedGameProfileCallback implements Consumer<GameProfile> {
    private final Consumer<GameProfile> delegate;
    private final long sessionGeneration;
    private final long workToken;
    private boolean completed;

    private TrackedGameProfileCallback(
            Consumer<GameProfile> delegate,
            long sessionGeneration,
            long workToken
    ) {
        this.delegate = delegate;
        this.sessionGeneration = sessionGeneration;
        this.workToken = workToken;
    }

    public static Consumer<GameProfile> wrap(Consumer<GameProfile> delegate) {
        long generation = ClientWorkSession.current();
        long token = PostLoginWorkTimer.markAuxiliaryWorkStarted(
                generation,
                "player-head profile"
        );
        if (generation < 0L) {
            return delegate;
        }
        return new TrackedGameProfileCallback(delegate, generation, token);
    }

    @Override
    public void accept(GameProfile profile) {
        try {
            if (ClientWorkSession.isCurrent(sessionGeneration)) {
                delegate.accept(profile);
            }
        } finally {
            finish();
        }
    }

    private synchronized void finish() {
        if (completed) {
            return;
        }
        completed = true;
        PostLoginWorkTimer.markWorkCompleted(workToken);
    }
}

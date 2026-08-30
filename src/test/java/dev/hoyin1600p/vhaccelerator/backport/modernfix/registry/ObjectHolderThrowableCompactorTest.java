package dev.hoyin1600p.vhaccelerator.backport.modernfix.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class ObjectHolderThrowableCompactorTest {
    @Test
    void replacesOnlySyntheticCapturedThrowables() throws Exception {
        Throwable firstCallingSite = new Throwable("first");
        Throwable secondCallingSite = new Throwable("second");
        Consumer<Object> first = handlerCapturing(firstCallingSite);
        Consumer<Object> second = handlerCapturing(secondCallingSite);
        HandlerWithRuntimeFailure runtimeState = new HandlerWithRuntimeFailure();

        ObjectHolderThrowableCompactor.Statistics statistics =
                ObjectHolderThrowableCompactor.compactHandlersForTesting(
                        List.of(first, second, runtimeState)
                );

        Throwable compactedFirst = capturedThrowable(first);
        Throwable compactedSecond = capturedThrowable(second);
        assertNotSame(firstCallingSite, compactedFirst);
        assertNotSame(secondCallingSite, compactedSecond);
        assertSame(compactedFirst, compactedSecond);
        assertSame(runtimeState.originalFailure, runtimeState.runtimeFailure);
        assertEquals(3, statistics.holdersVisited());
        assertEquals(2, statistics.throwablesCleared());
        assertEquals(0, statistics.failures());
    }

    private static Consumer<Object> handlerCapturing(Throwable callingSite) {
        return new Consumer<>() {
            @Override
            public void accept(Object ignored) {
                if (callingSite.getMessage() == null) {
                    throw new IllegalStateException(callingSite);
                }
            }
        };
    }

    private static Throwable capturedThrowable(Object handler)
            throws IllegalAccessException {
        for (Field field : handler.getClass().getDeclaredFields()) {
            if (field.isSynthetic()
                    && Throwable.class.isAssignableFrom(field.getType())) {
                field.trySetAccessible();
                return (Throwable) field.get(handler);
            }
        }
        throw new AssertionError("Synthetic Throwable capture not found");
    }

    private static final class HandlerWithRuntimeFailure {
        private final Throwable originalFailure = new Throwable("runtime");
        private final Throwable runtimeFailure = originalFailure;
    }
}

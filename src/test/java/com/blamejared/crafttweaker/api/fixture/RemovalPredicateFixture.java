package com.blamejared.crafttweaker.api.fixture;

import java.util.function.Predicate;

/** Exercises the legacy hook's existing built-in predicate package check. */
public record RemovalPredicateFixture<T>(Predicate<T> delegate) implements Predicate<T> {
    @Override public boolean test(T value) { return delegate.test(value); }
}

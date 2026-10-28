package dev.hoyin1600p.vhaccelerator.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** Runs on the server thread. Authorization precedes reads as well as writes. */
public final class ServerSettingsService {
    public enum Status { OK, DENIED, INVALID, UNAVAILABLE, SAVE_FAILED }

    public record Definition(Object defaultValue, Number min, Number max, boolean restartRequired,
                             Predicate<Object> validator) {
        public boolean valid(Object value) {
            return value != null && value.getClass() == defaultValue.getClass()
                    && (!(value instanceof Double number) || Double.isFinite(number))
                    && validator.test(value);
        }
    }

    public record Row(Object value, Object defaultValue, Number min, Number max, boolean restartRequired) { }
    public record Result(Status status, Map<String, Row> values, List<String> restartKeys) {
        public Result {
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            restartKeys = List.copyOf(restartKeys);
        }
        public static Result failure(Status status) { return new Result(status, Map.of(), List.of()); }
    }

    public interface Store {
        boolean loaded();
        Map<String, Definition> definitions();
        Object get(String key);
        void set(String key, Object value);
        void save();
        void afterSave(String key, Object value);
        void audit(String player, String key, Object before, Object after);
        default void failed(RuntimeException failure) { }
    }

    private ServerSettingsService() { }

    public static Result handle(BooleanSupplier operator, String player, Map<String, Object> request, Store store) {
        if (!operator.getAsBoolean()) return Result.failure(Status.DENIED);
        if (!store.loaded()) return Result.failure(Status.UNAVAILABLE);
        Map<String, Definition> definitions = store.definitions();
        if (request.size() > definitions.size()) return Result.failure(Status.INVALID);
        // Validate the whole request before mutating anything. No dotted-path lookup of user input.
        for (var change : request.entrySet()) {
            Definition definition = definitions.get(change.getKey());
            if (definition == null || !definition.valid(change.getValue())) return Result.failure(Status.INVALID);
        }
        if (!operator.getAsBoolean()) return Result.failure(Status.DENIED);
        Map<String, Object> before = new LinkedHashMap<>();
        request.forEach((key, value) -> {
            Object old = store.get(key);
            if (!Objects.equals(old, value)) before.put(key, old);
        });
        if (!before.isEmpty()) {
            try {
                before.keySet().forEach(key -> store.set(key, request.get(key)));
                store.save();
            } catch (RuntimeException failure) {
                // Restore memory and attempt to restore disk too; never report a failed save as applied.
                before.forEach(store::set);
                try { store.save(); } catch (RuntimeException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
                store.failed(failure);
                return Result.failure(Status.SAVE_FAILED);
            }
            before.forEach((key, old) -> {
                store.audit(player, key, old, request.get(key));
            });
            before.keySet().forEach(key -> store.afterSave(key, request.get(key)));
        }
        Map<String, Row> rows = new LinkedHashMap<>();
        definitions.forEach((key, definition) -> rows.put(key, new Row(store.get(key), definition.defaultValue(),
                definition.min(), definition.max(), definition.restartRequired())));
        return new Result(Status.OK, rows, before.keySet().stream()
                .filter(key -> definitions.get(key).restartRequired()).toList());
    }
}

package io.github.urbanog.vrp.json;

import io.github.urbanog.vrp.json.JsonValue.JsonArray;
import io.github.urbanog.vrp.json.JsonValue.JsonNull;
import io.github.urbanog.vrp.json.JsonValue.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Fluent builder for {@link JsonObject}; {@code null} Java values become JSON {@code null}. */
public final class JsonObjectBuilder {
    private final Map<String, JsonValue> members = new LinkedHashMap<>();

    public static JsonObjectBuilder object() {
        return new JsonObjectBuilder();
    }

    public JsonObjectBuilder put(String key, String value) {
        members.put(key, value == null ? JsonNull.INSTANCE : JsonValue.of(value));
        return this;
    }

    public JsonObjectBuilder put(String key, double value) {
        members.put(key, JsonValue.of(value));
        return this;
    }

    public JsonObjectBuilder put(String key, Double value) {
        members.put(key, value == null ? JsonNull.INSTANCE : JsonValue.of(value));
        return this;
    }

    public JsonObjectBuilder put(String key, long value) {
        members.put(key, JsonValue.of((double) value));
        return this;
    }

    public JsonObjectBuilder put(String key, boolean value) {
        members.put(key, JsonValue.of(value));
        return this;
    }

    public JsonObjectBuilder put(String key, JsonValue value) {
        members.put(key, value == null ? JsonNull.INSTANCE : value);
        return this;
    }

    public JsonObjectBuilder put(String key, JsonObjectBuilder value) {
        members.put(key, value.build());
        return this;
    }

    public <T> JsonObjectBuilder putArray(String key, Collection<T> items, Function<T, JsonValue> f) {
        members.put(key, array(items, f));
        return this;
    }

    public JsonObject build() {
        return new JsonObject(members);
    }

    public static <T> JsonArray array(Collection<T> items, Function<T, JsonValue> f) {
        List<JsonValue> out = new ArrayList<>(items.size());
        for (T t : items) {
            out.add(f.apply(t));
        }
        return new JsonArray(out);
    }

    public static JsonArray intArray(Collection<Integer> items) {
        return array(items, i -> JsonValue.of((double) i));
    }
}

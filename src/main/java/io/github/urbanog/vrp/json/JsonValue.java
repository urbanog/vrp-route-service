package io.github.urbanog.vrp.json;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable JSON value tree (RFC 8259).
 *
 * <p>The hierarchy is sealed, so a {@code switch} over a {@code JsonValue} is checked for
 * exhaustiveness by the compiler. Objects preserve insertion order of their members, which keeps
 * API responses stable and readable.
 */
public sealed interface JsonValue
        permits JsonValue.JsonObject,
                JsonValue.JsonArray,
                JsonValue.JsonString,
                JsonValue.JsonNumber,
                JsonValue.JsonBool,
                JsonValue.JsonNull {

    /** A JSON object; member order is preserved. */
    record JsonObject(Map<String, JsonValue> members) implements JsonValue {
        public JsonObject {
            Objects.requireNonNull(members, "members");
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        }

        public Optional<JsonValue> get(String key) {
            return Optional.ofNullable(members.get(key));
        }

        public boolean has(String key) {
            return members.containsKey(key);
        }
    }

    /** A JSON array. */
    record JsonArray(List<JsonValue> elements) implements JsonValue {
        public JsonArray {
            elements = List.copyOf(elements);
        }

        public int size() {
            return elements.size();
        }

        public JsonValue get(int i) {
            return elements.get(i);
        }
    }

    /** A JSON string (already unescaped). */
    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number. Stored as a {@code double}; integers up to 2^53 in magnitude are therefore
     * exact, which is ample for coordinates, demands and capacities.
     */
    record JsonNumber(double value) implements JsonValue {
        public JsonNumber {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("JSON numbers must be finite, got " + value);
            }
        }

        /** True if the number has no fractional part and fits in a {@code long} exactly. */
        public boolean isIntegral() {
            return value == Math.rint(value) && Math.abs(value) <= (double) (1L << 53);
        }
    }

    /** {@code true} or {@code false}. */
    record JsonBool(boolean value) implements JsonValue {}

    /** The JSON {@code null} literal. */
    enum JsonNull implements JsonValue {
        INSTANCE
    }

    // ---------------------------------------------------------------- convenience factories

    static JsonString of(String s) {
        return new JsonString(s);
    }

    static JsonNumber of(double d) {
        return new JsonNumber(d);
    }

    static JsonBool of(boolean b) {
        return new JsonBool(b);
    }

    /** Short type name used in validation messages, e.g. "string" or "array". */
    default String typeName() {
        return switch (this) {
            case JsonObject o -> "object";
            case JsonArray a -> "array";
            case JsonString s -> "string";
            case JsonNumber n -> "number";
            case JsonBool b -> "boolean";
            case JsonNull n -> "null";
        };
    }
}

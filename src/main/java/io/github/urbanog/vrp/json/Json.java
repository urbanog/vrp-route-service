package io.github.urbanog.vrp.json;

import io.github.urbanog.vrp.json.JsonValue.JsonArray;
import io.github.urbanog.vrp.json.JsonValue.JsonBool;
import io.github.urbanog.vrp.json.JsonValue.JsonNull;
import io.github.urbanog.vrp.json.JsonValue.JsonNumber;
import io.github.urbanog.vrp.json.JsonValue.JsonObject;
import io.github.urbanog.vrp.json.JsonValue.JsonString;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON parser and serializer with no dependencies.
 *
 * <p>The parser follows the RFC 8259 grammar exactly: no comments, no trailing commas, no
 * single-quoted strings, no leading zeros, no {@code NaN}. Nesting depth is bounded so that
 * hostile input cannot overflow the stack. Duplicate object keys are rejected, because silently
 * keeping one of them hides client bugs.
 */
public final class Json {
    /** Maximum nesting depth accepted by {@link #parse(String)}. */
    public static final int MAX_DEPTH = 256;

    private Json() {}

    // ================================================================== parsing

    public static JsonValue parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        JsonValue v = p.readValue(0);
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw new JsonParseException("Unexpected trailing characters", p.pos);
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        JsonValue readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonParseException("Nesting deeper than " + MAX_DEPTH, pos);
            }
            if (pos >= s.length()) {
                throw new JsonParseException("Unexpected end of input", pos);
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject(depth);
                case '[' -> readArray(depth);
                case '"' -> new JsonString(readString());
                case 't' -> literal("true", new JsonBool(true));
                case 'f' -> literal("false", new JsonBool(false));
                case 'n' -> literal("null", JsonNull.INSTANCE);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw new JsonParseException("Unexpected character '" + c + "'", pos);
                }
            };
        }

        private JsonValue literal(String word, JsonValue value) {
            if (!s.startsWith(word, pos)) {
                throw new JsonParseException("Invalid literal, expected '" + word + "'", pos);
            }
            pos += word.length();
            return value;
        }

        private JsonObject readObject(int depth) {
            pos++; // '{'
            Map<String, JsonValue> members = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return new JsonObject(members);
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw new JsonParseException("Expected string key", pos);
                }
                int keyPos = pos;
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                JsonValue value = readValue(depth + 1);
                if (members.put(key, value) != null) {
                    throw new JsonParseException("Duplicate key \"" + key + "\"", keyPos);
                }
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return new JsonObject(members);
                }
                if (c != ',') {
                    throw new JsonParseException("Expected ',' or '}'", pos - 1);
                }
            }
        }

        private JsonArray readArray(int depth) {
            pos++; // '['
            List<JsonValue> elements = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return new JsonArray(elements);
            }
            while (true) {
                skipWhitespace();
                elements.add(readValue(depth + 1));
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return new JsonArray(elements);
                }
                if (c != ',') {
                    throw new JsonParseException("Expected ',' or ']'", pos - 1);
                }
            }
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw new JsonParseException("Unterminated string", pos);
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new JsonParseException("Unescaped control character in string", pos - 1);
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw new JsonParseException("Unterminated escape", pos);
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> sb.append(readHex4());
                    default -> throw new JsonParseException("Invalid escape '\\" + e + "'", pos - 1);
                }
            }
        }

        private char readHex4() {
            if (pos + 4 > s.length()) {
                throw new JsonParseException("Truncated \\u escape", pos);
            }
            int v = 0;
            for (int i = 0; i < 4; i++) {
                int d = Character.digit(s.charAt(pos + i), 16);
                if (d < 0) {
                    throw new JsonParseException("Invalid hex digit in \\u escape", pos + i);
                }
                v = (v << 4) | d;
            }
            pos += 4;
            // Surrogate pairs arrive as two consecutive escapes; appending the UTF-16 code units
            // one by one reconstructs the original code point in the Java string.
            return (char) v;
        }

        private JsonNumber readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            if (peek() == '0') {
                pos++;
            } else if (isDigit(peek())) {
                while (isDigit(peek())) pos++;
            } else {
                throw new JsonParseException("Invalid number", pos);
            }
            if (peek() == '.') {
                pos++;
                if (!isDigit(peek())) {
                    throw new JsonParseException("Digit expected after decimal point", pos);
                }
                while (isDigit(peek())) pos++;
            }
            if (peek() == 'e' || peek() == 'E') {
                pos++;
                if (peek() == '+' || peek() == '-') pos++;
                if (!isDigit(peek())) {
                    throw new JsonParseException("Digit expected in exponent", pos);
                }
                while (isDigit(peek())) pos++;
            }
            double d = Double.parseDouble(s.substring(start, pos));
            if (!Double.isFinite(d)) {
                throw new JsonParseException("Number out of range", start);
            }
            return new JsonNumber(d);
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        private char next() {
            if (pos >= s.length()) {
                throw new JsonParseException("Unexpected end of input", pos);
            }
            return s.charAt(pos++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new JsonParseException("Expected '" + c + "'", pos - 1);
            }
        }
    }

    // ================================================================== writing

    /** Compact serialization. */
    public static String write(JsonValue v) {
        StringBuilder sb = new StringBuilder();
        write(v, sb, -1, 0);
        return sb.toString();
    }

    /** Pretty-printed serialization with two-space indentation. */
    public static String writePretty(JsonValue v) {
        StringBuilder sb = new StringBuilder();
        write(v, sb, 2, 0);
        return sb.toString();
    }

    private static void write(JsonValue v, StringBuilder sb, int indent, int level) {
        switch (v) {
            case JsonNull n -> sb.append("null");
            case JsonBool b -> sb.append(b.value());
            case JsonNumber n -> sb.append(formatNumber(n));
            case JsonString str -> writeString(str.value(), sb);
            case JsonArray a -> {
                if (a.elements().isEmpty()) {
                    sb.append("[]");
                    return;
                }
                sb.append('[');
                boolean first = true;
                for (JsonValue e : a.elements()) {
                    if (!first) sb.append(',');
                    first = false;
                    newline(sb, indent, level + 1);
                    write(e, sb, indent, level + 1);
                }
                newline(sb, indent, level);
                sb.append(']');
            }
            case JsonObject o -> {
                if (o.members().isEmpty()) {
                    sb.append("{}");
                    return;
                }
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, JsonValue> m : o.members().entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    newline(sb, indent, level + 1);
                    writeString(m.getKey(), sb);
                    sb.append(indent >= 0 ? ": " : ":");
                    write(m.getValue(), sb, indent, level + 1);
                }
                newline(sb, indent, level);
                sb.append('}');
            }
        }
    }

    private static void newline(StringBuilder sb, int indent, int level) {
        if (indent < 0) return;
        sb.append('\n');
        sb.append(" ".repeat(indent * level));
    }

    static String formatNumber(JsonNumber n) {
        double d = n.value();
        if (n.isIntegral()) {
            return Long.toString((long) d);
        }
        return Double.toString(d); // shortest repr that round-trips; always valid JSON when finite
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}

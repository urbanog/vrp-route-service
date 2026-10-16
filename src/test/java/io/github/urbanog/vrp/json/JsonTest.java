package io.github.urbanog.vrp.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.json.JsonValue.JsonArray;
import io.github.urbanog.vrp.json.JsonValue.JsonNumber;
import io.github.urbanog.vrp.json.JsonValue.JsonObject;
import io.github.urbanog.vrp.json.JsonValue.JsonString;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesAllValueKinds() {
        JsonValue v = Json.parse(" {\"a\": [1, -2.5, 3e2, true, false, null], \"b\": {\"c\": \"d\"}} ");
        JsonObject o = assertInstanceOf(JsonObject.class, v);
        JsonArray a = assertInstanceOf(JsonArray.class, o.members().get("a"));
        assertEquals(6, a.size());
        assertEquals(1.0, ((JsonNumber) a.get(0)).value(), 0.0);
        assertEquals(-2.5, ((JsonNumber) a.get(1)).value(), 0.0);
        assertEquals(300.0, ((JsonNumber) a.get(2)).value(), 0.0);
        assertEquals(new JsonValue.JsonBool(true), a.get(3));
        assertEquals(JsonValue.JsonNull.INSTANCE, a.get(5));
        assertEquals("d", ((JsonString) ((JsonObject) o.members().get("b")).members().get("c")).value());
    }

    @Test
    void decodesEscapesIncludingSurrogatePairs() {
        JsonString s = (JsonString) Json.parse("\"q\\\"b\\\\s\\/n\\n t\\t u\\u00e9 \\ud83d\\ude00\"");
        assertEquals("q\"b\\s/n\n t\t u\u00e9 \uD83D\uDE00", s.value());
    }

    @Test
    void rejectsMalformedInput() {
        for (String bad : List.of("", "{", "[1,]", "{\"a\":1,}", "01", "1.", "-", "1e", "tru", "\"abc",
                "{'a':1}", "[1 2]", "{\"a\" 1}", "\"\\x\"", "\"a\nb\"", "NaN", "{} x", "\"\\u12\"")) {
            assertThrows(JsonParseException.class, () -> Json.parse(bad), "should reject: " + bad);
        }
    }

    @Test
    void rejectsDuplicateKeys() {
        JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse("{\"a\":1,\"a\":2}"));
        assertTrue(e.getMessage().contains("Duplicate key"));
    }

    @Test
    void boundsNestingDepth() {
        String deep = "[".repeat(Json.MAX_DEPTH + 2) + "]".repeat(Json.MAX_DEPTH + 2);
        assertThrows(JsonParseException.class, () -> Json.parse(deep));
        String ok = "[".repeat(100) + "]".repeat(100);
        assertInstanceOf(JsonArray.class, Json.parse(ok));
    }

    @Test
    void writesIntegersWithoutFractionAndEscapesControlCharacters() {
        JsonObject o = JsonObjectBuilder.object()
                .put("n", 42)
                .put("x", 0.1)
                .put("s", "a\"b\u0001\n")
                .build();
        assertEquals("{\"n\":42,\"x\":0.1,\"s\":\"a\\\"b\\u0001\\n\"}", Json.write(o));
    }

    @Test
    void prettyPrintingRoundTrips() {
        String text = "{\"a\":[1,2,{\"b\":[]}],\"c\":{}}";
        JsonValue v = Json.parse(text);
        assertEquals(v, Json.parse(Json.writePretty(v)));
        assertEquals(text, Json.write(v));
    }

    /** Property: write then parse is the identity on random trees. */
    @Test
    void randomTreesRoundTrip() {
        Random rnd = new Random(7);
        for (int i = 0; i < 500; i++) {
            JsonValue v = randomValue(rnd, 0);
            assertEquals(v, Json.parse(Json.write(v)));
            assertEquals(v, Json.parse(Json.writePretty(v)));
        }
    }

    private static JsonValue randomValue(Random rnd, int depth) {
        int kind = depth > 4 ? rnd.nextInt(4) : rnd.nextInt(6);
        return switch (kind) {
            case 0 -> JsonValue.of(rnd.nextBoolean());
            case 1 -> JsonValue.of(rnd.nextBoolean() ? rnd.nextInt(2001) - 1000 : rnd.nextGaussian() * 1e3);
            case 2 -> {
                StringBuilder sb = new StringBuilder();
                int len = rnd.nextInt(8);
                for (int k = 0; k < len; k++) sb.append((char) rnd.nextInt(0x3000));
                yield JsonValue.of(sb.toString());
            }
            case 3 -> JsonValue.JsonNull.INSTANCE;
            case 4 -> {
                var list = new java.util.ArrayList<JsonValue>();
                for (int k = rnd.nextInt(4); k > 0; k--) list.add(randomValue(rnd, depth + 1));
                yield new JsonArray(list);
            }
            default -> {
                var map = new java.util.LinkedHashMap<String, JsonValue>();
                for (int k = rnd.nextInt(4); k > 0; k--) map.put("k" + rnd.nextInt(100), randomValue(rnd, depth + 1));
                yield new JsonObject(map);
            }
        };
    }
}

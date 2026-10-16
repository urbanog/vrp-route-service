package org.junit.jupiter.api;

import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.function.Executable;

/**
 * Minimal re-implementation of the subset of {@code org.junit.jupiter.api.Assertions} that the
 * test suite uses, with the same signatures and semantics. It exists only so that {@code
 * build.sh} can run the tests where JUnit cannot be downloaded; Maven uses real JUnit 5.
 */
public final class Assertions {
    private Assertions() {}

    public static void assertTrue(boolean condition) {
        if (!condition) fail("expected: <true> but was: <false>");
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message + " ==> expected: <true> but was: <false>");
    }

    public static void assertTrue(boolean condition, Supplier<String> message) {
        if (!condition) fail(message.get() + " ==> expected: <true> but was: <false>");
    }

    public static void assertFalse(boolean condition) {
        if (condition) fail("expected: <false> but was: <true>");
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message + " ==> expected: <false> but was: <true>");
    }

    public static void assertNull(Object actual) {
        if (actual != null) fail("expected: <null> but was: <" + actual + ">");
    }

    public static void assertNotNull(Object actual) {
        if (actual == null) fail("expected: not <null>");
    }

    public static void assertNotNull(Object actual, String message) {
        if (actual == null) fail(message + " ==> expected: not <null>");
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) fail(format(expected, actual));
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) fail(message + " ==> " + format(expected, actual));
    }

    public static void assertEquals(long expected, long actual) {
        if (expected != actual) fail(format(expected, actual));
    }

    public static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) fail(message + " ==> " + format(expected, actual));
    }

    public static void assertEquals(int expected, int actual) {
        if (expected != actual) fail(format(expected, actual));
    }

    public static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) fail(message + " ==> " + format(expected, actual));
    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (!(Math.abs(expected - actual) <= delta)) fail(format(expected, actual));
    }

    public static void assertEquals(double expected, double actual, double delta, String message) {
        if (!(Math.abs(expected - actual) <= delta)) fail(message + " ==> " + format(expected, actual));
    }

    public static void assertNotEquals(Object unexpected, Object actual) {
        if (Objects.equals(unexpected, actual)) fail("expected: not equal but was: <" + actual + ">");
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actual) {
        if (!expectedType.isInstance(actual)) {
            fail("Unexpected type, expected: <" + expectedType.getName() + "> but was: <"
                    + (actual == null ? "null" : actual.getClass().getName()) + ">");
        }
        return expectedType.cast(actual);
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) {
        return assertThrows(expectedType, executable, null);
    }

    public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable, String message) {
        try {
            executable.execute();
        } catch (Throwable t) {
            if (expectedType.isInstance(t)) return expectedType.cast(t);
            throw new AssertionError(prefix(message) + "Unexpected exception type thrown, expected: <"
                    + expectedType.getName() + "> but was: <" + t.getClass().getName() + ">", t);
        }
        throw new AssertionError(prefix(message) + "Expected " + expectedType.getName() + " to be thrown, but nothing was thrown.");
    }

    public static void assertDoesNotThrow(Executable executable) {
        try {
            executable.execute();
        } catch (Throwable t) {
            throw new AssertionError("Unexpected exception thrown: " + t, t);
        }
    }

    public static <T> T fail(String message) {
        throw new AssertionError(message);
    }

    private static String prefix(String message) {
        return message == null ? "" : message + " ==> ";
    }

    private static String format(Object expected, Object actual) {
        return "expected: <" + expected + "> but was: <" + actual + ">";
    }
}

package io.github.urbanog.vrp.json;

/** Thrown when input text is not well-formed JSON. Carries the character offset of the error. */
public final class JsonParseException extends RuntimeException {
    private final int offset;

    public JsonParseException(String message, int offset) {
        super(message + " at offset " + offset);
        this.offset = offset;
    }

    public int offset() {
        return offset;
    }
}

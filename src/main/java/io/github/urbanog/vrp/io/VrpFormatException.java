package io.github.urbanog.vrp.io;

/** Thrown when a CVRPLIB/TSPLIB file cannot be parsed; carries the 1-based line number. */
public final class VrpFormatException extends RuntimeException {
    private final int line;

    public VrpFormatException(String message, int line) {
        super("line " + line + ": " + message);
        this.line = line;
    }

    public int line() {
        return line;
    }
}

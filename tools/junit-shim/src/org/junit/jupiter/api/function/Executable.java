package org.junit.jupiter.api.function;

/** Shim of the JUnit 5 functional interface of the same name; used only by build.sh. */
@FunctionalInterface
public interface Executable {
    void execute() throws Throwable;
}

package junitshim;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tiny JUnit-5-compatible runner for offline builds: discovers every {@code *Test.class} under the
 * given directory, and for each {@code @Test} method creates a fresh instance (JUnit's default
 * per-method lifecycle), runs {@code @BeforeEach}, the test, then {@code @AfterEach}. Exits with
 * status 1 if any test fails.
 */
public final class ShimRunner {
    private ShimRunner() {}

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        List<String> classes;
        try (Stream<Path> s = Files.walk(root)) {
            classes = s.map(root::relativize)
                    .map(Path::toString)
                    .filter(f -> f.endsWith("Test.class") && !f.contains("$"))
                    .map(f -> f.substring(0, f.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
                    .sorted()
                    .toList();
        }
        int passed = 0;
        List<String> failures = new ArrayList<>();
        long t0 = System.nanoTime();
        for (String cn : classes) {
            Class<?> c = Class.forName(cn);
            Method[] methods = c.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(Method::getName));
            List<Method> before = annotated(methods, BeforeEach.class);
            List<Method> after = annotated(methods, AfterEach.class);
            int classPassed = 0;
            int classTotal = 0;
            for (Method m : methods) {
                if (!m.isAnnotationPresent(Test.class)) continue;
                classTotal++;
                try {
                    var ctor = c.getDeclaredConstructor();
                    ctor.setAccessible(true);
                    Object inst = ctor.newInstance();
                    try {
                        for (Method b : before) invoke(b, inst);
                        invoke(m, inst);
                    } finally {
                        for (Method a : after) invoke(a, inst);
                    }
                    passed++;
                    classPassed++;
                } catch (Throwable t) {
                    Throwable cause = t instanceof InvocationTargetException ite ? ite.getCause() : t;
                    failures.add(cn + "#" + m.getName() + ": " + cause);
                    cause.printStackTrace(System.out);
                }
            }
            System.out.printf("  %-60s %3d/%d%n", c.getSimpleName(), classPassed, classTotal);
        }
        double secs = (System.nanoTime() - t0) / 1e9;
        System.out.printf("%nTests run: %d, Failures: %d (%.1f s)%n", passed + failures.size(), failures.size(), secs);
        failures.forEach(f -> System.out.println("  FAILED " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static List<Method> annotated(Method[] ms, Class<? extends java.lang.annotation.Annotation> a) {
        return Arrays.stream(ms).filter(m -> m.isAnnotationPresent(a)).toList();
    }

    private static void invoke(Method m, Object inst) throws Throwable {
        if (Modifier.isStatic(m.getModifiers())) throw new IllegalStateException("static lifecycle methods unsupported");
        m.setAccessible(true);
        try {
            m.invoke(inst);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}

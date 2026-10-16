package io.github.urbanog.vrp.io;

import io.github.urbanog.vrp.model.Instance;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Benchmark instances bundled as classpath resources under {@code /instances}. */
public final class InstanceCatalog {
    /** Names of the bundled files (without the {@code .vrp} extension). */
    public static final List<String> BUNDLED = List.of("U-n10-k3", "U-n30-k4", "C-n50-k5", "U-n100-k8", "C-n200-k12");

    private final Map<String, Instance> instances = new LinkedHashMap<>();

    public static InstanceCatalog loadBundled() {
        InstanceCatalog c = new InstanceCatalog();
        for (String name : BUNDLED) {
            String path = "/instances/" + name + ".vrp";
            try (InputStream in = InstanceCatalog.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("missing resource " + path);
                c.instances.put(name, TsplibReader.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return c;
    }

    public List<Instance> all() {
        return List.copyOf(instances.values());
    }

    public Optional<Instance> get(String name) {
        return Optional.ofNullable(instances.get(name));
    }
}

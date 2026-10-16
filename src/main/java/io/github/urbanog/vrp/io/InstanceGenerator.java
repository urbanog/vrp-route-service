package io.github.urbanog.vrp.io;

import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeded random instance generator on a 1000 x 1000 integer grid with integer demands in
 * {@code [1, 10]}.
 *
 * <ul>
 *   <li>{@code uniform}: customers uniformly at random, depot at the centre.
 *   <li>{@code clustered}: customers around a few Gaussian cluster centres, depot at a corner,
 *       which is closer to typical shuttle/logistics geography and harder for savings.
 * </ul>
 *
 * The capacity is chosen so that roughly {@code customers / routeSize} vehicles are needed.
 */
public final class InstanceGenerator {
    public enum Layout { UNIFORM, CLUSTERED }

    private InstanceGenerator() {}

    public static Instance generate(String name, int n, int routeSize, Layout layout, long seed) {
        if (n < 1) throw new IllegalArgumentException("n must be >= 1");
        Random rnd = new Random(seed);
        List<Customer> cs = new ArrayList<>(n);
        Point depot = layout == Layout.UNIFORM ? new Point(500, 500) : new Point(50, 50);
        List<Point> centres = new ArrayList<>();
        int k = Math.max(2, n / 25);
        for (int i = 0; i < k; i++) centres.add(new Point(100 + rnd.nextInt(801), 100 + rnd.nextInt(801)));
        long total = 0;
        for (int i = 0; i < n; i++) {
            Point pt;
            if (layout == Layout.UNIFORM) {
                pt = new Point(rnd.nextInt(1001), rnd.nextInt(1001));
            } else {
                Point c = centres.get(rnd.nextInt(k));
                double x = Math.round(c.x() + 60 * rnd.nextGaussian());
                double y = Math.round(c.y() + 60 * rnd.nextGaussian());
                pt = new Point(Math.max(0, Math.min(1000, x)), Math.max(0, Math.min(1000, y)));
            }
            int q = 1 + rnd.nextInt(10);
            total += q;
            cs.add(new Customer(i + 2, pt, q)); // TSPLIB numbering: depot is node 1
        }
        int vehicles = Math.max(1, (int) Math.round((double) n / routeSize));
        int capacity = (int) Math.max(10, Math.ceil(total / (double) vehicles * 1.05));
        return new Instance(name, capacity, DistanceType.EUCLIDEAN_ROUNDED, depot, cs);
    }
}

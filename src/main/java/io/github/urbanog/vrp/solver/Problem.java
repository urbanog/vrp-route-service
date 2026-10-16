package io.github.urbanog.vrp.solver;

import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.Instance;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.IntStream;

/**
 * Dense internal representation used by all algorithms.
 *
 * <p>Node {@code 0} is the depot and nodes {@code 1..n} are the customers in instance order. The
 * full symmetric distance matrix is precomputed once ({@code O(n^2)} memory, about 32 MB for the
 * 2000-customer limit), so every move evaluation is a handful of array reads.
 */
public final class Problem {
    final int n;
    final int capacity;
    final double[][] dist;
    final int[] demand;
    final int[] externalId;
    private int[][] neighbours;

    public Problem(Instance instance) {
        this.n = instance.size();
        this.capacity = instance.capacity();
        this.dist = new double[n + 1][n + 1];
        this.demand = new int[n + 1];
        this.externalId = new int[n + 1];
        var pts = new io.github.urbanog.vrp.model.Point[n + 1];
        pts[0] = instance.depot();
        for (int i = 0; i < n; i++) {
            Customer c = instance.customers().get(i);
            pts[i + 1] = c.location();
            demand[i + 1] = c.demand();
            externalId[i + 1] = c.id();
        }
        for (int i = 0; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                double d = instance.distanceType().cost(pts[i], pts[j]);
                dist[i][j] = d;
                dist[j][i] = d;
            }
        }
    }

    public int customers() {
        return n;
    }

    public int capacity() {
        return capacity;
    }

    public double d(int i, int j) {
        return dist[i][j];
    }

    public int demand(int i) {
        return demand[i];
    }

    /**
     * For each customer, the other customers sorted by increasing distance (ties by index),
     * truncated to {@code k}. The full sorted lists are computed once, lazily, in {@code O(n^2 log
     * n)}; truncated views are fresh arrays.
     */
    synchronized int[][] nearestNeighbours(int k) {
        if (neighbours == null) {
            int[][] nb = new int[n + 1][];
            nb[0] = new int[0];
            for (int i = 1; i <= n; i++) {
                final int u = i;
                nb[i] = IntStream.rangeClosed(1, n)
                        .filter(v -> v != u)
                        .boxed()
                        .sorted(Comparator.comparingDouble((Integer v) -> dist[u][v]).thenComparing(v -> v))
                        .mapToInt(Integer::intValue)
                        .toArray();
            }
            neighbours = nb;
        }
        int kk = Math.max(0, Math.min(k, n - 1));
        int[][] out = new int[n + 1][];
        out[0] = new int[0];
        for (int i = 1; i <= n; i++) out[i] = Arrays.copyOf(neighbours[i], kk);
        return out;
    }

    @Override
    public String toString() {
        return "Problem[n=" + n + ", Q=" + capacity + ", demand=" + Arrays.toString(demand) + "]";
    }
}

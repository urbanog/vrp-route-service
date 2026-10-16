package io.github.urbanog.vrp.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Exact CVRP solver for tiny instances by dynamic programming over subsets, used as ground truth in
 * tests and for measuring optimality gaps.
 *
 * <ol>
 *   <li><b>Held–Karp.</b> For every subset {@code S} of customers and {@code j ∈ S}, {@code
 *       h(S, j)} is the cheapest path that leaves the depot, visits exactly {@code S} and ends at
 *       {@code j}. Then {@code tsp(S) = min_j h(S,j) + d(j,0)} is the cheapest single route serving
 *       {@code S}. Time {@code O(2^n n^2)}.
 *   <li><b>Set partitioning.</b> {@code f(∅) = 0} and {@code f(S) = min { tsp(T) + f(S \ T) }}
 *       over capacity-feasible {@code T ⊆ S} that contain the lowest customer of {@code S} (fixing
 *       that element avoids enumerating each partition once per ordering of its blocks). Time
 *       {@code O(3^n)}.
 * </ol>
 *
 * <p>With unlimited fleet size this is exactly the CVRP optimum. Feasible for {@code n ≤ 12}
 * (about 0.6M Held–Karp states and 0.5M partition steps).
 */
public final class ExactSolver {
    public static final int MAX_CUSTOMERS = 12;

    private ExactSolver() {}

    static WorkingSolution solve(Problem p) {
        int n = p.n;
        if (n > MAX_CUSTOMERS) {
            throw new IllegalArgumentException("exact solver supports at most " + MAX_CUSTOMERS + " customers");
        }
        int full = (1 << n) - 1;
        double inf = Double.POSITIVE_INFINITY;
        // Customer k (0-based bit) is node k+1.
        double[][] h = new double[1 << n][n];
        int[][] parent = new int[1 << n][n];
        for (double[] row : h) Arrays.fill(row, inf);
        int[] load = new int[1 << n];
        for (int s = 1; s <= full; s++) {
            int low = Integer.numberOfTrailingZeros(s);
            load[s] = load[s & (s - 1)] + p.demand[low + 1];
        }
        for (int k = 0; k < n; k++) {
            h[1 << k][k] = p.dist[0][k + 1];
            parent[1 << k][k] = -1;
        }
        for (int s = 1; s <= full; s++) {
            if (load[s] > p.capacity) continue; // infeasible as a single route; supersets too
            for (int j = 0; j < n; j++) {
                if ((s & (1 << j)) == 0 || h[s][j] == inf) continue;
                for (int k = 0; k < n; k++) {
                    if ((s & (1 << k)) != 0) continue;
                    int t = s | (1 << k);
                    double c = h[s][j] + p.dist[j + 1][k + 1];
                    if (c < h[t][k]) {
                        h[t][k] = c;
                        parent[t][k] = j;
                    }
                }
            }
        }
        double[] tsp = new double[1 << n];
        int[] tspEnd = new int[1 << n];
        Arrays.fill(tsp, inf);
        for (int s = 1; s <= full; s++) {
            if (load[s] > p.capacity) continue;
            for (int j = 0; j < n; j++) {
                if ((s & (1 << j)) != 0 && h[s][j] + p.dist[j + 1][0] < tsp[s]) {
                    tsp[s] = h[s][j] + p.dist[j + 1][0];
                    tspEnd[s] = j;
                }
            }
        }

        double[] f = new double[1 << n];
        int[] choice = new int[1 << n];
        Arrays.fill(f, inf);
        f[0] = 0;
        for (int s = 1; s <= full; s++) {
            int low = s & -s;
            int rest = s ^ low;
            // Enumerate T = low ∪ sub for every sub ⊆ rest.
            for (int sub = rest; ; sub = (sub - 1) & rest) {
                int t = sub | low;
                if (tsp[t] < inf) {
                    double c = tsp[t] + f[s ^ t];
                    if (c < f[s]) {
                        f[s] = c;
                        choice[s] = t;
                    }
                }
                if (sub == 0) break;
            }
        }

        List<Route> routes = new ArrayList<>();
        for (int s = full; s != 0; s ^= choice[s]) {
            int t = choice[s];
            int[] seq = new int[Integer.bitCount(t)];
            int j = tspEnd[t];
            int set = t;
            for (int pos = seq.length - 1; pos >= 0; pos--) {
                seq[pos] = j + 1;
                int pj = parent[set][j];
                set ^= 1 << j;
                j = pj;
            }
            routes.add(new Route(seq, p));
        }
        return new WorkingSolution(p, routes);
    }
}

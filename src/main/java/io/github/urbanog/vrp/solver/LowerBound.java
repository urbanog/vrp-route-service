package io.github.urbanog.vrp.solver;

import java.util.Arrays;

/**
 * A cheap, always-valid lower bound on the optimal CVRP cost.
 *
 * <p><b>Claim.</b> Let {@code K = ceil(sum q_i / Q)}, let {@code MST} be the weight of a minimum
 * spanning tree on the depot and all customers, and let {@code e_1 ≤ e_2 ≤ ...} be the sorted
 * depot distances {@code d(0,i)}. Then every feasible solution costs at least {@code MST + e_1 +
 * ... + e_K}.
 *
 * <p><b>Proof.</b> A feasible solution with {@code m} routes uses {@code m ≥ K} vehicles (bin
 * packing). From each route delete one of its two depot edges, say {@code (0, i_r)}. What remains
 * is a union of {@code m} paths starting at the depot that together cover every customer exactly
 * once, i.e. a spanning tree, so it weighs at least {@code MST}. The deleted edges join the depot
 * to {@code m} <em>distinct</em> customers (routes are disjoint), so they weigh at least {@code e_1
 * + ... + e_m ≥ e_1 + ... + e_K}. ∎
 *
 * <p>The argument uses only non-negativity of the costs, not the triangle inequality, so it is
 * also valid for TSPLIB-rounded distances. It is computed in {@code O(n^2)} with Prim's
 * algorithm. It is weak (31-43% below the best solution found on the bundled instances); it
 * certifies that a reported cost is not absurd, but does not give a tight gap.
 */
public final class LowerBound {
    private LowerBound() {}

    static double compute(Problem p) {
        int n = p.n;
        int total = n + 1;
        double[] key = new double[total];
        boolean[] in = new boolean[total];
        Arrays.fill(key, Double.POSITIVE_INFINITY);
        key[0] = 0;
        double mst = 0;
        for (int it = 0; it < total; it++) {
            int u = -1;
            for (int v = 0; v < total; v++) {
                if (!in[v] && (u < 0 || key[v] < key[u])) u = v;
            }
            in[u] = true;
            mst += key[u];
            for (int v = 0; v < total; v++) {
                if (!in[v] && p.dist[u][v] < key[v]) key[v] = p.dist[u][v];
            }
        }
        long totalDemand = 0;
        for (int i = 1; i <= n; i++) totalDemand += p.demand[i];
        int k = (int) Math.max(1, (totalDemand + p.capacity - 1) / p.capacity);
        double[] depot = new double[n];
        for (int i = 1; i <= n; i++) depot[i - 1] = p.dist[0][i];
        Arrays.sort(depot);
        double extra = 0;
        for (int i = 0; i < Math.min(k, n); i++) extra += depot[i];
        return mst + extra;
    }
}

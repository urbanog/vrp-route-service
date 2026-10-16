package io.github.urbanog.vrp.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parallel Clarke–Wright savings heuristic (Clarke &amp; Wright, 1964).
 *
 * <p>Start with one out-and-back route per customer. Joining the route ending in {@code i} with
 * the route starting in {@code j} removes the edges {@code (i,0)} and {@code (0,j)} and adds
 * {@code (i,j)}, saving {@code s(i,j) = d(0,i) + d(0,j) - d(i,j)}. Savings are processed in
 * decreasing order and a merge is applied whenever {@code i} and {@code j} are endpoints of two
 * different routes and the combined load fits the capacity. Routes are reversed where needed, which
 * is valid because distances are symmetric.
 *
 * <p>Complexity: {@code O(n^2 log n)} time, dominated by sorting the {@code n(n-1)/2} savings, and
 * {@code O(n^2)} memory. There are at most {@code n-1} merges, each {@code O(n)} (reversal and
 * concatenation), so merging adds only {@code O(n^2)}.
 */
public final class ClarkeWright {
    private ClarkeWright() {}

    static WorkingSolution solve(Problem p) {
        int n = p.n;
        // Each route is stored as a deque of nodes; routeOf[v] names the route containing v.
        List<List<Integer>> route = new ArrayList<>(n + 1);
        int[] routeOf = new int[n + 1];
        int[] load = new int[n + 1];
        route.add(null);
        for (int v = 1; v <= n; v++) {
            List<Integer> r = new ArrayList<>();
            r.add(v);
            route.add(r);
            routeOf[v] = v;
            load[v] = p.demand[v];
        }

        int m = n * (n - 1) / 2;
        long[] keys = new long[m];
        double[] sav = new double[m];
        int k = 0;
        for (int i = 1; i <= n; i++) {
            for (int j = i + 1; j <= n; j++) {
                sav[k] = p.dist[0][i] + p.dist[0][j] - p.dist[i][j];
                keys[k] = ((long) i << 32) | j;
                k++;
            }
        }
        Integer[] order = new Integer[m];
        for (int t = 0; t < m; t++) order[t] = t;
        // Decreasing savings, ties broken by (i, j) so the result is deterministic.
        Arrays.sort(order, (a, b) -> {
            int c = Double.compare(sav[b], sav[a]);
            return c != 0 ? c : Long.compare(keys[a], keys[b]);
        });

        for (int t = 0; t < m; t++) {
            int idx = order[t];
            if (sav[idx] <= 0) break; // merging can no longer reduce cost
            int i = (int) (keys[idx] >>> 32);
            int j = (int) (keys[idx] & 0xffffffffL);
            int ri = routeOf[i];
            int rj = routeOf[j];
            if (ri == rj || load[ri] + load[rj] > p.capacity) continue;
            List<Integer> a = route.get(ri);
            List<Integer> b = route.get(rj);
            boolean iEnd = a.get(a.size() - 1) == i;
            boolean iStart = a.get(0) == i;
            boolean jStart = b.get(0) == j;
            boolean jEnd = b.get(b.size() - 1) == j;
            if (!(iEnd || iStart) || !(jStart || jEnd)) continue; // interior node: cannot merge
            // Orient so that a ends with i and b starts with j.
            if (!iEnd) java.util.Collections.reverse(a);
            if (!jStart) java.util.Collections.reverse(b);
            // Union by size: append the smaller list into the larger one.
            int keep;
            int drop;
            if (a.size() >= b.size()) {
                a.addAll(b);
                keep = ri;
                drop = rj;
            } else {
                b.addAll(0, a);
                keep = rj;
                drop = ri;
            }
            for (int v : route.get(drop)) routeOf[v] = keep;
            load[keep] += load[drop];
            route.set(drop, null);
        }

        List<Route> out = new ArrayList<>();
        for (int v = 1; v <= n; v++) {
            List<Integer> r = route.get(v);
            if (r != null) out.add(new Route(r.stream().mapToInt(Integer::intValue).toArray(), p));
        }
        return new WorkingSolution(p, out);
    }
}

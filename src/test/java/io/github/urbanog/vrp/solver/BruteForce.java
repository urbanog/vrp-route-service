package io.github.urbanog.vrp.solver;

/**
 * Independent reference optimum for very small instances, deliberately using a different method
 * from {@link ExactSolver}: every solution is a permutation of the customers cut into consecutive
 * capacity-feasible routes, so we enumerate all {@code n!} permutations and, for each, find the
 * best cutting by the {@code O(n^2)} "split" dynamic program (Beasley 1983).
 */
final class BruteForce {
    private BruteForce() {}

    static double optimum(Problem p) {
        int n = p.n;
        int[] perm = new int[n];
        for (int i = 0; i < n; i++) perm[i] = i + 1;
        double best = Double.POSITIVE_INFINITY;
        do {
            best = Math.min(best, split(p, perm));
        } while (nextPermutation(perm));
        return best;
    }

    private static double split(Problem p, int[] perm) {
        int n = perm.length;
        double[] f = new double[n + 1];
        java.util.Arrays.fill(f, Double.POSITIVE_INFINITY);
        f[0] = 0;
        for (int i = 0; i < n; i++) {
            int load = 0;
            double cost = 0;
            for (int j = i; j < n; j++) {
                load += p.demand[perm[j]];
                if (load > p.capacity) break;
                cost += j == i ? p.dist[0][perm[j]] : p.dist[perm[j - 1]][perm[j]];
                f[j + 1] = Math.min(f[j + 1], f[i] + cost + p.dist[perm[j]][0]);
            }
        }
        return f[n];
    }

    private static boolean nextPermutation(int[] a) {
        int i = a.length - 2;
        while (i >= 0 && a[i] >= a[i + 1]) i--;
        if (i < 0) return false;
        int j = a.length - 1;
        while (a[j] <= a[i]) j--;
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
        for (int l = i + 1, r = a.length - 1; l < r; l++, r--) {
            t = a[l];
            a[l] = a[r];
            a[r] = t;
        }
        return true;
    }
}

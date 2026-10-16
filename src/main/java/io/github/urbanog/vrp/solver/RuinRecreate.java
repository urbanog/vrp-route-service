package io.github.urbanog.vrp.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Perturbation operator for the metaheuristic: <em>radial ruin</em> followed by <em>greedy
 * recreate</em> (in the spirit of Schrimpf et al., 2000).
 *
 * <p>Ruin removes a random seed customer together with its {@code q-1} nearest neighbours, which
 * opens up a geographic region that local search alone cannot reorganise. Recreate re-inserts the
 * removed customers one by one (in an order drawn uniformly from: largest demand first, farthest
 * from the depot first, or random) at the cheapest
 * capacity-feasible position over all routes, opening a new route only if no existing route has
 * room. Cost: {@code O(q * n)} per call.
 */
final class RuinRecreate {
    private final Problem p;
    private final Random rnd;
    private final int[][] nb;

    RuinRecreate(Problem p, Random rnd) {
        this.p = p;
        this.rnd = rnd;
        this.nb = p.nearestNeighbours(Math.max(1, p.n - 1));
    }

    void apply(WorkingSolution s, int q) {
        q = Math.max(1, Math.min(q, p.n));
        int seed = 1 + rnd.nextInt(p.n);
        boolean[] removed = new boolean[p.n + 1];
        List<Integer> pool = new ArrayList<>(q);
        removed[seed] = true;
        pool.add(seed);
        for (int k = 0; pool.size() < q && k < nb[seed].length; k++) {
            removed[nb[seed][k]] = true;
            pool.add(nb[seed][k]);
        }
        for (Route r : s.routes) {
            int[] keep = java.util.Arrays.stream(r.toArray()).filter(v -> !removed[v]).toArray();
            if (keep.length != r.size()) r.replace(keep, p);
        }
        s.removeEmptyRoutes();

        java.util.Collections.shuffle(pool, rnd);
        switch (rnd.nextInt(3)) {
            // Largest demand first packs vehicles tightly; the sort is stable, so ties stay shuffled.
            case 0 -> pool.sort((a, b) -> Integer.compare(p.demand[b], p.demand[a]));
            // Farthest from the depot first: those customers fix the shape of the routes.
            case 1 -> pool.sort((a, b) -> Double.compare(p.dist[0][b], p.dist[0][a]));
            default -> { } // random order
        }
        for (int u : pool) insertCheapest(s, u);
        s.recomputeCost();
    }

    private void insertCheapest(WorkingSolution s, int u) {
        Route best = null;
        int bestPos = -1;
        double bestDelta = 2 * p.dist[0][u]; // cost of a new route (0,u,0)
        for (Route r : s.routes) {
            if (r.load() + p.demand[u] > p.capacity) continue;
            for (int pos = 0; pos <= r.size(); pos++) {
                int a = pos == 0 ? 0 : r.get(pos - 1);
                int b = pos == r.size() ? 0 : r.get(pos);
                double delta = p.dist[a][u] + p.dist[u][b] - p.dist[a][b];
                if (delta < bestDelta - LocalSearch.EPS) {
                    bestDelta = delta;
                    best = r;
                    bestPos = pos;
                }
            }
        }
        if (best == null) {
            Route nr = new Route();
            nr.insert(0, u, p);
            s.routes.add(nr);
        } else {
            best.insert(bestPos, u, p);
        }
    }
}

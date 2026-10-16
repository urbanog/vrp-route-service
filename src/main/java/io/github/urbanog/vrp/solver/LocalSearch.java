package io.github.urbanog.vrp.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * First-improvement local search over four classical neighbourhoods, restricted to a granular
 * neighbourhood (each customer {@code u} is only combined with its {@code k} nearest customers
 * {@code v}).
 *
 * <ul>
 *   <li><b>2-opt</b> (intra-route): reverse a segment so that {@code u} and {@code v} become
 *       adjacent.
 *   <li><b>Relocate</b> (intra- and inter-route): move {@code u} directly after or before {@code
 *       v}; also move {@code u} into a new route of its own.
 *   <li><b>Swap</b> (inter-route): exchange {@code u} and {@code v}.
 *   <li><b>2-opt*</b> (inter-route tail exchange, a cross-exchange of route suffixes): cut the route
 *       of {@code u} after {@code u} and the route of {@code v} before {@code v}, and reconnect so
 *       that {@code u} is followed by {@code v}.
 * </ul>
 *
 * <p>Every move is evaluated in {@code O(1)} from the distance matrix (2-opt* needs prefix loads,
 * {@code O(route length)}) and is only applied if it is capacity-feasible and decreases the cost
 * by more than {@link #EPS}. The cost strictly decreases at every applied move and there are
 * finitely many solutions, so the search terminates in a local optimum with respect to all four
 * neighbourhoods (restricted to the granular neighbour lists).
 */
final class LocalSearch {
    static final double EPS = 1e-9;

    private final Problem p;
    private final int[][] nb;
    private final Random rnd;
    /** routeOf[v] = index into routes; posOf[v] = position inside that route. */
    private int[] routeOf;
    private int[] posOf;
    private List<Route> routes;
    private long movesApplied;

    LocalSearch(Problem p, int neighbourhoodSize, Random rnd) {
        this.p = p;
        this.nb = p.nearestNeighbours(neighbourhoodSize);
        this.rnd = rnd;
    }

    long movesApplied() {
        return movesApplied;
    }

    /** Improves {@code s} in place until no improving move exists. */
    void run(WorkingSolution s) {
        routes = s.routes;
        routeOf = new int[p.n + 1];
        posOf = new int[p.n + 1];
        for (int r = 0; r < routes.size(); r++) reindex(r);

        int[] order = new int[p.n];
        for (int i = 0; i < p.n; i++) order[i] = i + 1;

        boolean improved = true;
        while (improved) {
            if (Thread.currentThread().isInterrupted()) break;
            improved = false;
            shuffle(order);
            for (int u : order) {
                if (tryMovesFor(u)) {
                    improved = true;
                }
            }
        }
        s.removeEmptyRoutes();
        s.recomputeCost();
    }

    private boolean tryMovesFor(int u) {
        boolean any = false;
        boolean again = true;
        while (again) {
            again = false;
            for (int v : nb[u]) {
                if (routeOf[u] == routeOf[v]) {
                    if (twoOpt(u, v) || relocate(u, v, true) || relocate(u, v, false)) {
                        again = true;
                    }
                } else if (relocate(u, v, true) || relocate(u, v, false) || swap(u, v)
                        || twoOptStar(u, v)) {
                    again = true;
                }
                if (again) {
                    any = true;
                    movesApplied++;
                    break;
                }
            }
            if (!again && relocateToNewRoute(u)) {
                any = true;
                again = true;
                movesApplied++;
            }
        }
        return any;
    }

    // ------------------------------------------------------------------ moves

    /** Intra-route 2-opt making u and v adjacent: reverse the segment strictly after the first. */
    private boolean twoOpt(int u, int v) {
        Route r = routes.get(routeOf[u]);
        int i = posOf[u];
        int j = posOf[v];
        if (i > j) {
            int t = i;
            i = j;
            j = t;
        }
        if (j == i + 1) return false; // already adjacent
        int a = r.get(i);
        int an = r.get(i + 1);
        int b = r.get(j);
        int bn = r.next(j);
        // Variant 1: edges (a,an),(b,bn) -> (a,b),(an,bn); reverse positions i+1..j.
        double delta1 = p.dist[a][b] + p.dist[an][bn] - p.dist[a][an] - p.dist[b][bn];
        // Variant 2: edges (ap,a),(bp,b) -> (ap,bp),(a,b); reverse positions i..j-1.
        int ap = r.prev(i);
        int bp = r.get(j - 1);
        double delta2 = p.dist[ap][bp] + p.dist[a][b] - p.dist[ap][a] - p.dist[bp][b];
        if (delta1 <= delta2 && delta1 < -EPS) {
            r.reverse(i + 1, j);
        } else if (delta2 < -EPS) {
            r.reverse(i, j - 1);
        } else {
            return false;
        }
        reindex(routeOf[u]);
        return true;
    }

    /** Move u to directly after v ({@code after = true}) or directly before v. */
    private boolean relocate(int u, int v, boolean after) {
        int ru = routeOf[u];
        int rv = routeOf[v];
        Route from = routes.get(ru);
        Route to = routes.get(rv);
        if (ru != rv && to.load() + p.demand[u] > p.capacity) return false;
        int iu = posOf[u];
        int iv = posOf[v];
        int up = from.prev(iu);
        int un = from.next(iu);
        int a;
        int b;
        if (after) {
            a = v;
            b = to.next(iv);
        } else {
            a = to.prev(iv);
            b = v;
        }
        if (a == u || b == u) return false; // u already in that slot
        double removeGain = p.dist[up][u] + p.dist[u][un] - p.dist[up][un];
        double insertCost = p.dist[a][u] + p.dist[u][b] - p.dist[a][b];
        double delta = insertCost - removeGain;
        if (delta >= -EPS) return false;
        from.remove(iu, p);
        // Recompute v's position: it shifts left if it was after u in the same route.
        int jv = (ru == rv && iv > iu) ? iv - 1 : iv;
        to.insert(after ? jv + 1 : jv, u, p);
        reindex(ru);
        if (rv != ru) reindex(rv);
        return true;
    }

    /** Move u into a fresh route {@code (0, u, 0)} if that is cheaper. */
    private boolean relocateToNewRoute(int u) {
        Route from = routes.get(routeOf[u]);
        if (from.size() == 1) return false;
        int iu = posOf[u];
        int up = from.prev(iu);
        int un = from.next(iu);
        double delta = 2 * p.dist[0][u] - (p.dist[up][u] + p.dist[u][un] - p.dist[up][un]);
        if (delta >= -EPS) return false;
        int ru = routeOf[u];
        from.remove(iu, p);
        Route nr = new Route();
        nr.insert(0, u, p);
        routes.add(nr);
        reindex(ru);
        reindex(routes.size() - 1);
        return true;
    }

    /** Exchange u and v, which lie in different routes. */
    private boolean swap(int u, int v) {
        int ru = routeOf[u];
        int rv = routeOf[v];
        Route a = routes.get(ru);
        Route b = routes.get(rv);
        int du = p.demand[u];
        int dv = p.demand[v];
        if (a.load() - du + dv > p.capacity || b.load() - dv + du > p.capacity) return false;
        int iu = posOf[u];
        int iv = posOf[v];
        int up = a.prev(iu);
        int un = a.next(iu);
        int vp = b.prev(iv);
        int vn = b.next(iv);
        double delta = p.dist[up][v] + p.dist[v][un] - p.dist[up][u] - p.dist[u][un]
                + p.dist[vp][u] + p.dist[u][vn] - p.dist[vp][v] - p.dist[v][vn];
        if (delta >= -EPS) return false;
        a.set(iu, v, p);
        b.set(iv, u, p);
        reindex(ru);
        reindex(rv);
        return true;
    }

    /**
     * 2-opt*: with A = (A1 | A2) cut after u and B = (B1 | B2) cut before v, form A' = A1 + B2 and
     * B' = B1 + A2. Afterwards u is followed by v.
     */
    private boolean twoOptStar(int u, int v) {
        int ru = routeOf[u];
        int rv = routeOf[v];
        Route a = routes.get(ru);
        Route b = routes.get(rv);
        int iu = posOf[u];
        int iv = posOf[v];
        int un = a.next(iu);
        int vp = b.prev(iv);
        double delta = p.dist[u][v] + p.dist[vp][un] - p.dist[u][un] - p.dist[vp][v];
        if (delta >= -EPS) return false;
        int loadA1 = a.prefixLoad(iu, p);
        int loadB1 = b.prefixLoad(iv - 1, p);
        int loadA2 = a.load() - loadA1;
        int loadB2 = b.load() - loadB1;
        if (loadA1 + loadB2 > p.capacity || loadB1 + loadA2 > p.capacity) return false;
        int[] arrA = a.toArray();
        int[] arrB = b.toArray();
        int[] newA = new int[(iu + 1) + (arrB.length - iv)];
        System.arraycopy(arrA, 0, newA, 0, iu + 1);
        System.arraycopy(arrB, iv, newA, iu + 1, arrB.length - iv);
        int[] newB = new int[iv + (arrA.length - iu - 1)];
        System.arraycopy(arrB, 0, newB, 0, iv);
        System.arraycopy(arrA, iu + 1, newB, iv, arrA.length - iu - 1);
        a.replace(newA, p);
        b.replace(newB, p);
        reindex(ru);
        reindex(rv);
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private void reindex(int r) {
        Route route = routes.get(r);
        for (int i = 0; i < route.size(); i++) {
            int v = route.get(i);
            routeOf[v] = r;
            posOf[v] = i;
        }
    }

    private void shuffle(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    /** Neighbourhood names, for documentation and statistics. */
    static List<String> neighbourhoods() {
        return new ArrayList<>(List.of("2-opt", "relocate", "swap", "2-opt*"));
    }
}

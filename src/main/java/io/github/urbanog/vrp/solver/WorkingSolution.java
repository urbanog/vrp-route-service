package io.github.urbanog.vrp.solver;

import io.github.urbanog.vrp.model.Solution;
import java.util.ArrayList;
import java.util.List;

/** Mutable solution used inside the algorithms: a list of routes plus the cached total cost. */
final class WorkingSolution {
    final Problem p;
    final List<Route> routes;
    double cost;

    WorkingSolution(Problem p, List<Route> routes) {
        this.p = p;
        this.routes = new ArrayList<>(routes);
        recomputeCost();
    }

    WorkingSolution copy() {
        List<Route> rs = new ArrayList<>(routes.size());
        for (Route r : routes) rs.add(r.copy());
        WorkingSolution w = new WorkingSolution(p, rs);
        w.cost = cost;
        return w;
    }

    void recomputeCost() {
        double c = 0;
        for (Route r : routes) c += r.cost(p);
        cost = c;
    }

    void removeEmptyRoutes() {
        routes.removeIf(Route::isEmpty);
    }

    /** Converts to the public model, translating node indices to external customer ids. */
    Solution toSolution() {
        List<Solution.Route> out = new ArrayList<>();
        double total = 0;
        for (Route r : routes) {
            if (r.isEmpty()) continue;
            List<Integer> ids = new ArrayList<>(r.size());
            for (int i = 0; i < r.size(); i++) ids.add(p.externalId[r.get(i)]);
            double c = r.cost(p);
            total += c;
            out.add(new Solution.Route(ids, r.load(), c));
        }
        return new Solution(out, total);
    }
}

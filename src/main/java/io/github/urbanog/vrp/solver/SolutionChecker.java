package io.github.urbanog.vrp.solver;

import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import io.github.urbanog.vrp.model.Solution;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent feasibility checker: recomputes everything from the instance, sharing no code with
 * the algorithms, so it can be used as an oracle in tests and as a safety net before responses
 * are returned.
 */
public final class SolutionChecker {
    private SolutionChecker() {}

    /** Returns a list of human-readable problems; empty iff the solution is feasible and consistent. */
    public static List<String> check(Instance inst, Solution sol) {
        List<String> errors = new ArrayList<>();
        Map<Integer, Customer> byId = new HashMap<>();
        for (Customer c : inst.customers()) byId.put(c.id(), c);
        Set<Integer> seen = new HashSet<>();
        double total = 0;
        for (int r = 0; r < sol.routes().size(); r++) {
            Solution.Route route = sol.routes().get(r);
            if (route.customerIds().isEmpty()) errors.add("route " + r + " is empty");
            int load = 0;
            double cost = 0;
            Point prev = inst.depot();
            for (int id : route.customerIds()) {
                Customer c = byId.get(id);
                if (c == null) {
                    errors.add("route " + r + " visits unknown customer " + id);
                    continue;
                }
                if (!seen.add(id)) errors.add("customer " + id + " visited more than once");
                load += c.demand();
                cost += inst.distanceType().cost(prev, c.location());
                prev = c.location();
            }
            cost += inst.distanceType().cost(prev, inst.depot());
            if (load > inst.capacity()) {
                errors.add("route " + r + " load " + load + " exceeds capacity " + inst.capacity());
            }
            if (load != route.load()) errors.add("route " + r + " reports load " + route.load() + " but has " + load);
            if (Math.abs(cost - route.cost()) > 1e-6 * Math.max(1, cost)) {
                errors.add("route " + r + " reports cost " + route.cost() + " but has " + cost);
            }
            total += cost;
        }
        for (Customer c : inst.customers()) {
            if (!seen.contains(c.id())) errors.add("customer " + c.id() + " is not served");
        }
        if (Math.abs(total - sol.cost()) > 1e-6 * Math.max(1, total)) {
            errors.add("solution reports cost " + sol.cost() + " but routes sum to " + total);
        }
        return errors;
    }
}

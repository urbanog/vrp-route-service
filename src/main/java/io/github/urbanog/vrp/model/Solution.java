package io.github.urbanog.vrp.model;

import java.util.List;

/**
 * A solution expressed in external customer ids: each route starts and ends at the depot
 * (implicit) and visits {@code customerIds} in order.
 */
public record Solution(List<Route> routes, double cost) {
    public Solution {
        routes = List.copyOf(routes);
    }

    public record Route(List<Integer> customerIds, int load, double cost) {
        public Route {
            customerIds = List.copyOf(customerIds);
        }
    }
}

package io.github.urbanog.vrp.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A Capacitated Vehicle Routing Problem instance: one depot, an unlimited homogeneous fleet of
 * vehicles with capacity {@code capacity}, and customers with integer demands.
 *
 * <p>The canonical constructor only enforces structural invariants (non-null fields). Semantic
 * validation, which an API must report back to the client field by field, lives in {@link
 * #validate()}.
 */
public record Instance(
        String name, int capacity, DistanceType distanceType, Point depot, List<Customer> customers) {

    /** Hard upper bound on instance size accepted by the service. */
    public static final int MAX_CUSTOMERS = 2000;

    public Instance {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(distanceType, "distanceType");
        Objects.requireNonNull(depot, "depot");
        customers = List.copyOf(customers);
    }

    public int size() {
        return customers.size();
    }

    public long totalDemand() {
        long s = 0;
        for (Customer c : customers) s += c.demand();
        return s;
    }

    /** Trivial lower bound on the number of vehicles: ceil(total demand / capacity). */
    public int minVehicles() {
        return capacity <= 0 ? 0 : (int) ((totalDemand() + capacity - 1) / capacity);
    }

    /** A validation problem tied to a JSON-pointer-like field path. */
    public record Violation(String field, String message) {}

    /** Returns every semantic problem with the instance; empty means the instance is solvable. */
    public List<Violation> validate() {
        List<Violation> v = new ArrayList<>();
        if (name.isBlank()) v.add(new Violation("name", "must not be blank"));
        if (capacity <= 0) v.add(new Violation("capacity", "must be a positive integer"));
        if (customers.isEmpty()) v.add(new Violation("customers", "must contain at least one customer"));
        if (customers.size() > MAX_CUSTOMERS) {
            v.add(new Violation("customers", "at most " + MAX_CUSTOMERS + " customers are supported"));
        }
        Set<Integer> ids = new HashSet<>();
        for (int i = 0; i < customers.size(); i++) {
            Customer c = customers.get(i);
            String f = "customers[" + i + "]";
            if (!ids.add(c.id())) v.add(new Violation(f + ".id", "duplicate id " + c.id()));
            if (c.demand() < 0) v.add(new Violation(f + ".demand", "must be non-negative"));
            if (capacity > 0 && c.demand() > capacity) {
                v.add(new Violation(f + ".demand",
                        "demand " + c.demand() + " exceeds vehicle capacity " + capacity));
            }
        }
        return v;
    }
}

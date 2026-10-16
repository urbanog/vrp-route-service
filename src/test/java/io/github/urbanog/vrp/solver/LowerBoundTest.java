package io.github.urbanog.vrp.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import java.util.List;
import org.junit.jupiter.api.Test;

class LowerBoundTest {

    @Test
    void neverExceedsTheOptimum() {
        for (int seed = 0; seed < 60; seed++) {
            Instance inst = InstanceGenerator.generate("lb", 4 + seed % 7, 1 + seed % 5,
                    seed % 3 == 0 ? InstanceGenerator.Layout.CLUSTERED : InstanceGenerator.Layout.UNIFORM, seed);
            Problem p = new Problem(inst);
            assertTrue(LowerBound.compute(p) <= ExactSolver.solve(p).cost + 1e-9, "seed " + seed);
        }
    }

    @Test
    void matchesHandComputation() {
        // Collinear customers that each fill a vehicle: optimum = 2*(5+10) = 30.
        // MST = 10 (depot-a-b chain), K = 2, two cheapest depot edges = 5 + 10, so LB = 25.
        Instance inst = new Instance("t", 1, DistanceType.EUCLIDEAN, new Point(0, 0),
                List.of(new Customer(1, new Point(5, 0), 1), new Customer(2, new Point(10, 0), 1)));
        assertEquals(25.0, LowerBound.compute(new Problem(inst)), 1e-12);
    }
}

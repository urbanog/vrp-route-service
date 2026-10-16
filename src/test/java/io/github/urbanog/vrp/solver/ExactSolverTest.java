package io.github.urbanog.vrp.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.model.Instance;
import org.junit.jupiter.api.Test;

class ExactSolverTest {

    /** The subset DP agrees with an independent permutation + split enumeration. */
    @Test
    void matchesBruteForceOnRandomInstances() {
        for (int seed = 0; seed < 40; seed++) {
            int n = 3 + seed % 5; // 3..7 customers
            var layout = seed % 2 == 0 ? InstanceGenerator.Layout.UNIFORM : InstanceGenerator.Layout.CLUSTERED;
            Instance inst = InstanceGenerator.generate("bf", n, 1 + seed % 4, layout, seed);
            Problem p = new Problem(inst);
            WorkingSolution exact = ExactSolver.solve(p);
            assertEquals(BruteForce.optimum(p), exact.cost, 1e-9, "seed " + seed);
            assertTrue(SolutionChecker.check(inst, exact.toSolution()).isEmpty());
        }
    }

    @Test
    void handlesMaximumSize() {
        Instance inst = InstanceGenerator.generate("max", ExactSolver.MAX_CUSTOMERS, 4, InstanceGenerator.Layout.UNIFORM, 5);
        WorkingSolution s = ExactSolver.solve(new Problem(inst));
        assertTrue(SolutionChecker.check(inst, s.toSolution()).isEmpty());
    }

    @Test
    void refusesLargeInstances() {
        Instance inst = InstanceGenerator.generate("big", ExactSolver.MAX_CUSTOMERS + 1, 4, InstanceGenerator.Layout.UNIFORM, 5);
        assertThrows(IllegalArgumentException.class, () -> ExactSolver.solve(new Problem(inst)));
    }
}

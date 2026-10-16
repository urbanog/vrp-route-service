package io.github.urbanog.vrp.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.model.Customer;
import io.github.urbanog.vrp.model.DistanceType;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.model.Point;
import io.github.urbanog.vrp.model.Solution;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Property-based tests: on many random instances every algorithm must return a feasible solution
 * (every customer exactly once, capacity respected, reported loads and costs correct), and the
 * costs must be ordered {@code LB <= exact <= annealing <= local search <= savings}.
 */
class SolverPropertiesTest {
    private final CvrpSolver solver = new CvrpSolver();

    private static SolveOptions opts(Algorithm a) {
        return SolveOptions.defaults().withAlgorithm(a).withTimeLimit(60_000).withMaxIterations(60);
    }

    private static Instance random(Random rnd, int n) {
        int capacity = 5 + rnd.nextInt(40);
        List<Customer> cs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            // Demands up to the capacity, including 0 and exactly Q, to exercise edge cases.
            int q = switch (rnd.nextInt(10)) {
                case 0 -> 0;
                case 1 -> capacity;
                default -> 1 + rnd.nextInt(Math.max(1, capacity / 2));
            };
            cs.add(new Customer(100 + i, new Point(rnd.nextInt(200), rnd.nextInt(200)), q));
        }
        DistanceType dt = rnd.nextBoolean() ? DistanceType.EUCLIDEAN : DistanceType.EUCLIDEAN_ROUNDED;
        return new Instance("rnd", capacity, dt, new Point(rnd.nextInt(200), rnd.nextInt(200)), cs);
    }

    private static void assertFeasible(Instance inst, SolveResult r) {
        List<String> errs = SolutionChecker.check(inst, r.solution());
        assertTrue(errs.isEmpty(), () -> r.algorithm() + " on " + inst + ": " + errs);
    }

    @Test
    void allAlgorithmsAreFeasibleAndOrderedOnSmallInstances() {
        Random rnd = new Random(2024);
        for (int t = 0; t < 120; t++) {
            Instance inst = random(rnd, 1 + rnd.nextInt(ExactSolver.MAX_CUSTOMERS - 2));
            SolveResult cw = solver.solve(inst, opts(Algorithm.SAVINGS));
            SolveResult ls = solver.solve(inst, opts(Algorithm.LOCAL_SEARCH));
            SolveResult sa = solver.solve(inst, opts(Algorithm.ANNEALING));
            SolveResult ex = solver.solve(inst, opts(Algorithm.EXACT));
            for (SolveResult r : List.of(cw, ls, sa, ex)) assertFeasible(inst, r);
            double eps = 1e-6;
            assertTrue(cw.lowerBound() <= ex.solution().cost() + eps, "lower bound exceeds optimum");
            assertTrue(ex.solution().cost() <= sa.solution().cost() + eps, "heuristic beat the optimum");
            assertTrue(sa.solution().cost() <= ls.solution().cost() + eps, "annealing worse than its start");
            assertTrue(ls.solution().cost() <= cw.solution().cost() + eps, "local search worsened savings");
            assertTrue(ex.optimal());
        }
    }

    @Test
    void largerInstancesStayFeasible() {
        Random rnd = new Random(99);
        for (int t = 0; t < 25; t++) {
            Instance inst = random(rnd, 20 + rnd.nextInt(120));
            for (Algorithm a : List.of(Algorithm.SAVINGS, Algorithm.LOCAL_SEARCH, Algorithm.ANNEALING)) {
                SolveResult r = solver.solve(inst, opts(a));
                assertFeasible(inst, r);
                assertTrue(r.lowerBound() <= r.solution().cost() + 1e-6);
                assertTrue(r.solution().routes().size() >= inst.minVehicles());
            }
        }
    }

    @Test
    void sameSeedAndIterationCapGiveIdenticalResults() {
        Instance inst = InstanceGenerator.generate("det", 60, 8, InstanceGenerator.Layout.CLUSTERED, 11);
        SolveOptions o = SolveOptions.defaults().withTimeLimit(60_000).withMaxIterations(150).withSeed(5);
        Solution a = solver.solve(inst, o).solution();
        Solution b = solver.solve(inst, o).solution();
        assertEquals(a, b);
    }

    @Test
    void localSearchIsIdempotent() {
        Instance inst = InstanceGenerator.generate("ls", 80, 10, InstanceGenerator.Layout.UNIFORM, 3);
        Problem p = new Problem(inst);
        WorkingSolution s = ClarkeWright.solve(p);
        new LocalSearch(p, 20, new Random(1)).run(s);
        double c1 = s.cost;
        LocalSearch again = new LocalSearch(p, 20, new Random(2));
        again.run(s);
        assertEquals(c1, s.cost, 1e-9);
        assertEquals(0, again.movesApplied());
    }

    @Test
    void savingsOnAHandComputedExample() {
        // Two customers on each side of the depot on a line. Same-side savings are positive
        // (s = 10 + 20 - 10 = 20), cross-side savings are zero, so Clarke–Wright builds
        // one route per side: 2 * 20 + 2 * 20 = 80.
        Instance inst = new Instance("line", 100, DistanceType.EUCLIDEAN, new Point(0, 0), List.of(
                new Customer(1, new Point(10, 0), 1), new Customer(2, new Point(20, 0), 1),
                new Customer(3, new Point(-10, 0), 1), new Customer(4, new Point(-20, 0), 1)));
        SolveResult r = solver.solve(inst, opts(Algorithm.SAVINGS));
        assertEquals(80.0, r.solution().cost(), 1e-9);
        assertEquals(2, r.solution().routes().size());
        assertEquals(80.0, solver.solve(inst, opts(Algorithm.EXACT)).solution().cost(), 1e-9);
    }

    @Test
    void capacityForcesSeparateRoutes() {
        // Every demand equals the capacity, so the only feasible solution is n out-and-back trips.
        List<Customer> cs = new ArrayList<>();
        double expected = 0;
        for (int i = 1; i <= 6; i++) {
            Point pt = new Point(i * 3, i * 4); // distance 5i from the depot
            cs.add(new Customer(i, pt, 7));
            expected += 2 * 5 * i;
        }
        Instance inst = new Instance("full", 7, DistanceType.EUCLIDEAN, new Point(0, 0), cs);
        for (Algorithm a : Algorithm.values()) {
            SolveResult r = solver.solve(inst, opts(a));
            assertEquals(6, r.solution().routes().size(), a.name());
            assertEquals(expected, r.solution().cost(), 1e-9, a.name());
        }
    }

    @Test
    void singleCustomerAndCoincidentPoints() {
        Instance one = new Instance("one", 5, DistanceType.EUCLIDEAN, new Point(0, 0),
                List.of(new Customer(7, new Point(3, 4), 5)));
        for (Algorithm a : Algorithm.values()) {
            SolveResult r = solver.solve(one, opts(a));
            assertEquals(10.0, r.solution().cost(), 1e-9);
            assertEquals(List.of(7), r.solution().routes().get(0).customerIds());
        }
        List<Customer> same = new ArrayList<>();
        for (int i = 0; i < 9; i++) same.add(new Customer(i, new Point(1, 1), 1));
        Instance stacked = new Instance("stacked", 4, DistanceType.EUCLIDEAN, new Point(0, 0), same);
        for (Algorithm a : Algorithm.values()) assertFeasible(stacked, solver.solve(stacked, opts(a)));
    }
}

package io.github.urbanog.vrp.solver;

import io.github.urbanog.vrp.model.Instance;
import java.util.Random;

/** Entry point of the optimisation layer: runs the requested pipeline on a validated instance. */
public final class CvrpSolver {

    public SolveResult solve(Instance instance, SolveOptions options) {
        var violations = instance.validate();
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("invalid instance: " + violations);
        }
        long start = System.nanoTime();
        Problem p = new Problem(instance);
        Random rnd = new Random(options.seed());
        double lb = LowerBound.compute(p);

        WorkingSolution initial = ClarkeWright.solve(p);
        double initialCost = initial.cost;
        WorkingSolution result;
        long iterations = 0;
        boolean optimal = false;

        switch (options.algorithm()) {
            case SAVINGS -> result = initial;
            case LOCAL_SEARCH -> {
                result = initial.copy();
                new LocalSearch(p, 20, rnd).run(result);
            }
            case ANNEALING -> {
                var r = SimulatedAnnealing.run(
                        initial, options.timeLimitMillis() * 1_000_000L, options.maxIterations(), rnd);
                result = r.best();
                iterations = r.iterations();
            }
            case EXACT -> {
                if (p.n > ExactSolver.MAX_CUSTOMERS) {
                    throw new IllegalArgumentException(
                            "algorithm 'exact' supports at most " + ExactSolver.MAX_CUSTOMERS + " customers");
                }
                result = ExactSolver.solve(p);
                optimal = true;
            }
            default -> throw new AssertionError(options.algorithm());
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        var solution = result.toSolution();
        // An optimal solution is itself a lower bound; report the larger of the two.
        double bound = optimal ? Math.max(lb, solution.cost()) : lb;
        return new SolveResult(solution, options.algorithm(), bound, initialCost, ms, iterations, optimal);
    }
}

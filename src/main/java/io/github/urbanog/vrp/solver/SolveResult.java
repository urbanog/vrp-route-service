package io.github.urbanog.vrp.solver;

import io.github.urbanog.vrp.model.Solution;

/**
 * Result of a solver run.
 *
 * @param solution routes in external ids and total cost
 * @param lowerBound valid lower bound on the optimum (see {@link LowerBound})
 * @param initialCost cost of the Clarke–Wright start solution
 * @param runtimeMillis wall-clock time of the whole pipeline
 * @param iterations metaheuristic iterations (0 if not applicable)
 * @param optimal true iff the exact solver proved optimality
 */
public record SolveResult(
        Solution solution,
        Algorithm algorithm,
        double lowerBound,
        double initialCost,
        long runtimeMillis,
        long iterations,
        boolean optimal) {

    /** Relative gap to the lower bound, {@code (cost - LB) / cost}. */
    public double gapToLowerBound() {
        double c = solution.cost();
        return c <= 0 ? 0 : (c - lowerBound) / c;
    }
}

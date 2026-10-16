package io.github.urbanog.vrp.solver;

import java.util.Random;

/**
 * Iterated local search whose acceptance criterion is simulated annealing.
 *
 * <p>One iteration: copy the current solution, perturb it with {@link RuinRecreate}, repair it to
 * a local optimum with {@link LocalSearch}, and accept the candidate with the Metropolis rule
 *
 * <pre>
 *   accept  iff  c(candidate) &lt; c(current)  or  U &lt; exp(-(c(candidate) - c(current)) / T),
 * </pre>
 *
 * where {@code U ~ Uniform(0,1)}. The temperature decreases geometrically in the <em>progress</em>
 * {@code f ∈ [0,1)}, {@code T = T0 * (T1/T0)^f}, where {@code f} is the larger of the elapsed
 * fraction of the time limit and of the iteration cap. The schedule therefore adapts to however
 * many iterations fit in the budget. {@code T0} is set so that a candidate 2% worse than the start is
 * accepted with probability 1/2, and {@code T1 = T0 / 1000}. The best solution ever seen is
 * returned.
 */
final class SimulatedAnnealing {
    record Result(WorkingSolution best, long iterations, long accepted, long improvements) {}

    private SimulatedAnnealing() {}

    static Result run(WorkingSolution start, long timeLimitNanos, long maxIterations, Random rnd) {
        Problem p = start.p;
        LocalSearch ls = new LocalSearch(p, 20, rnd);
        RuinRecreate rr = new RuinRecreate(p, rnd);

        WorkingSolution current = start.copy();
        ls.run(current);
        WorkingSolution best = current.copy();

        double t0 = Math.max(1e-9, 0.02 * current.cost / Math.log(2));
        double t1 = t0 / 1000.0;
        int qMax = Math.max(2, Math.min(p.n, Math.max(5, p.n / 5)));

        long begin = System.nanoTime();
        long it = 0;
        long accepted = 0;
        long improvements = 0;
        while (it < maxIterations) {
            long elapsed = System.nanoTime() - begin;
            if (elapsed >= timeLimitNanos || Thread.currentThread().isInterrupted()) break;
            // Progress in [0,1): whichever budget (time or iterations) is closer to exhaustion.
            double frac = Math.max((double) elapsed / timeLimitNanos,
                    maxIterations == Long.MAX_VALUE ? 0.0 : (double) it / maxIterations);
            it++;
            double temp = t0 * Math.pow(t1 / t0, frac);

            WorkingSolution cand = current.copy();
            rr.apply(cand, 2 + rnd.nextInt(qMax - 1));
            ls.run(cand);

            double delta = cand.cost - current.cost;
            if (delta < 0 || rnd.nextDouble() < Math.exp(-delta / temp)) {
                current = cand;
                accepted++;
                if (current.cost < best.cost - LocalSearch.EPS) {
                    best = current.copy();
                    improvements++;
                }
            }
        }
        return new Result(best, it, accepted, improvements);
    }
}

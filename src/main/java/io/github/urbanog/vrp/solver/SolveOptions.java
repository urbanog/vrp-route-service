package io.github.urbanog.vrp.solver;

/**
 * Solver options.
 *
 * @param algorithm pipeline to run
 * @param timeLimitMillis wall-clock budget for {@link Algorithm#ANNEALING}
 * @param maxIterations iteration cap for annealing (useful for reproducible runs)
 * @param seed random seed; equal seeds and iteration caps give identical results
 */
public record SolveOptions(Algorithm algorithm, long timeLimitMillis, long maxIterations, long seed) {
    public static final long MAX_TIME_LIMIT_MILLIS = 60_000;

    public SolveOptions {
        if (timeLimitMillis < 1 || timeLimitMillis > MAX_TIME_LIMIT_MILLIS) {
            throw new IllegalArgumentException("timeLimitMillis must be in [1, " + MAX_TIME_LIMIT_MILLIS + "]");
        }
        if (maxIterations < 0) throw new IllegalArgumentException("maxIterations must be >= 0");
    }

    public static SolveOptions defaults() {
        return new SolveOptions(Algorithm.ANNEALING, 2_000, Long.MAX_VALUE, 42);
    }

    public SolveOptions withAlgorithm(Algorithm a) {
        return new SolveOptions(a, timeLimitMillis, maxIterations, seed);
    }

    public SolveOptions withTimeLimit(long ms) {
        return new SolveOptions(algorithm, ms, maxIterations, seed);
    }

    public SolveOptions withMaxIterations(long it) {
        return new SolveOptions(algorithm, timeLimitMillis, it, seed);
    }

    public SolveOptions withSeed(long s) {
        return new SolveOptions(algorithm, timeLimitMillis, maxIterations, s);
    }
}

package io.github.urbanog.vrp.solver;

import java.util.Locale;
import java.util.Optional;

/** Solver pipelines exposed by the API. */
public enum Algorithm {
    /** Clarke–Wright savings only. */
    SAVINGS,
    /** Clarke–Wright followed by local search to a local optimum. */
    LOCAL_SEARCH,
    /** Clarke–Wright, local search, then simulated-annealing ILS until the time limit. */
    ANNEALING,
    /** Subset dynamic programming; optimal, only for n ≤ {@value ExactSolver#MAX_CUSTOMERS}. */
    EXACT;

    public String wireName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optional<Algorithm> fromWireName(String s) {
        for (Algorithm a : values()) {
            if (a.wireName().equals(s)) return Optional.of(a);
        }
        return Optional.empty();
    }
}

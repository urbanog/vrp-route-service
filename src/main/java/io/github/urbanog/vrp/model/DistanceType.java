package io.github.urbanog.vrp.model;

/** How travel costs are derived from coordinates. */
public enum DistanceType {
    /** Exact Euclidean distance (a metric: satisfies the triangle inequality). */
    EUCLIDEAN,
    /**
     * Euclidean distance rounded to the nearest integer, the TSPLIB {@code EUC_2D} convention
     * used by CVRPLIB. Rounding can violate the triangle inequality by up to one unit.
     */
    EUCLIDEAN_ROUNDED;

    public double cost(Point a, Point b) {
        double d = a.distanceTo(b);
        return this == EUCLIDEAN ? d : Math.floor(d + 0.5); // TSPLIB nint()
    }
}

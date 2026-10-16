package io.github.urbanog.vrp.model;

/** A location in the plane. */
public record Point(double x, double y) {
    public Point {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }

    public double distanceTo(Point o) {
        return Math.hypot(x - o.x, y - o.y);
    }
}

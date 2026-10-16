package io.github.urbanog.vrp.model;

/**
 * A customer with an external identifier, a location and a non-negative integer demand.
 *
 * @param id external identifier, unique within an instance (TSPLIB node number for files)
 */
public record Customer(int id, Point location, int demand) {}

package io.github.urbanog.vrp.solver;

import java.util.Arrays;

/**
 * A mutable route: a sequence of customer nodes (depot excluded) with a cached load.
 *
 * <p>Position {@code i} is the {@code i}-th visited customer; the implicit predecessor of position
 * {@code 0} and successor of position {@code size()-1} is the depot (node 0).
 */
final class Route {
    private int[] nodes;
    private int size;
    private int load;

    Route() {
        this.nodes = new int[8];
    }

    Route(int[] customers, Problem p) {
        this.nodes = Arrays.copyOf(customers, Math.max(8, customers.length));
        this.size = customers.length;
        for (int i = 0; i < size; i++) load += p.demand[nodes[i]];
    }

    Route copy() {
        Route r = new Route();
        r.nodes = Arrays.copyOf(nodes, nodes.length);
        r.size = size;
        r.load = load;
        return r;
    }

    int size() {
        return size;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int load() {
        return load;
    }

    int get(int i) {
        return nodes[i];
    }

    /** Node before position {@code i}, or the depot. */
    int prev(int i) {
        return i == 0 ? 0 : nodes[i - 1];
    }

    /** Node after position {@code i}, or the depot. */
    int next(int i) {
        return i == size - 1 ? 0 : nodes[i + 1];
    }

    void insert(int pos, int node, Problem p) {
        if (size == nodes.length) nodes = Arrays.copyOf(nodes, 2 * size);
        System.arraycopy(nodes, pos, nodes, pos + 1, size - pos);
        nodes[pos] = node;
        size++;
        load += p.demand[node];
    }

    int remove(int pos, Problem p) {
        int node = nodes[pos];
        System.arraycopy(nodes, pos + 1, nodes, pos, size - pos - 1);
        size--;
        load -= p.demand[node];
        return node;
    }

    void set(int pos, int node, Problem p) {
        load += p.demand[node] - p.demand[nodes[pos]];
        nodes[pos] = node;
    }

    /** Reverses positions {@code i..j} inclusive. */
    void reverse(int i, int j) {
        while (i < j) {
            int t = nodes[i];
            nodes[i++] = nodes[j];
            nodes[j--] = t;
        }
    }

    /** Sum of demands of positions {@code 0..i} inclusive ({@code i = -1} gives 0). */
    int prefixLoad(int i, Problem p) {
        int s = 0;
        for (int k = 0; k <= i; k++) s += p.demand[nodes[k]];
        return s;
    }

    int[] toArray() {
        return Arrays.copyOf(nodes, size);
    }

    /** Replaces the whole content of the route and recomputes the load. */
    void replace(int[] content, Problem p) {
        nodes = Arrays.copyOf(content, Math.max(8, content.length));
        size = content.length;
        load = 0;
        for (int i = 0; i < size; i++) load += p.demand[nodes[i]];
    }

    double cost(Problem p) {
        if (size == 0) return 0;
        double c = p.dist[0][nodes[0]] + p.dist[nodes[size - 1]][0];
        for (int i = 0; i + 1 < size; i++) c += p.dist[nodes[i]][nodes[i + 1]];
        return c;
    }

    @Override
    public String toString() {
        return Arrays.toString(toArray()) + " load=" + load;
    }
}

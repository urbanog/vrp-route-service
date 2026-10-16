# Algorithms

This note explains every algorithm in `src/main/java/io/github/urbanog/vrp/solver`, with the
argument for why each one is correct and what it costs.

Notation: node `0` is the depot, nodes `1..n` are customers, `d(i,j)` is the (symmetric) travel
cost, `q_i` the demand of customer `i`, `Q` the vehicle capacity. A route is a sequence
`0, v_1, ..., v_k, 0` with `q_{v_1} + ... + q_{v_k} ≤ Q`. A solution is a set of routes that
visits every customer exactly once, and its cost is the sum of the edge costs. The fleet is
unlimited and homogeneous, so the number of vehicles is part of the output.

The pipeline is

```
Clarke–Wright savings  ──►  local search  ──►  simulated-annealing ILS (until the time limit)
                                          (exact DP instead, for n ≤ 12)
```

and every result is checked by `SolutionChecker`, which recomputes loads and costs from the
instance without sharing code with the algorithms.

## 1. Clarke–Wright savings (`ClarkeWright`)

Start with `n` out-and-back routes `0, i, 0`. If the route ending at `i` is joined to the route
starting at `j`, the edges `(i,0)` and `(0,j)` are replaced by `(i,j)`, which changes the cost by
`-s(i,j)` with

```
s(i,j) = d(0,i) + d(0,j) − d(i,j).
```

All `n(n−1)/2` savings are sorted in decreasing order, with ties broken by `(i,j)` so the result is
deterministic. A merge is performed when

1. `i` and `j` are in different routes,
2. both are *endpoints* of their routes (an interior customer has no edge to the depot to remove),
3. the combined load is at most `Q`, and
4. `s(i,j) > 0`.

Because `d` is symmetric, a route may be reversed so that `i` is its last customer and `j` the first
customer of the other route. This is the *parallel* version: many routes grow at the same time.

**Cost.** `O(n² log n)` time for the sort, `O(n²)` memory. There are at most `n − 1` merges, each
`O(n)`.

**Weakness.** The rule is greedy and never undoes a merge. On the random instances in the README,
savings alone is on average 3.6–7.4% above the optimum, but up to 36% on unlucky instances. Those
are typically instances where demands are large compared with `Q`, so the greedy choice of which
customers share a vehicle (a bin-packing decision) is wrong.

## 2. Local search (`LocalSearch`)

Local search repeatedly applies a cost-decreasing move until none exists. Four neighbourhoods are
used. Each move is evaluated in `O(1)` from the distance matrix (2-opt* also needs prefix loads)
and applied only if it is feasible and decreases the cost by more than `ε = 10⁻⁹`.

Write `u⁻` and `u⁺` for the predecessor and successor of `u` in its route (possibly the depot).

### 2-opt (intra-route)

Remove two edges of one route and reconnect it by reversing the segment between them:

```
before:  ... a → a⁺ → ... → b → b⁺ ...
after:   ... a → b → ... → a⁺ → b⁺ ...      (segment a⁺..b reversed)
Δ = d(a,b) + d(a⁺,b⁺) − d(a,a⁺) − d(b,b⁺)
```

The load of the route does not change, so 2-opt is always capacity-feasible. Its classic use is
to remove crossings. If two edges `(a,a⁺)` and `(b,b⁺)` cross, the triangle inequality makes
`Δ < 0`. The implementation tries both variants that make the two chosen customers `u, v`
adjacent: reversing after `u`, or reversing up to `v`.

### Relocate (intra- and inter-route)

Remove `u` from its route and insert it next to `v`, either directly after or directly before it:

```
Δ = [d(a,u) + d(u,b) − d(a,b)]  −  [d(u⁻,u) + d(u,u⁺) − d(u⁻,u⁺)]
       insertion between a and b        removal gain
```

Inserting into another route requires `load(route of v) + q_u ≤ Q`. A special case moves `u` into
a brand-new route `0, u, 0`. This is the only move that can *increase* the number of vehicles,
which is occasionally what lowers the cost.

### Swap (inter-route)

Exchange `u` and `v` between their two routes. It is feasible iff
`load(A) − q_u + q_v ≤ Q` and `load(B) − q_v + q_u ≤ Q`. `Δ` is the sum of the two
replacement costs. Swap is the move relocate cannot imitate when both routes are full.

### 2-opt* (inter-route tail exchange)

Cut route `A` after `u` and route `B` before `v`, and swap the tails:

```
A = A₁ u | u⁺ A₂        A' = A₁ u  v B₂
B = B₁ v⁻ | v B₂   ⟹   B' = B₁ v⁻ u⁺ A₂
Δ = d(u,v) + d(v⁻,u⁺) − d(u,u⁺) − d(v⁻,v)
```

It is feasible iff `load(A₁) + load(B₂) ≤ Q` and `load(B₁) + load(A₂) ≤ Q`. This is the
inter-route analogue of 2-opt (it untangles crossings *between* routes) and a special case of
cross-exchange in which both exchanged segments run to the end of the route.

### Granular neighbourhoods and termination

Pairs `(u, v)` are only considered when `v` is among the 20 nearest customers of `u` (Toth &
Vigo's "granular" idea): good solutions rarely connect distant customers. One sweep then costs
`O(20 · n)` move evaluations instead of `O(n²)`. Customers are visited in a random order (seeded),
and the first improving move is applied.

The search **terminates**: every applied move lowers the cost by more than `ε`, and there are
finitely many solutions. It ends in a solution that is locally optimal for all four
neighbourhoods restricted to the neighbour lists. The test `localSearchIsIdempotent` checks
exactly this: a second run applies zero moves.

## 3. Simulated-annealing iterated local search (`SimulatedAnnealing`, `RuinRecreate`)

Local optima of the four neighbourhoods are still several percent above the optimum. The
metaheuristic escapes them by large, structured changes.

**Perturbation (ruin and recreate).** Pick a random seed customer and remove it together with its
`q − 1` nearest neighbours, with `q` uniform in `[2, max(5, n/5)]`. This frees a whole
geographic region. Re-insert the removed customers one by one at the cheapest feasible position
over all routes. The insertion order is chosen at random among largest demand first, farthest
from the depot first, and random order. A new route is opened only when that is cheaper than every
feasible insertion. The result is then improved by local search.

**Acceptance (Metropolis rule).** With `Δ = c(candidate) − c(current)`,

```
accept  ⇔  Δ < 0   or   U < exp(−Δ / T),   U ~ Uniform(0, 1).
```

Improvements are always accepted. A deterioration of `Δ` is accepted with probability
`exp(−Δ/T)`, so small deteriorations are often accepted while `T` is high and almost never
when it is low.

**Cooling schedule.** Let `f ∈ [0,1)` be the progress through the budget: the larger of the elapsed
fraction of the time limit and the fraction of the iteration cap. Then

```
T(f) = T₀ · (T₁ / T₀)^f,       T₀ = 0.02 · c(start) / ln 2,       T₁ = T₀ / 1000.
```

`T₀` is chosen so that a candidate 2% worse than the start solution is accepted with probability
½ at the beginning. At the end, the same deterioration is accepted with probability
`2^(−1000)`, which is effectively never, so the search becomes a pure descent. Because the
schedule is defined by *progress* rather than iteration count, it works the same with a 200 ms
or a 60 s budget. The best solution ever seen is returned, not the current one.

**Determinism.** All randomness comes from one seeded `java.util.Random`. With a fixed seed and an
iteration cap (`maxIterations`) the output is bit-for-bit reproducible. With only a time limit it
depends on machine speed.

**Cost.** One iteration costs `O(q·n)` for recreate plus the local search. In the benchmark run
that was roughly 140 000 iterations/s for `n = 10` and 1 800 iterations/s for `n = 200`.

## 4. Exact solver (`ExactSolver`)

Used as ground truth in tests and in the benchmark. It is a two-level dynamic program over subsets
`S ⊆ {1..n}` stored as bitmasks.

1. **Held–Karp.** `h(S, j)` is the cheapest path `0 → … → j` visiting exactly `S`:
   `h({j}, j) = d(0,j)` and `h(S ∪ {k}, k) = min_j h(S, j) + d(j, k)`. Then
   `tsp(S) = min_j h(S, j) + d(j, 0)` is the best single route serving `S`. Subsets whose
   demand exceeds `Q` are skipped, and so are all their supersets. `O(2ⁿ n²)`.
2. **Set partitioning.** `f(∅) = 0` and `f(S) = min_T tsp(T) + f(S \ T)`, the minimum taken over
   feasible `T ⊆ S` that **contain the lowest-numbered customer of `S`**. Every partition of
   `S` has exactly one block containing that customer, so each partition is enumerated once
   rather than once per ordering of its blocks. Enumerating all `T ⊆ S` over all `S` is `O(3ⁿ)`.

Since the fleet is unlimited, `f({1..n})` is exactly the CVRP optimum. For `n = 12` this takes
about 3 ms. The test `ExactSolverTest` cross-checks it on 40 instances against a completely
different brute force: all `n!` customer orders, each cut optimally into routes by Beasley's
`O(n²)` split DP.

## 5. Lower bound (`LowerBound`)

**Claim.** Let `K = ⌈Σq_i / Q⌉`, let `MST` be the weight of a minimum spanning tree on all `n + 1`
nodes, and let `e₁ ≤ e₂ ≤ …` be the sorted depot distances `d(0,i)`. Every feasible solution costs
at least `MST + e₁ + … + e_K`.

**Proof.** A solution with `m` routes has `m ≥ K`, since each vehicle carries at most `Q`. From
every route delete one of its two depot edges, `(0, i_r)`. The rest is `m` paths hanging off the
depot that together cover every customer once. That is a spanning tree, so it costs at least
`MST`. The deleted edges join the depot to `m` *distinct* customers, so they cost at least
`e₁ + … + e_m ≥ e₁ + … + e_K`. ∎

The proof uses only `d ≥ 0` and not the triangle inequality, so it also holds for TSPLIB-rounded
distances. It runs in `O(n²)` (Prim). The bound is honest but weak: on the bundled instances it is
31–43% below the best solution found. It certifies that a reported cost is not absurd; it does not
give tight optimality gaps. Tighter bounds would need LP relaxations with capacity cuts, or
`q`-routes. The exact DP is used for real gaps on small instances.

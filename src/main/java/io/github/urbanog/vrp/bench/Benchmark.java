package io.github.urbanog.vrp.bench;

import io.github.urbanog.vrp.io.InstanceCatalog;
import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.io.TsplibReader;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.solver.Algorithm;
import io.github.urbanog.vrp.solver.CvrpSolver;
import io.github.urbanog.vrp.solver.ExactSolver;
import io.github.urbanog.vrp.solver.SolutionChecker;
import io.github.urbanog.vrp.solver.SolveOptions;
import io.github.urbanog.vrp.solver.SolveResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Command-line benchmark. Prints Markdown tables that are pasted into the README verbatim.
 *
 * <pre>
 *   java -cp out/main io.github.urbanog.vrp.bench.Benchmark [timeLimitMs] [seeds]
 *   java -cp out/main io.github.urbanog.vrp.bench.Benchmark --generate src/main/resources/instances
 * </pre>
 */
public final class Benchmark {
    private Benchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("--generate")) {
            generate(Path.of(args[1]));
            return;
        }
        long timeLimit = args.length > 0 ? Long.parseLong(args[0]) : 2000;
        int seeds = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        CvrpSolver solver = new CvrpSolver();
        System.out.printf(Locale.ROOT, "%n### Bundled instances (annealing %d ms, %d seeds, %d cores, Java %s)%n%n",
                timeLimit, seeds, Runtime.getRuntime().availableProcessors(), System.getProperty("java.version"));
        System.out.println("| instance | n | K_min | lower bound | savings | + local search | annealing best | annealing mean | vehicles | iterations/run | optimum (DP) |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Instance inst : InstanceCatalog.loadBundled().all()) {
            SolveResult cw = check(inst, solver.solve(inst, SolveOptions.defaults().withAlgorithm(Algorithm.SAVINGS)));
            SolveResult ls = check(inst, solver.solve(inst, SolveOptions.defaults().withAlgorithm(Algorithm.LOCAL_SEARCH)));
            double best = Double.POSITIVE_INFINITY;
            double sum = 0;
            long its = 0;
            int veh = 0;
            for (int s = 1; s <= seeds; s++) {
                SolveResult sa = check(inst, solver.solve(inst,
                        SolveOptions.defaults().withTimeLimit(timeLimit).withSeed(s)));
                sum += sa.solution().cost();
                its += sa.iterations();
                if (sa.solution().cost() < best) {
                    best = sa.solution().cost();
                    veh = sa.solution().routes().size();
                }
            }
            String opt = inst.size() <= ExactSolver.MAX_CUSTOMERS
                    ? String.format(Locale.ROOT, "%.0f", check(inst, solver.solve(inst,
                            SolveOptions.defaults().withAlgorithm(Algorithm.EXACT))).solution().cost())
                    : "n/a";
            System.out.printf(Locale.ROOT, "| %s | %d | %d | %.0f | %.0f | %.0f | %.0f | %.1f | %d | %d | %s |%n",
                    inst.name(), inst.size(), inst.minVehicles(), cw.lowerBound(), cw.solution().cost(),
                    ls.solution().cost(), best, sum / seeds, veh, its / seeds, opt);
        }

        System.out.printf(Locale.ROOT, "%n### Optimality gaps against the exact DP (random instances, n = 6..%d)%n%n",
                ExactSolver.MAX_CUSTOMERS);
        System.out.println("| n | instances | savings mean gap | savings max gap | LS mean gap | LS max gap | annealing (200 it) optimal | annealing max gap | exact DP mean time |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (int n = 6; n <= ExactSolver.MAX_CUSTOMERS; n += 2) {
            int count = 30;
            double cwSum = 0, cwMax = 0, lsSum = 0, lsMax = 0, saMax = 0;
            int saOpt = 0;
            long exactNanos = 0;
            for (int k = 0; k < count; k++) {
                var layout = k % 2 == 0 ? InstanceGenerator.Layout.UNIFORM : InstanceGenerator.Layout.CLUSTERED;
                Instance inst = InstanceGenerator.generate("g", n, 3, layout, 1000L * n + k);
                long t0 = System.nanoTime();
                SolveResult ex = check(inst, solver.solve(inst, SolveOptions.defaults().withAlgorithm(Algorithm.EXACT)));
                exactNanos += System.nanoTime() - t0;
                double opt = ex.solution().cost();
                double gCw = gap(check(inst, solver.solve(inst, SolveOptions.defaults().withAlgorithm(Algorithm.SAVINGS))), opt);
                double gLs = gap(check(inst, solver.solve(inst, SolveOptions.defaults().withAlgorithm(Algorithm.LOCAL_SEARCH))), opt);
                double gSa = gap(check(inst, solver.solve(inst, SolveOptions.defaults().withTimeLimit(60_000).withMaxIterations(200))), opt);
                cwSum += gCw;
                cwMax = Math.max(cwMax, gCw);
                lsSum += gLs;
                lsMax = Math.max(lsMax, gLs);
                saMax = Math.max(saMax, gSa);
                if (gSa < 1e-9) saOpt++;
            }
            System.out.printf(Locale.ROOT, "| %d | %d | %.2f%% | %.2f%% | %.2f%% | %.2f%% | %d/%d | %.2f%% | %.2f ms |%n",
                    n, count, 100 * cwSum / count, 100 * cwMax, 100 * lsSum / count, 100 * lsMax, saOpt, count,
                    100 * saMax, exactNanos / 1e6 / count);
        }
    }

    private static double gap(SolveResult r, double opt) {
        return (r.solution().cost() - opt) / opt;
    }

    private static SolveResult check(Instance inst, SolveResult r) {
        List<String> errs = SolutionChecker.check(inst, r.solution());
        if (!errs.isEmpty()) throw new IllegalStateException(inst.name() + ": " + errs);
        return r;
    }

    /** Writes the bundled instances; they are generated, not copied from any external benchmark set. */
    static void generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        record Spec(String name, int n, int routeSize, InstanceGenerator.Layout layout, long seed) {}
        List<Spec> specs = new ArrayList<>(List.of(
                new Spec("U-n10-k3", 10, 4, InstanceGenerator.Layout.UNIFORM, 10),
                new Spec("U-n30-k4", 30, 8, InstanceGenerator.Layout.UNIFORM, 30),
                new Spec("C-n50-k5", 50, 10, InstanceGenerator.Layout.CLUSTERED, 50),
                new Spec("U-n100-k8", 100, 13, InstanceGenerator.Layout.UNIFORM, 100),
                new Spec("C-n200-k12", 200, 17, InstanceGenerator.Layout.CLUSTERED, 200)));
        for (Spec s : specs) {
            Instance inst = InstanceGenerator.generate(s.name(), s.n(), s.routeSize(), s.layout(), s.seed());
            String text = "COMMENT : generated by vrp-route-service InstanceGenerator (layout="
                    + s.layout().name().toLowerCase(Locale.ROOT) + ", seed=" + s.seed() + ", K_min=" + inst.minVehicles() + ")\n";
            String body = TsplibReader.write(inst);
            int nl = body.indexOf('\n');
            Files.writeString(dir.resolve(s.name() + ".vrp"), body.substring(0, nl + 1) + text + body.substring(nl + 1));
            System.out.println("wrote " + s.name() + " K_min=" + inst.minVehicles());
        }
    }
}

package io.github.urbanog.vrp.jobs;

import io.github.urbanog.vrp.api.ApiCodec;
import io.github.urbanog.vrp.model.Instance;
import io.github.urbanog.vrp.solver.CvrpSolver;
import io.github.urbanog.vrp.solver.SolutionChecker;
import io.github.urbanog.vrp.solver.SolveOptions;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs solve jobs asynchronously on virtual threads.
 *
 * <p>Virtual threads make "one thread per job" cheap, but the solver is CPU-bound, so a {@link
 * Semaphore} caps the number of jobs that compute at the same time (by default the number of
 * cores). Jobs beyond that wait in state {@code QUEUED} without holding a platform thread.
 */
public final class JobService implements AutoCloseable {
    private final JobRepository repository;
    private final CvrpSolver solver;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits;

    public JobService(JobRepository repository, CvrpSolver solver, Clock clock, int maxConcurrent) {
        this.repository = repository;
        this.solver = solver;
        this.clock = clock;
        this.permits = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    public JobRepository repository() {
        return repository;
    }

    /** Stores a QUEUED job and schedules it; returns immediately. */
    public Job submit(Instance instance, SolveOptions options) {
        Job job = Job.queued(instance.name(), instance.size(), options.algorithm(), clock.instant());
        repository.save(job);
        executor.submit(() -> run(job, instance, options));
        return job;
    }

    private void run(Job queued, Instance instance, SolveOptions options) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            repository.save(queued.failed(clock.instant(), "interrupted while queued"));
            return;
        }
        Job running = queued.running(clock.instant());
        repository.save(running);
        try {
            var result = solver.solve(instance, options);
            var problems = SolutionChecker.check(instance, result.solution());
            if (!problems.isEmpty()) throw new IllegalStateException("solver produced infeasible solution: " + problems);
            repository.save(running.succeeded(clock.instant(), result.solution().cost(), ApiCodec.writeResult(instance, result)));
        } catch (RuntimeException e) {
            repository.save(running.failed(clock.instant(), e.getClass().getSimpleName() + ": " + e.getMessage()));
        } finally {
            permits.release();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

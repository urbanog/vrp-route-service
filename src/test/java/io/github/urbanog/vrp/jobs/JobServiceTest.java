package io.github.urbanog.vrp.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.urbanog.vrp.io.InstanceGenerator;
import io.github.urbanog.vrp.solver.Algorithm;
import io.github.urbanog.vrp.solver.CvrpSolver;
import io.github.urbanog.vrp.solver.SolveOptions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobServiceTest {

    private static Job await(JobRepository repo, Job job) throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            Job j = repo.findById(job.id()).orElseThrow();
            if (j.isTerminal()) return j;
            Thread.sleep(10);
        }
        throw new AssertionError("job did not finish: " + job.id());
    }

    @Test
    void runsManyJobsWithBoundedConcurrency() throws Exception {
        var repo = new InMemoryJobRepository();
        try (var service = new JobService(repo, new CvrpSolver(), Clock.systemUTC(), 2)) {
            List<Job> submitted = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                var inst = InstanceGenerator.generate("j" + i, 40, 8, InstanceGenerator.Layout.UNIFORM, i);
                submitted.add(service.submit(inst, SolveOptions.defaults().withTimeLimit(50)));
            }
            for (Job j : submitted) {
                Job done = await(repo, j);
                assertEquals(Job.Status.SUCCEEDED, done.status(), String.valueOf(done.error()));
                assertNotNull(done.resultJson());
                assertTrue(!done.finishedAt().isBefore(done.startedAt()));
                assertTrue(!done.startedAt().isBefore(done.createdAt()));
            }
            assertEquals(12, repo.countByStatus(Job.Status.SUCCEEDED));
            assertEquals(5, repo.findRecent(5).size());
        }
    }

    @Test
    void failuresAreRecordedNotThrown() throws Exception {
        var repo = new InMemoryJobRepository();
        try (var service = new JobService(repo, new CvrpSolver(), Clock.systemUTC(), 1)) {
            // 'exact' on 30 customers is refused by the solver; the job must end FAILED with a message.
            var inst = InstanceGenerator.generate("bad", 30, 8, InstanceGenerator.Layout.UNIFORM, 1);
            Job done = await(repo, service.submit(inst, SolveOptions.defaults().withAlgorithm(Algorithm.EXACT)));
            assertEquals(Job.Status.FAILED, done.status());
            assertTrue(done.error().contains("exact"), done.error());
        }
    }
}

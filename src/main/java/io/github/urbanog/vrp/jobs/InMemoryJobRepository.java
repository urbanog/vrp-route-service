package io.github.urbanog.vrp.jobs;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory repository. Writes for one id are atomic ({@link ConcurrentHashMap#put});
 * {@link #findRecent} is {@code O(N log N)}, acceptable for a demo store (a database would answer
 * it from the {@code created_at} index).
 */
public final class InMemoryJobRepository implements JobRepository {
    private final ConcurrentHashMap<UUID, Job> jobs = new ConcurrentHashMap<>();

    @Override
    public void save(Job job) {
        jobs.put(job.id(), job);
    }

    @Override
    public Optional<Job> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public List<Job> findRecent(int limit) {
        return jobs.values().stream()
                .sorted(Comparator.comparing(Job::createdAt).reversed().thenComparing(Job::id))
                .limit(limit)
                .toList();
    }

    @Override
    public long countByStatus(Job.Status status) {
        return jobs.values().stream().filter(j -> j.status() == status).count();
    }
}

package io.github.urbanog.vrp.jobs;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for jobs (hexagonal "port"; implementations are adapters).
 *
 * <p>{@link InMemoryJobRepository} is used here. A PostgreSQL adapter would implement the same
 * interface with the statements documented next to {@code db/schema.sql}; nothing above this
 * interface would change.
 */
public interface JobRepository {
    /** Inserts or replaces the job with the same id. */
    void save(Job job);

    Optional<Job> findById(UUID id);

    /** Most recent jobs first, at most {@code limit}. */
    List<Job> findRecent(int limit);

    /** Number of jobs in a given status. */
    long countByStatus(Job.Status status);
}

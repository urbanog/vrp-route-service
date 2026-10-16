package io.github.urbanog.vrp.jobs;

import io.github.urbanog.vrp.json.JsonValue;
import io.github.urbanog.vrp.solver.Algorithm;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of an asynchronous solve job. State transitions produce new snapshots:
 * {@code QUEUED -> RUNNING -> (SUCCEEDED | FAILED)}.
 *
 * @param resultJson the full solve response, stored as a JSON document (a {@code jsonb} column in
 *     the PostgreSQL schema)
 */
public record Job(
        UUID id,
        Status status,
        String instanceName,
        int customers,
        Algorithm algorithm,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Double cost,
        String error,
        JsonValue resultJson) {

    public enum Status { QUEUED, RUNNING, SUCCEEDED, FAILED }

    public Job {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    public static Job queued(String instanceName, int customers, Algorithm algorithm, Instant now) {
        return new Job(UUID.randomUUID(), Status.QUEUED, instanceName, customers, algorithm, now, null, null, null, null, null);
    }

    public Job running(Instant now) {
        return new Job(id, Status.RUNNING, instanceName, customers, algorithm, createdAt, now, null, null, null, null);
    }

    public Job succeeded(Instant now, double cost, JsonValue result) {
        return new Job(id, Status.SUCCEEDED, instanceName, customers, algorithm, createdAt, startedAt, now, cost, null, result);
    }

    public Job failed(Instant now, String message) {
        return new Job(id, Status.FAILED, instanceName, customers, algorithm, createdAt, startedAt, now, null, message, null);
    }

    public boolean isTerminal() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }
}

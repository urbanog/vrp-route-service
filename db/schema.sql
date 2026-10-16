-- PostgreSQL schema for persisting solve jobs (PostgreSQL 13+ for gen_random_uuid()).
--
-- The service currently ships InMemoryJobRepository; a JdbcJobRepository implementing the same
-- JobRepository interface would use exactly the statements at the bottom of this file. No code
-- above the repository interface changes.

CREATE TABLE IF NOT EXISTS solve_job (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    status        text        NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    instance_name text        NOT NULL,
    customers     integer     NOT NULL CHECK (customers > 0),
    algorithm     text        NOT NULL CHECK (algorithm IN ('savings', 'local-search', 'annealing', 'exact')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    started_at    timestamptz,
    finished_at   timestamptz,
    cost          double precision,
    error         text,
    result        jsonb,      -- full solve response; routes are read whole, never queried row-wise
    -- State machine invariants, enforced by the database as well as by the Job record.
    CHECK (started_at IS NULL OR started_at >= created_at),
    CHECK (finished_at IS NULL OR finished_at >= started_at),
    CHECK ((status = 'SUCCEEDED') = (result IS NOT NULL AND cost IS NOT NULL)),
    CHECK ((status = 'FAILED') = (error IS NOT NULL))
);

-- GET /api/jobs (newest first): an index scan on created_at, no sort.
CREATE INDEX IF NOT EXISTS solve_job_created_at_idx ON solve_job (created_at DESC, id);

-- Worker polling / health counters only care about unfinished jobs, which are few: a partial
-- index stays tiny however many finished jobs accumulate.
CREATE INDEX IF NOT EXISTS solve_job_active_idx ON solve_job (status, created_at)
    WHERE status IN ('QUEUED', 'RUNNING');

-- "Best known solution per instance" report.
CREATE INDEX IF NOT EXISTS solve_job_instance_cost_idx ON solve_job (instance_name, cost)
    WHERE status = 'SUCCEEDED';

-- ---------------------------------------------------------------------------------------------
-- Statements a JdbcJobRepository would use (all parameterised: no string concatenation).
-- ---------------------------------------------------------------------------------------------

-- save(Job): upsert, so each state transition is one idempotent statement.
-- INSERT INTO solve_job (id, status, instance_name, customers, algorithm, created_at,
--                        started_at, finished_at, cost, error, result)
-- VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
-- ON CONFLICT (id) DO UPDATE SET
--     status = EXCLUDED.status, started_at = EXCLUDED.started_at,
--     finished_at = EXCLUDED.finished_at, cost = EXCLUDED.cost,
--     error = EXCLUDED.error, result = EXCLUDED.result;

-- findById(id): primary-key lookup.
-- SELECT * FROM solve_job WHERE id = ?;

-- findRecent(limit): uses solve_job_created_at_idx.
-- SELECT id, status, instance_name, customers, algorithm, created_at, started_at, finished_at, cost, error
-- FROM solve_job ORDER BY created_at DESC, id LIMIT ?;

-- countByStatus(status): for QUEUED/RUNNING answered from the partial index.
-- SELECT count(*) FROM solve_job WHERE status = ?;

-- Multi-instance deployment: a worker claims the oldest queued job without blocking others.
-- UPDATE solve_job SET status = 'RUNNING', started_at = now()
-- WHERE id = (SELECT id FROM solve_job WHERE status = 'QUEUED'
--             ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1)
-- RETURNING *;

-- Best known cost per instance: uses solve_job_instance_cost_idx.
-- SELECT DISTINCT ON (instance_name) instance_name, cost, id, finished_at
-- FROM solve_job WHERE status = 'SUCCEEDED'
-- ORDER BY instance_name, cost, finished_at;

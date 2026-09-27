-- Per-project deployment number (1, 2, 3, ...) shown as the "version" in the history.
ALTER TABLE deployments ADD COLUMN version INTEGER;

UPDATE deployments d
SET version = numbered.version
FROM (
    SELECT id, row_number() OVER (PARTITION BY project_id ORDER BY created_at, id) AS version
    FROM deployments
) numbered
WHERE d.id = numbered.id;

ALTER TABLE deployments ALTER COLUMN version SET NOT NULL;

CREATE UNIQUE INDEX uq_deployments_project_version ON deployments (project_id, version);

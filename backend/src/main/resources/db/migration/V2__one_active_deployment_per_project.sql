-- Only one in-flight deployment per project; enforced by the database so concurrent requests cannot race.
CREATE UNIQUE INDEX uq_deployments_one_active_per_project
    ON deployments (project_id)
    WHERE status IN ('PENDING', 'BUILDING', 'DEPLOYING');

-- For a rollback deployment: the version whose image it restores. NULL for a regular deployment.
ALTER TABLE deployments ADD COLUMN rollback_of_version INTEGER;

ALTER TABLE deployments
    ADD CONSTRAINT ck_deployments_rollback_of_version CHECK (rollback_of_version IS NULL OR rollback_of_version >= 1);

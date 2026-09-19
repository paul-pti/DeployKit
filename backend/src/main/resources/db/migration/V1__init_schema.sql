CREATE TABLE projects (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(100) NOT NULL,
    repository_url VARCHAR(500) NOT NULL,
    branch         VARCHAR(255) NOT NULL DEFAULT 'main',
    port           INTEGER      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_name UNIQUE (name),
    CONSTRAINT ck_projects_port CHECK (port BETWEEN 1 AND 65535)
);

CREATE TABLE deployments (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id    UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    image         VARCHAR(500),
    commit_sha    VARCHAR(64),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    error_message TEXT,
    CONSTRAINT ck_deployments_status CHECK (
        status IN ('PENDING', 'BUILDING', 'DEPLOYING', 'RUNNING', 'FAILED', 'ROLLED_BACK')
    )
);

CREATE INDEX idx_deployments_project_created ON deployments (project_id, created_at DESC);
CREATE INDEX idx_deployments_status ON deployments (status);

CREATE TABLE environments (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id           UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name                 VARCHAR(50)  NOT NULL,
    kubernetes_namespace VARCHAR(63)  NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_environments_project_name UNIQUE (project_id, name)
);

CREATE TABLE deployment_logs (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    deployment_id UUID        NOT NULL REFERENCES deployments (id) ON DELETE CASCADE,
    timestamp     TIMESTAMPTZ NOT NULL DEFAULT now(),
    level         VARCHAR(10) NOT NULL DEFAULT 'INFO',
    message       TEXT        NOT NULL,
    CONSTRAINT ck_deployment_logs_level CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_deployment_logs_deployment_ts ON deployment_logs (deployment_id, timestamp);

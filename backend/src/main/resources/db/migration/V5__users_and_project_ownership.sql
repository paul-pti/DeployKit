CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(10)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Emails are stored lowercase; the index also makes case variants collide.
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

-- Projects belong to a user. Rows that exist before authentication have no owner: only administrators see them.
ALTER TABLE projects ADD COLUMN owner_id UUID REFERENCES users (id);
CREATE INDEX idx_projects_owner ON projects (owner_id);

-- A project name only has to be unique among the projects of the same owner.
ALTER TABLE projects DROP CONSTRAINT uq_projects_name;
CREATE UNIQUE INDEX uq_projects_owner_name
    ON projects (COALESCE(owner_id, '00000000-0000-0000-0000-000000000000'::uuid), name);

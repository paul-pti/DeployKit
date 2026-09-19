# Architecture

## Backend layers

```mermaid
flowchart TD
    C[controller<br/>HTTP, DTO validation] --> S[service<br/>business logic]
    S --> R[repository<br/>Spring Data JPA]
    R --> D[(PostgreSQL)]
    S --> E[domain<br/>entities, enums]
    C -.-> DTO[dto]
    S -.-> M[mapper]
    X[exception<br/>@RestControllerAdvice] -.-> C
```

- Controllers only translate HTTP to service calls; no business logic.
- DTOs are the API contract; entities never leave the service layer.
- Errors are RFC 7807 `ProblemDetail` (`application/problem+json`), produced in one place.
- Schema is owned by Flyway (`db/migration`); `ddl-auto: validate`.

## Deployment engine

```mermaid
sequenceDiagram
    participant UI as Dashboard
    participant C as DeploymentController
    participant S as DeploymentService
    participant R as DeploymentRunner (worker thread)
    participant H as Helm CLI
    participant K as Kubernetes API
    participant DB as PostgreSQL
    UI->>C: POST /api/projects/{id}/deploy
    C->>S: deploy()
    S->>DB: deployment PENDING
    S-->>UI: 202 + Location
    S->>R: run(id) on executor
    R->>DB: DEPLOYING
    R->>H: helm upgrade --install
    H->>K: apply chart
    loop until AVAILABLE, failure or timeout
        R->>K: deployment + pod status
        R->>DB: progress log
    end
    R->>DB: RUNNING or FAILED
    UI->>C: GET /api/deployments/{id} (polling)
```

Design decisions: [ADR 0004](adr/0004-async-deployment-engine.md).

## Data model (V1, V2)

```mermaid
erDiagram
    projects ||--o{ deployments : has
    projects ||--o{ environments : has
    deployments ||--o{ deployment_logs : has
```

Deployment states: `PENDING, BUILDING, DEPLOYING, RUNNING, FAILED, ROLLED_BACK` (enforced by a check constraint).
V2 adds a partial unique index allowing a single `PENDING`/`BUILDING`/`DEPLOYING` deployment per project.

## Frontend

`src/` is feature-oriented: `features/{projects,deployments,logs}` for feature code, with shared `components`,
`pages`, `hooks`, `services` (API calls only), `types` and `lib` (Axios and TanStack Query clients).
Components never call Axios directly; they use hooks that wrap services.

# ADR 0003: Configuration through environment variables

**Status:** accepted

**Context:** Secrets must never be committed, and the same artifact must run locally, in containers and on Kubernetes.

**Decision:** `application.yml` reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` with no defaults. Locally, a gitignored
repo-root `.env` (template: `.env.example`) feeds both Docker Compose and the `local` Spring profile
(via `spring.config.import`). Structured ECS JSON logging is on by default; `local` switches to plain text.

**Consequences:** A missing variable fails fast at startup. Later phases move secrets to Kubernetes Secrets / AWS.

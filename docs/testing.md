# Testing

```bash
cd backend && ./mvnw test                 # unit tests only, JDK 21, no Docker needed
cd backend && ./mvnw verify                # also runs the integration tests below (needs Docker running)
# or without a local JDK 21:
docker run --rm -v "$PWD/backend":/w -v deploykit-m2:/root/.m2 -w /w maven:3.9-eclipse-temurin-21 mvn -B verify

cd frontend && npm run lint && npm run test && npm run build
```

## Backend unit tests (`mvn test`)

Cover the health controller, global exception handler, `ProjectService` (Mockito), `ProjectController` (MockMvc:
validation, 201/204/400/404/409), `KubernetesService` (Fabric8 mock API server: namespace, deployment shape, rollout
status, pods, logs, error mapping) and the deployment engine: `DeploymentService`, `DeploymentRunner`,
`RolloutMonitor` (success, failure, timeout, fail-fast, transient errors), `HelmService` and `ProcessCommandRunner`
(real processes), image resolution, naming, the deploy, history and logs endpoints, and `DeploymentLogService`.

Authentication is covered at three levels: JWT issuing and validation, the login limiter and `SecurityProperties`;
`SecurityConfigurationTest`, which runs the real filter chain (no, garbage, tampered, forged-role, expired, wrong-issuer
and unsigned tokens all give 401; a `USER` gets 403 on `/api/users`); and the ownership rules in the services
(`ProjectAccessTest` and the service tests). Controller tests use `@WebMvcSecurity` and a default `USER` token.

All of the above use mocked repositories: none of it touches a real database.

## Backend integration tests (`mvn verify`, Phase 10)

`*IT.java` classes, run by Maven Failsafe, not Surefire, so they stay out of the fast `mvn test` loop. Each starts a
real PostgreSQL with Testcontainers (`support/AbstractPostgresIT`, one container shared by the whole run) and checks
what a mocked repository cannot:

- `FlywayMigrationIT` — the 5 migrations apply cleanly to a real database, and the full Spring context starts
  (`ddl-auto: validate` actually gets to run against real Postgres, not just in theory).
- `UserRepositoryIT` — `uq_users_email` really rejects a case-different duplicate.
- `ProjectRepositoryIT` — `uq_projects_owner_name` really scopes uniqueness per owner (two owners, or an owner and the
  legacy ownerless bucket, can't collide; the same owner can't reuse a name).
- `AuthenticationFlowIT` — login, JWT verification and ownership through real HTTP (`TestRestTemplate`), the real
  security filter chain and a real database.

Needs Docker running locally (Docker Desktop, Colima, ...); nothing else to configure. CI (`ubuntu-latest`) always has
Docker, so `mvn verify` runs there unconditionally (see below).

`KubernetesServiceClusterTest` runs against a real cluster (server-side apply, rollout, scaling, pods, logs) and is
skipped unless enabled. With the kind cluster running:

```bash
cd backend && DEPLOYKIT_IT_K8S=true ./mvnw test -Dtest=KubernetesServiceClusterTest
```

## Frontend unit and component tests (Vitest, Phase 10)

`npm run test` (or `npm run test:watch`). Configured in `vite.config.ts`'s `test` block, environment `jsdom`, with
`@testing-library/react` and no injected globals (tests import `describe`/`it`/`expect`/... from `vitest` explicitly,
matching the rest of the codebase). Coverage focuses on the authentication code (the newest and most security-sensitive
part): `lib/authStorage` (round-trip, expiry, malformed data), `lib/apiError`, `features/auth/RequireAuth` and
`RequireAdmin` (redirects), `pages/LoginPage` (success and wrong-password paths); `features/projects/ProjectForm` is
covered too, as the pattern to follow for the rest of the UI going forward. Network calls are mocked at the service
layer (`vi.mock('.../someService')`), never at `apiClient`/Axios.

## End-to-end tests (Playwright, Phase 10)

`frontend/e2e/*.spec.ts`, run with `npm run test:e2e`. Unlike the two suites above, these need the real stack running
(Postgres, backend, and the frontend dev server so `/api` is proxied to the backend, avoiding CORS):

```bash
docker compose up -d postgres
cd backend && SPRING_PROFILES_ACTIVE=local DEPLOYKIT_JWT_SECRET=$(openssl rand -base64 48) \
  DEPLOYKIT_ADMIN_EMAIL=admin@example.com DEPLOYKIT_ADMIN_PASSWORD=e2e-admin-password-123 \
  ./mvnw spring-boot:run &
cd frontend && npm run dev &
cd frontend && E2E_ADMIN_EMAIL=admin@example.com E2E_ADMIN_PASSWORD=e2e-admin-password-123 npm run test:e2e
```

`auth.spec.ts` covers a wrong password, the anonymous-visitor redirect to `/login` and back, and the full
administrator-creates-a-user flow (the new account has no Users link and `/users` bounces it to `/projects`). It reuses
whatever admin already exists (`E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD`, matching the bootstrap admin's credentials) and
creates its own user with a timestamped email, so the suite can be re-run against the same database without a 409.

The CI `e2e` job does the same against an ephemeral Postgres and backend; see
[github-actions.md](github-actions.md) and `.github/workflows/ci.yml`.

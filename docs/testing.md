# Testing

```bash
cd backend && ./mvnw test                 # JDK 21
# or without a local JDK 21:
docker run --rm -v "$PWD/backend":/w -v deploykit-m2:/root/.m2 -w /w maven:3.9-eclipse-temurin-21 mvn -B test
cd frontend && npm run lint && npm run build
```

Backend tests cover the health controller, global exception handler, `ProjectService` (Mockito),
`ProjectController` (MockMvc: validation, 201/204/400/404/409), `KubernetesService` (Fabric8 mock API server:
namespace, deployment shape, rollout status, pods, logs, error mapping) and the deployment engine:
`DeploymentService`, `DeploymentRunner`, `RolloutMonitor` (success, failure, timeout, fail-fast, transient errors),
`HelmService` and `ProcessCommandRunner` (real processes), image resolution, naming, the deploy, history and logs
endpoints, and `DeploymentLogService`.

Authentication is covered at three levels: JWT issuing and validation, the login limiter and `SecurityProperties`;
`SecurityConfigurationTest`, which runs the real filter chain (no, garbage, tampered, forged-role, expired, wrong-issuer
and unsigned tokens all give 401; a `USER` gets 403 on `/api/users`); and the ownership rules in the services
(`ProjectAccessTest` and the service tests). Controller tests use `@WebMvcSecurity` and a default `USER` token.

`KubernetesServiceClusterTest` runs against a real cluster (server-side apply, rollout, scaling, pods, logs) and is
skipped unless enabled. With the kind cluster running:

```bash
cd backend && DEPLOYKIT_IT_K8S=true ./mvnw test -Dtest=KubernetesServiceClusterTest
```

Testcontainers integration tests, Vitest and Playwright come in Phase 10. The CI that runs the checks on every push
is described in [github-actions.md](github-actions.md).

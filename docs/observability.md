# Observability

The backend exposes Prometheus metrics and OpenTelemetry traces. Nothing is required to run it; the optional local
stack (Prometheus, Grafana, Jaeger) is there to look at them. Design decisions: [ADR 0007](adr/0007-prometheus-and-otlp-tracing.md).

## Metrics

`GET /actuator/prometheus`, unauthenticated (like `/actuator/health`) so Prometheus can scrape it without a token —
see [security.md](security.md) for why that is an acceptable trade-off here. Alongside the usual JVM, process and
`http.server.requests` metrics (Micrometer's Spring Boot integration), DeployKit records:

| Metric | Type | Tags | What it means |
|---|---|---|---|
| `deploykit.deployments.total` | counter | `status`, `rollback` | Deployments reaching a final state |
| `deploykit.deployment.duration` | timer | `status` | Time from `DEPLOYING` to that final state (p95 published) |
| `deploykit.deployments.active` | gauge | | Deployments currently `PENDING`/`BUILDING`/`DEPLOYING` |
| `deploykit.auth.login.attempts` | counter | `result` (`success`/`failure`/`blocked`) | Login attempts by outcome |

Recorded in `observability.DeploymentMetrics` and `observability.AuthMetrics`, called only from `DeploymentRecorder`
and `AuthService` — the two places that already own every state transition, so a new endpoint cannot forget to
update a metric.

## Traces

OpenTelemetry via Micrometer Tracing, exported as OTLP/HTTP. **Off by default**
(`management.tracing.sampling.probability: 0`): with no collector listening, an enabled exporter would just log
periodic connection failures. Turn it on with `DEPLOYKIT_TRACING_SAMPLING=1` once the observability stack (or any
OTLP-compatible collector) is running at `DEPLOYKIT_OTLP_ENDPOINT` (default `http://localhost:4318/v1/traces`).

Traces cover HTTP requests and Spring Security's filter chain out of the box. The active trace and span id are also
added to every structured log line automatically (Spring Boot's ECS encoder picks them up once tracing is on the
classpath), so a log line and the trace it happened in can be cross-referenced.

## Local stack

```bash
docker compose --profile observability up -d prometheus grafana jaeger
# then run the backend with tracing turned on:
DEPLOYKIT_TRACING_SAMPLING=1 ./mvnw spring-boot:run
```

| Tool | URL | Notes |
|---|---|---|
| Prometheus | http://localhost:9090 | Scrapes the backend every 15s (`observability/prometheus.yml`) |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_ADMIN_PASSWORD` (default `admin`, change it). Prometheus and Jaeger datasources and the **DeployKit** dashboard are provisioned automatically. |
| Jaeger | http://localhost:16686 | Trace search and detail view |

The Prometheus scrape config targets both `host.docker.internal:8080` (backend run on the host, the usual local
setup) and `backend:8080` (the containerised backend, `--profile full`); whichever one is not running just shows as
"down", which is harmless.

The **DeployKit** Grafana dashboard has panels for deployments per minute by status, deployments currently in flight,
deployment duration (p95), login attempts by outcome, HTTP request rate and latency (p95), and JVM heap use.

## Kubernetes

Not covered yet: the applications DeployKit deploys are unrelated services with their own metrics, if any, and
DeployKit does not run itself in the cluster it manages until Phase 12 (AWS/EKS). At that point the backend's own
`/actuator/prometheus` would be scraped the same way any other pod is.

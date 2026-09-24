# ADR 0007: Prometheus metrics, OTLP tracing off by default

**Status:** accepted

**Context:** Phase 11 adds observability. The backend already has structured (ECS JSON) logging; metrics and traces
were missing.

**Decision**
- **Metrics via Micrometer + `/actuator/prometheus`**, pull-based (Prometheus scrapes the backend) rather than a
  push gateway, matching how the rest of the Spring Boot Actuator setup already works. The endpoint is unauthenticated,
  like `/actuator/health`: Prometheus does not send a bearer token by default, and adding one would need a separate
  credential to manage for a single internal scrape target.
- **Business metrics live next to the code that already owns the transition they measure** (`DeploymentRecorder` for
  deployments, `AuthService` for logins), in a small `observability` package, rather than being computed separately
  from stored data. A new deployment or login outcome cannot be added without going through these, so the metric
  cannot be forgotten either.
- **Tracing via Micrometer Tracing + the OpenTelemetry bridge, exported as OTLP/HTTP.** Sampling defaults to **0**
  (off): the alternative (a small default probability) would still make the exporter try to reach a collector that,
  for most local runs, is not there — producing periodic connection-failure log noise for no benefit. Tracing is
  opt-in (`DEPLOYKIT_TRACING_SAMPLING`), turned on together with the local stack.
- **Jaeger, not a separate OpenTelemetry Collector + tracing backend**, for the local stack: recent Jaeger versions
  accept OTLP directly, so one container gives both the OTLP receiver and the trace UI. A collector would only earn
  its place once traces need to fan out to more than one backend.

**Consequences**
- No code changes are needed to add a metric to an existing transition; a genuinely new kind of event still needs a
  new call to `DeploymentMetrics`/`AuthMetrics`.
- Running the backend without the observability stack is unaffected: metrics are always recorded (cheap, in-memory)
  and simply go unread; traces are not sampled, so nothing is exported.
- `/actuator/prometheus` is one more unauthenticated surface, tracked in [docs/security.md](../security.md); a
  `NetworkPolicy` scoping it to Prometheus's pod is a natural addition once DeployKit itself runs in a cluster.

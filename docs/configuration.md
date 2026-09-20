# Configuration

Configuration comes from environment variables and Spring profiles; nothing secret is committed
([ADR 0003](adr/0003-configuration-and-secrets.md)). Locally, the repo-root `.env` (template: `.env.example`) feeds both
Docker Compose and the backend `local` profile.

Docker Compose publishes Postgres (`5432`) and, with the `full` profile, the backend (`8080`) on `127.0.0.1` only, so
nothing is reachable from the network. The API has no authentication yet.

## Backend

| Variable | Used by | Purpose |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | default profile / containers | JDBC connection. No defaults. |
| `POSTGRES_*` | `local` profile, Compose | From `.env`; `local` builds the JDBC URL from these |
| `DEPLOYKIT_KUBERNETES_CONTEXT` | Kubernetes client | kubeconfig context to use (e.g. `kind-deploykit`); defaults to the current one |
| `DEPLOYKIT_HELM_CHART_PATH` | deployments | Chart directory, default `../helm/deploykit-app` (relative to `backend/`) |
| `HELM_BINARY` | deployments | Helm executable, default `helm` |
| `DEPLOYKIT_REGISTRY` | deployments | Registry of derived images, default `ghcr.io` |
| `DEPLOYKIT_DEPLOYMENT_ROLLOUT_TIMEOUT`, `..._POLL_INTERVAL`, `..._FAILURE_GRACE` | deployments | Defaults `5m`, `2s`, `30s` |
| `SERVER_PORT` | all | Default `8080` |
| `LOG_LEVEL` | all | Level for `com.deploykit` (default `INFO`) |

Logging is structured (ECS JSON) by default and plain text under the `local` profile.

## Frontend

Optional, in `frontend/.env.local` (template: `frontend/.env.example`):

| Variable | Purpose |
|---|---|
| `VITE_API_PROXY_TARGET` | Backend URL the Vite dev server proxies `/api` to (default `http://localhost:8080`) |
| `VITE_API_BASE_URL` | Base URL of API calls in a built app; empty means same-origin `/api` |

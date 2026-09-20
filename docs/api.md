# API reference

The backend listens on `http://localhost:8080` when run locally. Errors follow RFC 7807
(`application/problem+json`). The API has no authentication yet (Phase 9), do not expose it.

## Endpoints

| Method | Path | Success | Errors |
|---|---|---|---|
| `GET` | `/api/health` | 200 `{"status":"UP"}` | |
| `POST` | `/api/projects` | 201 + `Location` | 400 validation (`errors` per field), 409 duplicate name |
| `GET` | `/api/projects` | 200, newest first | |
| `GET` | `/api/projects/{id}` | 200 | 400 malformed id, 404 |
| `DELETE` | `/api/projects/{id}` | 204, also deletes the project's Kubernetes namespace | 400 malformed id, 404, 409 deployment in progress |
| `POST` | `/api/projects/{id}/deploy` | 202 + `Location`, deployment `PENDING` | 400 invalid image, 404, 409 deployment already in progress, 503 queue full |
| `GET` | `/api/projects/{id}/deployments` | 200, one page, newest first | 400 malformed id or unknown status, 404 |
| `GET` | `/api/deployments/{id}` | 200 | 400 malformed id, 404 |
| `GET` | `/api/deployments/{id}/logs` | 200, pod logs and workflow events | 400 malformed id or non-numeric `tail`, 404 |

`POST /api/projects` body: `name` (required, ≤100, unique), `repositoryUrl` (required, `https://github.com/owner/repo`),
`branch` (optional, defaults to `main`), `port` (required, 1-65535). Errors are RFC 7807 `application/problem+json`.

`POST /api/projects/{id}/deploy` takes an optional body: `image` (full reference, e.g. `nginx:1.27-alpine`) or
`commitSha`. Without a body the image is `ghcr.io/<owner>/<repo>:<branch>`, which the
[build pipeline](github-actions.md) publishes, tagged with the commit SHA and the branch name. How the deployment
then runs is described in [deployment-engine.md](deployment-engine.md).

## Deployment history

`GET /api/projects/{id}/deployments` returns a page, newest first:

```json
{ "content": [ { "id": "...", "version": 3, "status": "FAILED", "commitSha": null, "image": "ghcr.io/acme/app:main",
                 "createdAt": "...", "startedAt": "...", "finishedAt": "...", "errorMessage": "Pod ... is ErrImagePull" } ],
  "page": 0, "size": 20, "totalElements": 3, "totalPages": 1 }
```

- **Filter:** `?status=FAILED`, repeatable (`?status=FAILED&status=RUNNING`). An unknown status is a `400`.
- **Paging:** `page` (zero-based, default 0) and `size` (default 20, at most 100; larger values are capped). The sort is
  fixed, clients cannot choose it.
- **`version`** is the per-project deployment number (1, 2, 3, ...), stored in the database (migration V3), so it never
  changes. Rollbacks (Phase 8) will get their own number.
- **`commitSha`** is recorded when the deploy request names it, or when the image tag is itself a commit SHA (which
  is what the build pipeline produces). Deploying a branch-tagged image leaves it empty: DeployKit cannot know which
  commit a moving tag points to without asking GitHub.
- The **duration** is `finishedAt - startedAt`, computed by the dashboard.

The project page shows the history as a table (version, commit, status, created, duration, image, failure reason) with a
status filter and pagination, and refreshes itself every 3 seconds while a deployment is in flight.

## Deployment logs

`GET /api/deployments/{id}/logs?tail=200&previous=false` returns everything worth reading about a deployment:

```json
{ "deploymentId": "...", "status": "RUNNING",
  "pods": [ { "pod": "app-7d9f-x2", "phase": "Running", "ready": true, "restarts": 0, "reason": null,
              "log": "last lines of the container output...", "error": null } ],
  "podsNote": null,
  "events": [ { "timestamp": "2026-09-20T10:00:00Z", "level": "INFO", "message": "Deployment queued for image ..." } ] }
```

- **`pods`** are the Kubernetes pods of the application that run **this deployment's image**, with the last `tail` lines
  of their output (default 200, at most 5000). A pod that cannot give its logs (image still being pulled, or unable to
  be pulled) is reported with an `error` instead of failing the whole request.
- **`previous=true`** reads the previous container of each pod, which is what explains a `CrashLoopBackOff`.
- **`podsNote`** says why `pods` is empty: the deployment has not started, a newer deployment replaced its pods,
  the project was never deployed, or Kubernetes is unreachable. Pods only exist while their image is running, so the
  logs of an old deployment are gone once a newer one replaced it.
- **`events`** are the steps of the deployment workflow stored in `deployment_logs` (Helm output, rollout progress,
  failure reason, up to 1000 entries, oldest first). They stay available after the pods are gone and are the place to
  look when a deployment `FAILED` before its containers ever started.

On the project page, the **View** button of a history row opens its logs in a dark terminal with two tabs,
*Application* and *Deployment*. It refreshes every 3 seconds (a checkbox turns it off), follows the end of the output
like `tail -f`, and stops following while you scroll up to read. Until a row is chosen it shows the newest deployment.

# ADR 0004: Asynchronous deployment engine driven by the Helm CLI

**Status:** accepted

**Context:** A deployment takes seconds to minutes (Helm, image pull, rollout). Holding an HTTP request and a
database transaction open for that long is fragile, and the result must survive client disconnects.

**Decision**
- `POST /api/projects/{id}/deploy` records a `PENDING` deployment and returns `202 Accepted`. A bounded thread pool
  (4 workers, queue of 10, rejection reported as `503`) runs the workflow; clients poll `GET /api/deployments/{id}`.
- Each state change and log line is its own short transaction (`DeploymentRecorder`), so no connection is held while
  waiting on Helm or Kubernetes.
- Helm is driven through its CLI with an argument list (no shell) and validated values, because there is no mature
  Java Helm library. `CommandRunner` hides the process so callers are testable without the binary.
- Rollout status is read from the Kubernetes API (`KubernetesService`), not from `helm --wait`, so progress can be
  logged and pods stuck in `ImagePullBackOff`/`CrashLoopBackOff` fail fast.
- One in-flight deployment per project is enforced by a partial unique index (migration V2); the service check only
  gives a friendlier message.
- Deleting a project deletes its namespace (only if labelled `managed-by=deploykit`), best effort.

**Consequences**
- The engine assumes a **single backend instance**: on startup, deployments still in flight are marked `FAILED`
  because the workers that ran them are gone. Running several replicas needs a lease/heartbeat or a job queue.
- The backend needs the `helm` binary and cluster access. The Docker image does not contain Helm yet (Phase 12
  will run it in-cluster with a service account).
- Image pull secrets for private registries are not supported yet.

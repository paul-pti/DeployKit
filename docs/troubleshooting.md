# Troubleshooting

## Setup

- **`POSTGRES_PASSWORD` error from Compose:** create `.env` from `.env.example`.
- **Backend `Could not resolve placeholder 'DB_URL'`:** run with `SPRING_PROFILES_ACTIVE=local`, or export `DB_*`.
- **`Could not resolve placeholder 'POSTGRES_PASSWORD'` with `local`:** run from the `backend/` directory so `../.env` resolves.
- **Frontend shows "Backend unreachable":** the backend is not running on `:8080`.
- **Password changed but DB login fails:** the volume keeps the old password; `docker compose down -v` (deletes data).
- **Backend fails with `Port 8080 was already in use`:** another process holds it
  (`lsof -nP -iTCP:8080 -sTCP:LISTEN`).

## Deployments

- **Deployment `FAILED` with `Helm chart not found`:** start the backend from `backend/`, or set `DEPLOYKIT_HELM_CHART_PATH`.
- **Deployment `FAILED` with `Cannot execute 'helm'`:** install Helm and make sure it is on the backend's `PATH`.
- **Deployment `FAILED` with `Pod ... is ErrImagePull`/`ImagePullBackOff`:** the image does not exist or is private.
  Pass an existing `image` (for example `nginx:1.27-alpine`), or make sure the pipeline has published it and that the
  GHCR package is public ([github-actions.md](github-actions.md)).
- **`409 A deployment is already in progress`:** wait for the current one to finish, it fails on its own after at most
  the rollout timeout.

## Rollback

- **`409 Only the latest deployment (#N) can be rolled back`:** roll back from the newest deployment of the project.
- **`409 ... no earlier successful deployment with a different image`:** the project has no earlier version that ran
  successfully with another image. Pass an explicit `targetVersion` or deploy an image first.
- **The rollback deployed newer code than expected:** the restored image uses a moving tag (a branch name). Deploy
  commit-SHA tags to make rollbacks exact; the deployment log has a `WARN` line for this case.

## Logs

- **Logs say `container ... is waiting to start`:** the image is still being pulled or cannot be pulled. The
  *Deployment* tab has the reason; for a crash, tick *Previous container* to read the output of the last run.

## Kubernetes

- **`kubectl port-forward` shows nothing:** something else may hold the port (`lsof -nP -iTCP:<port> -sTCP:LISTEN`),
  or the forward listens on IPv6 only: use `--address 127.0.0.1,::1`.

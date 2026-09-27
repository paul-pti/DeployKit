# ADR 0005: A rollback is a new deployment

**Status:** accepted

**Context:** The history must record rollbacks (Phase 8), and rolling back has to work with what the platform already
guarantees: one in-flight deployment per project, a rollout that is monitored, logs that are stored.

**Decision**
- A rollback **creates a new deployment** (next version number, `rollback_of_version` = the restored version) instead
  of rewinding or editing an old one. It runs through the normal workflow, so it is monitored, logged, can fail, and
  cannot run concurrently with another deployment.
- It redeploys the image of the target version with `helm upgrade --install`, not `helm rollback`: the target is chosen
  from DeployKit's own history (which knows which versions ran successfully) rather than from Helm's revisions, and the
  same code path is exercised for every deployment.
- Only the **latest** deployment can be rolled back, so a rollback always undoes the current state and history stays
  linear. By default the target is the most recent earlier version that ran successfully **with a different image**.
- When a rollback reaches `RUNNING`, the deployments it replaced become `ROLLED_BACK` in the same transaction as the
  rollback's own status change. `FAILED` deployments keep their status, and a failed rollback changes nothing else.

**Consequences**
- History is append-only and self-explanatory: "#7 ↩ #5" reads as "version 7 restored version 5".
- A rollback is exact **only for immutable image tags**. With a moving tag (a branch name) the registry may serve newer
  content than at the original deployment. The rollback logs a warning; recording image digests at deployment time and
  deploying by digest would remove the limitation but needs chart and image-reference support.
- Rolling back a rollback is allowed and simply goes back to the previous successful version with another image.

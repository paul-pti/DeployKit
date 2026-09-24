# Security

What protects DeployKit today (Phase 9), and what does not yet.

## Authentication and authorization

- **Login** (`POST /api/auth/login`) checks an email and a password (bcrypt) and returns a signed JWT (HS256, 60 minutes
  by default). Every other `/api` call needs `Authorization: Bearer <token>`.
- **Roles:** `USER` manages their own projects, deployments, history and logs. `ADMIN` manages everything and is the
  only role that can create accounts (`/api/users`).
- **Ownership** is enforced in the **service layer** (`ProjectAccess`), not only on routes: every project and deployment
  is loaded through it. A resource that belongs to someone else answers **404**, exactly like a missing one, so ids
  cannot be probed. See [ADR 0006](adr/0006-jwt-and-ownership-in-the-service-layer.md).
- Accounts are created by an administrator (no self-registration). The first administrator comes from
  `DEPLOYKIT_ADMIN_EMAIL` / `DEPLOYKIT_ADMIN_PASSWORD`, created at startup if it does not exist.
- The React app keeps the token in `sessionStorage`, sends it on every call, clears everything (including the query
  cache) on logout, on expiry and on any 401, and hides the Users page from non-admins. That is only a convenience: the
  API enforces the rules.

## Risks and where they stand

| Risk | Status |
|---|---|
| Anonymous access to the API | Handled: 401 without a valid token; only login and health are public |
| A user reads or deploys someone else's project | Handled: ownership checks, 404 |
| Privilege escalation through a forged token | Handled: signature, issuer and expiry verified; role read from the signed claim |
| Credential guessing | Partly: 5 failures per client and 30 per email in 5 minutes, then 429. In memory, per instance |
| User enumeration through login | Handled: same answer and comparable timing for unknown email and wrong password |
| Secrets in Git or logs | Handled: JWT secret and passwords come from the environment; passwords and tokens are never logged |
| Clear-text traffic | **Not handled**: run behind TLS (Phase 12: load balancer / ingress) before exposing the API |
| Database reachable from the network | Compose binds to `127.0.0.1`; private subnet and RDS in Phase 12 |
| DeployKit's Kubernetes rights | **Not handled**: it uses the kubeconfig of whoever runs it. A least-privilege service account is needed |
| Tenants sharing a cluster | **Not handled**: one namespace per project, but no quotas, `NetworkPolicy` or Pod Security yet |
| Untrusted images | **Not handled**: any image reference is deployed. Needs an allow-list and scanning |
| Audit trail | **Not handled**: who deployed what is not recorded |
| Rate limiting of the whole API | **Not handled** (only login is limited) |
| Secrets manager | **Not handled**: environment variables only |

## Known limits

- **No refresh token:** a session ends when the token expires; the user signs in again.
- **A role change or account removal takes effect at token expiry**, since the role is read from the token.
- **The login limiter is in memory:** it resets on restart and is not shared between instances. Several users behind one
  address share the per-client budget.
- **`sessionStorage` is readable by scripts** if the page suffers an XSS. It avoids CSRF and cookies; a shorter TTL or an
  httpOnly cookie flow are the alternatives.
- **Projects created before Phase 9 have no owner** and are visible to administrators only.

# ADR 0006: Stateless JWT, ownership in the service layer

**Status:** accepted

**Context:** Phase 9 adds accounts and two roles. The API is stateless, may run in several instances, and the frontend
is a separate single-page app.

**Decision**
- **Stateless JWT** (HS256) issued by the backend and verified by Spring Security's resource server. No server session,
  no cookie, so no CSRF surface. The secret is `DEPLOYKIT_JWT_SECRET`; the backend **refuses to start without it**.
- Claims: `sub` (user id), `email`, `role`, `iss`, `iat`, `exp`. The decoder checks signature, issuer and expiry.
- The **route rules** are coarse: `/api/auth/login` and health are public, `/api/users/**` needs `ADMIN`, everything
  else under `/api` needs authentication, anything else is denied.
- **Ownership is decided in the service layer** through `ProjectAccess`, which loads projects and deployments for the
  current user. Deployments are reached through their project, so the rule cannot be forgotten on a new endpoint. A
  foreign resource is reported as **404**, identical to a missing one.
- Passwords use the `DelegatingPasswordEncoder` (bcrypt today, upgradable). Login runs a decoy hash for unknown emails
  and is rate limited.
- `projects.owner_id` (V5) is nullable so existing rows survive; ownerless projects are visible to administrators only.
  Project names are unique **per owner**.

**Consequences**
- Horizontal scaling needs no shared session store.
- A token cannot be revoked before it expires and carries the role as of login; a short TTL bounds both.
- HS256 means every verifier holds the signing secret. That is fine for a single backend; splitting services would call
  for an asymmetric key.
- The in-memory login limiter is per instance; a shared store is needed once there are several instances.

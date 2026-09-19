# ADR 0001: Modular monolith

**Status:** accepted

**Context:** DeployKit has one team and one deployable domain. Microservices would add network, deployment and
observability overhead without benefit.

**Decision:** A single Spring Boot application with layered packages. Boundaries are kept by package structure
(controller → service → repository) so features can be split later if needed.

**Consequences:** Simple local setup and transactions; must keep layering discipline in review.

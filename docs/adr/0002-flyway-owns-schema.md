# ADR 0002: Flyway owns the schema

**Status:** accepted

**Context:** Hibernate auto-DDL is unsafe beyond prototypes and hides schema changes.

**Decision:** All schema changes are Flyway migrations (`backend/src/main/resources/db/migration`).
`spring.jpa.hibernate.ddl-auto=validate` makes startup fail if entities and schema diverge. UUID primary keys
(`gen_random_uuid()`), `timestamptz` timestamps, and check constraints for status and level values.

**Consequences:** Applied migrations are immutable; changes go in new `V<n>__` files.

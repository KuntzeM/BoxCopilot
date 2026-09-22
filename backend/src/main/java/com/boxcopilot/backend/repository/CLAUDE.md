# Backend Persistence Instructions

Repositories and migrations define the persistence contract.

- Use Spring Data repository methods and existing query conventions before adding custom SQL.
- Production schema changes require a new numbered Flyway migration under `backend/src/main/resources/db/migration/`; never edit an applied migration.
- Keep entity mappings compatible with both H2 development/tests and PostgreSQL production where the profile supports both.
- Consider uniqueness, nullability, cascade behavior, indexes, and cleanup when changing entities or queries.
- Do not introduce eager loading to solve a service-layer transaction problem; fix the transaction boundary or query explicitly.
- Persistence tests should verify the behavior that matters, not implementation details of Hibernate.

Run the focused repository test first. Tests involving `PostgresContainerSupport` require Docker.

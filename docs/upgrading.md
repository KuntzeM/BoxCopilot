# Upgrading

Read the notes of every release between your version and the target. Back up the database and the images first, see [Backup and restore](ops/backup-restore.md).

| Release | Notes |
|---|---|
| 0.2.0 | [0.2.0](releases/0.2.0.md): upgrade steps in order (backup, `ADMIN_PASSWORD` of at least 12 characters, `chown` for the non-root backend, `TRUSTED_PROXY_PATTERN`, new variables, everyone signs in again, switching the CSP from report-only to enforce), the migrations V15 and V16, and the rollback procedure |
| 2.0.0 | [2.0.0](releases/2.0.0.md) |
| 2.0.1 | [2.0.1](releases/2.0.1.md): Node.js 26.13.1 in the Docker build, no upgrade steps |
| Spring Boot 4 | [Spring Boot 4 upgrade](releases/spring-boot-4-upgrade.md): the backend runs on Spring Boot 4.1. Preparation, changes, rollback and known issues |
| Security hardening 2026-11 | [Security hardening](releases/security-hardening-2026-11.md) |
| Single image | [Single image](releases/single-image.md): frontend and backend in one container |

## Database migrations

Schema changes ship as Flyway migrations under `backend/src/main/resources/db/migration/` and run on startup. An applied migration is never edited; fixes come as new migrations.

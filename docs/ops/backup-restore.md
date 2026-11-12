# Backup and restore

BoxPilot keeps its state in two places that belong together:

| What | Where (default `docker-compose.yml`) | Back up? |
|---|---|---|
| Database: users, boxes, items, MCP authorizations, magic links, and the file path of every item image | PostgreSQL, bind mount `./pgdata` | **Yes**, with `pg_dump` |
| Image files (thumbnails and large versions) | Backend bind mount `./images` (`/var/boxcopilot/images`) | **Yes** |
| Sessions | Redis | No. Redis holds only login sessions; after a restore everyone signs in again. |
| Logs | `./logs` | Optional |
| Configuration and secrets | `.env` | Yes, but keep it separate from the data backup (it holds passwords) |

The database references image files by path, so a dump and an image copy from different points in time can
disagree (items pointing at missing files, or files nobody references). Take both at the same time.

## Backup

Run from the directory that contains `docker-compose.yml` and `.env`.

```bash
STAMP=$(date +%Y%m%d-%H%M%S)
mkdir -p backups

# 1. Optional but safest: stop writers so that dump and images describe the same moment.
docker compose stop backend

# 2. Database. The credentials come from the postgres container's own environment.
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner' \
  | gzip > "backups/boxpilot-db-$STAMP.sql.gz"

# 3. Images, same moment.
tar -czf "backups/boxpilot-images-$STAMP.tar.gz" -C images .

# 4. Start the backend again.
docker compose start backend
```

`docker compose stop backend` costs a short outage (the image volume cannot change while it is stopped).
If you cannot afford it, skip steps 1 and 4: run steps 2 and 3 back to back during a quiet period and accept that a
photo uploaded in between may be missing from one of the two. Checks that the backup is complete:

```bash
gzip -t backups/boxpilot-db-$STAMP.sql.gz        # archive intact
tar -tzf backups/boxpilot-images-$STAMP.tar.gz | head
```

Copy both files off the host. Do the same **before every upgrade** (see [`docs/releases/0.2.0.md`](../releases/0.2.0.md)).
Flyway migrations only go forward, so a dump is the only way back to the previous schema.

## Restore

Order matters: database first, then images, backend last.

```bash
# 0. Stop everything that uses the data.
docker compose stop frontend backend

# 1. Empty database. The postgres container must be running.
docker compose up -d postgres
docker compose exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" --if-exists "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'

# 2. Load the dump.
gunzip -c backups/boxpilot-db-<STAMP>.sql.gz \
  | docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'

# 3. Replace the image files with the matching copy.
rm -rf images/* && tar -xzf backups/boxpilot-images-<STAMP>.tar.gz -C images
sudo chown -R 10001:10001 images        # the backend runs as uid 10001

# 4. Start the backend (and the frontend once it is healthy).
docker compose up -d
```

Notes:

- Restoring a dump taken on an older release into a newer image is fine: Flyway applies the missing migrations on
  start. Restoring into an **older** image than the dump was taken with only works if that image can read the schema
  (see "Rollback" in the release notes).
- Redis needs nothing. Sessions are gone, users sign in again.
- `.env` must contain the same `POSTGRES_USER` / `POSTGRES_DB` as at backup time, or adjust the commands.

## Test the restore

A backup nobody has restored is a hope. After the first backup, and now and then after that, restore into a throwaway
stack instead of production, for example on another host or in a separate compose project with its own directory,
`.env` and different `TRAEFIK_*_HOST` values (or the E2E overlay's ports). Then check:

1. The backend becomes healthy (`curl -s http://<host>:8080/actuator/health` answers `{"status":"UP"}`).
2. You can sign in and the box and item counts look right.
3. A few item images and the public link of a box open (`/public/{uuid}`), which proves the image files match the
   database.

Remove the throwaway stack afterwards (`docker compose -p <name> down -v`) and delete its `pgdata` and `images` directories, which are bind mounts that `down -v` leaves in place.

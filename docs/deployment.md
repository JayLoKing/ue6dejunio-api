# Run the containerized school system

Four private-network services are defined in `compose.yaml`: PostgreSQL 18,
Java 25 API, Linux amd64 prediction inference, and a public-facing static web server.
Only the web server publishes a loopback port locally; `/api/` is proxied internally.
Local builds and one synthetic end-to-end prediction passed on a disposable environment.
Production deployment, recovery checks and browser workflows still require validation.

## Quick path: a new, empty local installation

1. Keep the three repositories as sibling directories. Copy `.env.docker.example`
   to an untracked `.env.docker.local` (covered by `.gitignore`'s `*.local` rule).
   Existing application `.env` files are not deployment input and must not be copied.
2. Store generated secrets OUTSIDE all Git repositories. Set absolute paths in the
   local deployment environment: DB_ADMIN_PASSWORD_FILE, DB_PASSWORD_FILE, PREDICTION_TOKEN_FILE,
   JWT_PUBLIC_KEY_FILE, JWT_PRIVATE_KEY_FILE. Never commit those files. Use a strong,
   distinct administrator/application DB passwords and prediction token. Keep
   DB_ADMIN_USERNAME different from DB_USERNAME. Generate an RSA key pair, for example:

   ```sh
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out private.pem
   openssl pkey -in private.pem -pubout -out public.pem
   ```

3. API and IA run as UID/GID 10001. Give that UID read access to their mounted
   application-password, prediction-token and PEM files, plus read/traverse access
   to the IA artifact. The DB administrator password is mounted ONLY in db.
   Both DB password files must be readable by the image's `postgres` OS account:
   determine its UID after building with `docker compose --env-file .env.docker.local
   run --rm --no-deps --entrypoint id db postgres`. Grant read ACLs only to that UID
   and, for the shared application-password file, UID 10001. Keep parent directories
   traversable by the intended readers and deny unrelated users access. Do not use
   world-readable permissions. Local Compose file secrets may use bind mounts and
   ignore requested UID/mode remapping: verify actual mounted readability, not just
   YAML permissions. Apply equivalent owner/group/ACL controls to Dokploy mounts.
4. Provision the existing complete `tfdf_riesgo` artifact separately. Set
   MODEL_DIRECTORY to its parent. The artifact is read-only, private and unversioned;
   no training or transfer to a VPS is performed by these files.
5. Set SMTP and initial Director variables explicitly. A fresh database has no users,
   courses or students. The existing year bootstrap creates/activates the current
   La Paz calendar year when no year is active. The Director bootstrap creates its account
   only when its required fields are configured; manage catalogs/year after login.
6. Validate and build before starting services:

   ```sh
   docker compose --env-file .env.docker.local config --quiet
   docker compose --env-file .env.docker.local build
   docker compose --env-file .env.docker.local up -d
   docker compose --env-file .env.docker.local ps
   ```

The browser URL is `http://localhost:8080`. VITE_API_URL must be an absolute browser
URL ending in `/api`, with no trailing slash; the local example uses the same origin.
Never use `http://api:8090` as a browser URL. Rebuild web when this value changes.

## Database initialization and persistence

PostgreSQL 18 persists at `/var/lib/postgresql`, in the project's named `database`
volume. Keep COMPOSE_PROJECT_NAME stable. Never run `down -v` to redeploy or fix an
initialization error. Changing DB environment variables does not rewrite an existing
database's credentials. Take and verify backups before any future change.

On an EMPTY volume only, the PostgreSQL entrypoint runs `init.sh`. Default mode
`fresh` runs `schema.sql` and application-role grants with ON_ERROR_STOP in one
transaction. The SQL
refuses any target with user tables, views, sequences or foreign tables. It does not
run historical scripts or RESET files. A failed first initialization may leave an
initialized PGDATA: restarting does not rerun init scripts. Inspect the failure and
use a NEW disposable target; do not delete an existing volume automatically.

### Baseline provenance and limits

`docker/postgres/schema.sql` is a fresh-only candidate, not a migration runner.
Its DDL was reconciled against all 26 current entity mappings, the repository's
integration DDL, and critical V9–V28 changes. Integration fixture seed data was
excluded. It seeds only Director/Secretary/Teacher and the four knowledge areas.
It does not claim to reproduce every historical production index or data correction.

| Concern | Baseline representation |
|---|---|
| Current entity names | id_user/id_role and current course, enrollment, PDC tables |
| V9 criteria/activity scoring | exclusive target CHECK and two partial score UNIQUE indexes |
| V11–V12 PDC | monthly subject/entry structure, adaptations unique per plan/student |
| V13–V14 | notification subject/receipts; student status audit fields |
| V15–V16 | risk per subject/trimester; knowledge-area display order |
| V17–V21 | pedagogical reports and failure FK index; queue school-time default |
| V22–V24 | single active year, frozen results, homeroom/year partial UNIQUE |
| V25–V26 | score authors and constrained single pending Director handover |
| V27–V28 | normalized-email CHECK and partial phone UNIQUE |
| Grade totals / attendance | stored generated total_score; separate daily/session UNIQUE indexes |

Observed on PostgreSQL 18: API startup with Hibernate `validate` succeeded. Still inspect
generated totals, constraints and catalogs. Static reconciliation alone is NOT
runtime proof. Historical V1–V8 use incompatible old names; never mount docs/sql as
an initialization directory. This project has no Flyway/Liquibase. Future schema
updates require reviewed, explicit SQL and a backup; init scripts do not upgrade DBs.

## Explicit restore into another EMPTY target

This path is for a later authorized import, not part of fresh startup. Use a custom
`pg_dump -Fc` archive compatible with PostgreSQL 18 and a separately named project
and NEW empty volume. Set UE6_DATABASE_MODE=restore BEFORE its first initialization.
Start only `db`; do NOT start API/IA/web. Verify the target database identity and stop
all application writers. The restore helper refuses existing user relations and
requires an explicit confirmation. Example after independently reviewing the target:

```sh
docker compose --env-file .env.restore.local up -d db
docker compose --env-file .env.restore.local cp /private/backup.dump db:/tmp/backup.dump
docker compose --env-file .env.restore.local exec -e RESTORE_CONFIRM=EMPTY_TARGET_APPS_STOPPED db /opt/ue6/restore.sh /tmp/backup.dump
```

The helper validates the archive and runs pg_restore as the administrator in a
single transaction, with no `--clean`, `--create`, owner or ACL restoration. After
success it provisions a NEW non-owner application role and grants in a separate
transaction; on grant failure keep the API stopped and inspect the target, never
rerun restore over the now-populated database. Existing application roles are
rejected rather than inheriting unknown privileges/memberships. Only use trusted archives: database
dumps can contain executable SQL. Role/ACL and schema compatibility must be assessed
separately; do not import into a fresh-seeded schema. Plain SQL/data-only dumps are not
accepted by this procedure. After restoring, validate schema and counts before starting
writers. Retain the original backup and never overwrite the old database/volume.

## Independent Dokploy applications

Local sibling build contexts are NOT a deployment topology. In Dokploy create an
Application per repository with its own Dockerfile, plus a PostgreSQL database or
dedicated deployment of `docker/postgres/Dockerfile` from this repository. Existing
data is not implicitly moved. Use a private shared network and actual service DNS
names; only web gets a public HTTPS domain on port 8080. Configure API_UPSTREAM on
web to the API DNS:8090. API and IA need no public domain or published host ports.
Do not assume resource names or automatic cross-application network reachability.

API environment: DB_HOST/PORT/NAME/USERNAME, SPRING_PROFILES_ACTIVE=prod,
SPRING_CONFIG_IMPORT=configtree:/run/secrets/, PREDICTION_URL=http://<ia-dns>:8001,
CORS_ORIGINS=https://<web-domain>, FRONTEND_RESET_URL=https://<web-domain>/auth/reset-password,
SMTP variables and optional first-run Director variables. Mount config-tree files
named DB_PASSWORD and PREDICTION_TOKEN; mount the matching PEM pair and set
APP_SECURITY_JWT_PUBLIC_KEY=file:/run/secrets/jwt_public and
APP_SECURITY_JWT_PRIVATE_KEY=file:/run/secrets/jwt_private. Do not rely on classpath keys.
Config-tree/PEM mounts must be readable by UID 10001, while remaining private.

The DB image needs POSTGRES_DB, POSTGRES_USER (the administrator), POSTGRES_PASSWORD_FILE
(administrator secret), UE6_APP_USERNAME and UE6_APP_PASSWORD_FILE (application secret).
Compose maps DB_ADMIN_USERNAME/DB_ADMIN_PASSWORD_FILE to the administrator and
DB_USERNAME/DB_PASSWORD_FILE to the application. The API never receives admin credentials.
Fresh init creates a NEW LOGIN role with NOSUPERUSER, NOCREATEDB, NOCREATEROLE,
NOREPLICATION and NOBYPASSRLS; same-as-admin, reserved and existing role names fail closed.
Objects stay admin-owned. The role receives CONNECT, public-schema USAGE, table
SELECT/INSERT/UPDATE/DELETE and sequence USAGE/SELECT, plus equivalent administrator
default privileges for future public objects. PUBLIC database/schema grants are revoked;
no CREATE, ownership, role membership, TRUNCATE or elevated role attributes are granted.
Verify those privileges on a disposable instance before handling real data.
The API and DB APPLICATION-password mounts must contain the same value; keep the
ADMIN password different. API and IA prediction
token files must match. IA accepts UE6_API_TOKEN_FILE or UE6_API_TOKEN; use a private
artifact mount at /models with UE6_MODELS_DIR=/models. Never expose port 8001 or 5432.
Use Dokploy protected environment/file mounts; never put secret values in build args.
Do not trust forwarded IP headers without reviewing the actual proxy chain; the
existing direct resolver is unchanged. Per-user rate limiting still applies.

Use stable named volumes for DB. Provision model/keys outside disposable checkouts;
Dokploy reclones source on deployment. DB backups, key escrow and model transfer
require separately authorized destinations and operations. No registry publication,
remote login or DNS change is performed here.

References: [Dokploy Compose](https://docs.dokploy.com/docs/core/docker-compose),
[domains](https://docs.dokploy.com/docs/core/docker-compose/domains),
[Docker startup order](https://docs.docker.com/compose/how-tos/startup-order/),
[Postgres volume layout](https://hub.docker.com/_/postgres).

## Domain topology for jaylokingdev.tech

Use `https://ue6.jaylokingdev.tech` for the public web application. Configure the
web build with `VITE_API_URL=https://ue6.jaylokingdev.tech/api`, and set its runtime
`API_UPSTREAM` to the private API service DNS and port 8090. The existing Nginx
configuration forwards `/api/` without removing that prefix.

Only this web hostname needs a public DNS record and HTTPS route. API, IA and
PostgreSQL communicate over the private deployment network: they do not require
public DNS records, subdomains or published ports. Set API CORS_ORIGINS to the web
origin and FRONTEND_RESET_URL to `https://ue6.jaylokingdev.tech/auth/reset-password`.
Creating DNS records alone does not attach a domain to a Dokploy application;
configure the HTTPS application route separately. No DNS change was performed here.

## Verification and remaining work

### Observed local smoke: 2026-10-19

- API: `docker build -t ue6-smoke-api:local .` passed using cached layers.
- IA: `docker build --platform linux/amd64 -t ue6-smoke-ia:local ../ue6dejunio-ia`
  passed using cached layers. Web and DB reused previously built smoke images.
- Isolated Compose project `ue6-check-20261019095702` used a new database volume,
  synthetic credentials and the existing private model mount. All four services
  became healthy; no existing school database was modified.
- API startup passed Hibernate schema validation and bootstrapped a synthetic
  Director plus academic year 2026 against PostgreSQL 18.
- Web `/healthz` and `/auth/reset-password` returned HTTP 200 on local port 18086.
  Director login succeeded through the web `/api` reverse proxy.
- Year prediction on an empty database returned zero considered/predicted records.
  After inserting one synthetic student/subject fixture, the same endpoint returned
  `considered=1, predicted=1, changed=1`. The DB contained one `risk_predictions`
  record and IA logged `POST /predict/batch` HTTP 200 with the real model loaded.
- An earlier disposable harness failed because its secret paths did not exist;
  Docker created directories instead of files. A separate new project with secret
  files created before startup passed. Failed resources were preserved, not deleted.
- API image ID: `sha256:e6f28ccde761e54b9af0456d20478138874f0f4c3cc694827c1453e8240f2b2f`.
- IA image ID: `sha256:2cf25818882e48985e4ccff370f193066faec2cdfdda20236725f0602c3dd083`.

### Limits and production checks

- These are local build/smoke results, not a full unit/integration-suite or browser
  E2E result, and not a VPS deployment. `bootJar` does not execute the whole test suite.
- API healthcheck is TCP-only (8090), not `/actuator/health`; it cannot independently
  prove authentication, DB access or model connectivity. IA readiness requires JSON
  `status=ok`, not merely HTTP 200; web health checks static Nginx only.
- Validate generated totals/indexes/constraints, application-role privilege denials,
  restore refusal/success and restart persistence. Earlier DB verification is recorded
  separately in project memory; it was not rerun in this smoke.
- Still test bad/missing IA tokens, missing model, stopped DB/IA, logout, role access,
  API errors, browser reset flow and SSE behavior before handling real school data.
- Provision real SMTP, private keys, model permissions, backups and a tested recovery
  procedure. Neither model quality nor operational readiness follows from health alone.
- Long uncached builds can download several GB of Java/Bun/TensorFlow layers.
- Image base tags are not digest-pinned; recorded output IDs identify these local
  builds but do not make future tag-based builds reproducible.
- No VPS connection, SSH use, registry publication, Git push or DNS mutation occurred.

#!/bin/sh
set -eu
export PGUSER="${POSTGRES_USER:?}" PGDATABASE="${POSTGRES_DB:?}"
: "${UE6_APP_USERNAME:?Set a separate application role}"
test "$UE6_APP_USERNAME" != "$POSTGRES_USER" || {
  echo 'Application role must differ from the database administrator.' >&2; exit 1;
}
UE6_APP_PASSWORD=$(cat "${UE6_APP_PASSWORD_FILE:?}")
test -n "$UE6_APP_PASSWORD" || { echo 'Application password is empty.' >&2; exit 1; }
export UE6_APP_PASSWORD

application_sql() {
  # Read secrets through environment, not command arguments or shell-expanded SQL.
  # Reject any existing role, including inherited memberships/ownership we cannot trust.
  cat <<'SQL'
\getenv app_user UE6_APP_USERNAME
\getenv app_password UE6_APP_PASSWORD
\getenv database POSTGRES_DB
SELECT current_user <> :'app_user'
   AND octet_length(:'app_user') BETWEEN 1 AND 63
   AND :'app_user' NOT LIKE 'pg\_%' ESCAPE '\'
   AND NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user') AS new_app_role \gset
\if :new_app_role
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD %L',
              :'app_user', :'app_password') \gexec
\else
\echo 'Refusing application role: same as admin, reserved/invalid name, or already exists.'
\quit 1
\endif
REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE :"database" TO :"app_user";
GRANT USAGE ON SCHEMA public TO :"app_user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"app_user";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
SQL
}

# Called only after the separately guarded restore succeeds. No schema ownership moves.
if test "${1:-}" = --grant-app; then
  application_sql | psql --no-psqlrc --set ON_ERROR_STOP=1 --single-transaction --file -
  exit 0
fi
case "${UE6_DATABASE_MODE:-fresh}" in
  fresh)
    # PostgreSQL's entrypoint only calls this on first initialization of PGDATA.
    # The SQL itself also refuses an existing application schema.
    application_sql | psql --no-psqlrc --set ON_ERROR_STOP=1 --single-transaction \
      --file /opt/ue6/schema.sql --file -
    ;;
  restore)
    echo 'Empty target created. Restore explicitly before starting the API.'
    ;;
  *) echo 'UE6_DATABASE_MODE must be fresh or restore.' >&2; exit 1 ;;
esac

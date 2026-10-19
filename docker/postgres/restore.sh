#!/bin/sh
set -eu
test "${RESTORE_CONFIRM:-}" = EMPTY_TARGET_APPS_STOPPED || {
  echo 'Stop the API and set RESTORE_CONFIRM=EMPTY_TARGET_APPS_STOPPED.' >&2; exit 1;
}
test "$#" -eq 1 && test -f "$1" && test -r "$1" || {
  echo 'Pass one readable custom-format pg_dump archive.' >&2; exit 1;
}
test "$(head -c 5 "$1")" = PGDMP || {
  echo 'Only custom-format pg_dump archives are accepted.' >&2; exit 1;
}
export PGUSER="${POSTGRES_USER:?}" PGDATABASE="${POSTGRES_DB:?}"
if test -n "${POSTGRES_PASSWORD_FILE:-}"; then
  PGPASSWORD=$(cat "$POSTGRES_PASSWORD_FILE"); export PGPASSWORD
fi
# Read-only archive validation before the empty-target check. Never --clean/--create.
pg_restore --list "$1" >/dev/null
psql --no-psqlrc --set ON_ERROR_STOP=1 <<'SQL'
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
               AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r','p','v','m','S','f')) THEN
    RAISE EXCEPTION 'Restore refused: target contains user relations';
  END IF;
END $$;
SQL
pg_restore --exit-on-error --single-transaction --no-owner --no-privileges \
  --dbname "$PGDATABASE" "$1"
# Restored objects remain owned by the administrator; install a new non-owner role
# only after successful restoration. Failure leaves writers stopped for inspection.
exec /docker-entrypoint-initdb.d/10-ue6-init.sh --grant-app

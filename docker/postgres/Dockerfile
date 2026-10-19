FROM postgres:18-bookworm
COPY --chmod=755 docker/postgres/init.sh /docker-entrypoint-initdb.d/10-ue6-init.sh
COPY --chmod=755 docker/postgres/restore.sh /opt/ue6/restore.sh
COPY docker/postgres/schema.sql /opt/ue6/schema.sql

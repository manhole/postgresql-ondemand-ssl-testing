#!/usr/bin/env bash
set -Eeo pipefail

sed -i.bak -r 's/^(host all all all .+)$/#\1/' "${PGDATA}/pg_hba.conf"
echo "hostssl all user_hostssl all md5" >> "${PGDATA}/pg_hba.conf"
echo "hostnossl all user_hostnossl all md5" >> "${PGDATA}/pg_hba.conf"
diff "${PGDATA}/pg_hba.conf.bak" "${PGDATA}/pg_hba.conf" || true

sed -i.bak -r 's/^#ssl = off$/ssl = on/' "${PGDATA}/postgresql.conf"
diff "${PGDATA}/postgresql.conf.bak" "${PGDATA}/postgresql.conf" || true

CURRENT_DIR=$(cd $(dirname $0); pwd)

cp "${CURRENT_DIR}/server.crt" "${PGDATA}/server.crt"
cp "${CURRENT_DIR}/server.key" "${PGDATA}/server.key"
chmod 600 "${PGDATA}/server.key"

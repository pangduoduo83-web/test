#!/usr/bin/env bash
set -euo pipefail

PACKAGE_PATH="${1:-/home/admin/ioedu-flow/package.tgz}"
LIVE="/opt/ioedu"
STAMP="${FLOW_BUILD_NUMBER:-${BUILD_NUMBER:-$(date +%Y%m%d-%H%M%S)}}"
RELEASE="/opt/ioedu-releases/cloud-${STAMP}"
BACKUP="/opt/ioedu-backups/cloud-${STAMP}"
PROJECT="ioedu"

test -s "$PACKAGE_PATH"
test -s "$LIVE/.env"
mkdir -p "$RELEASE" "$BACKUP"
chmod 700 "$RELEASE" "$BACKUP"

# Keep production secrets outside the repository and outside the uploaded artifact.
cp -p "$LIVE/.env" "$BACKUP/live.env"
chmod 600 "$BACKUP/live.env"

cd "$LIVE"
docker inspect ioedu-backend ioedu-hub ioedu-frontend --format ''{{.Name}} {{.Config.Image}}'' > "$BACKUP/images.txt"
docker exec ioedu-mysql sh -c ''exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction --routines --events --triggers --set-gtid-purged=OFF'' | gzip > "$BACKUP/mysql.sql.gz"
gzip -t "$BACKUP/mysql.sql.gz"

tar -xzf "$PACKAGE_PATH" -C "$RELEASE"
test -s "$RELEASE/docker-compose.yml"
test -s "$RELEASE/docker-compose.https.yml"
cp -p "$LIVE/.env" "$RELEASE/.env"

# Give each release its own image tags so the previous release remains rollback-capable.
for pair in \
  "IOEDU_IMAGE=ioedu-backend:cloud-${STAMP}" \
  "IOEDU_HUB_IMAGE=ioedu-hub:cloud-${STAMP}" \
  "IOEDU_FRONTEND_IMAGE=ioedu-frontend:cloud-${STAMP}"; do
  key="${pair%%=*}"
  value="${pair#*=}"
  if grep -q "^\${key}=" "$LIVE/.env"; then
    sed -i -E "s#^\${key}=.*#\${key}=''${value}''#" "$RELEASE/.env"
  else
    printf "%s=''%s''\n" "$key" "$value" >> "$RELEASE/.env"
  fi
done
chmod 600 "$RELEASE/.env"

ln -sfn "$RELEASE" /opt/ioedu-current

compose() {
  docker compose --env-file "$RELEASE/.env" -p "$PROJECT" \
    -f "$RELEASE/docker-compose.yml" -f "$RELEASE/docker-compose.https.yml" "$@"
}

compose config -q
compose build backend hub frontend
compose up -d --no-build --no-deps --wait --wait-timeout 180 backend hub
compose up -d --no-build --no-deps --wait --wait-timeout 180 frontend

curl -fsS http://127.0.0.1:8093/ >/dev/null
docker exec ioedu-hub sh -c ''curl -fsS http://127.0.0.1:8081/actuator/health'' >/dev/null
docker inspect ioedu-backend ioedu-hub ioedu-frontend --format ''{{.Name}} {{.Config.Image}} {{.State.Status}}''
printf ''%s\n'' "$(date -Iseconds)" > "$RELEASE/deployed"
printf ''release=%s backup=%s\n'' "$RELEASE" "$BACKUP"


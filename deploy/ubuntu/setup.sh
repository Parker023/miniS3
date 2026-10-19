#!/usr/bin/env bash
# Installs or updates miniS3 on Ubuntu (22.04+). Safe to re-run: secrets are generated once and kept.
#
#   sudo bash setup.sh /path/to/minis3-storage-service.jar
#
# Creates: PostgreSQL database + user, system user `minis3`, /opt/minis3 (jar),
# /var/lib/minis3/storage (objects), /etc/minis3/minis3.env (secrets, root/minis3 only), systemd unit.
set -euo pipefail

JAR="${1:?usage: sudo bash setup.sh /path/to/minis3-storage-service.jar}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE=/etc/minis3/minis3.env
STORAGE_DIR=/var/lib/minis3/storage
DB_NAME=minis3
DB_USER=minis3

[[ $EUID -eq 0 ]] || { echo "run with sudo" >&2; exit 1; }
[[ -f "$JAR" ]] || { echo "jar not found: $JAR" >&2; exit 1; }

echo "==> packages (Java 21, PostgreSQL)"
if ! command -v java >/dev/null || ! java -version 2>&1 | grep -q '"21'; then
    apt-get update -qq
    apt-get install -y -qq openjdk-21-jre-headless
fi
if ! command -v psql >/dev/null; then
    apt-get update -qq
    apt-get install -y -qq postgresql
fi
systemctl enable --now postgresql
# a freshly installed cluster takes a few seconds before it accepts connections
for _ in $(seq 1 30); do
    pg_isready -q && break
    sleep 1
done
pg_isready -q || { echo "PostgreSQL is not accepting connections, see: pg_lsclusters" >&2; exit 1; }

echo "==> system user and directories"
id minis3 >/dev/null 2>&1 || useradd --system --home-dir /var/lib/minis3 --shell /usr/sbin/nologin minis3
install -d -o minis3 -g minis3 -m 750 /var/lib/minis3 "$STORAGE_DIR"
install -d -o root -g root -m 755 /opt/minis3
install -d -o root -g minis3 -m 750 /etc/minis3

echo "==> secrets"
if [[ -f "$ENV_FILE" ]]; then
    echo "    keeping existing $ENV_FILE"
    # read only what's needed; sourcing would break on hand-edited lines that aren't valid shell
    DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
    [[ -n "$DB_PASSWORD" ]] || { echo "DB_PASSWORD missing from $ENV_FILE" >&2; exit 1; }
    STORAGE_DIR="$(grep -E '^MINIS3_STORAGE_LOCATION=' "$ENV_FILE" | head -1 | cut -d= -f2-)"
    [[ "$STORAGE_DIR" == /* ]] || { echo "MINIS3_STORAGE_LOCATION in $ENV_FILE must be an absolute path, got: '$STORAGE_DIR'" >&2; exit 1; }
else
    DB_PASSWORD="$(openssl rand -base64 30 | tr -dc 'A-Za-z0-9' | head -c 32)"
    JWT_SECRET="$(openssl rand -base64 32)"
    umask 027
    cat > "$ENV_FILE" <<ENV
# miniS3 configuration, read by systemd (minis3.service). Keep private.
DB_URL=jdbc:postgresql://localhost:5432/${DB_NAME}
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}
MINIS3_STORAGE_LOCATION=${STORAGE_DIR}
# only reachable through 'tailscale serve' (or ssh), never directly from the network
SERVER_ADDRESS=127.0.0.1
# set to the tailscale serve URL so pre-signed links point at it:
# MINIS3_PUBLIC_URL=https://<machine>.<tailnet>.ts.net/storage/minis3
ENV
    chown root:minis3 "$ENV_FILE"
    chmod 640 "$ENV_FILE"
    echo "    generated new DB password and JWT secret in $ENV_FILE"
fi

echo "==> PostgreSQL database"
sudo -u postgres psql -q -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
        CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';
    ELSE
        ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASSWORD}';
    END IF;
END
\$\$;
SQL
if ! sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname = '${DB_NAME}'" | grep -q 1; then
    sudo -u postgres createdb -O "$DB_USER" "$DB_NAME"
fi

echo "==> application"
install -o root -g root -m 644 "$JAR" /opt/minis3/minis3-storage-service.jar
install -o root -g root -m 644 "$SCRIPT_DIR/minis3.service" /etc/systemd/system/minis3.service
systemctl daemon-reload
systemctl enable minis3 >/dev/null
systemctl restart minis3

echo "==> waiting for the service"
for _ in $(seq 1 60); do
    # an unauthenticated call answers 401 once the app is up
    code="$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:2399/storage/minis3/api/v1/bucket/ || true)"
    if [[ "$code" == "401" ]]; then
        echo "    miniS3 is up on http://127.0.0.1:2399/storage/minis3"
        break
    fi
    sleep 2
done
[[ "${code:-}" == "401" ]] || { echo "service did not come up, see: journalctl -u minis3 -n 100" >&2; exit 1; }

cat <<SUMMARY

PostgreSQL credentials (also in $ENV_FILE):
  host:     localhost:5432
  database: ${DB_NAME}
  user:     ${DB_USER}
  password: ${DB_PASSWORD}
  connect:  psql "postgresql://${DB_USER}:<password>@localhost:5432/${DB_NAME}"

Objects are stored in ${STORAGE_DIR}
Logs:     journalctl -u minis3 -f
Expose on your tailnet (HTTPS):  sudo tailscale serve --bg 2399
SUMMARY

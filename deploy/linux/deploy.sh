#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INSTALL_DIR="${INSTALL_DIR:-/opt/tradn}"
ENV_FILE="${ENV_FILE:-$ROOT/.env}"
for cmd in java mvn node npm nginx systemctl; do command -v "$cmd" >/dev/null || { echo "Required command not found: $cmd" >&2; exit 1; }; done
test -f "$ENV_FILE" || { echo "Missing environment file: $ENV_FILE" >&2; exit 1; }
(cd "$ROOT/tradn-backend" && mvn clean package)
(cd "$ROOT/tradn-web" && npm ci && npm run build)
if ! getent group tradn >/dev/null; then sudo groupadd --system tradn; fi
if ! id tradn >/dev/null 2>&1; then sudo useradd --system --gid tradn --home-dir "$INSTALL_DIR" --shell /usr/sbin/nologin tradn; fi
sudo install -d -o tradn -g tradn "$INSTALL_DIR" "$INSTALL_DIR/web"
sudo install -o tradn -g tradn "$ROOT"/tradn-backend/target/tradn-backend-*.jar "$INSTALL_DIR/tradn.jar"
sudo cp -a "$ROOT/tradn-web/dist/." "$INSTALL_DIR/web/"
sudo install -m 600 -o tradn -g tradn "$ENV_FILE" "$INSTALL_DIR/.env"
sudo tee /etc/systemd/system/tradn.service >/dev/null <<UNIT
[Unit]
Description=TradN API
After=network.target mysql.service redis.service minio.service
[Service]
User=tradn
WorkingDirectory=$INSTALL_DIR
EnvironmentFile=$INSTALL_DIR/.env
ExecStart=/usr/bin/java -jar $INSTALL_DIR/tradn.jar
Restart=on-failure
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now tradn
echo "TradN deployed to $INSTALL_DIR. Configure nginx to serve $INSTALL_DIR/web and proxy /api to 127.0.0.1:8080."

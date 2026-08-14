#!/usr/bin/env bash
set -euo pipefail

ONLYOFFICE_CONTAINER="${ONLYOFFICE_CONTAINER:-onlyoffice-document-server}"
NETWORK="${NETWORK:-office-net}"

if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo "Creating docker network: $NETWORK"
  docker network create "$NETWORK"
fi

if docker ps --format '{{.Names}}' | grep -qx "$ONLYOFFICE_CONTAINER"; then
  echo "Connecting $ONLYOFFICE_CONTAINER to $NETWORK"
  docker network connect "$NETWORK" "$ONLYOFFICE_CONTAINER" 2>/dev/null || true
fi

echo "Building and starting office-online-app"
if [ -z "${ONLYOFFICE_PUBLIC_URL:-}" ]; then
  echo "Warning: ONLYOFFICE_PUBLIC_URL is empty. Browser cannot load the OnlyOffice SDK."
  echo "Set it before deploying, e.g. ONLYOFFICE_PUBLIC_URL=http://<server-ip>:8080"
fi
if [ -z "${ONLYOFFICE_JWT_SECRET:-}" ]; then
  echo "Warning: ONLYOFFICE_JWT_SECRET is empty."
  echo "If OnlyOffice reports a security token error, set it to the same secret as the document server."
fi
if [ -z "${APP_PUBLIC_URL:-}" ] || echo "$APP_PUBLIC_URL" | grep -q 'office-online-app\|app:8081'; then
  echo "Warning: APP_PUBLIC_URL must be browser-accessible."
  echo "Set it before deploying, e.g. APP_PUBLIC_URL=http://<server-ip>:8081"
fi
docker compose -f docker-compose.app.yml up -d --build

echo "Deployment finished. App is on http://<server-ip>:8081"
docker ps --filter name=office-online-app

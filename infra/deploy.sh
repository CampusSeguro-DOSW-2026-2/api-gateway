#!/usr/bin/env bash
# Despliega el GW en la VM (ADR-006): borde Caddy + stack gw-<entorno>, y verifica el despliegue.
# Lo ejecuta el runner self-hosted desde .github/workflows/deploy.yml con las variables y secretos del Environment
# gw-<entorno> de GitHub. Es idempotente: volver a ejecutarlo con otro TAG es el rollback.
set -euo pipefail

falta() { echo "::error::Falta la variable $1 (Environment gw-${ENTORNO:-?} en GitHub)" >&2; exit 1; }
for VAR in ENTORNO APP_DOMAIN ACME_EMAIL JWT_PUBLIC_KEY CORS_ALLOWED_ORIGINS MS01_URI \
        GW_CLIENT_P12_BASE64 GW_CLIENT_KEYSTORE_PASSWORD GW_TRUSTED_CA_PEM; do
    [ -n "${!VAR:-}" ] || falta "$VAR"
done
[[ "$ENTORNO" =~ ^(test|prod)$ ]] || { echo "::error::ENTORNO debe ser test o prod" >&2; exit 1; }

REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
# Misma etiqueta que publica el CI (docker/metadata-action, type=sha): los 7 primeros caracteres del commit.
TAG="${TAG:-sha-$(git -C "$REPO_DIR" rev-parse HEAD | cut -c1-7)}"
[[ "$TAG" =~ ^sha-[0-9a-f]{7}$ ]] || { echo "::error::TAG debe tener la forma sha-xxxxxxx (recibido: $TAG)" >&2; exit 1; }
IMAGE="${IMAGE:-ghcr.io/campusseguro-dosw-2026-2/api-gateway}"
DEPLOY_DIR="${DEPLOY_DIR:-$HOME/campusseguro/api-gateway}"
PUERTO=$([ "$ENTORNO" = prod ] && echo 443 || echo 8443)
STACK_DIR="$DEPLOY_DIR/$ENTORNO"
EDGE_DIR="$DEPLOY_DIR/edge"

# Valores de una línea para el .env (entre comillas simples, sin interpolar): la llave PEM va con \n escapados.
una_linea() { printf '%s' "$1" | awk 'BEGIN{ORS=""} NR>1{print "\\n"} {print}'; }
linea_env() {
    local valor; valor="$(una_linea "$2")"
    [[ "$valor" != *"'"* ]] || { echo "::error::$1 no puede contener comillas simples" >&2; exit 1; }
    printf "%s='%s'\n" "$1" "$valor"
}

umask 077
mkdir -p "$STACK_DIR/mtls" "$EDGE_DIR"

echo "== Archivos del despliegue ($ENTORNO, $TAG) en $STACK_DIR"
printf '%s' "$GW_CLIENT_P12_BASE64" | base64 -d > "$STACK_DIR/mtls/gw-client.p12"
printf '%s\n' "$GW_TRUSTED_CA_PEM" > "$STACK_DIR/mtls/ca.pem"
cp "$REPO_DIR/infra/docker-compose.prod.yml" "$STACK_DIR/docker-compose.yml"
{
    linea_env ENTORNO "$ENTORNO"
    linea_env IMAGE "$IMAGE"
    linea_env TAG "$TAG"
    linea_env RUN_UID "$(id -u)"
    linea_env RUN_GID "$(id -g)"
    linea_env LOG_LEVEL "${LOG_LEVEL:-INFO}"
    linea_env JWT_PUBLIC_KEY "$JWT_PUBLIC_KEY"
    linea_env CORS_ALLOWED_ORIGINS "$CORS_ALLOWED_ORIGINS"
    linea_env MS01_URI "$MS01_URI"
    linea_env GW_CLIENT_KEYSTORE_PASSWORD "$GW_CLIENT_KEYSTORE_PASSWORD"
    for N in 01 02 03 04 05 06 07 08; do
        VAR="GW_SERVICE_KEY_MS$N"
        [ -z "${!VAR:-}" ] || linea_env "$VAR" "${!VAR}"
    done
} > "$STACK_DIR/.env"
printf '%s\n' "${GW_EXTRA_ENV:-}" > "$STACK_DIR/extra.env"

cp "$REPO_DIR/infra/edge/docker-compose.yml" "$REPO_DIR/infra/edge/Caddyfile.gateway" "$EDGE_DIR/"
{ linea_env APP_DOMAIN "$APP_DOMAIN"; linea_env ACME_EMAIL "$ACME_EMAIL"; } > "$EDGE_DIR/.env"

echo "== Borde (Caddy, puertos 80/443/8443)"
docker compose --project-directory "$EDGE_DIR" up -d

echo "== Imagen $IMAGE:$TAG"
# En la VM el runner entrega GHCR_USER/GHCR_TOKEN; PULL_POLICY=missing solo sirve para probar con una imagen local.
if [ -n "${GHCR_TOKEN:-}" ]; then
    printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "${GHCR_USER:?Falta GHCR_USER}" --password-stdin >/dev/null
fi
C=(docker compose --project-directory "$STACK_DIR")
"${C[@]}" config --quiet
"${C[@]}" up -d --remove-orphans --pull "${PULL_POLICY:-always}"
[ -z "${GHCR_TOKEN:-}" ] || docker logout ghcr.io >/dev/null

echo "== Verificación por https://$APP_DOMAIN:$PUERTO (espera hasta 3 min: arranque del GW y certificado)"
BASE="https://$APP_DOMAIN:$PUERTO"
for _ in $(seq 1 36); do
    if [ "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/actuator/health" || true)" = 200 ]; then
        PROTEGIDA="$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/users/me" || true)"
        [ "$PROTEGIDA" = 401 ] || { echo "::error::/api/v1/users/me sin token respondió $PROTEGIDA (se esperaba 401)"; exit 1; }
        echo "Despliegue verificado: health 200 y ruta protegida 401 ($ENTORNO, $TAG)"
        exit 0
    fi
    sleep 5
done
echo "::error::El GW no respondió 200 en $BASE/actuator/health. Revise: docker compose --project-directory $STACK_DIR logs"
"${C[@]}" logs --tail=50 gateway || true
exit 1

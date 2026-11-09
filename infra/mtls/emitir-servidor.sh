#!/usr/bin/env bash
# Emite el certificado de servidor de un microservicio con la CA del entorno (ADR-006).
# Uso: infra/mtls/emitir-servidor.sh <test|prod> <msNN> <IP o DNS>[,<IP o DNS>...]
# Ejemplo: infra/mtls/emitir-servidor.sh prod ms01 20.98.45.90
#
# Salida en infra/mtls/out/<entorno>/: <msNN>.crt y <msNN>.key. Se entregan solo al squad dueño, por un canal
# privado; los monta su Caddy (infra/caddy/Caddyfile.microservicio). La IP o el DNS deben ser los mismos de
# MSnn_URI en el GW, porque el GW verifica que el certificado corresponda al destino.
set -euo pipefail

ENTORNO="${1:-}"
NOMBRE="${2:-}"
HOSTS="${3:-}"
[[ "$ENTORNO" =~ ^(test|prod)$ && "$NOMBRE" =~ ^ms0[1-8]$ && -n "$HOSTS" ]] \
    || { echo "Uso: $0 <test|prod> <msNN> <IP o DNS>[,<IP o DNS>...]" >&2; exit 1; }

OUT="${MTLS_OUT:-$(cd "$(dirname "$0")" && pwd)/out}/$ENTORNO"
[ -f "$OUT/ca.key" ] || { echo "No existe $OUT/ca.key: ejecute antes generar-ca.sh $ENTORNO" >&2; exit 1; }

SAN=""
IFS=',' read -ra LISTA <<< "$HOSTS"
for HOST in "${LISTA[@]}"; do
    if [[ "$HOST" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then SAN+="IP:$HOST,"; else SAN+="DNS:$HOST,"; fi
done
SAN="${SAN%,}"

umask 077
cd "$OUT"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$NOMBRE.key"
openssl req -new -key "$NOMBRE.key" -subj "/O=CampusSeguro/CN=$NOMBRE-$ENTORNO" -out "$NOMBRE.csr"
printf '%s\n' "basicConstraints=critical,CA:FALSE" "keyUsage=critical,digitalSignature,keyEncipherment" \
    "extendedKeyUsage=serverAuth" "subjectAltName=$SAN" > "$NOMBRE.ext"
openssl x509 -req -in "$NOMBRE.csr" -CA ca.pem -CAkey ca.key -CAserial ca.srl -days 825 -sha256 \
    -extfile "$NOMBRE.ext" -out "$NOMBRE.crt"
rm -f "$NOMBRE.csr" "$NOMBRE.ext"

echo "Listo: $OUT/$NOMBRE.crt y $OUT/$NOMBRE.key ($SAN)"
echo "  Entregue al squad: $NOMBRE.crt, $NOMBRE.key y ca.pem"

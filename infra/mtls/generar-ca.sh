#!/usr/bin/env bash
# Crea la CA privada de un entorno y el certificado de cliente del GW firmado por ella (ADR-006).
# Uso: infra/mtls/generar-ca.sh <test|prod>        (en Windows, desde Git Bash)
#
# Salida en infra/mtls/out/<entorno>/ (ignorada por git; MTLS_OUT cambia la carpeta base):
#   ca.key          llave de la CA. Solo la guarda el dueño del GW; nunca se reparte ni se sube.
#   ca.pem          certificado de la CA. Es público: se entrega a cada squad para su Caddy.
#   ca.srl          serial de la CA (lo usa emitir-servidor.sh).
#   gw-client.p12   certificado de cliente del GW con su llave. Secreto del entorno gw-<entorno>.
#   gw-client.pass  contraseña del .p12. Secreto del entorno gw-<entorno>.
set -euo pipefail

ENTORNO="${1:-}"
[[ "$ENTORNO" =~ ^(test|prod)$ ]] || { echo "Uso: $0 <test|prod>" >&2; exit 1; }

OUT="${MTLS_OUT:-$(cd "$(dirname "$0")" && pwd)/out}/$ENTORNO"
if [ -e "$OUT/ca.key" ]; then
    echo "Ya existe $OUT/ca.key. Una CA nueva invalida todos los certificados del entorno; si es lo que quiere," >&2
    echo "borre la carpeta $OUT a mano y vuelva a ejecutar." >&2
    exit 1
fi

umask 077
mkdir -p "$OUT"
cd "$OUT"

# CA: 3 años. Solo firma certificados (pathlen:0 impide CA intermedias).
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out ca.key
openssl req -x509 -new -key ca.key -sha256 -days 1095 \
    -subj "/O=CampusSeguro/CN=CampusSeguro CA $ENTORNO" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -out ca.pem

# Cliente del GW: solo autenticación de cliente (clientAuth), así no sirve como certificado de servidor.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out gw-client.key
openssl req -new -key gw-client.key -subj "/O=CampusSeguro/CN=api-gateway-$ENTORNO" -out gw-client.csr
printf '%s\n' "basicConstraints=critical,CA:FALSE" "keyUsage=critical,digitalSignature,keyEncipherment" \
    "extendedKeyUsage=clientAuth" > gw-client.ext
openssl x509 -req -in gw-client.csr -CA ca.pem -CAkey ca.key -CAcreateserial -days 825 -sha256 \
    -extfile gw-client.ext -out gw-client.crt

PASS="$(openssl rand -base64 32 | tr -d '/+=')"
openssl pkcs12 -export -name api-gateway -inkey gw-client.key -in gw-client.crt -certfile ca.pem \
    -passout "pass:$PASS" -out gw-client.p12
printf '%s' "$PASS" > gw-client.pass
rm -f gw-client.key gw-client.csr gw-client.ext gw-client.crt

echo "Listo en $OUT"
echo "  Para los squads: ca.pem"
echo "  Secretos de gw-$ENTORNO: gw-client.p12 (en Base64) y gw-client.pass"
echo "  Guarde ca.key fuera del repositorio; sin ella no se pueden emitir más certificados de este entorno."

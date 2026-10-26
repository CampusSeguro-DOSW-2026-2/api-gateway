# Guía de integración con el API Gateway (squads MS02–MS08)

Con esta guía cada squad levanta en su máquina **GW + MS01 + su propio MS** y prueba su MS a través del Gateway, igual que en producción: login en MS01, token JWT y llamadas a sus rutas por el `8080`. No se toca el código del Gateway: sus rutas se declaran en **su** `docker-compose.yml` y **su** `.env`.

Contrato de referencia (cabeceras, errores, puertos, claves de servicio): [Estándares de Integración](https://github.com/CampusSeguro-DOSW-2026-2/documentation/wiki/Est%C3%A1ndares-de-Integraci%C3%B3n-y-Contratos-Transversales).

## 0. Requisitos

* Docker con Compose v2.20 o superior (`docker compose version`).
* `openssl`. En Windows viene con Git Bash.
* Su MS con `pom.xml` y `src/` en la raíz del repositorio y escuchando en su puerto del plan de puertos (MS0N → `808N`).

## 1. Descargar la plantilla en la raíz de su repositorio

Se recomienda trabajarla en una rama propia (p. ej. `integracion-gw`).

```bash
curl -o docker-compose.yml https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/feature/integracion/integracion/docker-compose.yml
curl -o .env https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/feature/integracion/integracion/.env.example
# [Windows]: curl.exe -o docker-compose.yml https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/feature/integracion/integracion/docker-compose.yml
# [Windows]: curl.exe -o .env https://raw.githubusercontent.com/CampusSeguro-DOSW-2026-2/api-gateway/feature/integracion/integracion/.env.example
```

Si su repositorio ya tiene un `docker-compose.yml`, descárguelo con otro nombre (`-o docker-compose.gw.yml`) y agregue `-f docker-compose.gw.yml` a cada comando `docker compose` de esta guía.

## 2. Agregar al `.gitignore`

Las llaves y el `.env` nunca se suben:

```gitignore
.env
*.pem
```

## 3. Cambiar lo marcado con `SQUAD:` en `docker-compose.yml`

La plantilla viene con MS04 de ejemplo. Cambie solo esto:

| Dónde | Valor de ejemplo (MS04) | Qué poner (ejemplo para MS03) |
|---|---|---|
| Nombre del servicio de su MS | `ms04-evacuation-and-check-in` | `ms03-alerts-and-emergencies` (nombre de su repositorio) |
| `GATEWAY_ROUTES_1_ID` | `ms04` | `ms03` |
| `GATEWAY_ROUTES_1_URI` | `http://ms04-evacuation-and-check-in:8084` | `http://ms03-alerts-and-emergencies:8083` |
| `GW_SERVICE_KEY_MS04` | `GW_SERVICE_KEY_MS04` | `GW_SERVICE_KEY_MS03` |
| `SERVER_PORT` de su MS | `8084` | `8083` |
| Variables propias de su MS | (ninguna) | p. ej. `DB_URL`; si tiene base de datos, agréguela como servicio en la red `internal`, **sin** `ports` (copie el bloque `ms01-db`) |

No cambie la ruta 0 (MS01) ni los servicios `ms01-*`. Si su MS ya tiene `Dockerfile`, puede reemplazar el bloque `build:` de su servicio por `build: .`.

## 4. Completar el `.env`

| Variable | Qué poner |
|---|---|
| `SQUAD_PATHS` | **Los prefijos de su `swagger.yml`**, separados por coma y sin espacios, todos bajo `/api/v1/`. Un recurso con subrutas lleva las dos formas: `/api/v1/alerts,/api/v1/alerts/**`. Esto es lo único que define qué rutas les envía el GW: lo que no esté aquí responde `404`. |
| `SQUAD_SERVICE_KEY` | Solo si su MS llama a otro MS. Genérela con `openssl rand -base64 48`. |
| `MS01_DB_PASSWORD` | Cualquier clave local. |
| `BOOTSTRAP_ADMIN_*` | Opcional: correo, contraseña, nombre y apellido de un primer ADMIN, para probar rutas de administración. |

Cuando cambien sus rutas, se cambia `SQUAD_PATHS` y se vuelve a ejecutar el paso 6 (no hace falta reconstruir el GW).

## 5. Generar el par de llaves JWT de desarrollo

MS01 firma los tokens con la privada y el GW los valida con la pública. Se hace una sola vez; en cada terminal nueva se repite solo el `export`.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
export JWT_PUBLIC_KEY="$(cat public.pem)" JWT_PRIVATE_KEY="$(cat private.pem)"
# [Windows]: los dos comandos openssl en Git Bash
# [Windows PowerShell]: $env:JWT_PUBLIC_KEY = Get-Content public.pem -Raw; $env:JWT_PRIVATE_KEY = Get-Content private.pem -Raw
```

## 6. Levantar todo

La primera vez tarda unos minutos: descarga la imagen del GW y construye MS01 desde GitHub.

```bash
docker compose pull api-gateway
docker compose up -d --build
docker compose ps
```

`docker compose ps` debe mostrar los cuatro servicios en ejecución y solo `api-gateway` con un puerto publicado (`8080`).

## 7. Probar a través del GW

```bash
# 7.1 El GW está arriba: 200 {"status":"UP"}
curl -i http://localhost:8080/actuator/health
# [Windows]: curl.exe -i http://localhost:8080/actuator/health

# 7.2 Registrar un usuario en MS01: 201
curl -i -X POST http://localhost:8080/api/v1/auth/register -H 'Content-Type: application/json' -d '{"primer_nombre":"Maria","primer_apellido":"Perez","correo":"maria.perez@escuelaing.edu.co","contrasena":"Campus#2026","confirmar_contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/register -H "Content-Type: application/json" -d "{\"primer_nombre\":\"Maria\",\"primer_apellido\":\"Perez\",\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\",\"confirmar_contrasena\":\"Campus#2026\"}"

# 7.3 Login: 200 con el token (copiar su valor como <token>)
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"maria.perez@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 7.4 Una ruta de su MS con el token: el GW la reenvía con X-User-Id, X-User-Role y X-Correlation-ID (sin el JWT)
curl -i http://localhost:8080/api/v1/<su-ruta> -H 'Authorization: Bearer <token>'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/<su-ruta> -H "Authorization: Bearer <token>"

# 7.5 La misma ruta sin token: 401 NO_AUTENTICADO sin llegar a su MS
curl -i http://localhost:8080/api/v1/<su-ruta>
# [Windows]: curl.exe -i http://localhost:8080/api/v1/<su-ruta>

# 7.6 Llamada interna como lo haría su MS (si definió SQUAD_SERVICE_KEY): el destino recibe X-Service-Name
curl -i http://localhost:8080/api/v1/users/me -H 'X-Service-Key: <SQUAD_SERVICE_KEY>' -H 'X-User-Id: <id-del-usuario>' -H 'X-User-Role: ESTUDIANTE'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "X-Service-Key: <SQUAD_SERVICE_KEY>" -H "X-User-Id: <id-del-usuario>" -H "X-User-Role: ESTUDIANTE"

# 7.7 Ver qué hizo el GW: una línea JSON "Solicitud procesada" por solicitud, con status y upstream (msNN o gw)
docker compose logs -f api-gateway
```

## 8. Qué significa cada respuesta del GW

| Respuesta | Causa | Qué revisar |
|---|---|---|
| `404 NO_ENCONTRADO`, `upstream: gw` | La ruta no está en `SQUAD_PATHS` | El prefijo en el `.env`; repetir el paso 6 |
| `401 NO_AUTENTICADO` | Sin token, token vencido (1 h) o firmado con otra llave | Repetir 7.3; `JWT_PUBLIC_KEY` y `JWT_PRIVATE_KEY` del mismo par |
| `502 SERVICIO_NO_DISPONIBLE` | Su MS está caído, o respondió un error que no cumple el `ErrorResponse` de los Estándares | `docker compose logs <su-servicio>`; el formato de sus errores |
| `504 TIEMPO_AGOTADO` | Su MS tardó más de 3000 ms o aún está arrancando | Esperar a que arranque; `docker compose logs <su-servicio>` |

## 9. Bajar el despliegue

```bash
docker compose down
# Con -v también se borra la base de datos de MS01 (los usuarios registrados)
docker compose down -v
```

## 10. Cuando su contrato esté listo

Envíen al equipo del GW sus prefijos definitivos (los de `SQUAD_PATHS`) y el enlace a su `swagger.yml` en el repositorio documentation. Así se agregan de forma fija a la tabla de rutas del GW (decisión abierta 4 de los Estándares).

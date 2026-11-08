# API GATEWAY (punto de enlace)

GW - Single point of entry, routing, and cross-cutting controls orchestrator.

| Name                            | GitHub Username                   |
|---------------------------------|-----------------------------------|
| Daniel Santiago Morales Perdomo | `Fukuyaku88`                      |
| Edgar Daniel Ruiz Patiño        | `DaniRuiz11`                      |
| Miguel Ángel Acero Laverde      | `Miguelim101`                     |
| Thomas Sebastian García Gomez   | `thomassebastiangarciagomez-ctrl` |
| Rodrigo Gualtero                | `Rodrigoreg92`                    |

---

El Gateway es la única puerta HTTP de CampusSeguro, tanto para el tráfico externo como entre microservicios. Enruta a MS01–MS08, valida localmente el JWT y propaga la identidad en cabeceras. Además centraliza CORS, la normalización de errores, la correlación y el logging técnico. No administra identidad ni ejecuta lógica de dominio.

* **Ubicación documentation:** [Repositorio de documentacion](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/api-gateway/api-gateway)

## Cómo ejecutar y probar

Requisitos: Java 21, Maven 3.9 y Docker. Variables disponibles: [`.env.example`](.env.example).

```bash
# 0. Copiar variables de entorno a local
cp .env.example .env
# [Windows CMD]: copy .env.example .env
# [Windows PowerShell]: cp .env.example .env

# 1. Compilar y ejecutar todas las pruebas (falla si la cobertura es menor a 70 %)
mvn verify

# 2. Solo la suite de integración (INT-GW-01…12, 14 y 15 contra dobles de los MS)
mvn test -Dgroups=integracion
# [Windows PowerShell]: mvn test "-Dgroups=integracion"

# 3. Ver el reporte de cobertura
xdg-open target/site/jacoco/index.html
# [Windows]: start target/site/jacoco/index.html

# 4. Generar el par RSA de desarrollo (private.pem va a MS01 como JWT_PRIVATE_KEY; nunca se versiona)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
# [Windows]: los mismos dos comandos en Git Bash (incluye openssl)

# 5. Exportar la llave pública y las claves de servicio de desarrollo (MS04 y MS03) para el GW (en la misma terminal donde
#    se levanta el GW; la clave real de cada MS se genera con: openssl rand -base64 48)
export JWT_PUBLIC_KEY="$(cat public.pem)"
export GW_SERVICE_KEY_MS04=clave-de-desarrollo-ms04-de-mas-de-32-caracteres
export GW_SERVICE_KEY_MS03=clave-de-desarrollo-ms03-de-mas-de-32-caracteres
# [Windows PowerShell]: $env:JWT_PUBLIC_KEY = Get-Content public.pem -Raw
# [Windows PowerShell]: $env:GW_SERVICE_KEY_MS04 = "clave-de-desarrollo-ms04-de-mas-de-32-caracteres"
# [Windows PowerShell]: $env:GW_SERVICE_KEY_MS03 = "clave-de-desarrollo-ms03-de-mas-de-32-caracteres"

# 6. Levantar el GW en local (8080; Actuator en 9090; MS01 esperado en localhost:8081)
mvn spring-boot:run -Dspring-boot.run.profiles=local
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# --- En otra terminal o en Postman manual ---

# 7. Ruta sin destino: 404 NO_ENCONTRADO sin llamar a ningún MS (sin JWT)
curl -i http://localhost:8080/api/v1/inexistente
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente

# 8. Ruta pública con MS01 apagado: no exige JWT y responde 502 SERVICIO_NO_DISPONIBLE
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"maria.perez@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 9. Con MS01 encendido en 8081 (con JWT_PRIVATE_KEY = private.pem), el login llega a MS01 sin cambios y devuelve el token (repetir el paso 8)

# 10. Con MS01 encendido: un error de MS01 que cumple el ErrorResponse (p. ej. 401 por credenciales) llega sin cambios;
#    si no lo cumple (sin correlation_id, cuerpo por defecto de Spring), el GW responde 502 SERVICIO_NO_DISPONIBLE
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"no.existe@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"no.existe@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 11. Ruta protegida sin JWT o con un JWT inválido: 401 NO_AUTENTICADO sin llamar a MS01
curl -i http://localhost:8080/api/v1/users/me
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer token.invalido.x'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer token.invalido.x"

# 12. Dos cabeceras Authorization: 400 VALIDACION
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer a' -H 'Authorization: Bearer b'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer a" -H "Authorization: Bearer b"

# 13. Con MS01 encendido: el token del paso 9 permite la ruta protegida y la solicitud llega a MS01
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <token>'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>"

# 14. Con MS01 encendido: cabeceras de identidad falsas no llegan al MS; MS01 responde el perfil del dueño del token
#     (el GW elimina Authorization, X-User-*, X-Service-* del cliente e inyecta X-User-Id/X-User-Role del JWT)
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <token>' -H 'X-User-Id: 11111111-1111-4111-8111-111111111111' -H 'X-User-Role: ADMIN'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>" -H "X-User-Id: 11111111-1111-4111-8111-111111111111" -H "X-User-Role: ADMIN"

# 15. Con MS01 encendido y un token de ESTUDIANTE: la acción de ADMIN responde el 403 PROHIBIDO de MS01 sin cambios
curl -i -X PATCH http://localhost:8080/api/v1/users/status -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"ids":["3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"],"estado":"INACTIVO"}'
# [Windows CMD/PowerShell]: curl.exe -i -X PATCH http://localhost:8080/api/v1/users/status -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"ids\":[\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"],\"estado\":\"INACTIVO\"}"

# 16. Llamada interna: con la clave de MS04 (sin JWT) y la identidad del usuario por el que actúa, la ruta protegida se
#       acepta y MS01 recibe X-Service-Name: ms04 y los X-User-* (con MS01 apagado responde 502, nunca 401)
curl -i http://localhost:8080/api/v1/users/me -H 'X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres' -H 'X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42' -H 'X-User-Role: ESTUDIANTE'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres" -H "X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42" -H "X-User-Role: ESTUDIANTE"

# 17. Clave inválida: 401 NO_AUTENTICADO. X-User-Id sin X-User-Role, o Authorization junto con X-Service-Key: 400 VALIDACION
curl -i http://localhost:8080/api/v1/users/me -H 'X-Service-Key: clave-inventada-de-mas-de-32-caracteres-x'
curl -i http://localhost:8080/api/v1/users/me -H 'X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres' -H 'X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42'
curl -i http://localhost:8080/api/v1/users/me -H 'X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres' -H 'Authorization: Bearer x'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "X-Service-Key: clave-inventada-de-mas-de-32-caracteres-x"
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres" -H "X-User-Id: 3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres" -H "Authorization: Bearer x"

# 18. Con MS01 encendido: rutas internas de MS01 (RF-MS01-09) con la clave de MS03 (sin JWT): MS01 recibe
#     X-Service-Name: ms03 y responde los usuarios del rol y el batch con usuarios y no_encontrados
curl -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE&page=0&size=100" -H 'X-Service-Key: clave-de-desarrollo-ms03-de-mas-de-32-caracteres'
curl -i -X POST http://localhost:8080/api/v1/users/batch -H 'X-Service-Key: clave-de-desarrollo-ms03-de-mas-de-32-caracteres' -H 'Content-Type: application/json' -d '{"ids":["3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"]}'
# [Windows]: curl.exe -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE&page=0&size=100" -H "X-Service-Key: clave-de-desarrollo-ms03-de-mas-de-32-caracteres"
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/users/batch -H "X-Service-Key: clave-de-desarrollo-ms03-de-mas-de-32-caracteres" -H "Content-Type: application/json" -d "{\"ids\":[\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"]}"

# 19. Rutas internas sin clave o con el JWT de un usuario: 401 NO_AUTENTICADO sin llamar a MS01. Con la clave de MS04,
#     que MS01 no autoriza (INTERNAL_ALLOWED_SERVICES = ms03,ms05), responde el 403 PROHIBIDO de MS01 sin cambios
curl -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H 'X-Service-Name: ms03'
curl -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H 'Authorization: Bearer <token>'
curl -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H 'X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres'
# [Windows]: curl.exe -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H "X-Service-Name: ms03"
# [Windows]: curl.exe -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H "Authorization: Bearer <token>"
# [Windows]: curl.exe -i "http://localhost:8080/api/v1/users/by-role?rol=ESTUDIANTE" -H "X-Service-Key: clave-de-desarrollo-ms04-de-mas-de-32-caracteres"

# 20. Correlación válida: el UUID v4 enviado vuelve igual en la cabecera X-Correlation-ID y en correlation_id
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456"

# 21. Correlación inválida o ausente: el GW genera un UUID v4 nuevo y no rechaza la solicitud
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: no-es-uuid'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: no-es-uuid"

# 22. Preflight CORS desde el origen permitido (perfil local: http://localhost:5173): 200 con Access-Control-*, sin JWT y sin llamar a MS01
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"

# 23. Preflight desde otro origen o pidiendo una cabecera interna (X-User-Role): 403 PROHIBIDO sin Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: https://otro-sitio.com' -H 'Access-Control-Request-Method: GET'
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: x-user-role'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: https://otro-sitio.com" -H "Access-Control-Request-Method: GET"
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: x-user-role"

# 24. Preflight a una ruta inexistente: 404 NO_ENCONTRADO
curl -i -X OPTIONS http://localhost:8080/api/v1/inexistente -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/inexistente -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET"

# 25. Solicitud real desde el origen permitido: Access-Control-Allow-Origin y Access-Control-Expose-Headers: X-Correlation-ID
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Origin: http://localhost:5173'
# [Windows]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Origin: http://localhost:5173"

# 26. Health público del GW (sin JWT): 200 {"status":"UP"}, aunque MS01 esté apagado
curl -i http://localhost:8080/actuator/health
# [Windows]: curl.exe -i http://localhost:8080/actuator/health

# 27. Métricas solo en el puerto de gestión: 404 en 8080 y 200 en 9090
curl -i http://localhost:8080/actuator/metrics
curl http://localhost:9090/actuator/metrics
# [Windows]: curl.exe -i http://localhost:8080/actuator/metrics
# [Windows]: curl.exe http://localhost:9090/actuator/metrics

# 28. Log de acceso: en la terminal del GW cada solicitud deja una línea JSON "Solicitud procesada" con
#     timestamp, level, correlation_id, method, path, status, duration_ms, upstream y origen (sin cuerpos ni tokens)

# 29. Configuración inválida (timeout, JWT_PUBLIC_KEY o CORS_ALLOWED_ORIGINS inválidos): el GW no arranca y el log indica la causa
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms"

# 30. Construir la imagen Docker
docker build -t campusseguro/api-gateway:local .

# 31. Ejecutar la imagen (perfil docker con el frontend en http://localhost:5173; toma JWT_PUBLIC_KEY de la terminal
#     del paso 5; un destino inalcanzable responde 502)
docker run --rm -p 8080:8080 -e JWT_PUBLIC_KEY campusseguro/api-gateway:local

# --- Despliegue con Docker Compose: GW + MS01 + su base de datos; solo se publica el 8080 (INT-GW-13) ---
# Requiere el repositorio ms01-identity-and-access junto a api-gateway y, en la terminal, JWT_PUBLIC_KEY (paso 5)
# y la llave privada del mismo par para MS01.

# 32. Exportar la llave privada para MS01 y levantar todo
export JWT_PRIVATE_KEY="$(cat private.pem)"
docker compose up -d --build
# [Windows PowerShell]: $env:JWT_PRIVATE_KEY = Get-Content private.pem -Raw; docker compose up -d --build

# 33. Registro, login y perfil a través del GW contra el MS01 real (pasos 8, 9 y 13 con MS01 encendido)

# 34. Aislamiento: solo api-gateway publica un puerto; el 9090 del GW no responde desde el host
docker compose ps
curl -m 3 http://localhost:9090/actuator/health
# [Windows]: curl.exe -m 3 http://localhost:9090/actuator/health

# 35. Bajar el despliegue (con -v también se borra la base de datos de MS01)
docker compose down

# --- Prueba de carga RNF-GW-01 (P95 de latencia agregada <= 50 ms a 50 solicitudes/s durante 60 s) ---

# 36. Levantar el doble de MS01 con latencia fija de 20 ms en el puerto 8091 (se deja corriendo)
java perf/StubMs01.java 8091 20

# 37. En otra terminal (con JWT_PUBLIC_KEY exportada), levantar el GW apuntando al doble
MS01_URI=http://localhost:8091 mvn spring-boot:run -Dspring-boot.run.profiles=local
# [Windows PowerShell]: $env:MS01_URI = "http://localhost:8091"; mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# 38. En otra terminal, ejecutar k6 y guardar el resultado (umbral en latencia_agregada_gw; el resultado de referencia
#     está en perf/results/rnf-gw-01.txt)
docker run --rm --network host -v "$PWD/perf:/scripts:ro" -e MS01_URL=http://localhost:8091 grafana/k6:1.3.0 run /scripts/load-test.js
# [Windows PowerShell]: docker run --rm -v "${PWD}/perf:/scripts:ro" -e GW_URL=http://host.docker.internal:8080 -e MS01_URL=http://host.docker.internal:8091 grafana/k6:1.3.0 run /scripts/load-test.js

# --- mTLS del perfil cloud en local (ADR-006, INT-GW-16): GW -> Caddy de un MS (plantilla de los squads) -> eco ---
# Todo queda en infra/mtls/out/, que git ignora. En Windows, los comandos con openssl y curl van en Git Bash.

# 39. Crear las CA de prueba y de producción y el certificado de servidor del MS para esta máquina
infra/mtls/generar-ca.sh test
infra/mtls/generar-ca.sh prod
infra/mtls/emitir-servidor.sh test ms01 localhost,127.0.0.1
infra/mtls/emitir-servidor.sh prod ms01 localhost,127.0.0.1
# [Windows]: los mismos cuatro comandos en Git Bash

# 40. Armar la carpeta de la "VM del MS" con la plantilla y los certificados (como la recibe un squad)
for e in test prod; do mkdir -p infra/mtls/out/vm/certs/$e; cp infra/mtls/out/$e/ms01.crt infra/mtls/out/vm/certs/$e/servidor.crt; cp infra/mtls/out/$e/ms01.key infra/mtls/out/vm/certs/$e/servidor.key; cp infra/mtls/out/$e/ca.pem infra/mtls/out/vm/certs/$e/ca.pem; done
cp infra/caddy/Caddyfile.microservicio infra/caddy/docker-compose.microservicio.yml infra/mtls/out/vm/
chmod -R a+rX infra/mtls/out/vm
# [Windows]: los mismos comandos en Git Bash (sin el chmod)

# 41. Levantar un eco como MS01 de test y el Caddy del MS (443 = prod, 8443 = test; ambos bloques apuntan al eco)
docker network create campus-edge
docker run -d --name eco-ms01 --network campus-edge --network-alias ms01-test caddy:2.10.2 caddy respond --listen :8081 --body "ms01 test"
(cd infra/mtls/out/vm && UPSTREAM_PROD=ms01-test:8081 UPSTREAM_TEST=ms01-test:8081 docker compose -f docker-compose.microservicio.yml up -d)
# [Windows PowerShell]: los dos primeros comandos iguales y luego:
# [Windows PowerShell]: cd infra/mtls/out/vm; $env:UPSTREAM_PROD = "ms01-test:8081"; $env:UPSTREAM_TEST = "ms01-test:8081"; docker compose -f docker-compose.microservicio.yml up -d; cd ../../../..

# 42. Sin certificado de cliente, Caddy corta el handshake (certificate required); con el del GW de test, responde el eco;
#     el GW de test contra el puerto de prod (otra CA) también se rechaza (unknown ca)
curl --cacert infra/mtls/out/test/ca.pem https://127.0.0.1:8443/
curl --cacert infra/mtls/out/test/ca.pem --cert-type P12 --cert "infra/mtls/out/test/gw-client.p12:$(cat infra/mtls/out/test/gw-client.pass)" https://127.0.0.1:8443/
curl --cacert infra/mtls/out/prod/ca.pem --cert-type P12 --cert "infra/mtls/out/test/gw-client.p12:$(cat infra/mtls/out/test/gw-client.pass)" https://127.0.0.1:443/
# [Windows]: los mismos tres comandos en Git Bash

# 43. GW con el perfil cloud (en la terminal del paso 5): el login llega al eco por mTLS (200 "ms01 test");
#     con MS01_URI=https://127.0.0.1:443 (el MS de prod no acepta al GW de test) responde 502 SERVICIO_NO_DISPONIBLE
export GW_CLIENT_KEYSTORE=file:./infra/mtls/out/test/gw-client.p12 GW_TRUSTED_CA=file:./infra/mtls/out/test/ca.pem
export GW_CLIENT_KEYSTORE_PASSWORD="$(cat infra/mtls/out/test/gw-client.pass)"
MS01_URI=https://127.0.0.1:8443 mvn spring-boot:run -Dspring-boot.run.profiles=local,cloud
curl -i -X POST http://localhost:8080/api/v1/auth/login        # en otra terminal
# [Windows PowerShell]: $env:GW_CLIENT_KEYSTORE = "file:./infra/mtls/out/test/gw-client.p12"; $env:GW_TRUSTED_CA = "file:./infra/mtls/out/test/ca.pem"
# [Windows PowerShell]: $env:GW_CLIENT_KEYSTORE_PASSWORD = Get-Content infra/mtls/out/test/gw-client.pass -Raw
# [Windows PowerShell]: $env:MS01_URI = "https://127.0.0.1:8443"; mvn spring-boot:run "-Dspring-boot.run.profiles=local,cloud"
# [Windows]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login        # en otra terminal

# 44. Detenga el GW del paso 43 (Ctrl+C). Una contraseña o un keystore inválidos impiden el arranque (RNF-GW-06):
#     el log dice "password was incorrect" y el proceso termina
GW_CLIENT_KEYSTORE_PASSWORD=otra mvn spring-boot:run -Dspring-boot.run.profiles=local,cloud
# [Windows PowerShell]: $env:GW_CLIENT_KEYSTORE_PASSWORD = "otra"; mvn spring-boot:run "-Dspring-boot.run.profiles=local,cloud"

# 45. Bajar la prueba
(cd infra/mtls/out/vm && UPSTREAM_PROD=x UPSTREAM_TEST=x docker compose -f docker-compose.microservicio.yml down)
docker rm -f eco-ms01 && docker network rm campus-edge
# [Windows PowerShell]: cd infra/mtls/out/vm; $env:UPSTREAM_PROD = "x"; $env:UPSTREAM_TEST = "x"; docker compose -f docker-compose.microservicio.yml down; cd ../../../..
# [Windows PowerShell]: docker rm -f eco-ms01; docker network rm campus-edge
```

## Despliegue en la nube (test y producción)

Diseño y riesgos: ADR-006 en la documentación del GW. Cada MS vive en su propia VM (Azure o AWS) y solo el GW llega a
él: el firewall solo abre sus puertos a la IP del GW y su Caddy exige el certificado de cliente del GW (mTLS). En la
VM del GW corren dos stacks, `gw-prod` y `gw-test`, detrás de un mismo Caddy de borde:

| Puerto de la VM del GW | Uso |
|---|---|
| `80` | Let's Encrypt (Caddy obtiene y renueva el certificado de `APP_DOMAIN`) |
| `443` | GW de producción |
| `8443` | GW de prueba |

CI/CD:
- **Cada push** compila, prueba y analiza el código (`ci.yml`).
- **En `develop` y `main`**, además, publica la imagen `ghcr.io/campusseguro-dosw-2026-2/api-gateway:sha-xxxxxxx`.
- **Test es automático:** cada push a `develop` despliega en el entorno `gw-test`.
- **Producción es manual:** desde `main` y con aprobación, promueve la misma imagen que ya pasó por test.

Los comandos de la VM son de Linux, por SSH. En Windows, los comandos del PC van en Git Bash, salvo los marcados
`[Windows PowerShell]`.

```bash
# 1. (PC del dueño del GW, una sola vez) Crear las CA y los certificados de cliente del GW de cada entorno.
#    Guarde infra/mtls/out/ fuera del repositorio (git ya lo ignora) y nunca reparta ca.key.
infra/mtls/generar-ca.sh test
infra/mtls/generar-ca.sh prod
# [Windows]: los mismos comandos en Git Bash

# 2. (PC) Por cada MS que se integra, emitir su certificado de servidor con la IP estática de su VM y entregarle al squad,
#    por un canal privado, <msNN>.crt, <msNN>.key y ca.pem de cada entorno (más su GATEWAY_SERVICE_KEY)
infra/mtls/emitir-servidor.sh test ms01 <IP-VM-ms01>
infra/mtls/emitir-servidor.sh prod ms01 <IP-VM-ms01>

# 3. (Portal de Azure o AWS) Crear la VM del GW: Ubuntu 24.04, 2 vCPU / 4 GB, disco de 30 GB e IP pública estática
#    (Azure: IP Standard estática; AWS: Elastic IP). Reglas de entrada del NSG/Security Group:
#      22/tcp  solo desde su IP          80/tcp, 443/tcp y 8443/tcp desde Internet          nada más (ni 8080 ni 9090)
#    APP_DOMAIN puede ser <IP>.sslip.io (resuelve a la IP de la VM sin comprar un dominio).

# 4. (PC) Entrar a la VM e instalar Docker (luego salir y volver a entrar para aplicar el grupo docker)
ssh -i <llave>.pem <usuario>@<IP-VM-GW>
curl -fsSL https://get.docker.com | sudo sh && sudo usermod -aG docker $USER && exit
# [Windows PowerShell]: ssh -i <llave>.pem <usuario>@<IP-VM-GW>        (y los mismos comandos dentro de la VM)

# 5. (VM) Instalar el runner self-hosted con la etiqueta gw: en GitHub, repositorio api-gateway > Settings > Actions >
#    Runners > New self-hosted runner > Linux x64; copiar sus comandos de descarga y luego (con el token que muestra):
./config.sh --url https://github.com/CampusSeguro-DOSW-2026-2/api-gateway --token <TOKEN> --labels gw --unattended
sudo ./svc.sh install && sudo ./svc.sh start
#    Verificación: el runner aparece "Idle" en GitHub. El token nunca va al repositorio ni a capturas.

# 6. (GitHub) El repositorio es público: Settings > Actions > General > "Fork pull request workflows from outside
#    collaborators" = "Require approval for all external contributors". Así un PR externo no puede ejecutarse en la VM.

# 7. (GitHub) Settings > Environments: crear gw-test (Deployment branches: develop) y gw-prod (Required reviewers: los
#    responsables del GW; Deployment branches: main). En cada uno, las variables y secretos de la tabla de abajo.
#    El .p12 se carga en Base64:
base64 -w0 infra/mtls/out/test/gw-client.p12
# [Windows PowerShell]: [Convert]::ToBase64String([IO.File]::ReadAllBytes("infra/mtls/out/test/gw-client.p12"))

# 8. Desplegar test: hacer merge a develop. En Actions, el workflow CI termina con el job "desplegar-test", que dice
#    "Despliegue verificado: health 200 y ruta protegida 401 (test, sha-xxxxxxx)".

# 9. Desplegar producción: hacer merge de develop a main. En Actions > "Desplegar a producción" > Run workflow (rama main),
#    escribir el tag que ya funcionó en test (sha-xxxxxxx) y aprobar el despliegue. Otro tag anterior = rollback.

# 10. Verificar desde su PC: health y ruta protegida por el GW; acceso directo a un MS bloqueado por su firewall
curl -i https://<APP_DOMAIN>/actuator/health
curl -i https://<APP_DOMAIN>:8443/api/v1/users/me
curl -m 5 -k https://<IP-VM-ms01>:443/
# [Windows]: curl.exe -i https://<APP_DOMAIN>/actuator/health
# [Windows]: curl.exe -i https://<APP_DOMAIN>:8443/api/v1/users/me
# [Windows]: curl.exe -m 5 -k https://<IP-VM-ms01>:443/
#     Esperado: 200 {"status":"UP"}, 401 NO_AUTENTICADO y timeout (el NSG/SG del MS solo acepta la IP del GW).

# 11. (VM) Operar: estado, logs y reinicio de un entorno (nunca "down -v")
cd ~/campusseguro/api-gateway/prod && docker compose ps && docker compose logs --tail=100 gateway
docker compose restart gateway
```

| En `gw-test` y `gw-prod` | Tipo | Valor |
|---|---|---|
| `APP_DOMAIN` | Variable | Igual en los dos, p. ej. `20.98.45.90.sslip.io`. |
| `ACME_EMAIL` | Variable | Correo para los avisos de Let's Encrypt. Igual en los dos. |
| `CORS_ALLOWED_ORIGINS` | Variable | Origen del frontend de ese entorno. |
| `MS01_URI` | Variable | `https://<IP-VM-ms01>:8443` en test y `https://<IP-VM-ms01>:443` en prod. |
| `GW_EXTRA_ENV` | Variable (opcional) | Líneas `CLAVE=valor` extra, p. ej. las `GATEWAY_ROUTES_*` de otros MS. |
| `LOG_LEVEL`, `DEPLOY_DIR` | Variable (opcional) | `INFO` y `~/campusseguro/api-gateway` por defecto. |
| `JWT_PUBLIC_KEY` | Secreto | Llave pública del MS01 de ese entorno (PEM completo). |
| `GW_CLIENT_P12_BASE64` | Secreto | `gw-client.p12` del entorno, en Base64 (paso 7). |
| `GW_CLIENT_KEYSTORE_PASSWORD` | Secreto | Contenido de `gw-client.pass` del entorno. |
| `GW_TRUSTED_CA_PEM` | Secreto | Contenido de `ca.pem` del entorno. |
| `GW_SERVICE_KEY_MS01` … `_MS08` | Secreto | Solo las de los MS que llaman a través del GW (`openssl rand -base64 48`), distintas por entorno. |

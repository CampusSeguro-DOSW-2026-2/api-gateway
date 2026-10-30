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

* **Ubicación documentation:** [Repositorio de documentacion](https://github.com/CampusSeguro-DOSW-2026-2/documentation/tree/api-gateway-docs)

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

# 5. Exportar la llave pública y una clave de servicio de desarrollo (MS04) para el GW (en la misma terminal donde
#    se levanta el GW; la clave real de cada MS se genera con: openssl rand -base64 48)
export JWT_PUBLIC_KEY="$(cat public.pem)"
export GW_SERVICE_KEY_MS04=clave-de-desarrollo-ms04-de-mas-de-32-caracteres
# [Windows PowerShell]: $env:JWT_PUBLIC_KEY = Get-Content public.pem -Raw
# [Windows PowerShell]: $env:GW_SERVICE_KEY_MS04 = "clave-de-desarrollo-ms04-de-mas-de-32-caracteres"

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

# 18. Correlación válida: el UUID v4 enviado vuelve igual en la cabecera X-Correlation-ID y en correlation_id
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456"

# 19. Correlación inválida o ausente: el GW genera un UUID v4 nuevo y no rechaza la solicitud
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: no-es-uuid'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: no-es-uuid"

# 20. Preflight CORS desde el origen permitido (perfil local: http://localhost:5173): 200 con Access-Control-*, sin JWT y sin llamar a MS01
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: authorization'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"

# 21. Preflight desde otro origen o pidiendo una cabecera interna (X-User-Role): 403 PROHIBIDO sin Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: https://otro-sitio.com' -H 'Access-Control-Request-Method: GET'
curl -i -X OPTIONS http://localhost:8080/api/v1/users/me -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' -H 'Access-Control-Request-Headers: x-user-role'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: https://otro-sitio.com" -H "Access-Control-Request-Method: GET"
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/users/me -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: x-user-role"

# 22. Preflight a una ruta inexistente: 404 NO_ENCONTRADO
curl -i -X OPTIONS http://localhost:8080/api/v1/inexistente -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET'
# [Windows]: curl.exe -i -X OPTIONS http://localhost:8080/api/v1/inexistente -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET"

# 23. Solicitud real desde el origen permitido: Access-Control-Allow-Origin y Access-Control-Expose-Headers: X-Correlation-ID
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Origin: http://localhost:5173'
# [Windows]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Origin: http://localhost:5173"

# 24. Health público del GW (sin JWT): 200 {"status":"UP"}, aunque MS01 esté apagado
curl -i http://localhost:8080/actuator/health
# [Windows]: curl.exe -i http://localhost:8080/actuator/health

# 25. Métricas solo en el puerto de gestión: 404 en 8080 y 200 en 9090
curl -i http://localhost:8080/actuator/metrics
curl http://localhost:9090/actuator/metrics
# [Windows]: curl.exe -i http://localhost:8080/actuator/metrics
# [Windows]: curl.exe http://localhost:9090/actuator/metrics

# 26. Log de acceso: en la terminal del GW cada solicitud deja una línea JSON "Solicitud procesada" con
#     timestamp, level, correlation_id, method, path, status, duration_ms, upstream y origen (sin cuerpos ni tokens)

# 27. Configuración inválida (timeout, JWT_PUBLIC_KEY o CORS_ALLOWED_ORIGINS inválidos): el GW no arranca y el log indica la causa
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms"

# 28. Construir la imagen Docker
docker build -t campusseguro/api-gateway:local .

# 29. Ejecutar la imagen (perfil docker con el frontend en http://localhost:5173; toma JWT_PUBLIC_KEY de la terminal
#     del paso 5; un destino inalcanzable responde 502)
docker run --rm -p 8080:8080 -e JWT_PUBLIC_KEY campusseguro/api-gateway:local

# --- Despliegue con Docker Compose: GW + MS01 + su base de datos; solo se publica el 8080 (INT-GW-13) ---
# Requiere el repositorio ms01-identity-and-access junto a api-gateway y, en la terminal, JWT_PUBLIC_KEY (paso 5)
# y la llave privada del mismo par para MS01.

# 30. Exportar la llave privada para MS01 y levantar todo
export JWT_PRIVATE_KEY="$(cat private.pem)"
docker compose up -d --build
# [Windows PowerShell]: $env:JWT_PRIVATE_KEY = Get-Content private.pem -Raw; docker compose up -d --build

# 31. Registro, login y perfil a través del GW contra el MS01 real (pasos 8, 9 y 13 con MS01 encendido)

# 32. Aislamiento: solo api-gateway publica un puerto; el 9090 del GW no responde desde el host
docker compose ps
curl -m 3 http://localhost:9090/actuator/health
# [Windows]: curl.exe -m 3 http://localhost:9090/actuator/health

# 33. Bajar el despliegue (con -v también se borra la base de datos de MS01)
docker compose down

# --- Prueba de carga RNF-GW-01 (P95 de latencia agregada <= 50 ms a 50 solicitudes/s durante 60 s) ---

# 34. Levantar el doble de MS01 con latencia fija de 20 ms en el puerto 8091 (se deja corriendo)
java perf/StubMs01.java 8091 20

# 35. En otra terminal (con JWT_PUBLIC_KEY exportada), levantar el GW apuntando al doble
MS01_URI=http://localhost:8091 mvn spring-boot:run -Dspring-boot.run.profiles=local
# [Windows PowerShell]: $env:MS01_URI = "http://localhost:8091"; mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# 36. En otra terminal, ejecutar k6 y guardar el resultado (umbral en latencia_agregada_gw; el resultado de referencia
#     está en perf/results/rnf-gw-01.txt)
docker run --rm --network host -v "$PWD/perf:/scripts:ro" -e MS01_URL=http://localhost:8091 grafana/k6:1.3.0 run /scripts/load-test.js
# [Windows PowerShell]: docker run --rm -v "${PWD}/perf:/scripts:ro" -e GW_URL=http://host.docker.internal:8080 -e MS01_URL=http://host.docker.internal:8091 grafana/k6:1.3.0 run /scripts/load-test.js
```

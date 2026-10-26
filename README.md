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

# 2. Ver el reporte de cobertura
xdg-open target/site/jacoco/index.html
# [Windows]: start target/site/jacoco/index.html

# 3. Generar el par RSA de desarrollo (private.pem va a MS01 como JWT_PRIVATE_KEY; nunca se versiona)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out private.pem
openssl rsa -in private.pem -pubout -out public.pem
# [Windows]: los mismos dos comandos en Git Bash (incluye openssl)

# 4. Exportar la llave pública para el GW (en la misma terminal donde se levanta el GW)
export JWT_PUBLIC_KEY="$(cat public.pem)"
# [Windows PowerShell]: $env:JWT_PUBLIC_KEY = Get-Content public.pem -Raw

# 5. Levantar el GW en local (8080; Actuator en 9090; MS01 esperado en localhost:8081)
mvn spring-boot:run -Dspring-boot.run.profiles=local
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# --- En otra terminal o en Postman manual ---

# 6. Ruta sin destino: 404 NO_ENCONTRADO sin llamar a ningún MS (sin JWT)
curl -i http://localhost:8080/api/v1/inexistente
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente

# 7. Ruta pública con MS01 apagado: no exige JWT y responde 502 SERVICIO_NO_DISPONIBLE
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"maria.perez@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 8. Con MS01 encendido en 8081 (con JWT_PRIVATE_KEY = private.pem), el login llega a MS01 sin cambios y devuelve el token (repetir el paso 7)

# 9. Con MS01 encendido: un error de MS01 que cumple el ErrorResponse (p. ej. 401 por credenciales) llega sin cambios;
#    si no lo cumple (sin correlation_id, cuerpo por defecto de Spring), el GW responde 502 SERVICIO_NO_DISPONIBLE
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"no.existe@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"no.existe@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 10. Ruta protegida sin JWT o con un JWT inválido: 401 NO_AUTENTICADO sin llamar a MS01
curl -i http://localhost:8080/api/v1/users/me
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer token.invalido.x'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer token.invalido.x"

# 11. Dos cabeceras Authorization: 400 VALIDACION
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer a' -H 'Authorization: Bearer b'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer a" -H "Authorization: Bearer b"

# 12. Con MS01 encendido: el token del paso 8 permite la ruta protegida y la solicitud llega a MS01
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <token>'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>"

# 13. Con MS01 encendido: cabeceras de identidad falsas no llegan al MS; MS01 responde el perfil del dueño del token
#     (el GW elimina Authorization, X-User-*, X-Service-* del cliente e inyecta X-User-Id/X-User-Role del JWT)
curl -i http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <token>' -H 'X-User-Id: 11111111-1111-4111-8111-111111111111' -H 'X-User-Role: ADMIN'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/users/me -H "Authorization: Bearer <token>" -H "X-User-Id: 11111111-1111-4111-8111-111111111111" -H "X-User-Role: ADMIN"

# 14. Con MS01 encendido y un token de ESTUDIANTE: la acción de ADMIN responde el 403 PROHIBIDO de MS01 sin cambios
curl -i -X PATCH http://localhost:8080/api/v1/users/status -H 'Authorization: Bearer <token>' -H 'Content-Type: application/json' -d '{"ids":["3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42"],"estado":"INACTIVO"}'
# [Windows CMD/PowerShell]: curl.exe -i -X PATCH http://localhost:8080/api/v1/users/status -H "Authorization: Bearer <token>" -H "Content-Type: application/json" -d "{\"ids\":[\"3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42\"],\"estado\":\"INACTIVO\"}"

# 15. Correlación válida: el UUID v4 enviado vuelve igual en la cabecera X-Correlation-ID y en correlation_id
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: c19b472d-7a2a-4d3b-a12b-abcdef123456"

# 16. Correlación inválida o ausente: el GW genera un UUID v4 nuevo y no rechaza la solicitud
curl -i http://localhost:8080/api/v1/inexistente -H 'X-Correlation-ID: no-es-uuid'
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente -H "X-Correlation-ID: no-es-uuid"

# 17. Health del GW en el puerto de gestión
curl http://localhost:9090/actuator/health
# [Windows]: curl.exe http://localhost:9090/actuator/health

# 18. Configuración inválida (timeout o JWT_PUBLIC_KEY ausente/inválida): el GW no arranca y el log indica la causa
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms"

# 19. Construir la imagen Docker
docker build -t campusseguro/api-gateway:local .

# 20. Ejecutar la imagen (perfil docker; toma JWT_PUBLIC_KEY de la terminal del paso 4; un destino inalcanzable responde 502)
docker run --rm -p 8080:8080 -e JWT_PUBLIC_KEY campusseguro/api-gateway:local
```

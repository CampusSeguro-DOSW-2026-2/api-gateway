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

# 3. Levantar el GW en local (8080; Actuator en 9090; MS01 esperado en localhost:8081)
mvn spring-boot:run -Dspring-boot.run.profiles=local
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local"

# --- En otra terminal o en Postman manual ---

# 4. Ruta sin destino: 404 NO_ENCONTRADO sin llamar a ningún MS
curl -i http://localhost:8080/api/v1/inexistente
# [Windows]: curl.exe -i http://localhost:8080/api/v1/inexistente

# 5. Ruta de MS01 con MS01 apagado: 502 SERVICIO_NO_DISPONIBLE
curl -i -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"correo":"maria.perez@escuelaing.edu.co","contrasena":"Campus#2026"}'
# [Windows CMD/PowerShell]: curl.exe -i -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d "{\"correo\":\"maria.perez@escuelaing.edu.co\",\"contrasena\":\"Campus#2026\"}"

# 6. Con MS01 encendido en 8081, la misma solicitud llega a MS01 sin cambios (repetir el paso 5)

# 7. Health del GW en el puerto de gestión
curl http://localhost:9090/actuator/health
# [Windows]: curl.exe http://localhost:9090/actuator/health

# 8. Configuración inválida: el GW no arranca y el log indica la causa
mvn spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms
# [Windows PowerShell]: mvn spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.arguments=--gateway.downstream-timeout=0ms"

# 9. Construir la imagen Docker
docker build -t campusseguro/api-gateway:local .

# 10. Ejecutar la imagen (perfil docker; un destino inalcanzable responde 502)
docker run --rm -p 8080:8080 campusseguro/api-gateway:local
```
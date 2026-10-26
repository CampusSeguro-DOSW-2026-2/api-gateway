package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

/** Política de acceso de una ruta (Estándares §2, GW-C §3). */
public enum AccessPolicy {

    /** Sin credencial. */
    PUBLICA,
    /** Solo microservicios con clave de servicio (RF-GW-08). */
    INTERNA,
    /** JWT válido (o clave de servicio, RF-GW-08). Es la política por defecto. */
    PROTEGIDA
}

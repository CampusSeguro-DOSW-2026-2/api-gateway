package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

/**
 * Microservicio que llama a través del GW, identificado por su clave de servicio (RF-GW-08). Queda en
 * el atributo {@link #ATTRIBUTE} del exchange para inyectar {@code X-Service-Name} y registrar el
 * {@code origen} del log de acceso.
 */
public record CallingService(String name) {

    public static final String ATTRIBUTE = CallingService.class.getName();
}

package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import org.springframework.http.HttpStatus;

/** Errores que genera el propio GW (GW-C §4). Los mensajes son genéricos y no exponen detalles internos. */
public enum GatewayError {

    NOT_FOUND(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Ruta no encontrada."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_INTERNO", "Error interno del servidor."),
    SERVICE_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "SERVICIO_NO_DISPONIBLE", "Servicio temporalmente no disponible."),
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "TIEMPO_AGOTADO", "El servicio de destino no respondió a tiempo.");

    private final HttpStatus status;
    private final String codigo;
    private final String mensaje;

    GatewayError(HttpStatus status, String codigo, String mensaje) {
        this.status = status;
        this.codigo = codigo;
        this.mensaje = mensaje;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public String mensaje() {
        return mensaje;
    }
}

package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

/**
 * Corta la solicitud con un error del catálogo del GW. Los filtros la lanzan (p. ej. JWT inválido →
 * {@link GatewayError#UNAUTHORIZED}) y el manejador global la convierte en {@link ErrorResponse}.
 */
public class GatewayException extends RuntimeException {

    private final GatewayError error;

    public GatewayException(GatewayError error) {
        super(error.codigo());
        this.error = error;
    }

    public GatewayError error() {
        return error;
    }
}

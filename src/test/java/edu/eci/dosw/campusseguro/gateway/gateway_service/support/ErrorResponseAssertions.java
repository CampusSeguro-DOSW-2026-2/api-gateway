package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

/** Verifica el contrato `ErrorResponse` de los Estándares §7.1 sobre un cuerpo JSON. */
public final class ErrorResponseAssertions {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> INTERNAL_DETAILS =
            List.of("localhost", "127.0.0.1", "Exception", "java.", "ms01-identity-and-access", ".invalid", "trace");

    private ErrorResponseAssertions() {
    }

    @SuppressWarnings("unchecked")
    public static void assertGatewayError(String body, int status, String codigo, String mensaje) {
        Map<String, Object> json = JSON.readValue(body, Map.class);
        assertThat(json).containsOnlyKeys("status", "codigo", "mensaje", "errores", "correlation_id");
        assertThat(json).containsEntry("status", status)
                .containsEntry("codigo", codigo)
                .containsEntry("mensaje", mensaje)
                .containsEntry("errores", List.of());
        assertThat(UUID.fromString((String) json.get("correlation_id")).version()).isEqualTo(4);
        assertThat(body).doesNotContain(INTERNAL_DETAILS);
    }
}

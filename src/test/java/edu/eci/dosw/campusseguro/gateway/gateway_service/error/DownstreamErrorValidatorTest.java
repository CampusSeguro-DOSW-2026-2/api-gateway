package edu.eci.dosw.campusseguro.gateway.gateway_service.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class DownstreamErrorValidatorTest {

    private static final String CORRELATION_ID = "c19b472d-7a2a-4d3b-a12b-abcdef123456";

    private final DownstreamErrorValidator validator = new DownstreamErrorValidator(JsonMapper.builder().build());

    private static String error(int status, String correlationId) {
        return "{\"status\":" + status + ",\"codigo\":\"CONFLICTO\",\"mensaje\":\"El correo ya está registrado.\","
                + "\"errores\":[],\"correlation_id\":\"" + correlationId + "\"}";
    }

    private java.util.Optional<String> violation(int status, String body) {
        return validator.violation(status, body.getBytes(StandardCharsets.UTF_8), CORRELATION_ID);
    }

    @Test
    void shouldAcceptConformingError() {
        assertThat(violation(409, error(409, CORRELATION_ID))).isEmpty();
        assertThat(violation(400, "{\"status\":400,\"codigo\":\"VALIDACION\",\"mensaje\":\"Datos inválidos.\","
                + "\"errores\":[{\"campo\":\"correo\",\"mensaje\":\"Inválido.\"}],\"correlation_id\":\"" + CORRELATION_ID + "\"}"))
                .isEmpty();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
        "<html>error</html>                                    | cuerpo no JSON",
        "''                                                    | cuerpo no es un objeto JSON",
        "[1,2]                                                 | cuerpo no es un objeto JSON",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[]} | campo faltante: correlation_id",
        "{\"timestamp\":\"2026-10-25T10:00:00Z\",\"status\":409,\"error\":\"Conflict\",\"path\":\"/api/v1/auth/register\"} | campo faltante: codigo",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"c19b472d-7a2a-4d3b-a12b-abcdef123456\",\"timestamp\":\"t\"} | campo adicional: timestamp",
        "{\"status\":400,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"c19b472d-7a2a-4d3b-a12b-abcdef123456\"} | status distinto del código HTTP",
        "{\"status\":\"409\",\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"c19b472d-7a2a-4d3b-a12b-abcdef123456\"} | status distinto del código HTTP",
        "{\"status\":409,\"codigo\":null,\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"c19b472d-7a2a-4d3b-a12b-abcdef123456\"} | codigo o mensaje no son texto",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":null,\"correlation_id\":\"c19b472d-7a2a-4d3b-a12b-abcdef123456\"} | errores no es una lista",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":\"11111111-1111-4111-8111-111111111111\"} | correlation_id distinto del de la solicitud",
        "{\"status\":409,\"codigo\":\"CONFLICTO\",\"mensaje\":\"x\",\"errores\":[],\"correlation_id\":null} | correlation_id distinto del de la solicitud"
    })
    void shouldRejectNonConformingErrorWithItsReason(String body, String reason) {
        assertThat(violation(409, body)).contains(reason);
    }

    @Test
    void shouldNotIncludeTheBodyInTheReason() {
        assertThat(violation(409, "{\"secreto\":\"jdbc:postgresql://db:5432\"}").orElseThrow())
                .doesNotContain("jdbc", "secreto");
    }
}

package edu.eci.dosw.campusseguro.gateway.gateway_service.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.json.JsonMapper;

class GatewayJsonLogFormatterTest {

    private final GatewayJsonLogFormatter formatter = new GatewayJsonLogFormatter();
    private final ch.qos.logback.classic.Logger logger = new LoggerContext().getLogger("prueba");

    @SuppressWarnings("unchecked")
    private Map<String, Object> format(LoggingEvent event) {
        String line = formatter.format(event);
        assertThat(line).endsWith("\n").doesNotContain("\n{");
        return JsonMapper.builder().build().readValue(line, Map.class);
    }

    @Test
    void shouldWriteOneJsonLineWithTheStandardFieldNamesAndTheKeyValuePairsAtTopLevel() {
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.WARN, "Solicitud {}", null, new Object[] {"procesada"});
        event.addKeyValuePair(new KeyValuePair("correlation_id", "c19b472d-7a2a-4d3b-a12b-abcdef123456"));
        event.addKeyValuePair(new KeyValuePair("status", 404));

        Map<String, Object> json = format(event);

        assertThat(json).containsKeys("timestamp", "logger")
                .containsEntry("level", "WARN")
                .containsEntry("mensaje", "Solicitud procesada")
                .containsEntry("correlation_id", "c19b472d-7a2a-4d3b-a12b-abcdef123456")
                .containsEntry("status", 404)
                .doesNotContainKey("stack_trace");
    }

    @Test
    void shouldIncludeTheStackTraceOnlyWhenThereIsAThrowable() {
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.ERROR, "falla", new IllegalStateException("x"), null);

        assertThat((String) format(event).get("stack_trace")).startsWith("java.lang.IllegalStateException: x");
    }
}

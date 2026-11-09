package edu.eci.dosw.campusseguro.gateway.gateway_service.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLogFormatter;

/**
 * Una línea JSON por evento con los nombres de campo de los Estándares §8: {@code timestamp},
 * {@code level}, {@code mensaje} y, como campos de primer nivel, los pares clave-valor del evento
 * (p. ej. los del log de acceso). Se activa con {@code logging.structured.format.console}.
 */
public class GatewayJsonLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

    private static final JsonWriter<ILoggingEvent> WRITER = JsonWriter.<ILoggingEvent>of(members -> {
        members.add("timestamp", event -> event.getInstant().toString());
        members.add("level", event -> event.getLevel().toString());
        members.add("logger", ILoggingEvent::getLoggerName);
        members.add("mensaje", ILoggingEvent::getFormattedMessage);
        members.from(ILoggingEvent::getKeyValuePairs)
                .whenNotEmpty()
                .<String, Object>usingPairs((pairs, field) -> pairs.forEach(pair -> field.accept(pair.key, pair.value)));
        members.add("stack_trace", ILoggingEvent::getThrowableProxy).whenNotNull().as(ThrowableProxyUtil::asString);
    }).withNewLineAtEnd();

    @Override
    public String format(ILoggingEvent event) {
        return WRITER.writeToString(event);
    }
}

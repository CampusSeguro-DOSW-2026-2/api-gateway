package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import edu.eci.dosw.campusseguro.gateway.gateway_service.logging.GatewayJsonLogFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/** Captura los eventos de log en memoria, sin depender del formato de la consola. */
public final class CapturedLogs implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final GatewayJsonLogFormatter FORMATTER = new GatewayJsonLogFormatter();

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLogs(String loggerName) {
        logger = (Logger) LoggerFactory.getLogger(loggerName);
        appender.start();
        logger.addAppender(appender);
    }

    public static CapturedLogs of(Class<?> type) {
        return new CapturedLogs(type.getName());
    }

    public static CapturedLogs all() {
        return new CapturedLogs(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** Cada evento tal como sale a la consola: una línea JSON. */
    public String asJsonLines() {
        return events().stream().map(FORMATTER::format).collect(Collectors.joining());
    }

    /** Los eventos como objetos JSON, para revisar sus campos. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> asJson() {
        return events().stream().map(event -> (Map<String, Object>) JSON.readValue(FORMATTER.format(event), Map.class)).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}

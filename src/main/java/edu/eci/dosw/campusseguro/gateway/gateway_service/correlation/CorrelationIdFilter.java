package edu.eci.dosw.campusseguro.gateway.gateway_service.correlation;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Paso 1 del pipeline (RF-GW-06): asigna el `X-Correlation-ID` de la solicitud antes que cualquier
 * otro filtro, también para las rutas sin destino. Conserva el valor del cliente solo si llega una
 * única cabecera con un UUID v4 válido; si no, genera uno. Nunca rechaza la solicitud.
 *
 * <p>El valor queda en el atributo {@link CorrelationIdProvider#ATTRIBUTE} y en el contexto de
 * Reactor ({@link #CONTEXT_KEY}), se envía al MS como única cabecera y se fija en toda respuesta,
 * aunque el MS devuelva otro (RN-GW-16).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String CONTEXT_KEY = "correlationId";

    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolve(exchange.getRequest());
        exchange.getAttributes().put(CorrelationIdProvider.ATTRIBUTE, correlationId);
        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(HEADER, correlationId);
            return Mono.empty();
        });
        ServerWebExchange forwarded = exchange.mutate()
                .request(request -> request.headers(headers -> headers.set(HEADER, correlationId)))
                .build();
        return chain.filter(forwarded).contextWrite(context -> context.put(CONTEXT_KEY, correlationId));
    }

    private static String resolve(ServerHttpRequest request) {
        List<String> values = request.getHeaders().getValuesAsList(HEADER);
        if (values.size() == 1 && UUID_V4.matcher(values.get(0)).matches()) {
            return values.get(0);
        }
        return UUID.randomUUID().toString();
    }
}

package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import edu.eci.dosw.campusseguro.gateway.gateway_service.correlation.CorrelationIdFilter;
import edu.eci.dosw.campusseguro.gateway.gateway_service.security.IdentityHeadersFilter;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Consulta a MS01 la instantánea de cortes de sesión por la misma conexión (y el mismo mTLS) con la que el
 * gateway le reenvía las peticiones
 */
@Component
public class SessionCutsClient {

    /** Nombre con el que el gateway se identifica ante la ruta interna de MS01 */
    public static final String SERVICE_NAME = "gateway";

    private final WebClient webClient;
    private final SessionCutsProperties properties;

    /**
     * Crea el cliente de la consulta de cortes de sesión
     *
     * @param httpClient cliente de red del gateway, con la configuración de TLS de las rutas
     * @param properties configuración de la consulta
     */
    public SessionCutsClient(HttpClient httpClient, SessionCutsProperties properties) {
        this.webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient)).build();
        this.properties = properties;
    }

    /**
     * Consulta los cortes de sesión vigentes en MS01
     *
     * @return la instantánea recibida; termina con error si MS01 no responde a tiempo, responde un error o
     *         envía un cuerpo que no es una instantánea válida
     */
    public Mono<SessionCutSnapshot> fetch() {
        return webClient.get()
                .uri(properties.url())
                .header(IdentityHeadersFilter.SERVICE_NAME, SERVICE_NAME)
                .header(CorrelationIdFilter.HEADER, UUID.randomUUID().toString())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(SessionCutsResponse.class)
                .timeout(properties.timeout())
                .map(this::toSnapshot);
    }

    /**
     * Convierte la respuesta de MS01 en la instantánea que usa el gateway
     *
     * @param response respuesta recibida
     * @return la instantánea con los cortes por usuario y por rol
     * @throws IllegalArgumentException si falta alguna de las dos listas o algún corte no tiene identificador o
     *         fecha válidos
     */
    private SessionCutSnapshot toSnapshot(SessionCutsResponse response) {
        List<SessionCutsResponse.UserCut> userCuts = response.usuarios();
        List<SessionCutsResponse.RoleCut> roleCuts = response.roles();
        if (userCuts == null || roleCuts == null) {
            throw new IllegalArgumentException("La respuesta no trae las listas de usuarios y roles");
        }
        Map<String, Instant> users = new HashMap<>();
        for (SessionCutsResponse.UserCut cut : userCuts) {
            if (cut == null || cut.id() == null || cut.validFrom() == null) {
                throw new IllegalArgumentException("Hay un corte de usuario incompleto");
            }
            users.put(cut.id().toLowerCase(Locale.ROOT), Instant.parse(cut.validFrom()));
        }
        Map<String, Instant> roles = new HashMap<>();
        for (SessionCutsResponse.RoleCut cut : roleCuts) {
            if (cut == null || cut.rol() == null || cut.validFrom() == null) {
                throw new IllegalArgumentException("Hay un corte de rol incompleto");
            }
            roles.put(cut.rol(), Instant.parse(cut.validFrom()));
        }
        return new SessionCutSnapshot(Map.copyOf(users), Map.copyOf(roles));
    }
}

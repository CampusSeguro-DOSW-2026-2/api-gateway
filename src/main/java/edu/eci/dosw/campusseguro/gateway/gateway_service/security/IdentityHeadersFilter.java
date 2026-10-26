package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Paso 5 del pipeline (RF-GW-03): construye las cabeceras técnicas hacia el MS.
 * <ol>
 *   <li>Elimina, sin distinguir mayúsculas/minúsculas, toda identidad, credencial y cabecera
 *       hop-by-hop que envió el cliente, también en rutas públicas (RN-GW-10, RN-GW-11).</li>
 *   <li>Inyecta una sola {@value #USER_ID} y una sola {@value #USER_ROLE} desde el
 *       {@link AuthenticatedUser} que dejó el control de acceso. Sin él (ruta pública), no hay
 *       identidad.</li>
 * </ol>
 * La identidad nunca se toma de la solicitud: solo del atributo, que llena quien validó la
 * credencial (hoy el JWT; con RF-GW-08, también la clave de servicio).
 */
@Component
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String SERVICE_NAME = "X-Service-Name";
    public static final String SERVICE_KEY = "X-Service-Key";

    /** Lo que el cliente nunca puede hacer llegar al MS: identidad, credenciales y hop-by-hop. */
    static final List<String> REMOVED_HEADERS = List.of(
            USER_ID, USER_ROLE, SERVICE_NAME, HttpHeaders.AUTHORIZATION, SERVICE_KEY,
            HttpHeaders.CONNECTION, "Keep-Alive", HttpHeaders.PROXY_AUTHENTICATE, HttpHeaders.PROXY_AUTHORIZATION,
            HttpHeaders.TE, HttpHeaders.TRAILER, HttpHeaders.TRANSFER_ENCODING, HttpHeaders.UPGRADE);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        AuthenticatedUser user = exchange.getAttribute(AuthenticatedUser.ATTRIBUTE);
        ServerWebExchange forwarded = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    REMOVED_HEADERS.forEach(headers::remove);
                    if (user != null) {
                        headers.set(USER_ID, user.userId());
                        headers.set(USER_ROLE, String.join(",", user.roles()));
                    }
                }))
                .build();
        return chain.filter(forwarded);
    }

    /** Después del control de acceso (que ya leyó {@code Authorization}) y antes del reenvío. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}

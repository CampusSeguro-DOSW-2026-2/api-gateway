package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Identidad que propaga un MS autenticado con su clave (RF-GW-08): {@code X-User-Id} y
 * {@code X-User-Role} van juntos, una sola vez y con formato válido; si no, {@code 400 VALIDACION}
 * (A3). Sin ninguna de las dos, la llamada no actúa en nombre de un usuario.
 */
final class PropagatedIdentity {

    private PropagatedIdentity() {
    }

    static Optional<AuthenticatedUser> from(HttpHeaders headers) {
        List<String> userIds = headers.getOrEmpty(IdentityHeadersFilter.USER_ID);
        List<String> roles = headers.getOrEmpty(IdentityHeadersFilter.USER_ROLE);
        if (userIds.isEmpty() && roles.isEmpty()) {
            return Optional.empty();
        }
        if (userIds.size() != 1 || roles.size() != 1 || !IdentityFormat.isUserId(userIds.get(0))) {
            throw new GatewayException(GatewayError.VALIDATION);
        }
        List<String> roleList = Arrays.asList(roles.get(0).split(",", -1));
        if (!roleList.stream().allMatch(IdentityFormat::isRole)) {
            throw new GatewayException(GatewayError.VALIDATION);
        }
        return Optional.of(new AuthenticatedUser(userIds.get(0), roleList));
    }
}

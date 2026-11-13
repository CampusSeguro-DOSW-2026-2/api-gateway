package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;

/**
 * Identidad que propaga un MS autenticado con su clave (RF-GW-08): {@code X-User-Id}, {@code X-User-Role} y
 * {@code X-User-Permission} van juntos, una sola vez cada una y con formato válido; si no, {@code 400 VALIDACION}
 * (A3). Sin ninguna de las tres, la llamada no actúa en nombre de un usuario.
 */
final class PropagatedIdentity {

    private PropagatedIdentity() {
    }

    /**
     * Lee la identidad que acompaña a una llamada interna
     *
     * @param headers cabeceras de la llamada
     * @return la identidad propagada, o vacío si la llamada no trae ninguna de las tres cabeceras
     * @throws GatewayException {@link GatewayError#VALIDATION} si falta alguna de las tres, se repite o su formato
     *         es inválido
     */
    static Optional<AuthenticatedUser> from(HttpHeaders headers) {
        List<String> userIds = headers.getOrEmpty(IdentityHeadersFilter.USER_ID);
        List<String> roles = headers.getOrEmpty(IdentityHeadersFilter.USER_ROLE);
        List<String> permissions = headers.getOrEmpty(IdentityHeadersFilter.USER_PERMISSION);
        if (userIds.isEmpty() && roles.isEmpty() && permissions.isEmpty()) {
            return Optional.empty();
        }
        if (userIds.size() != 1 || roles.size() != 1 || permissions.size() != 1
                || !IdentityFormat.isUserId(userIds.get(0))) {
            throw new GatewayException(GatewayError.VALIDATION);
        }
        List<String> roleList = Arrays.asList(roles.get(0).split(",", -1));
        List<String> permissionList = Arrays.asList(permissions.get(0).split(",", -1));
        if (!roleList.stream().allMatch(IdentityFormat::isRole)
                || !permissionList.stream().allMatch(IdentityFormat::isPermission)) {
            throw new GatewayException(GatewayError.VALIDATION);
        }
        return Optional.of(new AuthenticatedUser(userIds.get(0), roleList, permissionList));
    }
}

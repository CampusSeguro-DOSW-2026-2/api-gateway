package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.List;

/**
 * Identidad tomada de un JWT válido o propagada por un MS autenticado, queda en el atributo
 * {@link #ATTRIBUTE} del exchange para pasársela al MS destino (RF-GW-03)
 *
 * @param userId id del usuario ({@code sub})
 * @param roles roles en el orden del token
 * @param permissions permisos en el orden del token
 */
public record AuthenticatedUser(String userId, List<String> roles, List<String> permissions) {

    public static final String ATTRIBUTE = AuthenticatedUser.class.getName();

    public AuthenticatedUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}

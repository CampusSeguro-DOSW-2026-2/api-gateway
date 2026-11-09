package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.List;

/**
 * Identidad tomada de un JWT válido: {@code sub} y {@code roles} en el orden del token. Queda en el
 * atributo {@link #ATTRIBUTE} del exchange para propagarla al MS (RF-GW-03).
 */
public record AuthenticatedUser(String userId, List<String> roles) {

    public static final String ATTRIBUTE = AuthenticatedUser.class.getName();

    public AuthenticatedUser {
        roles = List.copyOf(roles);
    }
}

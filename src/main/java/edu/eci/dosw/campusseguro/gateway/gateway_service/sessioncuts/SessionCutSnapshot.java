package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import java.time.Instant;
import java.util.Map;

/**
 * Cortes de sesión conocidos por el gateway en un momento dado
 *
 * @param users instante de corte por id de usuario, con el id en minúsculas
 * @param roles instante de corte por nombre de rol
 */
public record SessionCutSnapshot(Map<String, Instant> users, Map<String, Instant> roles) {

    /**
     * Keeps immutable copies of the cuts, so the snapshot cannot change after it is published
     *
     * @param users cut instant by user id
     * @param roles cut instant by role name
     * @throws NullPointerException if a map, a key or a value is null
     */
    public SessionCutSnapshot {
        users = Map.copyOf(users);
        roles = Map.copyOf(roles);
    }

    /** Sin ningún corte, el estado con el que arranca el gateway */
    public static final SessionCutSnapshot EMPTY = new SessionCutSnapshot(Map.of(), Map.of());
}

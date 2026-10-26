package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.regex.Pattern;

/** Formato de la identidad que el GW acepta, venga del JWT o de un MS autenticado (Estándares §3 y §4). */
final class IdentityFormat {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private IdentityFormat() {
    }

    static boolean isUserId(String value) {
        return value != null && UUID.matcher(value).matches();
    }

    static boolean isRole(Object value) {
        return value instanceof String text && ROLE.matcher(text).matches();
    }
}

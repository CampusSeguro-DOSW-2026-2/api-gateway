package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.util.regex.Pattern;

/**
 * Formato de la identidad que el GW acepta, venga del JWT o de un MS autenticado (Estándares §3 y §4)
 */
final class IdentityFormat {

    private static final Pattern UUID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");
    private static final Pattern PERMISSION = Pattern.compile("^ms[0-9]{2}:[a-z][a-z0-9_]*:[a-z][a-z0-9_]*$");

    private IdentityFormat() {
    }

    /**
     * Indica si un valor es el id de un usuario (un UUID)
     *
     * @param value valor a revisar
     * @return true si es un UUID en cualquier combinación de mayúsculas y minúsculas
     */
    static boolean isUserId(String value) {
        return value != null && UUID.matcher(value).matches();
    }

    /**
     * Indica si un valor es un nombre de rol con el formato de los Estándares
     *
     * @param value valor a revisar
     * @return true si es un texto de 2 a 30 caracteres con mayúsculas, dígitos y guion bajo, que empieza con una letra
     */
    static boolean isRole(Object value) {
        return value instanceof String text && ROLE.matcher(text).matches();
    }

    /**
     * Indica si un valor es un código de permiso con el formato de los Estándares
     *
     * @param value valor a revisar
     * @return true si es un texto con la forma ms&lt;dos dígitos&gt;:&lt;recurso&gt;:&lt;acción&gt;
     */
    static boolean isPermission(Object value) {
        return value instanceof String text && PERMISSION.matcher(text).matches();
    }
}

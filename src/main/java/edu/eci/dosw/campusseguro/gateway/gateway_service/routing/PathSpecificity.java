package edu.eci.dosw.campusseguro.gateway.gateway_service.routing;

/**
 * Orden de evaluación de un patrón de ruta: cuanto más largo su prefijo literal, antes se evalúa,
 * para que gane el prefijo más específico (RF-GW-01, paso 2).
 */
final class PathSpecificity {

    private PathSpecificity() {
    }

    static int orderOf(String pathPattern) {
        return -literalPrefix(pathPattern).length();
    }

    static String literalPrefix(String pathPattern) {
        int end = pathPattern.length();
        for (char wildcard : new char[] {'*', '?', '{'}) {
            int index = pathPattern.indexOf(wildcard);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String prefix = pathPattern.substring(0, end);
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}

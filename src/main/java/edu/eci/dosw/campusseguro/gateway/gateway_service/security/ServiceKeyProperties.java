package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import jakarta.validation.constraints.AssertTrue;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Claves de servicio por MS ({@code GW_SERVICE_KEY_MS01} … {@code _MS08}, GW-C §5). Solo son
 * obligatorias para los MS que llaman; una vacía se ignora. Las validaciones nunca muestran las
 * claves en el error de arranque.
 */
@Validated
@ConfigurationProperties(prefix = "gateway")
public record ServiceKeyProperties(Map<String, String> serviceKeys) {

    /** Longitud mínima de una clave (Estándares §5). */
    static final int MIN_KEY_LENGTH = 32;

    /** Las claves configuradas (no vacías), por nombre del MS. */
    public Map<String, String> configuredKeys() {
        if (serviceKeys == null) {
            return Map.of();
        }
        return serviceKeys.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @AssertTrue(message = "los nombres de las claves de servicio deben ser ms01…ms08")
    public boolean isServiceNamesValid() {
        return configuredKeys().keySet().stream().allMatch(name -> name.matches("^ms0[1-8]$"));
    }

    @AssertTrue(message = "cada GW_SERVICE_KEY_MSnn debe tener al menos 32 caracteres")
    public boolean isKeysLongEnough() {
        return configuredKeys().values().stream().allMatch(key -> key.length() >= MIN_KEY_LENGTH);
    }

    @AssertTrue(message = "cada MS debe tener su propia GW_SERVICE_KEY_MSnn: hay claves repetidas")
    public boolean isKeysUnique() {
        Set<String> seen = new HashSet<>();
        return configuredKeys().values().stream().allMatch(seen::add);
    }
}

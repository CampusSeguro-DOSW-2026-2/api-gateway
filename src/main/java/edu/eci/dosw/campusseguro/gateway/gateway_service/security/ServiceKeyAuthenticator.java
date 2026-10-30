package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Identifica al MS llamante por su clave de servicio (RF-GW-08). Compara resúmenes SHA-256 con
 * {@link MessageDigest#isEqual} contra todas las claves, sin cortar en la primera coincidencia: el
 * tiempo no depende de la clave recibida ni revela su longitud. Las claves nunca se registran.
 */
@Component
public class ServiceKeyAuthenticator {

    private final Map<String, byte[]> digestsByService;

    public ServiceKeyAuthenticator(ServiceKeyProperties properties) {
        this.digestsByService = properties.configuredKeys().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> sha256(entry.getValue())));
    }

    /** @throws GatewayException {@link GatewayError#UNAUTHORIZED} si la clave no es de ningún MS */
    public CallingService authenticate(String serviceKey) {
        byte[] presented = sha256(serviceKey == null ? "" : serviceKey);
        String caller = null;
        for (Map.Entry<String, byte[]> entry : digestsByService.entrySet()) {
            if (MessageDigest.isEqual(presented, entry.getValue())) {
                caller = entry.getKey();
            }
        }
        if (caller == null) {
            throw new GatewayException(GatewayError.UNAUTHORIZED);
        }
        return new CallingService(caller);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}

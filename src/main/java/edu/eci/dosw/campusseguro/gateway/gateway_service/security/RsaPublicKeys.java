package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Lee la llave pública RSA de `JWT_PUBLIC_KEY` (PEM X.509 o solo su contenido Base64). */
final class RsaPublicKeys {

    /** Tamaño mínimo exigido por los Estándares §1 (RSA 2048). */
    static final int MIN_KEY_BITS = 2048;

    private RsaPublicKeys() {
    }

    /**
     * @throws IllegalArgumentException si no es una llave pública RSA válida de al menos 2048 bits
     */
    static RSAPublicKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("JWT_PUBLIC_KEY es obligatoria");
        }
        String base64 = pem.replace("\\n", "\n")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        RSAPublicKey key;
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException("JWT_PUBLIC_KEY no es una llave pública RSA válida en formato PEM", e);
        }
        if (key.getModulus().bitLength() < MIN_KEY_BITS) {
            throw new IllegalArgumentException("JWT_PUBLIC_KEY debe ser RSA de al menos " + MIN_KEY_BITS + " bits");
        }
        return key;
    }
}

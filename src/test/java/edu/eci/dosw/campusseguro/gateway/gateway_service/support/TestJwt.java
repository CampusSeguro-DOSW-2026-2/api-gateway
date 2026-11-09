package edu.eci.dosw.campusseguro.gateway.gateway_service.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/** Emite JWT como MS01 con un par RSA generado en memoria (nunca se versionan llaves). */
public final class TestJwt {

    public static final String ISSUER = "ms01-identity-and-access";
    public static final String AUDIENCE = "campusseguro";
    public static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    public static final List<String> ROLES = List.of("BRIGADISTA", "ESTUDIANTE");

    private static final KeyPair KEYS = newKeyPair(2048);

    private TestJwt() {
    }

    public static KeyPair newKeyPair(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String publicKeyPem() {
        return pem(KEYS);
    }

    public static String pem(KeyPair keys) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
    }

    /** Claims válidos según los Estándares §3, emitidos en {@code issuedAt} con vigencia de 1 h. */
    public static JWTClaimsSet.Builder validClaims(Instant issuedAt) {
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(USER_ID)
                .claim("roles", ROLES)
                .claim("email", "maria.perez@escuelaing.edu.co")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(Duration.ofHours(1))));
    }

    public static String validToken() {
        return sign(validClaims(Instant.now()).build());
    }

    public static String sign(JWTClaimsSet claims) {
        return sign(claims, KEYS.getPrivate());
    }

    public static String sign(JWTClaimsSet claims, PrivateKey key) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("ms01-2026-01").build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}

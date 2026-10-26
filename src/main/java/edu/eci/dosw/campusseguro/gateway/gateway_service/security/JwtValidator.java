package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Valida localmente y sin estado el JWT de MS01 (RF-GW-02, RN-GW-06): sin llamadas de red, sin
 * introspección ni revocación. Todo rechazo es {@link GatewayError#UNAUTHORIZED}; el motivo va al
 * log en DEBUG y el token nunca se registra (RN-GW-11).
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final RSASSAVerifier verifier;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public JwtValidator(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtValidator(JwtProperties properties, Clock clock) {
        this.verifier = new RSASSAVerifier(RsaPublicKeys.parse(properties.publicKey()));
        this.properties = properties;
        this.clock = clock;
    }

    public AuthenticatedUser validate(String token) {
        SignedJWT jwt = parse(token);
        // El algoritmo se exige antes de verificar la firma: rechaza none, HS256 y cualquier otro.
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw reject("algoritmo distinto de RS256");
        }
        if (!verify(jwt)) {
            throw reject("firma inválida");
        }
        JWTClaimsSet claims = claimsOf(jwt);
        validateIssuerAndAudience(claims);
        validateTimes(claims);
        return new AuthenticatedUser(subject(claims), roles(claims));
    }

    private static SignedJWT parse(String token) {
        try {
            return SignedJWT.parse(token);
        } catch (ParseException e) {
            throw reject("token mal formado");
        }
    }

    private boolean verify(SignedJWT jwt) {
        try {
            return jwt.verify(verifier);
        } catch (JOSEException e) {
            return false;
        }
    }

    private static JWTClaimsSet claimsOf(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw reject("claims mal formados");
        }
    }

    private void validateIssuerAndAudience(JWTClaimsSet claims) {
        if (!properties.issuer().equals(claims.getIssuer())) {
            throw reject("iss incorrecto");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(properties.audience())) {
            throw reject("aud incorrecto");
        }
    }

    private void validateTimes(JWTClaimsSet claims) {
        Instant now = clock.instant();
        Duration skew = properties.clockSkew();
        Instant expiresAt = instantOf(claims.getExpirationTime(), "exp");
        Instant issuedAt = instantOf(claims.getIssueTime(), "iat");
        if (now.isAfter(expiresAt.plus(skew))) {
            throw reject("token expirado");
        }
        if (issuedAt.isAfter(now.plus(skew))) {
            throw reject("iat en el futuro");
        }
        if (claims.getNotBeforeTime() != null && now.plus(skew).isBefore(claims.getNotBeforeTime().toInstant())) {
            throw reject("nbf en el futuro");
        }
        if (Duration.between(issuedAt, expiresAt).compareTo(properties.maxLifetime()) > 0) {
            throw reject("vigencia mayor que la máxima");
        }
    }

    private static Instant instantOf(Date date, String claim) {
        if (date == null) {
            throw reject(claim + " ausente");
        }
        return date.toInstant();
    }

    private static String subject(JWTClaimsSet claims) {
        String subject = claims.getSubject();
        if (!IdentityFormat.isUserId(subject)) {
            throw reject("sub no es UUID");
        }
        return subject;
    }

    private static List<String> roles(JWTClaimsSet claims) {
        if (!(claims.getClaim("roles") instanceof List<?> roles) || roles.isEmpty()) {
            throw reject("roles ausente o vacío");
        }
        if (!roles.stream().allMatch(IdentityFormat::isRole)) {
            throw reject("roles con formato inválido");
        }
        return roles.stream().map(String.class::cast).toList();
    }

    private static GatewayException reject(String reason) {
        log.debug("JWT rechazado: {}", reason);
        return new GatewayException(GatewayError.UNAUTHORIZED);
    }
}

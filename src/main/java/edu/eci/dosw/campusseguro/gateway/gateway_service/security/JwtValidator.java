package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts.SessionCutStore;
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
 * Valida localmente el JWT de MS01 (RF-GW-02): firma, claims y cortes de sesión, sin llamadas de red en la
 * petición ni introspección. Los cortes los consulta aparte {@link SessionCutStore} y aquí solo se comparan
 * en memoria. Todo rechazo es {@link GatewayError#UNAUTHORIZED}; el motivo va al log en DEBUG y el token nunca
 * se registra (RN-GW-11).
 */
@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final RSASSAVerifier verifier;
    private final JwtProperties properties;
    private final Clock clock;
    private final SessionCutStore sessionCuts;

    /**
     * Crea el validador con el reloj del sistema
     *
     * @param properties configuración de la validación del JWT
     * @param sessionCuts cortes de sesión vigentes
     */
    @Autowired
    public JwtValidator(JwtProperties properties, SessionCutStore sessionCuts) {
        this(properties, Clock.systemUTC(), sessionCuts);
    }

    /**
     * Crea el validador con el reloj indicado
     *
     * @param properties configuración de la validación del JWT
     * @param clock reloj con el que se comparan las fechas del token
     * @param sessionCuts cortes de sesión vigentes
     */
    JwtValidator(JwtProperties properties, Clock clock, SessionCutStore sessionCuts) {
        this.verifier = new RSASSAVerifier(RsaPublicKeys.parse(properties.publicKey()));
        this.properties = properties;
        this.clock = clock;
        this.sessionCuts = sessionCuts;
    }

    /**
     * Valida un JWT y entrega la identidad que lleva
     *
     * @param token JWT recibido en el esquema Bearer
     * @return el usuario, sus roles y sus permisos
     * @throws GatewayException {@link GatewayError#UNAUTHORIZED} si el token es inválido, expiró o lo cortó un
     *         cambio de acceso
     */
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
        String subject = subject(claims);
        List<String> roles = roles(claims);
        List<String> permissions = permissions(claims);
        if (sessionCuts.isCut(subject, roles, claims.getIssueTime().toInstant())) {
            throw reject("token anterior a un corte de sesión");
        }
        return new AuthenticatedUser(subject, roles, permissions);
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

    /**
     * Lee los permisos del token y comprueba su formato
     *
     * @param claims claims del token
     * @return los permisos en el orden del token
     * @throws GatewayException {@link GatewayError#UNAUTHORIZED} si faltan, están vacíos o alguno tiene formato
     *         inválido
     */
    private static List<String> permissions(JWTClaimsSet claims) {
        if (!(claims.getClaim("permissions") instanceof List<?> permissions) || permissions.isEmpty()) {
            throw reject("permissions ausente o vacío");
        }
        if (!permissions.stream().allMatch(IdentityFormat::isPermission)) {
            throw reject("permissions con formato inválido");
        }
        return permissions.stream().map(String.class::cast).toList();
    }

    private static GatewayException reject(String reason) {
        log.debug("JWT rechazado: {}", reason);
        return new GatewayException(GatewayError.UNAUTHORIZED);
    }
}

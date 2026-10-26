package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuración de la validación del JWT (GW-C §5). Incompleta o inválida, impide el arranque. */
@Validated
@ConfigurationProperties(prefix = "gateway.jwt")
public record JwtProperties(
        String publicKey,
        @NotBlank(message = "JWT_ISSUER es obligatorio") String issuer,
        @NotBlank(message = "JWT_AUDIENCE es obligatorio") String audience,
        @NotNull(message = "JWT_CLOCK_SKEW_SECONDS es obligatorio") Duration clockSkew,
        @NotNull(message = "JWT_MAX_LIFETIME_SECONDS es obligatorio") Duration maxLifetime) {

    @AssertTrue(message = "JWT_PUBLIC_KEY debe ser una llave pública RSA (PEM) de al menos 2048 bits")
    public boolean isPublicKeyValid() {
        try {
            RsaPublicKeys.parse(publicKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @AssertTrue(message = "JWT_CLOCK_SKEW_SECONDS no puede ser negativo y JWT_MAX_LIFETIME_SECONDS debe ser mayor que 0")
    public boolean isDurationsValid() {
        return clockSkew == null || maxLifetime == null || (!clockSkew.isNegative() && maxLifetime.isPositive());
    }
}

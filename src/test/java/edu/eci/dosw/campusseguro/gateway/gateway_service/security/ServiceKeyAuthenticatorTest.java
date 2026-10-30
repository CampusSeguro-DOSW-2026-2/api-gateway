package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayError;
import edu.eci.dosw.campusseguro.gateway.gateway_service.error.GatewayException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** CA-GW-08-02: la clave identifica al MS; cualquier otra es 401. */
class ServiceKeyAuthenticatorTest {

    private static String keyOf(String service) {
        return "clave-de-servicio-de-" + service + "-con-mas-de-32-caracteres";
    }

    private static final Map<String, String> ALL_KEYS = IntStream.rangeClosed(1, 8)
            .mapToObj(n -> "ms0" + n)
            .collect(Collectors.toMap(service -> service, ServiceKeyAuthenticatorTest::keyOf));

    private final ServiceKeyAuthenticator authenticator = new ServiceKeyAuthenticator(new ServiceKeyProperties(ALL_KEYS));

    @Test
    void shouldIdentifyEachServiceByItsOwnKey() {
        ALL_KEYS.forEach((service, key) -> assertThat(authenticator.authenticate(key)).isEqualTo(new CallingService(service)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"clave-inventada-de-mas-de-32-caracteres-x", "clave-de-servicio-de-ms04-con-mas-de-32-caractere"})
    void shouldRejectAnUnknownKey(String key) {
        assertThatThrownBy(() -> authenticator.authenticate(key))
                .isInstanceOfSatisfying(GatewayException.class,
                        e -> assertThat(e.error()).isEqualTo(GatewayError.UNAUTHORIZED));
    }

    @Test
    void shouldRejectEveryKeyWhenNoServiceIsConfigured() {
        ServiceKeyAuthenticator withoutKeys = new ServiceKeyAuthenticator(new ServiceKeyProperties(null));

        assertThatThrownBy(() -> withoutKeys.authenticate(keyOf("ms04"))).isInstanceOf(GatewayException.class);
    }
}

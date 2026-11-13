package edu.eci.dosw.campusseguro.gateway.gateway_service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Formato de roles y permisos que el gateway acepta (Estándares §3 y §4)
 */
class IdentityFormatTest {

    /**
     * Los nombres de rol con mayúsculas, dígitos y guion bajo, de 2 a 30 caracteres y que empiezan con letra, son
     * válidos
     *
     * @param role nombre de rol válido
     */
    @ParameterizedTest
    @ValueSource(strings = {"AB", "ESTUDIANTE", "ROL_NUEVO_2", "A1", "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234"})
    void shouldAcceptRoleNamesWithTheStoredFormat(String role) {
        assertThat(IdentityFormat.isRole(role)).isTrue();
    }

    /**
     * Los nombres de rol que rompen el formato guardado son inválidos
     *
     * @param role nombre de rol inválido
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "A", "a1", "estudiante", "Estudiante", "1ROL", "_ROL", "ROL-X", "ROL X", "ROL,X",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ12345", "ÁRBOL"})
    void shouldRejectRoleNamesThatBreakTheStoredFormat(String role) {
        assertThat(IdentityFormat.isRole(role)).isFalse();
    }

    /**
     * Un valor que no es texto o es nulo no es un rol
     */
    @Test
    void shouldRejectRolesThatAreNotText() {
        assertThat(IdentityFormat.isRole(null)).isFalse();
        assertThat(IdentityFormat.isRole(7)).isFalse();
        assertThat(IdentityFormat.isRole(List.of("ESTUDIANTE"))).isFalse();
    }

    /**
     * Los códigos de permiso con la forma ms&lt;dos dígitos&gt;:&lt;recurso&gt;:&lt;acción&gt; son válidos
     *
     * @param permission código de permiso válido
     */
    @ParameterizedTest
    @ValueSource(strings = {"ms01:profile:read", "ms01:role:read_own", "ms04:checkin:write", "ms99:a:b",
            "ms08:indicators:read", "ms06:drill:compare_report"})
    void shouldAcceptPermissionsWithTheCatalogFormat(String permission) {
        assertThat(IdentityFormat.isPermission(permission)).isTrue();
    }

    /**
     * Los códigos que no tienen esa forma son inválidos
     *
     * @param permission código de permiso inválido
     */
    @ParameterizedTest
    @ValueSource(strings = {"", "ms1:profile:read", "ms001:profile:read", "MS01:profile:read", "ms01:profile",
            "ms01:profile:read:all", "ms01:Profile:read", "ms01:1profile:read", "ms01:profile:read-all",
            "ms01::read", "ms01:profile:", " ms01:profile:read", "ms01:profile:read ",
            "ms01:profile:read,ms01:role:manage", "ms01:perfil:leer\n"})
    void shouldRejectPermissionsThatBreakTheCatalogFormat(String permission) {
        assertThat(IdentityFormat.isPermission(permission)).isFalse();
    }

    /**
     * Un valor que no es texto o es nulo no es un permiso
     */
    @Test
    void shouldRejectPermissionsThatAreNotText() {
        assertThat(IdentityFormat.isPermission(null)).isFalse();
        assertThat(IdentityFormat.isPermission(7)).isFalse();
        assertThat(IdentityFormat.isPermission(List.of("ms01:profile:read"))).isFalse();
    }

    /**
     * Un id de usuario es válido solo si es un UUID, en minúsculas o mayúsculas
     */
    @Test
    void shouldAcceptOnlyUuidsAsUserIds() {
        assertThat(IdentityFormat.isUserId("3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42")).isTrue();
        assertThat(IdentityFormat.isUserId("3F6C2A9E-8B1D-4C7A-9E2F-1A5B7C9D0E42")).isTrue();
        assertThat(IdentityFormat.isUserId("USR-1034")).isFalse();
        assertThat(IdentityFormat.isUserId("")).isFalse();
        assertThat(IdentityFormat.isUserId(null)).isFalse();
    }
}

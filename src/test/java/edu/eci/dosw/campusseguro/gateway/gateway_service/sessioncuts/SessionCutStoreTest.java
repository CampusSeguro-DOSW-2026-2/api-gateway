package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Decisión de si un token quedó cortado por el corte del usuario o el de alguno de sus roles
 */
class SessionCutStoreTest {

    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final Instant CUT = Instant.parse("2026-11-10T23:05:12Z");

    private final SessionCutStore store = new SessionCutStore();

    /**
     * Sin cortes conocidos ningún token queda cortado
     */
    @Test
    void shouldNotCutAnythingBeforeTheFirstSnapshot() {
        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(3000))).isFalse();
    }

    /**
     * Un token emitido antes del corte del usuario queda cortado
     */
    @Test
    void shouldCutATokenIssuedBeforeTheUserCut() {
        store.replace(new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of()));

        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isTrue();
    }

    /**
     * Un token emitido en el mismo segundo del corte se acepta
     */
    @Test
    void shouldAcceptATokenIssuedAtTheSameSecondAsTheCut() {
        store.replace(new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of()));

        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT)).isFalse();
    }

    /**
     * Un token emitido después del corte se acepta
     */
    @Test
    void shouldAcceptATokenIssuedAfterTheCut() {
        store.replace(new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of()));

        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.plusSeconds(1))).isFalse();
    }

    /**
     * El corte de uno de los roles del token basta para cortarlo
     */
    @Test
    void shouldCutATokenIssuedBeforeTheCutOfOneOfItsRoles() {
        store.replace(new SessionCutSnapshot(Map.of(), Map.of("BRIGADISTA", CUT)));

        assertThat(store.isCut(USER_ID, List.of("BRIGADISTA", "ESTUDIANTE"), CUT.minusSeconds(1))).isTrue();
    }

    /**
     * El corte de un rol que el token no tiene no lo afecta
     */
    @Test
    void shouldIgnoreTheCutOfARoleTheTokenDoesNotHave() {
        store.replace(new SessionCutSnapshot(Map.of(), Map.of("PROFESOR", CUT)));

        assertThat(store.isCut(USER_ID, List.of("BRIGADISTA", "ESTUDIANTE"), CUT.minusSeconds(1))).isFalse();
    }

    /**
     * El corte de otro usuario no afecta al usuario del token
     */
    @Test
    void shouldIgnoreTheCutOfAnotherUser() {
        store.replace(new SessionCutSnapshot(Map.of("11111111-2222-4333-8444-555555555555", CUT), Map.of()));

        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isFalse();
    }

    /**
     * El id del usuario se compara sin distinguir mayúsculas de minúsculas
     */
    @Test
    void shouldMatchTheUserIdIgnoringCase() {
        store.replace(new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of()));

        assertThat(store.isCut(USER_ID.toUpperCase(), List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isTrue();
    }

    /**
     * Una instantánea nueva reemplaza por completo a la anterior
     */
    @Test
    void shouldReplaceThePreviousSnapshotCompletely() {
        store.replace(new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of("BRIGADISTA", CUT)));
        store.replace(new SessionCutSnapshot(Map.of(), Map.of()));

        assertThat(store.isCut(USER_ID, List.of("BRIGADISTA"), CUT.minusSeconds(1))).isFalse();
    }
}

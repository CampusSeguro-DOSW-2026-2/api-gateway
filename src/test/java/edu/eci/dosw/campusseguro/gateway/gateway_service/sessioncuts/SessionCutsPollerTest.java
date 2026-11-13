package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.eci.dosw.campusseguro.gateway.gateway_service.support.CapturedLogs;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Consulta periódica de cortes de sesión: primera consulta inmediata, reemplazo de la instantánea y
 * conservación de la última cuando MS01 falla
 */
class SessionCutsPollerTest {

    private static final String USER_ID = "3f6c2a9e-8b1d-4c7a-9e2f-1a5b7c9d0e42";
    private static final Instant CUT = Instant.parse("2026-11-10T23:05:12Z");
    private static final SessionCutSnapshot WITH_CUT = new SessionCutSnapshot(Map.of(USER_ID, CUT), Map.of());

    private final SessionCutsClient client = mock(SessionCutsClient.class);
    private final SessionCutStore store = new SessionCutStore();
    private CapturedLogs logs;
    private SessionCutsPoller poller;

    /**
     * Crea el consultor con el intervalo indicado y empieza a capturar los logs
     */
    @BeforeEach
    void captureLogs() {
        logs = CapturedLogs.of(SessionCutsPoller.class);
    }

    /**
     * Detiene el consultor y deja de capturar los logs
     */
    @AfterEach
    void stopPoller() {
        if (poller != null) {
            poller.stop();
        }
        logs.close();
    }

    /**
     * Crea el consultor con el intervalo indicado
     *
     * @param interval tiempo entre consultas
     * @return el consultor, sin iniciar
     */
    private SessionCutsPoller pollerEvery(Duration interval) {
        SessionCutsProperties properties = new SessionCutsProperties(URI.create("http://localhost:8081/cortes"),
                interval, Duration.ofSeconds(3));
        poller = new SessionCutsPoller(client, store, properties);
        return poller;
    }

    /**
     * Antes de iniciar no está en marcha, y al iniciar y detener cambia su estado
     */
    @Test
    void shouldReportItsRunningState() {
        when(client.fetch()).thenReturn(Mono.just(SessionCutSnapshot.EMPTY));
        SessionCutsPoller active = pollerEvery(Duration.ofHours(1));

        assertThat(active.isRunning()).isFalse();
        active.start();
        assertThat(active.isRunning()).isTrue();
        active.stop();
        assertThat(active.isRunning()).isFalse();
    }

    /**
     * Detener un consultor que nunca se inició no falla
     */
    @Test
    void shouldStopWithoutHavingStarted() {
        SessionCutsPoller idle = pollerEvery(Duration.ofHours(1));

        idle.stop();

        assertThat(idle.isRunning()).isFalse();
    }

    /**
     * La primera consulta se hace al iniciar, sin esperar el intervalo
     */
    @Test
    void shouldQueryRightAwayOnStartAndKeepTheSnapshot() {
        when(client.fetch()).thenReturn(Mono.just(WITH_CUT));

        pollerEvery(Duration.ofHours(1)).start();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isTrue());
    }

    /**
     * Cada consulta reemplaza por completo la instantánea anterior
     */
    @Test
    void shouldReplaceTheSnapshotOnEveryQuery() {
        AtomicInteger calls = new AtomicInteger();
        when(client.fetch()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.just(WITH_CUT);
            }
            return Mono.just(SessionCutSnapshot.EMPTY);
        });

        pollerEvery(Duration.ofMillis(50)).start();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(calls.get()).isGreaterThan(2);
            assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isFalse();
        });
    }

    /**
     * Si MS01 falla se conservan los últimos cortes, se deja un aviso y el consultor sigue en marcha
     */
    @Test
    void shouldKeepTheLastSnapshotAndWarnWhenMs01Fails() {
        AtomicInteger calls = new AtomicInteger();
        when(client.fetch()).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.just(WITH_CUT);
            }
            return Mono.error(new IllegalStateException("MS01 caído"));
        });

        SessionCutsPoller active = pollerEvery(Duration.ofMillis(50));
        active.start();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(calls.get()).isGreaterThan(3));
        assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isTrue();
        assertThat(active.isRunning()).isTrue();
        assertThat(logs.events()).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo("WARN");
            assertThat(event.getFormattedMessage()).contains("se conservan los últimos").contains("MS01 caído");
        });
    }

    /**
     * Cuando MS01 vuelve a responder, la instantánea nueva reemplaza a la conservada
     */
    @Test
    void shouldRecoverWhenMs01AnswersAgain() {
        AtomicInteger calls = new AtomicInteger();
        when(client.fetch()).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                return Mono.just(WITH_CUT);
            }
            if (call < 4) {
                return Mono.error(new IllegalStateException("MS01 caído"));
            }
            return Mono.just(SessionCutSnapshot.EMPTY);
        });

        pollerEvery(Duration.ofMillis(50)).start();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(store.isCut(USER_ID, List.of("ESTUDIANTE"), CUT.minusSeconds(1))).isFalse());
        assertThat(calls.get()).isGreaterThanOrEqualTo(4);
    }

    /**
     * Una consulta lenta no se solapa con la siguiente
     */
    @Test
    void shouldNotRunTwoQueriesAtTheSameTime() {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();
        when(client.fetch()).thenAnswer(invocation -> Mono.defer(() -> {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            return Mono.just(SessionCutSnapshot.EMPTY).delayElement(Duration.ofMillis(120));
        }).doOnTerminate(() -> {
            running.decrementAndGet();
            finished.incrementAndGet();
        }));

        pollerEvery(Duration.ofMillis(20)).start();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(finished.get()).isGreaterThanOrEqualTo(3));
        assertThat(maxRunning.get()).isEqualTo(1);
    }

    /**
     * Al detenerlo no hace más consultas
     */
    @Test
    void shouldNotQueryAnymoreAfterStopping() throws InterruptedException {
        AtomicInteger calls = new AtomicInteger();
        when(client.fetch()).thenAnswer(invocation -> {
            calls.incrementAndGet();
            return Mono.just(SessionCutSnapshot.EMPTY);
        });
        SessionCutsPoller active = pollerEvery(Duration.ofMillis(30));
        active.start();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(calls.get()).isGreaterThan(2));

        active.stop();
        Thread.sleep(100);
        int callsAfterStopping = calls.get();
        Thread.sleep(200);

        assertThat(calls.get()).isEqualTo(callsAfterStopping);
    }
}

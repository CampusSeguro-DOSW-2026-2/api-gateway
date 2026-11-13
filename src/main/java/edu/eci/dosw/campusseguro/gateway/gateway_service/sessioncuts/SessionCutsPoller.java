package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consulta los cortes de sesión de MS01 al arrancar y luego a intervalos fijos, y deja la última instantánea
 * recibida en {@link SessionCutStore}
 */
@Component
public class SessionCutsPoller implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SessionCutsPoller.class);

    private final SessionCutsClient client;
    private final SessionCutStore store;
    private final SessionCutsProperties properties;
    private volatile Disposable subscription;

    /**
     * Crea el consultor periódico de cortes de sesión
     *
     * @param client cliente que consulta a MS01
     * @param store almacén donde queda la última instantánea
     * @param properties configuración de la consulta
     */
    public SessionCutsPoller(SessionCutsClient client, SessionCutStore store, SessionCutsProperties properties) {
        this.client = client;
        this.store = store;
        this.properties = properties;
    }

    /**
     * Inicia las consultas: la primera de inmediato y las demás cada intervalo configurado
     */
    @Override
    public void start() {
        subscription = Flux.interval(Duration.ZERO, properties.interval())
                .onBackpressureDrop()
                .concatMap(tick -> client.fetch()
                        .doOnNext(store::replace)
                        .onErrorResume(error -> {
                            log.warn("No se pudieron consultar los cortes de sesión, se conservan los últimos: {}",
                                    error.toString());
                            return Mono.empty();
                        }), 1)
                .subscribe();
    }

    /**
     * Detiene las consultas
     */
    @Override
    public void stop() {
        Disposable active = subscription;
        if (active != null) {
            active.dispose();
        }
    }

    /**
     * Indica si las consultas están en marcha
     *
     * @return true si hay una consulta periódica activa
     */
    @Override
    public boolean isRunning() {
        Disposable active = subscription;
        return active != null && !active.isDisposed();
    }
}

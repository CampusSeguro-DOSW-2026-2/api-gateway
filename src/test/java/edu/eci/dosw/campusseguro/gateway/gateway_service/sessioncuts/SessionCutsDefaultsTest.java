package edu.eci.dosw.campusseguro.gateway.gateway_service.sessioncuts;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * RF-MS01-11: los valores por defecto de application.yaml fijan el retraso máximo de un corte de sesión en 7 s
 * (consulta cada 4 s más hasta 3 s de espera)
 */
class SessionCutsDefaultsTest {

    /**
     * Lee el application.yaml del servicio sin variables de entorno que cambien los valores por defecto
     *
     * @return el entorno con las propiedades del archivo
     * @throws IOException si el archivo no se puede leer
     */
    private StandardEnvironment environmentFromApplicationYaml() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("application",
                new ClassPathResource("application.yaml"));
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.forEach(environment.getPropertySources()::addLast);
        environment.setConversionService((ConfigurableConversionService) ApplicationConversionService.getSharedInstance());
        return environment;
    }

    /**
     * Sin configuración el gateway consulta los cortes cada 4 segundos
     *
     * @throws IOException si el archivo no se puede leer
     */
    @Test
    void defaultInterval_isFourSeconds() throws IOException {
        StandardEnvironment environment = environmentFromApplicationYaml();

        assertThat(environment.getProperty("gateway.session-cuts.interval", Duration.class))
                .isEqualTo(Duration.ofSeconds(4));
    }

    /**
     * Sin configuración la consulta espera la respuesta de MS01 como máximo 3 segundos
     *
     * @throws IOException si el archivo no se puede leer
     */
    @Test
    void defaultTimeout_isThreeSeconds() throws IOException {
        StandardEnvironment environment = environmentFromApplicationYaml();

        assertThat(environment.getProperty("gateway.session-cuts.timeout", Duration.class))
                .isEqualTo(Duration.ofSeconds(3));
    }

    /**
     * El retraso máximo de un corte con los valores por defecto es el intervalo más el tiempo de espera: 7 s
     *
     * @throws IOException si el archivo no se puede leer
     */
    @Test
    void defaultMaximumDelay_isSevenSeconds() throws IOException {
        StandardEnvironment environment = environmentFromApplicationYaml();

        Duration interval = environment.getProperty("gateway.session-cuts.interval", Duration.class);
        Duration timeout = environment.getProperty("gateway.session-cuts.timeout", Duration.class);

        assertThat(interval.plus(timeout)).isEqualTo(Duration.ofSeconds(7));
    }
}

package com.mikedev.mutxamelcf.mvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** app.cierre-temporada.permitir-antes-de-inicio: falso por defecto y en producción, verdadero solo en DEV (local). */
class CierreTemporadaConfiguracionTest {

    private static final String PROPIEDAD = "app.cierre-temporada.permitir-antes-de-inicio";

    // Maven ejecuta los tests desde la carpeta del módulo
    private static Properties cargar(String fichero) throws IOException {
        Properties propiedades = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", fichero))) {
            propiedades.load(in);
        }
        return propiedades;
    }

    @Test
    void porDefectoYEnProduccionEstaBloqueado() throws IOException {
        assertThat(cargar("application.properties").getProperty(PROPIEDAD)).isEqualTo("false");
        assertThat(cargar("application-prod.properties").getProperty(PROPIEDAD)).isEqualTo("false");
    }

    @Test
    void enLocalSePermiteParaEnsayarElCierreEnDev() throws IOException {
        assertThat(cargar("application-local.properties").getProperty(PROPIEDAD)).isEqualTo("true");
    }
}

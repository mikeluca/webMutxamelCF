package com.mikedev.mutxamelcf.mvc.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.google.firebase.FirebaseApp;

/** La política de privacidad en castellano, valenciano e inglés: claves nuevas, orden de los apartados y fecha. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PoliticaPrivacidadTest {

    @MockBean
    private FirebaseApp firebaseApp;

    @Autowired
    private MockMvc mockMvc;

    private String pagina(String idioma) throws Exception {
        return mockMvc.perform(get("/politicaPrivacidad").param("lang", idioma)).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static Properties mensajes(String fichero) throws Exception {
        Properties p = new Properties();

        try (InputStream in = new ClassPathResource(fichero).getInputStream()) {
            p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        }

        return p;
    }

    /** El HTML escapa lo que Thymeleaf pinta con th:text (comillas, etc.): se compara con el texto ya escapado. */
    private static String escapado(String texto) {
        // Solo lo que escapa Thymeleaf (las tildes y las comillas latinas salen tal cual)
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    @ParameterizedTest
    @CsvSource({ "es,messages_es.properties,noviembre de 2026", "ca,messages_ca.properties,novembre de 2026",
            "en,messages_en.properties,November 2026" })
    void laPaginaLlevaLasClavesNuevasYLaFecha(String idioma, String fichero, String fecha) throws Exception {

        Properties m = mensajes(fichero);
        String html = pagina(idioma);

        for (String clave : new String[] { "politicaPrivacidad.app.datos.preferencias",
                "politicaPrivacidad.app.datos.diagnostico", "politicaPrivacidad.app.publicacion.titulo",
                "politicaPrivacidad.app.publicacion.primerEquipo", "politicaPrivacidad.app.publicacion.redes",
                "politicaPrivacidad.app.publicacion.oposicion", "politicaPrivacidad.p2", "politicaPrivacidad.p7",
                "politicaPrivacidad.app.intro", "politicaPrivacidad.app.sinPermisos",
                "politicaPrivacidad.app.finalidad", "politicaPrivacidad.app.proveedores" }) {

            String texto = m.getProperty(clave);

            assertThat(texto).as(clave + " en " + fichero).isNotBlank();
            assertThat(html).as(clave + " pintada en " + idioma).contains(escapado(texto));
        }

        // La fecha de la última revisión sale de la clave, no escrita a mano
        assertThat(m.getProperty("politicaPrivacidad.p8.fecha")).isEqualTo(fecha);
        assertThat(html).contains("<strong>" + fecha + "</strong>").doesNotContain("agosto de 2025")
                .doesNotContain("octubre de 2026").doesNotContain("October 2026");
    }

    @ParameterizedTest
    @CsvSource({ "es", "ca", "en" })
    void losApartadosVanEnSuSitio(String idioma) throws Exception {

        String html = pagina(idioma);
        Properties m = mensajes("messages_" + idioma + ".properties");

        int actividad = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.datos.actividad")));
        int preferencias = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.datos.preferencias")));
        int diagnostico = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.datos.diagnostico")));
        int proveedores = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.proveedores")));
        int tituloPublicacion = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.publicacion.titulo")));
        int oposicion = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.publicacion.oposicion")));
        int seguridad = html.indexOf(escapado(m.getProperty("politicaPrivacidad.app.seguridad")));

        assertThat(actividad).isPositive();
        assertThat(preferencias).isGreaterThan(actividad);
        assertThat(diagnostico).isGreaterThan(preferencias);
        assertThat(tituloPublicacion).isGreaterThan(proveedores);
        assertThat(oposicion).isGreaterThan(tituloPublicacion);
        assertThat(seguridad).isGreaterThan(oposicion);

        // Preferencias y diagnóstico son dos puntos de la lista; el título del bloque es un h3 como "eliminar cuenta"
        assertThat(html).contains("<li>" + escapado(m.getProperty("politicaPrivacidad.app.datos.preferencias")))
                .contains("<li>" + escapado(m.getProperty("politicaPrivacidad.app.datos.diagnostico")))
                .contains("<h3>" + escapado(m.getProperty("politicaPrivacidad.app.publicacion.titulo")) + "</h3>");
    }

    @Test
    void ningunaClaveDeLaPoliticaQuedaSinTextoEnNingunIdioma() throws Exception {

        String plantilla = new String(new ClassPathResource("templates/politicaPrivacidad.html").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("#\\{(politicaPrivacidad\\.[A-Za-z0-9_.]+)}").matcher(plantilla);
        Set<String> claves = new HashSet<>();

        while (matcher.find()) {
            claves.add(matcher.group(1));
        }

        assertThat(claves).contains("politicaPrivacidad.app.publicacion.titulo", "politicaPrivacidad.p8.fecha",
                "politicaPrivacidad.app.datos.diagnostico");

        for (String fichero : new String[] { "messages.properties", "messages_es.properties", "messages_ca.properties",
                "messages_en.properties" }) {

            Properties m = mensajes(fichero);

            for (String clave : claves) {
                assertThat(m.getProperty(clave)).as(clave + " en " + fichero).isNotBlank();
            }
        }

        // messages.properties es el castellano
        Properties base = mensajes("messages.properties");
        Properties es = mensajes("messages_es.properties");

        for (String clave : claves) {
            assertThat(base.getProperty(clave)).as("messages.properties igual que es: " + clave)
                    .isEqualTo(es.getProperty(clave));
        }

        assertThat(Locale.getDefault()).isNotNull();
    }
}

package com.mikedev.mutxamelcf.serviceimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

class TextosAvisosTest {

    private static final List<String> IDIOMAS = List.of("es", "ca", "en");

    private static final Pattern MARCADOR = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private final TextosAvisos textos = new TextosAvisos();

    private static Set<String> marcadores(String plantilla) {

        Set<String> marcadores = new TreeSet<>();
        Matcher matcher = MARCADOR.matcher(plantilla);

        while (matcher.find()) {
            marcadores.add(matcher.group(1));
        }

        return marcadores;
    }

    // ---------- Los tres ficheros dicen lo mismo ----------

    @Test
    void lasTresVersionesTienenLasMismasClaves() {
        Set<String> es = new TreeSet<>(textos.claves("es"));

        assertThat(es).isNotEmpty();
        assertThat(new TreeSet<>(textos.claves("ca"))).as("claves de ca").isEqualTo(es);
        assertThat(new TreeSet<>(textos.claves("en"))).as("claves de en").isEqualTo(es);
    }

    @Test
    void lasTresVersionesTienenLosMismosMarcadoresEnCadaClave() {
        for (String clave : textos.claves("es")) {
            Set<String> esperados = marcadores(textos.plantilla("es", clave));

            for (String idioma : List.of("ca", "en")) {
                assertThat(marcadores(textos.plantilla(idioma, clave)))
                        .as("marcadores de '%s' en %s", clave, idioma)
                        .isEqualTo(esperados);
            }
        }
    }

    @Test
    void ningunTextoGeneradoDejaUnMarcadorSinSustituir() {
        for (String idioma : IDIOMAS) {
            for (String clave : textos.claves(idioma)) {

                Map<String, String> parametros = new HashMap<>();
                marcadores(textos.plantilla(idioma, clave)).forEach(m -> parametros.put(m, "valor-" + m));

                String texto = textos.texto(idioma, clave, parametros);

                assertThat(MARCADOR.matcher(texto).find()).as("'%s' en %s: %s", clave, idioma, texto).isFalse();
                parametros.values().forEach(valor -> assertThat(texto).contains(valor));
            }
        }
    }

    @Test
    void ningunaClaveEstaVaciaYLosTitulosNoLlevanSaltosDeLinea() {
        for (String idioma : IDIOMAS) {
            for (String clave : textos.claves(idioma)) {
                String plantilla = textos.plantilla(idioma, clave);

                assertThat(plantilla).as("'%s' en %s", clave, idioma).isNotBlank();

                if (clave.endsWith(".titulo")) {
                    assertThat(plantilla).as("título '%s' en %s", clave, idioma).doesNotContain("\n");
                }
            }
        }
    }

    // ---------- Sustitución ----------

    @Test
    void laSustitucionEsLiteralYLosApostrofosDelValencianoVanTalCual() {
        String texto = textos.texto("ca", "convocatoria.nueva.jugador",
                "equipo", "Cadet A", "rival", "L'Alcúdia C.F.", "fecha", "dissabte 25/10", "hora", "11:00",
                "horaCitacion", "10:15", "lugar", "Camp d'Esports");

        assertThat(texto).isEqualTo("T'han convocat amb Cadet A per al partit contra L'Alcúdia C.F. del dissabte 25/10"
                + " a les 11:00.\n\nCitació: 10:15 a Camp d'Esports.");
    }

    @Test
    void loQueEscribeLaGenteNoSeVuelveAInterpretar() {
        // Un rival con llaves, con "$" o con "\\" no rompe la sustitución ni se sustituye a su vez.
        String texto = textos.texto("es", "convocatoria.quitado.jugador",
                "rival", "{fecha} $1 \\ X", "fecha", "sábado 25/10");

        assertThat(texto).isEqualTo("Ya no estás en la convocatoria del partido contra {fecha} $1 \\ X del sábado 25/10.");
    }

    @Test
    void elBarraNDelFicheroEsUnSaltoDeLinea() {
        assertThat(textos.plantilla("es", "convocatoria.nueva.jugador")).contains("\n\n").doesNotContain("\\n");
        assertThat(textos.texto("en", "directo.alineacion.texto", "once", "1, 2", "suplentes", "3"))
                .isEqualTo("Starting eleven: 1, 2\n\nSubstitutes: 3");
    }

    @Test
    void losTextosSeLeenEnUtf8() {
        assertThat(textos.texto("es", "ducha")).isEqualTo("🚿 Ducha recomendable después del partido");
        assertThat(textos.texto("ca", "convocatoria.anulada.titulo")).isEqualTo("Convocatòria anul·lada");
    }

    @Test
    void unaClaveQueNoExisteEsUnErrorDeProgramacion() {
        assertThatThrownBy(() -> textos.texto("es", "no.existe")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unIdiomaDesconocidoUsaElCastellano() {
        assertThat(textos.texto("fr", "ducha")).isEqualTo(textos.texto("es", "ducha"));
        assertThat(textos.texto(null, "ducha")).isEqualTo(textos.texto("es", "ducha"));
    }

    @Test
    void siFaltaUnaClaveEnOtroIdiomaSeUsaLaDelCastellanoYSeDejaUnWarning() {
        Logger logger = (Logger) LoggerFactory.getLogger(TextosAvisos.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            // Solo para la prueba: se quita la clave del catálogo en inglés.
            TextosAvisos incompleto = new TextosAvisos();
            incompleto.quitarClaveSoloParaPruebas("en", "ducha");

            assertThat(incompleto.texto("en", "ducha")).isEqualTo(incompleto.texto("es", "ducha"));
            assertThat(appender.list).anySatisfy(evento -> {
                assertThat(evento.getLevel()).isEqualTo(Level.WARN);
                assertThat(evento.getFormattedMessage()).contains("ducha").contains("en");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    // ---------- Formatos ----------

    @Test
    void laFechaLlevaElDiaDeLaSemanaEnCadaIdioma() {
        LocalDate sabado = LocalDate.of(2026, 10, 24);

        assertThat(textos.fecha("es", sabado)).isEqualTo("sábado 24/10");
        assertThat(textos.fecha("ca", sabado)).isEqualTo("dissabte 24/10");
        assertThat(textos.fecha("en", sabado)).isEqualTo("Saturday 24/10");
        assertThat(textos.fecha("es", LocalDate.of(2026, 11, 2))).isEqualTo("lunes 2/11");
        assertThat(textos.fecha("es", null)).isEmpty();
    }

    @Test
    void laFechaCompletaEsDiaMesAnioSinCeros() {
        for (String idioma : IDIOMAS) {
            assertThat(textos.fechaCompleta(idioma, LocalDate.of(2027, 6, 10))).isEqualTo("10/6/2027");
            assertThat(textos.fechaCompleta(idioma, LocalDate.of(2027, 11, 2))).isEqualTo("2/11/2027");
        }
    }

    @Test
    void laHoraEsHHmmSinLaH() {
        assertThat(textos.hora(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(textos.hora(LocalTime.of(18, 30))).isEqualTo("18:30");
        assertThat(textos.hora(null)).isEmpty();
    }

    @Test
    void laListaDeNombresUsaLaPalabraDeCadaIdioma() {
        List<String> tres = List.of("Ana", "Luis", "Marta");

        assertThat(textos.lista("es", tres)).isEqualTo("Ana, Luis y Marta");
        assertThat(textos.lista("ca", tres)).isEqualTo("Ana, Luis i Marta");
        assertThat(textos.lista("en", tres)).isEqualTo("Ana, Luis and Marta");
        assertThat(textos.lista("es", List.of("Ana", "Luis"))).isEqualTo("Ana y Luis");
        assertThat(textos.lista("en", List.of("Ana"))).isEqualTo("Ana");
        assertThat(textos.lista("ca", List.of())).isEmpty();
    }

    // ---------- Piezas compuestas ----------

    @Test
    void elMotivoVaComoParrafoAparteSoloSiLoHay() {
        assertThat(textos.conMotivo("es", "Base.", "Examen")).isEqualTo("Base.\n\nMotivo: Examen");
        assertThat(textos.conMotivo("ca", "Base.", " Examen ")).isEqualTo("Base.\n\nMotiu: Examen");
        assertThat(textos.conMotivo("en", "Base.", "Exam")).isEqualTo("Base.\n\nReason: Exam");
        assertThat(textos.conMotivo("es", "Base.", null)).isEqualTo("Base.");
        assertThat(textos.conMotivo("es", "Base.", "  ")).isEqualTo("Base.");
    }

    @Test
    void elMotivoDeUnaCancelacionPierdeEspaciosYPuntosFinales() {
        assertThat(TextosAvisos.sinPuntoFinal("  Lluvia intensa. . ")).isEqualTo("Lluvia intensa");
        assertThat(TextosAvisos.sinPuntoFinal("Campo cerrado...")).isEqualTo("Campo cerrado");
        assertThat(TextosAvisos.sinPuntoFinal("v1.2 del campo")).isEqualTo("v1.2 del campo");
        assertThat(TextosAvisos.sinPuntoFinal(" . ")).isEmpty();
        assertThat(TextosAvisos.sinPuntoFinal(null)).isEmpty();
    }
}
